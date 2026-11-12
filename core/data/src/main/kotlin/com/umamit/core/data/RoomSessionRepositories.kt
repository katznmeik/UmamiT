package com.umamit.core.data

import android.util.Log
import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.entity.ActiveBrewEntity
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.SessionDescriptorEntity
import com.umamit.core.model.BrewSession
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.model.TastingLimits
import com.umamit.core.model.TimerSlot
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * [SessionRepository] on Room (05 §3.6, §3.7, §3.10). [openedDates] is the app's one memory of the opening
 * dates completions set (SCR-06); a repository built without it keeps its own.
 */
class RoomSessionRepository @Inject constructor(
    private val db: UmamiDatabase,
    private val settings: SettingsRepository,
    private val time: WallClock,
    private val openedDates: OpenedDateChanges = OpenedDateChanges(),
) : SessionRepository {
    private val sessions = db.sessionDao()
    private val tasting = db.tastingDao()

    override suspend fun startSession(start: SessionStart) {
        val now = time.nowMs()
        db.withTransaction {
            sessions.insert(start.toEntity(now))
            sessions.insertSteps(start.steps.mapIndexed { position, step -> step.toEntity(start.id, position, now) })
        }
    }

    override suspend fun replacePendingSteps(sessionId: String, fromIndex: Int, steps: List<PlannedStep>) {
        val now = time.nowMs()
        db.withTransaction {
            sessions.deleteStepsFrom(sessionId, fromIndex)
            sessions.insertSteps(steps.mapIndexed { i, step -> step.toEntity(sessionId, fromIndex + i, now) })
        }
    }

    /**
     * A missing row changes nothing (logged): the call runs inside the controller's transition
     * transaction, and the `Persist` there brings the rows to the engine's plan anyway (D-052, D-054).
     */
    override suspend fun recordStep(sessionId: String, stepIndex: Int, record: StepRecord) {
        val now = time.nowMs()
        db.withTransaction {
            val row = sessions.steps(sessionId).firstOrNull { it.position == stepIndex }
            if (row == null) {
                Log.w(TAG, "session $sessionId has no step $stepIndex to record; left to the snapshot")
            } else {
                sessions.updateSteps(listOf(row.recorded(record, now).timerColumns()))
            }
        }
    }

    /**
     * Completes the session (TMR-12, 05 §3.7). Its step rows stay as they are: from v0.4.0 the
     * never-started ones too, without `actual_seconds` (Q-19, D-100; until v0.3.0 they were
     * deleted here), so *So nochmal brühen* can prefill them. `planned_step_count` is the planned
     * total; `ended_at` comes from the ended rows only, and the cost from the leaf.
     *
     * An opened date it sets is remembered for the „Gespeichert" sheet, in memory only; a completion that
     * sets none forgets what an earlier attempt for the session remembered. The transaction reads and writes
     * what it did in v0.4.0 (SCR-06, D-104). In production this runs inside the controller's outer
     * transaction (`TransitionEffects.persist`), so the memory is written before that transaction commits;
     * a rollback leaves a stale entry, which is harmless: [openedDateChange] checks it against the rows.
     */
    override suspend fun completeSession(sessionId: String, autoFinished: Boolean) {
        val deductStock = deductStock()
        val now = time.nowMs()
        val completion = db.withTransaction {
            val session = sessions.get(sessionId)?.takeIf { it.status == SessionStatus.IN_PROGRESS }
                ?: return@withTransaction null
            val rows = sessions.steps(sessionId)
            val endedAt = rows.mapNotNull { it.endedAt }.maxOrNull() ?: now
            val teaId = session.teaId
            val events = teaId?.let { db.inventoryDao().eventsForTea(it).map { e -> e.toModel() } }
            val opened = teaId?.let { db.updateOpenedDate(it, session, events.orEmpty(), now) }
            val leaf = session.leafGrams?.takeIf { it > 0.0 }
            if (deductStock && teaId != null && leaf != null) db.deductLeaf(teaId, sessionId, leaf, endedAt, now)
            sessions.update(
                session.copy(
                    status = SessionStatus.COMPLETED,
                    plannedStepCount = rows.size,
                    costCents = costCents(leaf, events),
                    endedAt = endedAt,
                    autoFinished = autoFinished,
                    updatedAt = now,
                ),
            )
            Completion(opened)
        }
        when {
            completion == null -> Unit
            completion.opened == null -> openedDates.forget(sessionId)
            else -> openedDates.record(completion.opened)
        }
    }

    /** What a completion that ran did to the opened date: [opened] null when it set none. */
    private class Completion(val opened: OpenedDateChange?)

    /**
     * The remembered change, checked against the rows: an outer transaction that rolled the completion
     * back, or a date changed since, leaves nothing to undo.
     */
    override suspend fun openedDateChange(sessionId: String): OpenedDateChange? {
        val change = openedDates.of(sessionId) ?: return null
        return db.withTransaction {
            change.takeIf {
                sessions.get(sessionId)?.status == SessionStatus.COMPLETED &&
                    db.teaDao().get(change.teaId)?.openedDate == change.openedDate
            }
        }
    }

    override suspend fun undoOpenedDate(change: OpenedDateChange) {
        val now = time.nowMs()
        db.withTransaction {
            if (db.teaDao().get(change.teaId)?.openedDate == change.openedDate) {
                db.teaDao().setOpenedDate(change.teaId, change.previous, now)
            }
        }
        openedDates.forget(change.sessionId)
    }

    override suspend fun discardSession(sessionId: String) = sessions.delete(sessionId)

    /**
     * *Bestand abziehen* (SET-15), read at completion. The read runs inside the controller's
     * transition transaction, so an unreadable settings file must not throw there and roll back
     * its `Persist` (rule 8, D-054): it counts as the default.
     */
    private suspend fun deductStock(): Boolean = settings.settings
        .catch { e -> if (e is IOException) emit(UserSettings()) else throw e }
        .first()
        .deductStock

    override suspend fun getSession(id: String): BrewSession? = db.withTransaction {
        sessions.get(id)?.toModel(sessions.steps(id), tasting.descriptors(id))
    }

    override suspend fun inProgressSessionIds(): List<String> = sessions.inProgressIds()

    override fun observeRecentSessions(limit: Int): Flow<List<BrewSession>> = sessions.observeFinished().map { rows ->
        // The rows are small; only the shown ones load their steps and descriptors.
        db.withTransaction {
            rows.take(limit).map { it.toModel(sessions.steps(it.id), tasting.descriptors(it.id)) }
        }
    }
}

