package com.umamit.core.timer.engine

import com.umamit.core.model.ActiveBrewSnapshot
import com.umamit.core.model.TimerPhase

/*
 * The consistency checks of a restored snapshot (05 §3.12, D-048, D-054): every value `restore` refuses is one
 * the engine never produces, so the controller clears it instead of running a wrong timer.
 */

/** The fields each phase may set (05 §3.12); every other phase field must be null, false or 0. */
private val PHASE_FIELDS = mapOf(
    TimerPhase.READY to setOf("ready_since"),
    TimerPhase.FINISHED to setOf("ready_since"),
    TimerPhase.RUNNING to setOf("deadline", "pre_alert", "pre_alert_played", "paused_total", "step_started"),
    TimerPhase.PAUSED to setOf("remaining", "paused_since", "pre_alert_played", "paused_total", "step_started"),
    TimerPhase.ALERTING to setOf("deadline", "alert_played", "paused_total", "step_started", "ringing_since"),
)

internal fun requirePhaseFields(row: ActiveBrewSnapshot) {
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
        if (row.alertRingingSinceElapsedMs != null || row.alertRingingSinceWallMs != null) add("ringing_since")
    }
    val foreign = set - PHASE_FIELDS.getValue(row.phase)
    require(foreign.isEmpty()) { "fields $foreign do not belong to ${row.phase}" }
}

/**
 * Values the engine never produces (D-054): a pause outside 1 ms – planned, a
 * demoted step that runs or alerts (any user event ends the demotion), a
 * pre-alert on a long step, and a played pre-alert that is still due.
 */
internal fun requireReachable(state: TimerState.Active) {
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

        is TimerState.Alerting -> {
            require(!state.session.demoted) { "an alert is never demoted" }
            require(!state.held || !state.alertPlayed) { "a held alert is never reported played" }
        }

        is TimerState.Ready, is TimerState.Finished -> Unit
    }
}

/**
 * The plan of [state] is a prefix of ended steps, then the current step
 * (started, or pending in Ready), then pending steps; Finished has only ended
 * steps.
 */
internal fun requirePlanShape(state: TimerState.Active) {
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
