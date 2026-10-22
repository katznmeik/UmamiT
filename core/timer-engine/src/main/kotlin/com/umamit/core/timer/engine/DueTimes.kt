package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading

/**
 * When the time-based events of a state are due (04 §6.2). Each field is one
 * alarm type with a fixed request code; null means "not due in this state"
 * (cancel it). The controller schedules alarms and its in-process wake-up from
 * this set after every reduce, and recovery recomputes it from a persisted
 * state. A time-based event is accepted only when it is due at `now`.
 *
 * #31 adds the overtime timeout, idle demote and midnight times as further
 * nullable fields.
 *
 * @property preAlertAtElapsedMs the pre-alert tick of the running step, until it played.
 * @property deadlineElapsedMs the deadline of the running step.
 * @property quickCancelAtWallMs the quick timer's inactivity cancel in Ready or Paused, 30 min after the
 *   last interaction (04 §10), on the wall clock like the other waiting timeouts.
 */
data class DueTimes(
    val preAlertAtElapsedMs: Long? = null,
    val deadlineElapsedMs: Long? = null,
    val quickCancelAtWallMs: Long? = null,
) {
    companion object {
        /** Nothing due: every alarm cancelled. */
        val NONE = DueTimes()
    }
}

/** Inactivity after which an unstarted or paused quick timer is cancelled (04 §10). */
internal const val QUICK_TIMER_IDLE_MS = 30 * 60_000L

/** True when a time-based event due at [dueAtElapsedMs] (null: not due in this state) is due at [now]. */
internal fun isDue(dueAtElapsedMs: Long?, now: ClockReading) = dueAtElapsedMs != null && now.elapsedMs >= dueAtElapsedMs

/** [isDue] for an event due on the wall clock at [dueAtWallMs]. */
internal fun isDueWall(dueAtWallMs: Long?, now: ClockReading) = dueAtWallMs != null && now.wallMs >= dueAtWallMs

internal fun dueTimesOf(state: TimerState): DueTimes = when (state) {
    is TimerState.Running -> DueTimes(
        preAlertAtElapsedMs = state.preAlertAtElapsedMs.takeUnless { state.preAlertPlayed },
        deadlineElapsedMs = state.deadlineElapsedMs,
    )

    is TimerState.Ready, is TimerState.Paused -> DueTimes(quickCancelAtWallMs = quickCancelAt(state.session))

    TimerState.Idle,
    is TimerState.Alerting,
    is TimerState.Finished,
    -> DueTimes.NONE
}

private fun quickCancelAt(session: TimerSession): Long? =
    (session.lastInteractionWallMs + QUICK_TIMER_IDLE_MS).takeIf { session.kind == SessionKind.Quick }