/**
 * [SessionTasting] on Room (05 §3.7 „Tasting writes", „Infusion tasting writes", §3.8): each write one transaction.
 * The session's inputs on a `COMPLETED` session only, setting its `updated_at`; an infusion's taste and note on its
 * ended, non-*Spülen* step row of a session in progress or completed, column by column. Stock, cost and `active_brew`
 * are never read or written here.
 */
class RoomSessionTasting @Inject constructor(private val db: UmamiDatabase, private val time: WallClock) :
    SessionTasting {
    private val tasting = db.tastingDao()

    override suspend fun setRating(sessionId: String, rating: Double?): TastingWrite {
        if (rating != null && !TastingLimits.isValidRating(rating)) return TastingWrite.INVALID
        return tasting.setRating(sessionId, rating, time.nowMs()).written()
    }

    override suspend fun setInfusionTaste(
        sessionId: String,
        position: Int,
        axis: TasteAxis,
        value: Int?,
    ): TastingWrite {
        if (value != null && !TastingLimits.isValidTaste(value)) return TastingWrite.INVALID
        return infusionWrite(sessionId, position) { tasting.setInfusionTaste(sessionId, position, axis.name, value) }
    }

    override suspend fun clearInfusionTaste(sessionId: String, position: Int): TastingWrite =
        infusionWrite(sessionId, position) { tasting.clearInfusionTaste(sessionId, position) }

    override suspend fun setInfusionNote(sessionId: String, position: Int, note: String?): TastingWrite {
        val trimmed = note?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed != null && trimmed.length > TastingLimits.MAX_INFUSION_NOTE_LENGTH) return TastingWrite.INVALID
        return infusionWrite(sessionId, position) { tasting.setInfusionNote(sessionId, position, trimmed) }
    }

    /**
     * One infusion write in its own transaction (05 §3.7 „Infusion tasting writes"): [write] runs only when the
     * session is in progress or completed and its row at [position] is ended and not *Spülen*; a `COMPLETED`
     * session's `updated_at` follows. An in-progress session's row and `active_brew` are never touched.
     */
    private suspend fun infusionWrite(sessionId: String, position: Int, write: suspend () -> Int): TastingWrite {
        val now = time.nowMs()
        return db.withTransaction {
            val target = tasting.infusionTarget(sessionId, position)
            when {
                target == null || target.status !in INFUSION_STATUSES -> TastingWrite.NOT_COMPLETED

                target.kind == null || target.kind == StepKind.RINSE || target.ended != true ->
                    TastingWrite.NOT_AN_INFUSION

                write() == 0 -> TastingWrite.NOT_AN_INFUSION

                else -> {
                    if (target.status == SessionStatus.COMPLETED) tasting.touch(sessionId, now)
                    TastingWrite.SAVED
                }
            }
        }
    }

    override suspend fun setNotes(sessionId: String, notes: String?): TastingWrite {
        val trimmed = notes?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed != null && trimmed.length > TastingLimits.MAX_NOTES_LENGTH) return TastingWrite.INVALID
        return tasting.setNotes(sessionId, trimmed, time.nowMs()).written()
    }

    override suspend fun setDescriptor(sessionId: String, key: String, ticked: Boolean): TastingWrite {
        // A tick needs a key the app knows; unticking any stored key stays allowed (unknown keys stay stored).
        if (key.isBlank() || (ticked && TasteDescriptor.fromKey(key) == null)) return TastingWrite.INVALID
        val now = time.nowMs()
        return db.withTransaction {
            when {
                !tasting.isCompleted(sessionId) -> TastingWrite.NOT_COMPLETED

                !ticked -> {
                    tasting.deleteDescriptor(sessionId, key)
                    tasting.touch(sessionId, now).written()
                }

                tasting.hasDescriptor(sessionId, key) -> tasting.touch(sessionId, now).written()

                tasting.descriptorCount(sessionId) >= TastingLimits.MAX_DESCRIPTORS -> TastingWrite.DESCRIPTOR_LIMIT

                else -> {
                    tasting.insertDescriptor(SessionDescriptorEntity(sessionId, key, createdAt = now))
                    tasting.touch(sessionId, now).written()
                }
            }
        }
    }

    /** A single-row UPDATE's count as the write's outcome: 0 rows means no completed session of that id. */
    private fun Int.written(): TastingWrite = if (this > 0) TastingWrite.SAVED else TastingWrite.NOT_COMPLETED
}

