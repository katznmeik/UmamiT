package com.umamit.core.testing

import com.umamit.core.common.snap5
import com.umamit.core.data.ActiveBrewStore
import com.umamit.core.data.DatabaseTransactions
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SessionStart
import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.BrewSession
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.SessionStepRow
import com.umamit.core.model.StockSummary
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

// In-memory fakes of the session repositories and the active brew store (05 §3.6, §3.7,
// §3.12). They share their rows like the database: the store's snapshot writes the
// session's step rows, and discarding a session drops its stock event and the active brew.
// Not modelled: the new-package rule of INV-06 (the fake sets only a missing opened date)
// and rollbacks (FakeDatabaseTransactions runs the block as is).

/** The session tables of a fake database; completion uses [collection]'s teas and stock and [settings]. */
class FakeSessions(
    internal val collection: FakeCollection = FakeCollection(),
    internal val settings: SettingsRepository = FakeSettingsRepository(),
    internal val nowMs: () -> Long = { 0L },
) {
    internal val sessionRows = MutableStateFlow<Map<String, BrewSession>>(emptyMap())
    internal val activeRow = MutableStateFlow<TimerSnapshot?>(null)

    val sessions = FakeSessionRepository(this)
    val activeBrew = FakeActiveBrewStore(this)
    val transactions: DatabaseTransactions = FakeDatabaseTransactions

    internal fun updateSession(id: String, transform: (BrewSession) -> BrewSession) = sessionRows.update { map ->
        map[id]?.let { map + (id to transform(it)) } ?: map
    }
}

/** Fake [SessionRepository]; create it through [FakeSessions]. */
class FakeSessionRepository internal constructor(private val s: FakeSessions) : SessionRepository {
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

    override suspend fun replacePendingSteps(
        sessionId: String,
        fromIndex: Int,
        steps: List<PlannedStep>,
        preAlertSeconds: Int,
    ) = s.updateSession(sessionId) { session ->
        session.copy(
            preAlertSeconds = preAlertSeconds,
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
        s.updateSession(sessionId) {
            it.copy(
                status = SessionStatus.COMPLETED,
                plannedStepCount = it.steps.size,
                steps = it.steps.filter { row -> row.actualSeconds != null },
                costCents = if (leaf != null && perGram != null) (leaf * perGram).roundToLong() else null,
                endedAtMs = endedAt,
                autoFinished = autoFinished,
            )
        }
    }

    override suspend fun discardSession(sessionId: String) {
        s.collection.eventRows.update { map -> map.filterValues { it.sessionId != sessionId } }
        s.activeRow.update { if (it?.activeBrew?.sessionId == sessionId) null else it }
        s.sessionRows.update { it - sessionId }
    }

    override suspend fun getSession(id: String): BrewSession? = s.sessionRows.value[id]

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
    )
}

/** Fake [ActiveBrewStore]; create it through [FakeSessions]. */
class FakeActiveBrewStore internal constructor(private val s: FakeSessions) : ActiveBrewStore {
    override suspend fun read(): TimerSnapshot? = s.activeRow.value?.let { snapshot ->
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

    override suspend fun persist(snapshot: TimerSnapshot?) {
        snapshot?.activeBrew?.sessionId?.let { id ->
            check(id in s.sessionRows.value) { "no session $id" }
            val rows = snapshot.steps.map { it.copy(tempC = it.tempC?.let { t -> snap5(t) }) }
            s.updateSession(id) { it.copy(steps = rows) }
        }
        s.activeRow.value = snapshot
    }

    override suspend fun clear() {
        s.activeRow.value = null
    }
}

/** Runs the block without a transaction: the fakes cannot roll back. */
object FakeDatabaseTransactions : DatabaseTransactions {
    override suspend fun <R> inTransaction(block: suspend () -> R): R = block()
}
