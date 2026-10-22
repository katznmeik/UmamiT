package com.umamit.core.timer.engine

/**
 * When the time-based events of a state are due (04 §6.2). Each field is one
 * alarm type with a fixed request code; null means "not due in this state"
 * (cancel it). The controller schedules alarms and its in-process wake-up from
 * this set after every reduce, and recovery recomputes it from a persisted
 * state. A time-based event is accepted only when it is due at `now`.
 *
 * #31 adds the overtime timeout, idle demote, midnight and quick-timer cancel
 * times as further nullable fields.
 *
 * @property preAlertAtElapsedMs the pre-alert tick of the running step, until it played.
 * @property deadlineElapsedMs the deadline of the running step.
 */
data class DueTimes(val preAlertAtElapsedMs: Long? = null, val deadlineElapsedMs: Long? = null) {
    companion object {
        /** Nothing due: every alarm cancelled. */
        val NONE = DueTimes()
    }
}

internal fun dueTimesOf(state: TimerState): DueTimes = when (state) {
    is TimerState.Running -> DueTimes(
        preAlertAtElapsedMs = state.preAlertAtElapsedMs.takeUnless { state.preAlertPlayed },
        deadlineElapsedMs = state.deadlineElapsedMs,
    )

    TimerState.Idle,
    is TimerState.Ready,
    is TimerState.Paused,
    is TimerState.Alerting,
    is TimerState.Finished,
    -> DueTimes.NONE
}