private const val TAG = "SessionRepository"

/** The sessions whose infusions take tasting writes (05 §3.7, D-114): one running and one completed. */
private val INFUSION_STATUSES = setOf(SessionStatus.IN_PROGRESS, SessionStatus.COMPLETED)

/** Stock at or below this counts as run out (05 §7: grams are stored to 0.1 g). */
private const val EMPTY_GRAMS = 0.001

/** The BREW stock event of a completed session (SET-15, INV-02); at most one per session. */
private suspend fun UmamiDatabase.deductLeaf(
    teaId: String,
    sessionId: String,
    leafGrams: Double,
    endedAt: Long,
    now: Long,
) {
    val brew = InventoryEvent(
        id = UUID.randomUUID().toString(),
        teaId = teaId,
        type = InventoryEventType.BREW,
        deltaGrams = -leafGrams,
        occurredAtMillis = endedAt,
        sessionId = sessionId,
        priceCents = null,
        currency = null,
    )
    inventoryDao().upsert(brew.toEntity(createdAt = now, updatedAt = now))
}

/**
 * *Geöffnet am* (INV-06): set to the session's local date when the tea has none yet, or
 * when its latest INITIAL/PURCHASE is newer than the opened date and the stock had run
 * out before that purchase (a new package). [events] are the tea's events before this
 * session's deduction. Returns the change when the date it set is a new one (SCR-06).
 */
private suspend fun UmamiDatabase.updateOpenedDate(
    teaId: String,
    session: BrewSessionEntity,
    events: List<InventoryEvent>,
    now: Long,
): OpenedDateChange? {
    val tea = teaDao().get(teaId) ?: return null
    val zone = zoneOf(session.zoneId)
    val opened = tea.openedDate
    val newPackage = opened != null && events
        .filter { it.type in PACKAGE_TYPES }
        .maxByOrNull { it.occurredAtMillis }
        ?.let { purchase ->
            val stockBefore = events.filter { it.occurredAtMillis < purchase.occurredAtMillis }
                .sumOf { it.deltaGrams }
            purchase.occurredAtMillis.localDate(zone) > opened && stockBefore <= EMPTY_GRAMS
        } == true
    val date = session.startedAt.localDate(zone)
    return if (opened == null || newPackage) {
        teaDao().setOpenedDate(teaId, date, now)
        OpenedDateChange(session.id, teaId, previous = opened, openedDate = date).takeIf { date != opened }
    } else {
        null
    }
}

/** The local date of this wall time in [zone]. */
private fun Long.localDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

