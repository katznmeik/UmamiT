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
    requireReachable(state)
    return state
}

/**
 * Values the engine never produces (D-054): a pause outside 1 ms – planned, a
 * demoted step that runs or alerts (any user event ends the demotion), a
 * pre-alert on a long step, and a played pre-alert that is still due.
 */
private fun requireReachable(state: TimerState.Active) {
    when (state) {
        is TimerState.Paused ->
            require(state.remainingMs in 1..state.plannedMs) { "a pause keeps 1 ms up to the planned time" }

        is TimerState.Running -> {
            require(!state.session.demoted) { "a running step is never demoted" }
            require(state.preAlertAtElapsedMs == null || !state.preAlertPlayed) { "a played pre-alert is not due" }
            require(state.preAlertAtElapsedMs == null || !state.session.isLongStep(state.stepIndex)) {
                "a long step has no pre-alert"
            }
        }

        is TimerState.Alerting -> require(!state.session.demoted) { "an alert is never demoted" }

        is TimerState.Ready, is TimerState.Finished -> Unit
    }
}

/** The fields each phase may set (05 §3.12); every other phase field must be null, false or 0. */
private val PHASE_FIELDS = mapOf(
    TimerPhase.READY to setOf("ready_since"),
    TimerPhase.FINISHED to setOf("ready_since"),
    TimerPhase.RUNNING to setOf("deadline", "pre_alert", "pre_alert_played", "paused_total", "step_started"),
    TimerPhase.PAUSED to setOf("remaining", "paused_since", "pre_alert_played", "paused_total", "step_started"),
    TimerPhase.ALERTING to setOf("deadline", "alert_played", "paused_total", "step_started"),
)

private fun requirePhaseFields(row: ActiveBrewSnapshot) {
    val set = buildSet {
        if (row.deadlineElapsedMs != null || row.deadlineWallMs != null) add("deadline")
        if (row.preAlertElapsedMs != null) add("pre_alert")
        if (row.preAlertPlayed) add("pre_alert_played")
        if (row.remainingMs != null) add("remaining")
        if (row.pausedTotalMs != 0L) add("paused_total")
        if (row.pausedSinceElapsedMs != null || row.pausedSinceWallMs != null) add("paused_since")
        if (row.readySinceWallMs != null) add("ready_since")
        if (row.stepStartedWallMs != null) add("step_started")
        if (row.alertPlayed) add("alert_played")
    }
    val foreign = set - PHASE_FIELDS.getValue(row.phase)
    require(foreign.isEmpty()) { "fields $foreign do not belong to ${row.phase}" }
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

        is TimerState.Ready -> state.stepIndex == ended && rest.isNotEmpty() && rest.all { it == StepStatus.PENDING }

        is TimerState.StepStarted -> state.stepIndex == ended && rest.firstOrNull() == StepStatus.STARTED &&
            rest.drop(1).all { it == StepStatus.PENDING }
    }
    require(ok) { "the plan must be ended steps, the current step, then pending steps: $rest at ${state.phase}" }
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
