package com.umamit.core.timer.engine

import com.umamit.core.model.ActiveBrewSnapshot
import com.umamit.core.model.SessionStepRow
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import java.time.DateTimeException
import java.time.ZoneId

/**
 * What the controller persists for an active state (04 §7.1): the
 * `active_brew` row and, for a brew session, its `session_step` rows in
 * position order. The quick timer has no step rows.
 */
data class TimerSnapshot(val activeBrew: ActiveBrewSnapshot, val steps: List<SessionStepRow>)

/*
 * The lossless mapping between the engine state and its snapshot (05 §3.12).
 * A step's status is not stored: a row with recorded values is ended (DONE or
 * SKIPPED), the current step of Running, Paused or Alerting is STARTED, every
 * other row is PENDING.
 */

internal fun snapshotOf(state: TimerState): TimerSnapshot? {
    if (state !is TimerState.Active) return null
    val session = state.session
    val quick = session.kind == SessionKind.Quick
    val base = ActiveBrewSnapshot(
        sessionId = session.brewSessionId,
        isQuickTimer = quick,
        quickDurationSeconds = if (quick) session.steps.single().plannedSeconds else null,
        stepIndex = if (state is TimerState.OnStep) state.stepIndex else session.steps.size,
        phase = state.phase,
        deadlineElapsedMs = null,
        deadlineWallMs = null,
        preAlertElapsedMs = null,
        preAlertPlayed = false,
        remainingMs = null,
        pausedTotalMs = (state as? TimerState.StepStarted)?.pausedTotalMs ?: 0,
        pausedSinceElapsedMs = null,
        pausedSinceWallMs = null,
        readySinceWallMs = null,
        stepStartedWallMs = (state as? TimerState.StepStarted)?.stepStartedWallMs,
        alertPlayed = false,
        bootCount = session.bootCount,
        lastInteractionWallMs = session.lastInteractionWallMs,
        demoted = session.demoted,
        preAlertOverrideSeconds = session.preAlert.sessionOverrideSeconds,
        preAlertInheritedSeconds = session.preAlert.inheritedSeconds,
        zoneId = session.zone.id,
    )
    val activeBrew = when (state) {
        is TimerState.Ready -> base.copy(readySinceWallMs = state.readySinceWallMs)

        is TimerState.Finished -> base.copy(readySinceWallMs = state.readySinceWallMs)

        is TimerState.Running -> base.copy(
            deadlineElapsedMs = state.deadlineElapsedMs,
            deadlineWallMs = state.deadlineWallMs,
            preAlertElapsedMs = state.preAlertAtElapsedMs,
            preAlertPlayed = state.preAlertPlayed,
        )

        is TimerState.Paused -> base.copy(
            remainingMs = state.remainingMs,
            pausedSinceElapsedMs = state.pausedSinceElapsedMs,
            pausedSinceWallMs = state.pausedSinceWallMs,
            preAlertPlayed = state.preAlertPlayed,
        )

        is TimerState.Alerting -> base.copy(
            deadlineElapsedMs = state.deadlineElapsedMs,
            deadlineWallMs = state.deadlineWallMs,
            alertPlayed = state.alertPlayed,
        )
    }
    val rows = if (quick) emptyList() else session.steps.mapIndexed(::rowOf)
    return TimerSnapshot(activeBrew, rows)
}

/** The state of [snapshot]; Idle for none. Throws [IllegalArgumentException] for an inconsistent snapshot. */
internal fun stateOf(snapshot: TimerSnapshot?): TimerState {
    if (snapshot == null) return TimerState.Idle
    val row = snapshot.activeBrew
    val state = when (row.phase) {
        TimerPhase.READY -> TimerState.Ready(sessionOf(snapshot), row.stepIndex, required(row.readySinceWallMs))

        TimerPhase.FINISHED -> TimerState.Finished(sessionOf(snapshot), required(row.readySinceWallMs))

        TimerPhase.RUNNING -> TimerState.Running(
            session = sessionOf(snapshot),
            stepIndex = row.stepIndex,
            stepStartedWallMs = required(row.stepStartedWallMs),
            pausedTotalMs = row.pausedTotalMs,
            deadlineElapsedMs = required(row.deadlineElapsedMs),
            deadlineWallMs = required(row.deadlineWallMs),
            preAlertAtElapsedMs = row.preAlertElapsedMs,
            preAlertPlayed = row.preAlertPlayed,
        )

        TimerPhase.PAUSED -> TimerState.Paused(
            session = sessionOf(snapshot),
            stepIndex = row.stepIndex,
            stepStartedWallMs = required(row.stepStartedWallMs),
            pausedTotalMs = row.pausedTotalMs,
            remainingMs = required(row.remainingMs),
            pausedSinceElapsedMs = required(row.pausedSinceElapsedMs),
            pausedSinceWallMs = required(row.pausedSinceWallMs),
            preAlertPlayed = row.preAlertPlayed,
        )

        TimerPhase.ALERTING -> TimerState.Alerting(
            session = sessionOf(snapshot),
            stepIndex = row.stepIndex,
            stepStartedWallMs = required(row.stepStartedWallMs),
            pausedTotalMs = row.pausedTotalMs,
            deadlineElapsedMs = required(row.deadlineElapsedMs),
            deadlineWallMs = required(row.deadlineWallMs),
            alertPlayed = row.alertPlayed,
        )
    }
    requirePlanShape(state)
    return state
}

