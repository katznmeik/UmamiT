package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading

/**
 * The result of one [BrewTimerEngine.reduce]: the new [state], the [effects]
 * to execute in order, and whether the event was accepted. A non-accepted
 * event always returns the same state instance and no effects.
 */
data class TimerTransition(val state: TimerState, val effects: List<TimerEffect>, val outcome: TransitionOutcome)

/** Whether the engine applied an event (logged by the controller for diagnostics, 06 §5.6). */
sealed interface TransitionOutcome {

    /** The event was applied. */
    data object Accepted : TransitionOutcome

    /** The event does not apply to the state (a duplicate, a race, a stale alarm); harmless. */
    data object Ignored : TransitionOutcome

    /** The event was refused for a [reason] the UI may explain. */
    data class Rejected(val reason: RejectionReason) : TransitionOutcome
}

/** Why an event was refused. */
sealed interface RejectionReason {

    /** A session or quick timer is already [active] (GEN-03, TMR-21: „Erst die … -Sitzung beenden?"). */
    data class ActiveTimerExists(val active: SessionKind) : RejectionReason

    /** `editSteps` would change, remove or reorder a started or ended step (SCR-31). */
    data object StartedStepChanged : RejectionReason

    /** `editSteps` would leave the session without a non-rinse step (PRF-04). */
    data object NoBrewStepLeft : RejectionReason
}

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
     * allowed only before the deadline and [TimerEvent.DeadlineReached] only
     * when it is due, so a late or early deadline event never ends the wrong
     * step.
     */
    fun reduce(state: TimerState, event: TimerEvent, now: ClockReading): TimerTransition {
        val transition = when (state) {
            TimerState.Idle -> onIdle(event, now)

            is TimerState.Active -> onStepEvent(state, event, now) ?: when (state.session.kind) {
                is SessionKind.Brew -> onBrewEvent(state, event, now)
                SessionKind.Quick -> onQuickEvent(state, event, now)
            }
        }
        return transition ?: notAccepted(state, event)
    }

    /**
     * When the time-based events of [state] are due. Every accepted transition
     * emits this set for its new state as [TimerEffect.ScheduleAlarms]; the
     * controller also re-arms its in-process wake-up from it after an ignored
     * event, and recovery recomputes it from a persisted state.
     */
    fun dueTimes(state: TimerState): DueTimes = dueTimesOf(state)

    /**
     * True when [TimerEvent.FinishSession] in [state] would discard the session
     * instead of completing it, because no non-rinse step was or would be
     * completed (a started step counts as completed). The UI confirms such a
     * finish first (TMR-12 AC1).
     */
    fun finishWouldDiscard(state: TimerState): Boolean =
        state is TimerState.Active && state.session.kind is SessionKind.Brew && !finishCompletes(state)

    /**
     * The step *Weiterer Aufguss* appends to [plan] (TMR-10): the last non-rinse
     * step's kind and temperature, 1.5 × its duration rounded half up to 5 s
     * below 10 min and to whole minutes from 10 min on, within 5 s – 24 h.
     * The Schritte-Sheet uses it for *Aufguss hinzufügen*.
     */
    fun defaultAddedStep(plan: List<PlannedStep>): PlannedStep = defaultAddedStepOf(plan)

    /**
     * The display values of [state] at [now]; null in Idle. Remaining time is
     * rounded up, so „0:01" never means the tea is done; overtime is rounded
     * down (04 §5). Ready shows the step's planned duration; a running step
     * past its deadline shows 0 until [TimerEvent.DeadlineReached] arrives.
     */
    fun display(state: TimerState, now: ClockReading): TimerDisplay? = when (state) {
        TimerState.Idle -> null
        is TimerState.Ready -> TimerDisplay(state.plannedMs / MS_PER_SECOND, 0)
        is TimerState.Running -> TimerDisplay(ceilSeconds(state.deadlineElapsedMs - now.elapsedMs), 0)
        is TimerState.Paused -> TimerDisplay(ceilSeconds(state.remainingMs), 0)
        is TimerState.Alerting -> TimerDisplay(0, floorSeconds(now.elapsedMs - state.deadlineElapsedMs))
        is TimerState.Finished -> TimerDisplay(0, 0)
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
}
