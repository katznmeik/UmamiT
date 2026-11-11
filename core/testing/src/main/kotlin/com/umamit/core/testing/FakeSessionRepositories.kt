package com.umamit.core.testing

import com.umamit.core.common.ClockReading
import com.umamit.core.common.snap5
import com.umamit.core.data.ActiveBrewStore
import com.umamit.core.data.DatabaseTransactions
import com.umamit.core.data.JournalRepository
import com.umamit.core.data.JournalSession
import com.umamit.core.data.OpenedDateChange
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SessionStart
import com.umamit.core.data.SessionTasting
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.TastingWrite
import com.umamit.core.model.BrewSession
import com.umamit.core.model.DescriptorCount
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.SessionStepRow
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockSummary
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TastingLimits
import com.umamit.core.model.TeaRating
import com.umamit.core.model.TeaTasting
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerSnapshot
import com.umamit.core.timer.engine.TimerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToLong

// In-memory fakes of the session repositories and the active brew store (05 §3.6, §3.7,
// §3.12). They share their rows like the database: the store's snapshot writes the
// session's step rows, and discarding a session drops its stock event and the active brew.
// Not modelled: the new-package rule of INV-06 (the fake sets only a missing opened date, and
// reports that for SCR-06's Rückgängig as Room does) and rollbacks (FakeDatabaseTransactions
// runs the block as is).

/** [FakeSessions.startActiveBrew]'s session: its Vorwarnung and its start. */
private const val ACTIVE_PRE_ALERT_SECONDS = 10
private const val ACTIVE_STARTED_AT_MS = 1_000L

/** The session tables of a fake database; completion uses [collection]'s teas and stock and [settings]. */
class FakeSessions(
    internal val collection: FakeCollection = FakeCollection(),
    internal val settings: SettingsRepository = FakeSettingsRepository(),
    internal val nowMs: () -> Long = { 0L },
) {
    internal val sessionRows = MutableStateFlow<Map<String, BrewSession>>(emptyMap())
    internal val activeRows = MutableStateFlow<Map<TimerSlot, TimerSnapshot>>(emptyMap())

    val sessions = FakeSessionRepository(this)
    val activeBrew = FakeActiveBrewStore(this)
    val journal = FakeJournalRepository(this)
    val tasting = FakeSessionTasting(this)
    val transactions: DatabaseTransactions = FakeDatabaseTransactions

    init {
        // As on Room: the recipe of a session in `active_brew` can't be deleted (E15, D-101 F12).
        collection.activeRecipeId = combine(activeRows, sessionRows) { active, rows ->
            active.values.firstNotNullOfOrNull { row ->
                row.activeBrew.sessionId?.let { rows[it] }?.snapshot?.profileId
            }
        }.distinctUntilChanged()
    }

    /** A brew session of [snapshot] (one 60 s infusion) waits in READY in `active_brew`: the running brew (E15). */
    suspend fun startActiveBrew(snapshot: SessionSnapshot, id: String = "active") {
        val steps = listOf(PlannedStep(StepKind.INFUSION, 60, 70))
        val preAlert = PreAlertSettings.inherited(null, ACTIVE_PRE_ALERT_SECONDS)
        val start = TimerEvent.StartSession(id, steps, preAlert, longStepsOnly = false)
        val now = ClockReading(ACTIVE_STARTED_AT_MS, ACTIVE_STARTED_AT_MS, ZoneOffset.UTC, 1)
        val ready = BrewTimerEngine.reduce(TimerState.Idle, start, now).state
        sessions.startSession(
            SessionStart(id, snapshot, steps, ACTIVE_PRE_ALERT_SECONDS, ACTIVE_STARTED_AT_MS, "UTC"),
        )
        activeBrew.persist(TimerSlot.BREW, BrewTimerEngine.snapshot(ready))
    }

    /**
     * Stores [key] as ticked on [sessionId] without the vocabulary check of [FakeSessionTasting], as a database
     * written by a later version holds it (05 §3.8): the key stays stored and counted, though no chip shows it.
     */
    fun storeDescriptorKey(sessionId: String, key: String) =
        updateSession(sessionId) { it.copy(descriptorKeys = (it.descriptorKeys + key).distinct()) }

    internal fun updateSession(id: String, transform: (BrewSession) -> BrewSession) = sessionRows.update { map ->
        map[id]?.let { map + (id to transform(it)) } ?: map
    }
}