private fun sessionOf(snapshot: TimerSnapshot): TimerSession {
    val row = snapshot.activeBrew
    require(row.isQuickTimer == (row.sessionId == null)) { "a brew session has an id, the quick timer none" }
    val started = row.phase in STARTED_PHASES
    val steps = if (row.isQuickTimer) {
        require(snapshot.steps.isEmpty()) { "the quick timer has no step rows" }
        val status = if (started) StepStatus.STARTED else StepStatus.PENDING
        listOf(SessionStep(StepKind.INFUSION, required(row.quickDurationSeconds), null, status, null))
    } else {
        require(row.quickDurationSeconds == null && snapshot.steps.isNotEmpty()) { "a brew session has step rows" }
        snapshot.steps.mapIndexed { index, step ->
            require(step.position == index) { "step rows in position order 0..n-1, was ${step.position} at $index" }
            stepOf(step, current = started && index == row.stepIndex)
        }
    }
    return TimerSession(
        kind = row.sessionId?.let(SessionKind::Brew) ?: SessionKind.Quick,
        steps = steps,
        preAlert = PreAlertSettings(row.preAlertOverrideSeconds, row.preAlertInheritedSeconds),
        zone = zoneOf(row.zoneId),
        lastInteractionWallMs = row.lastInteractionWallMs,
        bootCount = row.bootCount,
        demoted = row.demoted,
    )
}

private fun zoneOf(id: String): ZoneId = try {
    ZoneId.of(id)
} catch (e: DateTimeException) {
    throw IllegalArgumentException("unknown time zone $id", e)
}

private val STARTED_PHASES = setOf(TimerPhase.RUNNING, TimerPhase.PAUSED, TimerPhase.ALERTING)

private fun rowOf(position: Int, step: SessionStep) = SessionStepRow(
    position = position,
    kind = step.kind,
    plannedSeconds = step.plannedSeconds,
    tempC = step.tempC,
    actualSeconds = step.record?.actualSeconds,
    pausedSeconds = step.record?.pausedSeconds,
    overtimeSeconds = step.record?.overtimeSeconds,
    startedAtWallMs = step.record?.startedAtWallMs,
    endedAtWallMs = step.record?.endedAtWallMs,
    skipped = step.record?.skipped ?: false,
    overtimeAutoAck = step.record?.overtimeAutoAck ?: false,
)

private fun stepOf(row: SessionStepRow, current: Boolean): SessionStep {
    val record = row.actualSeconds?.let { actual ->
        StepRecord(
            plannedSeconds = row.plannedSeconds,
            actualSeconds = actual,
            pausedSeconds = required(row.pausedSeconds),
            overtimeSeconds = required(row.overtimeSeconds),
            startedAtWallMs = row.startedAtWallMs,
            endedAtWallMs = row.endedAtWallMs,
            skipped = row.skipped,
            overtimeAutoAck = row.overtimeAutoAck,
        )
    }
    val status = when {
        record == null && current -> StepStatus.STARTED
        record == null -> StepStatus.PENDING
        record.skipped -> StepStatus.SKIPPED
        else -> StepStatus.DONE
    }
    return SessionStep(row.kind, row.plannedSeconds, row.tempC, status, record)
}

/**
 * The plan of [state] is a prefix of ended steps, then the current step
 * (started, or pending in Ready), then pending steps; Finished has only ended
 * steps.
 */
private fun requirePlanShape(state: TimerState.Active) {
    val steps = state.session.steps
    val ended = steps.takeWhile { it.status == StepStatus.DONE || it.status == StepStatus.SKIPPED }.size
    val rest = steps.drop(ended).map { it.status }
    val ok = when (state) {
        is TimerState.Finished -> rest.isEmpty()

        is TimerState.Ready -> state.stepIndex == ended && rest.all { it == StepStatus.PENDING }

        is TimerState.StepStarted -> state.stepIndex == ended && rest.firstOrNull() == StepStatus.STARTED &&
            rest.drop(1).all { it == StepStatus.PENDING }
    }
    require(ok) { "the plan must be ended steps, the current step, then pending steps: $rest at ${state.phase}" }
}

private fun <T : Any> required(value: T?): T = requireNotNull(value) { "a required snapshot value is missing" }
