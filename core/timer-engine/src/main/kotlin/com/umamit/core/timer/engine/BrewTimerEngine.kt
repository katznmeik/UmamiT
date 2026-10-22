package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.model.BrewMethod

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

    /**
     * The clock reading comes from another boot than the state's elapsed
     * values; the controller has to send [TimerEvent.Recover] first (04 §7.2).
     */
    data object BootChanged : RejectionReason

    /** `startQuickTimer` with a duration outside 5 s – 24 h or a pre-alert other than 0 or 5–30 s (04 §10). */
    data object InvalidQuickTimer : RejectionReason
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
     * step. In an active state, a reading from another boot than the state's
     * is rejected until [TimerEvent.Recover] has rebased the state (04 §7.2).
     */
    fun reduce(state: TimerState, event: TimerEvent, now: ClockReading): TimerTransition = dispatch(state, event, now)

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
     * The persisted form of [state] (04 §7.1, 05 §3.12): the `active_brew`
     * row and the session's step rows; null in Idle (the row is cleared).
     * [restore] maps it back to an equal state.
     */
    fun snapshot(state: TimerState): TimerSnapshot? = snapshotOf(state)

    /**
     * The state persisted as [snapshot]; Idle for none. Throws
     * [IllegalArgumentException] for an inconsistent snapshot, which the
     * controller logs and clears instead of running a wrong timer. After a
     * process start the controller sends [TimerEvent.Recover] next.
     */
    fun restore(snapshot: TimerSnapshot?): TimerState = stateOf(snapshot)

    /**
     * The step *Weiterer Aufguss* appends to [plan] (TMR-10): the last non-rinse
     * step's kind and temperature, 1.5 × its duration rounded half up to 5 s
     * below 10 min and to whole minutes from 10 min on, within 5 s – 24 h.
     * The Schritte-Sheet uses it for *Aufguss hinzufügen*.
     */
    fun defaultAddedStep(plan: List<PlannedStep>): PlannedStep = defaultAddedStepOf(plan)

    /**
     * True for a brew [method] whose steps are all long steps however short
     * (cold and ice brew, TMR-18): the value of [TimerEvent.StartSession.longStepsOnly]
     * and [TimerSnapshot.longStepsOnly].
     */
    fun longStepsOnly(method: BrewMethod): Boolean = method == BrewMethod.COLD_BREW || method == BrewMethod.ICE_BREW

    /**
     * True when the current step of [state] is a long step (≥ 10 min, or any
     * step of a cold or ice brew; TMR-18): it runs without the foreground
     * service and without a pre-alert, and the notification shows its end
     * („Fertig um 14:30"). False in Idle and Finished.
     */
    fun isLongStep(state: TimerState): Boolean = state is TimerState.OnStep && state.session.isLongStep(state.stepIndex)

    /**
     * The wall-clock time at which the running long step of [state] ends, for
     * „Fertig um 14:30" (TMR-18): now plus the remaining time on the monotonic
     * clock, so it follows a change of the wall clock (E1). Null unless a long
     * step is running.
     */
    fun longStepEndWallMs(state: TimerState, now: ClockReading): Long? =
        if (state is TimerState.Running && isLongStep(state)) {
            now.wallMs + (state.deadlineElapsedMs - now.elapsedMs)
        } else {
            null
        }

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
}