/** Fake [SessionRepository]; create it through [FakeSessions]. */
class FakeSessionRepository internal constructor(private val s: FakeSessions) : SessionRepository {
    /** The opening dates completions set, by session (SCR-06). */
    private val openedDates = mutableMapOf<String, OpenedDateChange>()

    override suspend fun startSession(start: SessionStart) = s.sessionRows.update { map ->
        check(start.id !in map) { "session ${start.id} exists" }
        map + (
            start.id to BrewSession(
                id = start.id,
                status = SessionStatus.IN_PROGRESS,
                snapshot = start.snapshot.copy(baseTempC = start.snapshot.baseTempC?.let { snap5(it) }),
                preAlertSeconds = start.preAlertSeconds,
                plannedStepCount = start.steps.size,
                costCents = null,
                startedAtMs = start.startedAtMs,
                endedAtMs = null,
                zoneId = start.zoneId,
                autoFinished = false,
                steps = start.steps.mapIndexed { position, step -> step.row(position) },
            )
            )
    }

    override suspend fun replacePendingSteps(sessionId: String, fromIndex: Int, steps: List<PlannedStep>) =
        s.updateSession(sessionId) { session ->
            session.copy(
                steps = session.steps.take(fromIndex) + steps.mapIndexed { i, step -> step.row(fromIndex + i) },
            )
        }

    override suspend fun recordStep(sessionId: String, stepIndex: Int, record: StepRecord) =
        s.updateSession(sessionId) { session ->
            check(session.steps.any { it.position == stepIndex }) { "session $sessionId has no step $stepIndex" }
            session.copy(
                steps = session.steps.map { row ->
                    if (row.position != stepIndex) {
                        row
                    } else {
                        row.copy(
                            plannedSeconds = record.plannedSeconds,
                            actualSeconds = record.actualSeconds,
                            pausedSeconds = record.pausedSeconds,
                            overtimeSeconds = record.overtimeSeconds,
                            startedAtWallMs = record.startedAtWallMs,
                            endedAtWallMs = record.endedAtWallMs,
                            skipped = record.skipped,
                            overtimeAutoAck = record.overtimeAutoAck,
                        )
                    }
                },
            )
        }

    override suspend fun completeSession(sessionId: String, autoFinished: Boolean) {
        val session = s.sessionRows.value[sessionId]?.takeIf { it.status == SessionStatus.IN_PROGRESS } ?: return
        val deductStock = s.settings.settings.first().deductStock
        val endedAt = session.steps.mapNotNull { it.endedAtWallMs }.maxOrNull() ?: s.nowMs()
        val teaId = session.snapshot.teaId
        val leaf = session.snapshot.leafGrams?.takeIf { it > 0.0 }
        val perGram = teaId?.let { id ->
            StockSummary.of(s.collection.eventRows.value.values.filter { it.teaId == id }).costPerGramCents
        }
        if (teaId != null && teaId in s.collection.teaRows.value) {
            val tea = s.collection.teaRows.value.getValue(teaId)
            if (tea.openedDate == null) {
                val opened = Instant.ofEpochMilli(session.startedAtMs).atZone(ZoneId.of(session.zoneId)).toLocalDate()
                s.collection.teas.saveTea(tea.copy(openedDate = opened))
                openedDates[sessionId] = OpenedDateChange(sessionId, teaId, previous = null, openedDate = opened)
            }
            if (deductStock && leaf != null) {
                s.collection.stock.addEvent(
                    InventoryEvent(
                        "brew-$sessionId",
                        teaId,
                        InventoryEventType.BREW,
                        -leaf,
                        endedAt,
                        sessionId = sessionId,
                        currency = null,
                    ),
                )
            }
        }
        // As on Room from v0.4.0 (Q-19, D-100): the never-started rows stay, without an actual time.
        s.updateSession(sessionId) {
            it.copy(
                status = SessionStatus.COMPLETED,
                plannedStepCount = it.steps.size,
                costCents = if (leaf != null && perGram != null) (leaf * perGram).roundToLong() else null,
                endedAtMs = endedAt,
                autoFinished = autoFinished,
            )
        }
    }

    override suspend fun openedDateChange(sessionId: String): OpenedDateChange? = openedDates[sessionId]?.takeIf {
        s.sessionRows.value[sessionId]?.status == SessionStatus.COMPLETED &&
            s.collection.teaRows.value[it.teaId]?.openedDate == it.openedDate
    }

    override suspend fun undoOpenedDate(change: OpenedDateChange) {
        val tea = s.collection.teaRows.value[change.teaId]
        if (tea?.openedDate == change.openedDate) s.collection.teas.saveTea(tea.copy(openedDate = change.previous))
        openedDates.remove(change.sessionId)
    }

