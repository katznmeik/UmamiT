package com.umamit.core.data

import android.util.Log
import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.model.BrewSession
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerSnapshot
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** [SessionRepository] on Room (05 §3.6, §3.7, §3.10). */
class RoomSessionRepository @Inject constructor(
    private val db: UmamiDatabase,
    private val settings: SettingsRepository,
    private val time: WallClock,
) : SessionRepository {
    private val sessions = db.sessionDao()

    override suspend fun startSession(start: SessionStart) {
        val now = time.nowMs()
        db.withTransaction {
            sessions.insert(start.toEntity(now))
            sessions.insertSteps(start.steps.mapIndexed { position, step -> step.toEntity(start.id, position, now) })
        }
    }

    override suspend fun replacePendingSteps(
        sessionId: String,
        fromIndex: Int,
        steps: List<PlannedStep>,
        preAlertSeconds: Int,
    ) {
        val now = time.nowMs()
        db.withTransaction {
            sessions.deleteStepsFrom(sessionId, fromIndex)
            sessions.insertSteps(steps.mapIndexed { i, step -> step.toEntity(sessionId, fromIndex + i, now) })
            sessions.setPreAlert(sessionId, preAlertSeconds, now)
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
                sessions.updateSteps(listOf(row.recorded(record, now)))
            }
        }
    }

    override suspend fun completeSession(sessionId: String, autoFinished: Boolean) {
        val deductStock = deductStock()
        val now = time.nowMs()
        db.withTransaction {
            val session = sessions.get(sessionId)?.takeIf { it.status == SessionStatus.IN_PROGRESS }
                ?: return@withTransaction
            val rows = sessions.steps(sessionId)
            sessions.deleteUnrecordedSteps(sessionId)
            val endedAt = rows.mapNotNull { it.endedAt }.maxOrNull() ?: now
            val teaId = session.teaId
            val events = teaId?.let { db.inventoryDao().eventsForTea(it).map { e -> e.toModel() } }
            teaId?.let { updateOpenedDate(it, session, events.orEmpty(), now) }
            val leaf = session.leafGrams?.takeIf { it > 0.0 }
            if (deductStock && teaId != null && leaf != null) deductLeaf(teaId, sessionId, leaf, endedAt, now)
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
        }
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

    /** The BREW stock event of a completed session (SET-15, INV-02); at most one per session. */
    private suspend fun deductLeaf(teaId: String, sessionId: String, leafGrams: Double, endedAt: Long, now: Long) {
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
        db.inventoryDao().upsert(brew.toEntity(createdAt = now, updatedAt = now))
    }

    override suspend fun getSession(id: String): BrewSession? = db.withTransaction {
        sessions.get(id)?.toModel(sessions.steps(id))
    }

    /**
     * *Geöffnet am* (INV-06): set to the session's local date when the tea has none yet, or
     * when its latest INITIAL/PURCHASE is newer than the opened date and the stock had run
     * out before that purchase (a new package). [events] are the tea's events before this
     * session's deduction.
     */
    private suspend fun updateOpenedDate(
        teaId: String,
        session: BrewSessionEntity,
        events: List<InventoryEvent>,
        now: Long,
    ) {
        val tea = db.teaDao().get(teaId) ?: return
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
        if (opened == null || newPackage) db.teaDao().setOpenedDate(teaId, session.startedAt.localDate(zone), now)
    }

    private fun Long.localDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    private companion object {
        /** Stock at or below this counts as run out (05 §7: grams are stored to 0.1 g). */
        const val EMPTY_GRAMS = 0.001
    }
}

private const val TAG = "SessionRepository"

private val PACKAGE_TYPES = setOf(InventoryEventType.INITIAL, InventoryEventType.PURCHASE)

/**
 * Leaf × Σ price ÷ Σ grams of the priced INITIAL/PURCHASE events (INV-08), in exact decimals
 * and rounded half up once, so an exact half is never a cent low (D-054); null without leaf
 * or priced stock.
 */
private fun costCents(leafGrams: Double?, events: List<InventoryEvent>?): Long? {
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

/** [ActiveBrewStore] on Room (05 §3.12): the row and the session's step rows in one transaction. */
class RoomActiveBrewStore @Inject constructor(private val db: UmamiDatabase, private val time: WallClock) :
    ActiveBrewStore {
    private val activeBrew = db.activeBrewDao()
    private val sessions = db.sessionDao()

    override suspend fun read(): TimerSnapshot? = db.withTransaction {
        val row = activeBrew.get() ?: return@withTransaction null
        val steps = row.sessionId?.let { sessions.steps(it).map { step -> step.toRow() } }.orEmpty()
        // A cold or ice brew has long steps only; that follows from the session's method (D-053).
        val method = row.sessionId?.let { sessions.get(it)?.method }
        TimerSnapshot(row.toSnapshot(), steps, longStepsOnly = method?.let(BrewTimerEngine::longStepsOnly) ?: false)
    }

    override suspend fun persist(snapshot: TimerSnapshot?) {
        if (snapshot == null) return clear()
        val now = time.nowMs()
        db.withTransaction {
            snapshot.activeBrew.sessionId?.let { syncSteps(it, snapshot, now) }
            val createdAt = activeBrew.get()?.createdAt ?: now
            activeBrew.upsert(snapshot.activeBrew.toEntity(createdAt = createdAt, updatedAt = now))
        }
    }

    override suspend fun clear() = activeBrew.clear()

    /** Makes the session's rows equal [snapshot]'s step rows, keeping row ids, notes and creation times. */
    private suspend fun syncSteps(sessionId: String, snapshot: TimerSnapshot, now: Long) {
        val existing = sessions.steps(sessionId).associateBy { it.position }
        val changed = snapshot.steps.mapNotNull { row ->
            existing[row.position]?.let { old -> old.with(row, now).takeIf { it !== old } }
        }
        sessions.deleteStepsFrom(sessionId, snapshot.steps.size)
        sessions.updateSteps(changed)
        sessions.insertSteps(snapshot.steps.filter { it.position !in existing }.map { it.toEntity(sessionId, now) })
    }
}

/** [DatabaseTransactions] on Room: nested repository transactions join the outer one. */
class RoomDatabaseTransactions @Inject constructor(private val db: UmamiDatabase) : DatabaseTransactions {
    override suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction(block)
}
