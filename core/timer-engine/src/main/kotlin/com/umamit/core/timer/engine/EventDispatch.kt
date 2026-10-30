package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading

/*
 * Which transition an event selects (04 §3.2, §7.2, §10). Idle only starts a
 * timer; `recover` comes first; a start while a timer is active is refused
 * as a second timer (also before recovery, so the UI can still ask, TMR-21);
 * any other event from another boot is rejected; then the waiting timeouts,
 * the step lifecycle the brew session and the quick timer share, then the
 * events of the session kind. An accepted transition then follows the
 * foreground-service contract ([withServiceBack]). Null means "not allowed".
 */

internal fun dispatch(state: TimerState, event: TimerEvent, now: ClockReading): TimerTransition {
    val transition = when {
        state !is TimerState.Active -> onIdle(event, now)

        event == TimerEvent.Recover -> recover(state, now)

        event == TimerEvent.RecoverHeld -> recover(state, now, holdAlert = state.session.kind == SessionKind.Quick)

        event.isStart -> null

        state.session.bootCount != now.bootCount ->
            TimerTransition(state, emptyList(), TransitionOutcome.Rejected(RejectionReason.BootChanged))

        else -> onActive(state, event, now)?.withServiceBack(state)
    }
    return transition ?: notAccepted(state, event)
}

/** An event of the current boot in an active state: the waiting timeouts, the step lifecycle, the kind's events. */
private fun onActive(state: TimerState.Active, event: TimerEvent, now: ClockReading) =
    onWaitingTimeout(state, event, now) ?: onStepEvent(state, event, now) ?: onKindEvent(state, event, now)

private fun onKindEvent(state: TimerState.Active, event: TimerEvent, now: ClockReading) = when (state.session.kind) {
    is SessionKind.Brew -> onBrewEvent(state, event, now)
    SessionKind.Quick -> onQuickEvent(state, event, now)
}

/** The timeouts of a waiting session (TMR-14), each only when it is due. */
private fun onWaitingTimeout(state: TimerState.Active, event: TimerEvent, now: ClockReading): TimerTransition? {
    if (state !is TimerState.Waiting) return null
    val due = dueTimesOf(state)
    return when (event) {
        TimerEvent.IdleDemote -> if (isDueWall(due.idleDemoteAtWallMs, now)) idleDemote(state) else null
        TimerEvent.MidnightTimeout -> if (isDueWall(due.midnightAtWallMs, now)) midnightTimeout(state) else null
        else -> null
    }
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

    is TimerState.Alerting -> when {
        // A held alert plays nothing, so it is never reported completed (04 §9.3).
        event == TimerEvent.AlertCompleted && !state.alertPlayed && !state.held -> alertCompleted(state)

        event == TimerEvent.OvertimeTimeout && isDue(dueTimesOf(state).overtimeTimeoutAtElapsedMs, now) ->
            overtimeTimedOut(state, now)

        else -> null
    }

    is TimerState.Finished -> null
}

/**
 * Pause only before the deadline, the deadline only when due, so the alert is
 * never lost or early. A deadline event 10 min or more late applies the
 * overtime timeout, as recovery does, instead of starting an alert.
 */
private fun onRunning(state: TimerState.Running, event: TimerEvent, now: ClockReading) = when {
    event == TimerEvent.Pause && now.elapsedMs < state.deadlineElapsedMs -> pause(state, now)

    event == TimerEvent.PreAlertReached && isDue(dueTimesOf(state).preAlertAtElapsedMs, now) ->
        preAlertReached(state, now)

    event == TimerEvent.DeadlineReached && isDue(dueTimesOf(state).deadlineElapsedMs, now) ->
        if (isOvertimeTimedOut(state.deadlineElapsedMs, now)) overtimeTimedOut(state, now) else deadlineReached(state)

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

private val TimerEvent.isStart: Boolean get() = this is TimerEvent.StartSession || this is TimerEvent.StartQuickTimer

private fun notAccepted(state: TimerState, event: TimerEvent): TimerTransition {
    val outcome = if (event.isStart && state is TimerState.Active) {
        TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(state.session.kind))
    } else {
        TransitionOutcome.Ignored
    }
    return TimerTransition(state, emptyList(), outcome)
}
