package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.TimerEffect.ReplacePendingSteps
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification

/*
 * Changes to the session plan while a session is active (04 §3.2 `addStep`,
 * `editSteps`; TMR-10, TMR-11, SCR-31). The plan is always a prefix of started
 * or ended steps followed by a suffix of pending steps; these transitions only
 * ever replace the pending suffix. Brew sessions only: the quick timer has no
 * plan to edit (04 §10).
 */

private const val SHORT_ROUNDING_SECONDS = 5L
private const val LONG_ROUNDING_SECONDS = 60L
internal const val MIN_STEP_SECONDS = 5
internal const val MAX_STEP_SECONDS = 86_400

/**
 * The *Weiterer Aufguss* default (TMR-10): based on the last non-rinse step
 * of [plan] (the last step if all are rinses), with its kind (a rinse base
 * gives an infusion) and temperature, and 1.5 × its duration, rounded half up
 * to 5 s below 10 min and to whole minutes from 10 min on, clamped to 5 s – 24 h.
 */
internal fun defaultAddedStepOf(plan: List<PlannedStep>): PlannedStep {
    require(plan.isNotEmpty()) { "a plan has at least one step" }
    val base = plan.lastOrNull { it.kind != StepKind.RINSE } ?: plan.last()
    val kind = if (base.kind == StepKind.RINSE) StepKind.INFUSION else base.kind
    return PlannedStep(kind, extendedDuration(base.plannedSeconds), base.tempC)
}

/** 1.5 × [seconds], rounded per PRF-04 and clamped to the step bounds. */
private fun extendedDuration(seconds: Int): Int {
    val halfSeconds = seconds * 3L // 1.5 × seconds, in half seconds
    val unit = if (halfSeconds < LONG_STEP_SECONDS * 2L) SHORT_ROUNDING_SECONDS else LONG_ROUNDING_SECONDS
    val rounded = (halfSeconds + unit) / (unit * 2) * unit
    return rounded.coerceIn(MIN_STEP_SECONDS.toLong(), MAX_STEP_SECONDS.toLong()).toInt()
}

/** *Weiterer Aufguss*: append the default step to the pending suffix. */
internal fun addStep(state: TimerState.Active, now: ClockReading): TimerTransition? {
    val plan = state.session.steps.map { it.planned }
    return replacePending(state, state.pendingSuffix() + defaultAddedStepOf(plan), now)
}

/**
 * The Schritte-Sheet's plan and pre-alert (SCR-31). The started or ended
 * prefix must come back unchanged and at least one non-rinse step must stay,
 * else the request is rejected and changes nothing. Brew sessions only.
 */
internal fun editSteps(state: TimerState.Active, event: TimerEvent.EditSteps, now: ClockReading): TimerTransition? {
    val prefixSize = state.session.startedPrefixSize
    val reason = when {
        event.steps.take(prefixSize) != state.session.steps.take(prefixSize).map { it.planned } ->
            RejectionReason.StartedStepChanged

        event.steps.none { it.kind != StepKind.RINSE } -> RejectionReason.NoBrewStepLeft

        else -> null
    }
    if (reason != null) return TimerTransition(state, emptyList(), TransitionOutcome.Rejected(reason))
    return replacePending(state, event.steps.drop(prefixSize), now, state.session.preAlert.with(event.preAlert))
}

/**
 * Replaces the pending suffix of [state]'s plan with [pending] and sets the
 * session's [preAlert]. A waiting state re-points: Ready waits for the first
 * pending step, Finished when none is left; a started step keeps its state.
 * Null for the quick timer.
 */
private fun replacePending(
    state: TimerState.Active,
    pending: List<PlannedStep>,
    now: ClockReading,
    preAlert: PreAlertSettings = state.session.preAlert,
): TimerTransition? {
    val sessionId = state.session.brewSessionId ?: return null
    val fromIndex = state.session.startedPrefixSize
    val session = state.session.touched(now).copy(
        steps = state.session.steps.take(fromIndex) + pending.map { it.asPending() },
        preAlert = preAlert,
    )
    val newState = when (state) {
        is TimerState.Ready -> waitingFor(session, state.readySinceWallMs)

        is TimerState.Finished -> waitingFor(session, state.readySinceWallMs).let {
            // A step added to a finished plan is ready from now on.
            if (it is TimerState.Ready) it.copy(readySinceWallMs = now.wallMs) else it
        }

        is TimerState.Running -> state.copy(
            session = session,
            preAlertAtElapsedMs = editedPreAlertAt(state, session, now),
        )

        is TimerState.Paused -> state.copy(session = session)

        is TimerState.Alerting -> state.copy(session = session)
    }
    return accepted(
        newState,
        data = listOf(ReplacePendingSteps(sessionId, fromIndex, pending)),
        side = listOf(UpdateNotification),
    )
}

/**
 * The running step's pre-alert after an edit to [session]: it follows the new
 * effective N, unless it already played. An edit never consumes a pre-alert
 * that is already due but whose `preAlertReached` is still queued (D-054): it
 * stays due while the step still has a pre-alert, and `preAlertReached`
 * decides (1-s rule). Only *Aus* or a step without a pre-alert removes it.
 */
private fun editedPreAlertAt(state: TimerState.Running, session: TimerSession, now: ClockReading): Long? {
    val stillHasOne = session.preAlertLeadMs(state.stepIndex) != null
    val dueAlready = isDue(state.preAlertAtElapsedMs, now)
    return if (dueAlready && stillHasOne && !state.preAlertPlayed) {
        state.preAlertAtElapsedMs
    } else {
        preAlertAt(session, state.stepIndex, state.deadlineElapsedMs, state.preAlertPlayed, now)
    }
}

/** Ready for the first pending step of [session], or Finished when none is left. */
internal fun waitingFor(session: TimerSession, readySinceWallMs: Long): TimerState.Active {
    val next = session.steps.indexOfFirst { it.status == StepStatus.PENDING }
    return if (next >= 0) {
        TimerState.Ready(session, next, readySinceWallMs)
    } else {
        TimerState.Finished(session, readySinceWallMs)
    }
}

/** The pending steps at the end of the plan. */
private fun TimerState.Active.pendingSuffix(): List<PlannedStep> =
    session.steps.drop(session.startedPrefixSize).map { it.planned }

private fun PlannedStep.asPending() = SessionStep(kind, plannedSeconds, tempC, StepStatus.PENDING, record = null)
