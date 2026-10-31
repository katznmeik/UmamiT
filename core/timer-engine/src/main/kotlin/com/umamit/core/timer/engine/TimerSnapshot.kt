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
 * position order. The quick timer has no step rows. [longStepsOnly] is not
 * an `active_brew` column: the controller sets it from `brew_session.method`
 * with [BrewTimerEngine.longStepsOnly] (a cold or ice brew); it is false for
 * the quick timer (D-053).
 */
data class TimerSnapshot(
    val activeBrew: ActiveBrewSnapshot,
    val steps: List<SessionStepRow>,
    val longStepsOnly: Boolean,
)

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
        preAlertInheritedSeconds = session.preAlert.inheritedSeconds,
        zoneId = session.zone.id,
        // The quick timer's choice of SCR-07 (umamit.db v3, D-097); a brew session's are in its step rows.
        quickPreAlertSeconds = if (quick) session.steps.single().preAlertSeconds else null,
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
            alertRingingSinceElapsedMs = state.ringingSinceElapsedMs,
            alertRingingSinceWallMs = state.ringingSinceWallMs,
        )
    }
    val rows = if (quick) emptyList() else session.steps.mapIndexed(::rowOf)
    return TimerSnapshot(activeBrew, rows, session.longStepsOnly)
}

/**
 * The state of [snapshot]; Idle for none. Every inconsistency (a missing or
 * out-of-range value, a field of another phase, a plan that is not ended
 * steps, the current step, then pending steps) throws
 * [IllegalArgumentException] and nothing else, so the controller can clear
 * `active_brew` instead of running a wrong timer.
 */
internal fun stateOf(snapshot: TimerSnapshot?): TimerState {
    if (snapshot == null) return TimerState.Idle
    val row = snapshot.activeBrew
    requirePhaseFields(row)
    require(row.pausedTotalMs >= 0) { "a paused total is never negative" }
    val state = when (row.phase) {
        TimerPhase.READY -> TimerState.Ready(sessionOf(snapshot), row.stepIndex, required(row.readySinceWallMs))

        TimerPhase.FINISHED -> TimerState.Finished(sessionOf(snapshot), required(row.readySinceWallMs)).also {
            require(row.stepIndex == it.session.steps.size) { "FINISHED stores the number of steps as its index" }
        }

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

        TimerPhase.ALERTING -> alertingOf(snapshot)
    }
    requirePlanShape(state)
    requireReachable(state)
    return state
}

/**
 * The ALERTING state of [snapshot] (05 §3.12, D-098): a quick alert without a ringing start is held; a brew alert
 * is never held, so one stored without it (a row the interim builds of v0.3.0 wrote) rings since its deadline.
 */
private fun alertingOf(snapshot: TimerSnapshot): TimerState.Alerting {
    val row = snapshot.activeBrew
    require((row.alertRingingSinceElapsedMs == null) == (row.alertRingingSinceWallMs == null)) {
        "a ringing start has both its elapsed and its wall value, or neither"
    }
    val deadlineElapsedMs = required(row.deadlineElapsedMs)
    val deadlineWallMs = required(row.deadlineWallMs)
    val ringsSinceDeadline = !row.isQuickTimer && row.alertRingingSinceElapsedMs == null
    return TimerState.Alerting(
        session = sessionOf(snapshot),
        stepIndex = row.stepIndex,
        stepStartedWallMs = required(row.stepStartedWallMs),
        pausedTotalMs = row.pausedTotalMs,
        deadlineElapsedMs = deadlineElapsedMs,
        deadlineWallMs = deadlineWallMs,
        alertPlayed = row.alertPlayed,
        ringingSinceElapsedMs = if (ringsSinceDeadline) deadlineElapsedMs else row.alertRingingSinceElapsedMs,
        ringingSinceWallMs = if (ringsSinceDeadline) deadlineWallMs else row.alertRingingSinceWallMs,
    )
}

private fun sessionOf(snapshot: TimerSnapshot): TimerSession {
    val row = snapshot.activeBrew
    require(row.isQuickTimer == (row.sessionId == null)) { "a brew session has an id, the quick timer none" }
    require(!(row.isQuickTimer && snapshot.longStepsOnly)) { "the quick timer is no cold or ice brew" }
    val started = row.phase in STARTED_PHASES
    val steps = if (row.isQuickTimer) {
        require(snapshot.steps.isEmpty()) { "the quick timer has no step rows" }
        require(row.quickPreAlertSeconds?.let(::isQuickPreAlert) ?: true) {
            "a quick timer's pre-alert choice is 0 or 5–30 s, was ${row.quickPreAlertSeconds}"
        }
        val status = if (started) StepStatus.STARTED else StepStatus.PENDING
        listOf(
            SessionStep(
                StepKind.INFUSION,
                required(row.quickDurationSeconds),
                null,
                status,
                null,
                row.quickPreAlertSeconds,
            ),
        )
    } else {
        require(row.quickDurationSeconds == null && snapshot.steps.isNotEmpty()) { "a brew session has step rows" }
        require(row.quickPreAlertSeconds == null) { "a brew session's pre-alerts are in its step rows" }
        snapshot.steps.mapIndexed { index, step ->
            require(step.position == index) { "step rows in position order 0..n-1, was ${step.position} at $index" }
            stepOf(step, current = started && index == row.stepIndex)
        }
    }
    return TimerSession(
        kind = row.sessionId?.let(SessionKind::Brew) ?: SessionKind.Quick,
        steps = steps,
        preAlert = PreAlertSettings(row.preAlertInheritedSeconds),
        zone = zoneOf(row.zoneId),
        lastInteractionWallMs = row.lastInteractionWallMs,
        bootCount = row.bootCount,
        demoted = row.demoted,
        longStepsOnly = snapshot.longStepsOnly,
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
    preAlertSeconds = step.preAlertSeconds,
)

private fun stepOf(row: SessionStepRow, current: Boolean): SessionStep {
    require(row.actualSeconds != null || row == row.unrecorded()) {
        "a step without recorded values has no recorded fields"
    }
    require(row.actualSeconds == null || row.skipped == (row.startedAtWallMs == null && row.endedAtWallMs == null)) {
        "a skipped step has no times, a done step both"
    }
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
    return SessionStep(row.kind, row.plannedSeconds, row.tempC, status, record, row.preAlertSeconds)
}

private fun SessionStepRow.unrecorded() = copy(
    actualSeconds = null,
    pausedSeconds = null,
    overtimeSeconds = null,
    startedAtWallMs = null,
    endedAtWallMs = null,
    skipped = false,
    overtimeAutoAck = false,
)

private fun <T : Any> required(value: T?): T = requireNotNull(value) { "a required snapshot value is missing" }
