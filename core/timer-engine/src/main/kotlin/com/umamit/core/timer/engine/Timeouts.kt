package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopAlert

/*
 * The timeouts of 04 §3.2 that end or change a step without a user event.
 * Recovery (04 §7.2) applies them silently; #31 adds the timeout events, their
 * due times in DueTimes, idle demote and the midnight timeout.
 */

/** Unacknowledged time after the deadline after which the step counts as poured on time (04 §3.2). */
internal const val OVERTIME_TIMEOUT_MS = 10 * 60_000L

/** True when the overtime timeout of a deadline at [deadlineElapsedMs] has passed at [now]. */
internal fun isOvertimeTimedOut(deadlineElapsedMs: Long, now: ClockReading) =
    now.elapsedMs >= deadlineElapsedMs + OVERTIME_TIMEOUT_MS

/**
 * The overtime timeout (04 §3.2 `overtimeTimeout`): a step left unacknowledged
 * for 10 min after its deadline counts as poured on time, with the auto-ack
 * flag, and the session waits for the next step. Not an interaction, and
 * silent: no alert, no saved sheet. The quick timer just ends.
 */
internal fun overtimeTimedOut(state: TimerState.StepStarted, now: ClockReading): TimerTransition =
    if (state.session.kind == SessionKind.Quick) {
        endQuickTimer(state)
    } else {
        moveOn(
            state,
            StepStatus.DONE,
            autoAckRecord(state),
            now,
            leaveEffects = listOfNotNull(StopAlert.takeIf { state is TimerState.Alerting }, StartForeground),
            interaction = false,
        )
    }
