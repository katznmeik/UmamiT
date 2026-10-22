package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading

/*
 * Which transition an event selects (04 §3.2, §7.2, §10). Idle only starts a
 * timer; `recover` comes first; any other event from another boot is
 * rejected; then the step lifecycle the brew session and the quick timer
 * share, then the events of the session kind. Null means "not allowed".
 */

internal fun dispatch(state: TimerState, event: TimerEvent, now: ClockReading): TimerTransition {
    val transition = when {
        state !is TimerState.Active -> onIdle(event, now)

        event == TimerEvent.Recover -> recover(state, now)

        state.session.bootCount != now.bootCount ->
            TimerTransition(state, emptyList(), TransitionOutcome.Rejected(RejectionReason.BootChanged))

        else -> onStepEvent(state, event, now) ?: when (state.session.kind) {
            is SessionKind.Brew -> onBrewEvent(state, event, now)
            SessionKind.Quick -> onQuickEvent(state, event, now)
        }
    }
    return transition ?: notAccepted(state, event)
}

private fun onIdle(event: TimerEvent, now: ClockReading) = when (event) {
    is TimerEvent.StartSession -> startSession(event, now)
    is TimerEvent.StartQuickTimer -> startQuickTimer(event, now)
    else -> null
}

/** The step lifecycle events a brew session and the quick timer share (04 §3.2, §10). */
private fun onStepEvent(state: TimerState.Active, event: TimerEvent, now: ClockReading) = when (state) {
    is TimerState.Ready -> if (event == TimerEvent.Start) start(state, now) else null

    is TimerState.Running -> onRunning(state, event, now)

    is TimerState.Paused -> if (event == TimerEvent.Resume) resume(state, now) else null

    is TimerState.Alerting -> if (event == TimerEvent.AlertCompleted &&
        !state.alertPlayed
    ) {
        alertCompleted(state)
    } else {
        null
    }

    is TimerState.Finished -> null
}

/** Pause only before the deadline, the deadline only when due, so the alert is never lost or early. */
private fun onRunning(state: TimerState.Running, event: TimerEvent, now: ClockReading) = when {
    event == TimerEvent.Pause && now.elapsedMs < state.deadlineElapsedMs -> pause(state, now)
    event == TimerEvent.DeadlineReached && isDue(dueTimesOf(state).deadlineElapsedMs, now) -> deadlineReached(state)
    else -> null
}

/** The events only a brew session has. */
private fun onBrewEvent(state: TimerState.Active, event: TimerEvent, now: ClockReading) = when (event) {
    TimerEvent.SkipStep -> (state as? TimerState.Ready)?.let { skipStep(it, now) }

    TimerEvent.EndStepNow -> when (state) {
        is TimerState.Running -> endStepNow(state, now)
        is TimerState.Paused -> endStepNow(state, now)
        else -> null
    }

    TimerEvent.Poured -> (state as? TimerState.Alerting)?.let { poured(it, now) }

    TimerEvent.FinishSession -> finishSession(state, now)

    TimerEvent.Discard -> discard(state)

    TimerEvent.AddStep -> addStep(state, now)

    is TimerEvent.EditSteps -> editSteps(state, event, now)

    else -> null
}

private fun notAccepted(state: TimerState, event: TimerEvent): TimerTransition {
    val isStart = event is TimerEvent.StartSession || event is TimerEvent.StartQuickTimer
    val outcome = if (isStart && state is TimerState.Active) {
        TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(state.session.kind))
    } else {
        TransitionOutcome.Ignored
    }
    return TimerTransition(state, emptyList(), outcome)
}