    override suspend fun discardSession(sessionId: String) {
        s.collection.eventRows.update { map -> map.filterValues { it.sessionId != sessionId } }
        s.activeRows.update { rows -> rows.filterValues { it.activeBrew.sessionId != sessionId } }
        s.sessionRows.update { it - sessionId }
    }

    override suspend fun getSession(id: String): BrewSession? = s.sessionRows.value[id]

    override suspend fun inProgressSessionIds(): List<String> =
        s.sessionRows.value.values.filter { it.status == SessionStatus.IN_PROGRESS }.map { it.id }

    override fun observeRecentSessions(limit: Int): Flow<List<BrewSession>> = s.sessionRows.map { map ->
        map.values.filter { it.status != SessionStatus.IN_PROGRESS }.sortedByDescending { it.startedAtMs }.take(limit)
    }

    private fun PlannedStep.row(position: Int) = SessionStepRow(
        position = position,
        kind = kind,
        plannedSeconds = plannedSeconds,
        tempC = tempC?.let { snap5(it) },
        actualSeconds = null,
        pausedSeconds = null,
        overtimeSeconds = null,
        startedAtWallMs = null,
        endedAtWallMs = null,
        skipped = false,
        overtimeAutoAck = false,
        preAlertSeconds = preAlertSeconds,
    )
}

/**
 * Fake [JournalRepository] on the fake session rows, newest start first, emitting again on
 * every write; create it through [FakeSessions]. [coverPhotos] maps a tea id to its photo file.
 */
class FakeJournalRepository internal constructor(private val s: FakeSessions) : JournalRepository {
    val coverPhotos = MutableStateFlow<Map<String, java.io.File>>(emptyMap())

    override fun observeSessions(): Flow<List<JournalSession>> = s.sessionRows.map { rows ->
        rows.values.sortedByDescending { it.startedAtMs }.map { session ->
            JournalSession(session, session.snapshot.teaId?.let { coverPhotos.value[it] })
        }
    }

    /** As on Room: a finished session goes with its stock event and its descriptors; one in progress stays (D-102). */
    override suspend fun deleteSession(sessionId: String) {
        val session = s.sessionRows.value[sessionId] ?: return
        if (session.status == SessionStatus.IN_PROGRESS) return
        s.collection.eventRows.update { map -> map.filterValues { it.sessionId != sessionId } }
        s.sessionRows.update { it - sessionId }
    }

    override fun observeSession(sessionId: String): Flow<JournalSession?> = s.sessionRows.map { rows ->
        rows[sessionId]?.let { session ->
            JournalSession(session, session.snapshot.teaId?.let { coverPhotos.value[it] })
        }
    }

    override fun observeTeaTasting(teaId: String): Flow<TeaTasting> = s.sessionRows.map { rows ->
        val sessions = finished(rows).filter { it.snapshot.teaId == teaId }
        TeaTasting(
            rating = ratingOf(sessions),
            tasteAverages = TasteAxis.entries.mapNotNull { axis ->
                sessions.mapNotNull { it.taste[axis] }.takeIf { it.isNotEmpty() }?.let { axis to it.average() }
            }.toMap(),
            descriptorCounts = sessions.flatMap { it.descriptorKeys }.groupingBy { it }.eachCount()
                .map { (key, count) -> DescriptorCount(key, count) }
                .sortedWith(compareByDescending<DescriptorCount> { it.count }.thenBy { it.key }),
        )
    }

    private fun finished(rows: Map<String, BrewSession>) = rows.values.filter { it.status != SessionStatus.IN_PROGRESS }

    private fun ratingOf(sessions: List<BrewSession>): TeaRating? =
        sessions.mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.let { TeaRating(it.average(), it.size) }
}

/**
 * Fake [SessionTasting] on the fake session rows, with Room's rules (05 §3.7): a `COMPLETED` session only, the
 * bounds, trimmed notes and at most 20 descriptors; create it through [FakeSessions]. [writes] counts the calls,
 * written or refused, so a screen test can check that nothing was saved.
 */
class FakeSessionTasting internal constructor(private val s: FakeSessions) : SessionTasting {
    var writes = 0
        private set

    override suspend fun setRating(sessionId: String, rating: Double?): TastingWrite =
        write(sessionId, invalid = rating != null && !TastingLimits.isValidRating(rating)) { it.copy(rating = rating) }

