package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading

/** The result of one [BrewTimerEngine.reduce]: the new [state] and the [effects] to execute in order. */
data class TimerTransition(val state: TimerState, val effects: List<TimerEffect>)

/**
 * What the timer shows, in whole seconds (04 §5). German formatting (`m:ss`,
 * `h:mm:ss` from one hour on, „+0:07") stays in the UI.
 */
data class TimerDisplay(val remainingSeconds: Long, val overtimeSeconds: Long)

/**
 * The brew timer state machine (04 §3–§5): a pure function of state, event
 * and the current [ClockReading], with no coroutines, Android types or I/O.
 * The controller owns the state, feeds events one at a time, executes the
 * returned effects and persists every state (04 §2.1, §4).
 *
 * Countdown arithmetic uses only [ClockReading.elapsedMs], so a change of the
 * wall clock or the time zone never changes a running step (E1).
 */
object BrewTimerEngine {

    /**
     * Applies [event] to [state] at [now], following the table of 04 §3.2. An
     * event the state does not allow returns the same state and no effects, so
     * duplicates are harmless (04 §2.4, E5). In Running, [TimerEvent.Pause] is
     * allowed only before the deadline and [TimerEvent.DeadlineReached] only at
     * or after it, so a late or early deadline event never ends the wrong step.
     */
    fun reduce(state: TimerState, event: TimerEvent, now: ClockReading): TimerTransition {
        val transition = when (state) {
            TimerState.Idle -> if (event is TimerEvent.StartSession) startSession(event, now) else null
            is TimerState.Ready -> onReady(state, event, now)
            is TimerState.Running -> onRunning(state, event, now)
            is TimerState.Paused -> onPaused(state, event, now)
            is TimerState.Alerting -> onAlerting(state, event, now)
            is TimerState.Finished -> onSessionEvent(state, event, now)
        }
        return transition ?: TimerTransition(state, emptyList())
    }

    /**
     * The display values of [state] at [now]; null in Idle. Remaining time is
     * rounded up, so „0:01" never means the tea is done; overtime is rounded
     * down (04 §5). Ready shows the step's planned duration.
     */
    fun display(state: TimerState, now: ClockReading): TimerDisplay? = when (state) {
        TimerState.Idle -> null
        is TimerState.Ready -> TimerDisplay(state.plannedMs / MS_PER_SECOND, 0)
        is TimerState.Running -> TimerDisplay(ceilSeconds(state.deadlineElapsedMs - now.elapsedMs), 0)
        is TimerState.Paused -> TimerDisplay(ceilSeconds(state.remainingMs), 0)
        is TimerState.Alerting -> TimerDisplay(0, floorSeconds(now.elapsedMs - state.deadlineElapsedMs))
        is TimerState.Finished -> TimerDisplay(0, 0)
    }

    private fun onReady(state: TimerState.Ready, event: TimerEvent, now: ClockReading) = when (event) {
        TimerEvent.Start -> start(state, now)
        TimerEvent.SkipStep -> skipStep(state, now)
        else -> onSessionEvent(state, event, now)
    }

    private fun onRunning(state: TimerState.Running, event: TimerEvent, now: ClockReading) = when (event) {
        TimerEvent.Pause -> if (now.elapsedMs < state.deadlineElapsedMs) pause(state, now) else null
        TimerEvent.DeadlineReached -> if (now.elapsedMs >= state.deadlineElapsedMs) deadlineReached(state) else null
        TimerEvent.EndStepNow -> endStepNow(state, now)
        else -> onSessionEvent(state, event, now)
    }

    private fun onPaused(state: TimerState.Paused, event: TimerEvent, now: ClockReading) = when (event) {
        TimerEvent.Resume -> resume(state, now)
        TimerEvent.EndStepNow -> endStepNow(state, now)
        else -> onSessionEvent(state, event, now)
    }

    private fun onAlerting(state: TimerState.Alerting, event: TimerEvent, now: ClockReading) = when (event) {
        TimerEvent.Poured -> poured(state, now)
        else -> onSessionEvent(state, event, now)
    }

    /** The events every active state allows. */
    private fun onSessionEvent(state: TimerState.Active, event: TimerEvent, now: ClockReading) = when (event) {
        TimerEvent.FinishSession -> finishSession(state, now)
        TimerEvent.Discard -> discard(state)
        else -> null
    }

    private fun ceilSeconds(ms: Long): Long = Math.ceilDiv(ms.coerceAtLeast(0), MS_PER_SECOND)

    private fun floorSeconds(ms: Long): Long = Math.floorDiv(ms.coerceAtLeast(0), MS_PER_SECOND)
}