private val PACKAGE_TYPES = setOf(InventoryEventType.INITIAL, InventoryEventType.PURCHASE)

/**
 * Leaf × Σ price ÷ Σ grams of the priced INITIAL/PURCHASE events (INV-08), in exact decimals
 * and rounded half up once, so an exact half is never a cent low (D-054); null without leaf
 * or priced stock. The session editor's leaf change uses it too (05 §3.7 „Edit").
 */
internal fun costCents(leafGrams: Double?, events: List<InventoryEvent>?): Long? {
    val priced = events.orEmpty().filter { it.type in PACKAGE_TYPES && it.priceCents != null }
    val grams = priced.fold(BigDecimal.ZERO) { sum, event -> sum + event.deltaGrams.toBigDecimal() }
    if (leafGrams == null || grams.signum() <= 0) return null
    val price = priced.sumOf { checkNotNull(it.priceCents) }.toBigDecimal()
    return (leafGrams.toBigDecimal() * price).divide(grams, 0, RoundingMode.HALF_UP).toLong()
}

/** The session's zone; one this device's time-zone data doesn't know counts as the device's (D-054). */
private fun zoneOf(id: String): ZoneId = try {
    ZoneId.of(id)
} catch (e: DateTimeException) {
    Log.w(TAG, "unknown time zone $id; using the device's", e)
    ZoneId.systemDefault()
}

/**
 * [ActiveBrewStore] on Room (05 §3.12): a slot's row and, for a brew session, its step rows in one
 * transaction.
 */
class RoomActiveBrewStore @Inject constructor(private val db: UmamiDatabase, private val time: WallClock) :
    ActiveBrewStore {
    private val activeBrew = db.activeBrewDao()
    private val sessions = db.sessionDao()

    override suspend fun read(slot: TimerSlot): TimerSnapshot? = db.withTransaction {
        activeBrew.get(slot)?.let { snapshotOf(it) }
    }

    override suspend fun persist(slot: TimerSlot, snapshot: TimerSnapshot?) {
        if (snapshot == null) return clear(slot)
        require(snapshot.activeBrew.fitsIn(slot)) { "a ${snapshot.activeBrew.slot} snapshot is not stored as $slot" }
        val now = time.nowMs()
        db.withTransaction { write(slot, snapshot, now) }
    }

    override suspend fun clear(slot: TimerSlot) = activeBrew.delete(slot)

    /** The snapshot of [row] with its session's step rows; called inside a transaction. */
    private suspend fun snapshotOf(row: ActiveBrewEntity): TimerSnapshot {
        val steps = row.sessionId?.let { sessions.steps(it).map { step -> step.toRow() } }.orEmpty()
        // A cold or ice brew has long steps only; that follows from the session's method (D-053).
        val method = row.sessionId?.let { sessions.get(it)?.method }
        return TimerSnapshot(
            row.toSnapshot(),
            steps,
            longStepsOnly = method?.let(BrewTimerEngine::longStepsOnly) ?: false,
        )
    }

    /** Writes [snapshot]'s row as [slot]'s and its session's step rows; called inside a transaction. */
    private suspend fun write(slot: TimerSlot, snapshot: TimerSnapshot, now: Long) {
        val row = snapshot.activeBrew
        row.sessionId?.let { syncSteps(it, snapshot, now) }
        val createdAt = activeBrew.get(slot)?.createdAt ?: now
        activeBrew.upsert(row.toEntity(slot = slot, createdAt = createdAt, updatedAt = now))
    }

    /**
     * Makes the session's rows equal [snapshot]'s step rows, keeping row ids, notes and creation times. The infusions'
     * taste and notes are never written here (05 §3.7, D-114): the update leaves their columns out.
     */
    private suspend fun syncSteps(sessionId: String, snapshot: TimerSnapshot, now: Long) {
        val existing = sessions.steps(sessionId).associateBy { it.position }
        val changed = snapshot.steps.mapNotNull { row ->
            existing[row.position]?.let { old -> old.with(row, now).takeIf { it !== old } }
        }
        sessions.deleteStepsFrom(sessionId, snapshot.steps.size)
        sessions.updateSteps(changed.map { it.timerColumns() })
        sessions.insertSteps(snapshot.steps.filter { it.position !in existing }.map { it.toEntity(sessionId, now) })
    }
}

/** [DatabaseTransactions] on Room: nested repository transactions join the outer one. */
class RoomDatabaseTransactions @Inject constructor(private val db: UmamiDatabase) : DatabaseTransactions {
    override suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction(block)
}