    override suspend fun setTaste(sessionId: String, axis: TasteAxis, value: Int?): TastingWrite =
        write(sessionId, invalid = value != null && !TastingLimits.isValidTaste(value)) { session ->
            session.copy(taste = session.taste.with(axis, value))
        }

    override suspend fun clearTaste(sessionId: String): TastingWrite =
        write(sessionId) { it.copy(taste = TasteProfile.EMPTY) }

    override suspend fun setNotes(sessionId: String, notes: String?): TastingWrite {
        val trimmed = notes?.trim()?.takeIf { it.isNotEmpty() }
        val tooLong = trimmed != null && trimmed.length > TastingLimits.MAX_NOTES_LENGTH
        return write(sessionId, invalid = tooLong) { it.copy(notes = trimmed) }
    }

    override suspend fun setDescriptor(sessionId: String, key: String, ticked: Boolean): TastingWrite {
        val session = s.sessionRows.value[sessionId]
        val keys = session?.descriptorKeys.orEmpty()
        val full = ticked && key !in keys && keys.size >= TastingLimits.MAX_DESCRIPTORS
        val unknown = key.isBlank() || (ticked && TasteDescriptor.fromKey(key) == null)
        return if (!unknown && full && session?.status == SessionStatus.COMPLETED) {
            writes++
            TastingWrite.DESCRIPTOR_LIMIT
        } else {
            write(sessionId, invalid = unknown) {
                val chosen = if (ticked) (it.descriptorKeys + key).distinct() else it.descriptorKeys - key
                it.copy(descriptorKeys = chosen)
            }
        }
    }

    /**
     * Applies [change] to a completed session; [invalid] is [TastingWrite.INVALID] and is checked before the
     * session, as on Room.
     */
    private fun write(
        sessionId: String,
        invalid: Boolean = false,
        change: (BrewSession) -> BrewSession,
    ): TastingWrite {
        writes++
        val session = s.sessionRows.value[sessionId]?.takeIf { it.status == SessionStatus.COMPLETED }
        return when {
            invalid -> TastingWrite.INVALID

            session == null -> TastingWrite.NOT_COMPLETED

            else -> {
                s.updateSession(sessionId, change)
                TastingWrite.SAVED
            }
        }
    }

    private fun TasteProfile.with(axis: TasteAxis, value: Int?) = when (axis) {
        TasteAxis.UMAMI -> copy(umami = value)
        TasteAxis.SWEETNESS -> copy(sweetness = value)
        TasteAxis.BITTERNESS -> copy(bitterness = value)
        TasteAxis.ASTRINGENCY -> copy(astringency = value)
        TasteAxis.BODY -> copy(body = value)
        TasteAxis.AROMA -> copy(aroma = value)
    }
}

/** Fake [ActiveBrewStore], one row per [TimerSlot] as on Room (05 §3.12); create it through [FakeSessions]. */
class FakeActiveBrewStore internal constructor(private val s: FakeSessions) : ActiveBrewStore {
    override suspend fun read(slot: TimerSlot): TimerSnapshot? = s.activeRows.value[slot]?.let { snapshot ->
        val session = snapshot.activeBrew.sessionId?.let { s.sessionRows.value[it] }
        if (session == null) {
            snapshot
        } else {
            // As on Room: a cold or ice brew has long steps only (D-053).
            snapshot.copy(
                steps = session.steps,
                longStepsOnly = BrewTimerEngine.longStepsOnly(session.snapshot.method),
            )
        }
    }

    override suspend fun persist(slot: TimerSlot, snapshot: TimerSnapshot?) {
        if (snapshot == null) return clear(slot)
        require(snapshot.activeBrew.fitsIn(slot)) { "a ${snapshot.activeBrew.slot} snapshot is not stored as $slot" }
        write(slot, snapshot)
    }

    override suspend fun clear(slot: TimerSlot) = s.activeRows.update { it - slot }

    private fun write(slot: TimerSlot, snapshot: TimerSnapshot) {
        snapshot.activeBrew.sessionId?.let { id ->
            check(id in s.sessionRows.value) { "no session $id" }
            val rows = snapshot.steps.map { it.copy(tempC = it.tempC?.let { t -> snap5(t) }) }
            s.updateSession(id) { it.copy(steps = rows) }
        }
        s.activeRows.update { it + (slot to snapshot) }
    }
}

/** Runs the block without a transaction: the fakes cannot roll back. */
object FakeDatabaseTransactions : DatabaseTransactions {
    override suspend fun <R> inTransaction(block: suspend () -> R): R = block()
}
