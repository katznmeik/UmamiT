package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopAlert
import com.umamit.core.timer.engine.TimerEffect.StopForeground
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification

/*
 * The timeouts of 04 §3.2 that end or change a session without a user event,
 * each accepted only when DueTimes says it is due. Recovery (04 §7.2) applies
 * them silently. None of them is an interaction.
 */

/** Unacknowledged time after the deadline after which the step counts as poured on time (04 §3.2). */
internal const val OVERTIME_TIMEOUT_MS = 10 * 60_000L

/** True when the overtime timeout of a deadline at [deadlineElapsedMs] has passed at [now]. */
internal fun isOvertimeTimedOut(deadlineElapsedMs: Long, now: ClockReading) =
    now.elapsedMs >= deadlineElapsedMs + OVERTIME_TIMEOUT_MS

/**
 * When the silent end of [this] alert comes (04 §3.2, §9.3; D-098): a brew alert 10 min after its deadline; a
 * quick alert 10 min after it last started ringing, and never while it is held.
 */
internal val TimerState.Alerting.overtimeTimeoutAtElapsedMs: Long?
    get() = when (session.kind) {
        is SessionKind.Brew -> deadlineElapsedMs + OVERTIME_TIMEOUT_MS
        SessionKind.Quick -> ringingSinceElapsedMs?.plus(OVERTIME_TIMEOUT_MS)
    }

/** True when the silent end of [this] alert has come at [now]. */
internal fun TimerState.Alerting.isTimedOut(now: ClockReading): Boolean =
    overtimeTimeoutAtElapsedMs?.let { now.elapsedMs >= it } ?: false

/**
 * The overtime timeout (04 §3.2 `overtimeTimeout`): a step left unacknowledged
 * for 10 min after its deadline counts as poured on time, with the auto-ack
 * flag, and the session waits for the next step. Not an interaction, and
 * silent: no alert, no saved sheet. The quick timer just ends.
 */
internal fun overtimeTimedOut(state: TimerState.WithDeadline, now: ClockReading): TimerTransition =
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

/**
 * `idleDemote` (TMR-14, 04 §3.2): the state stays, the session is marked
 * demoted, the foreground service stops (which removes notifications 1 and 2)
 * and the notification is posted again as a regular ongoing one.
 */
internal fun idleDemote(state: TimerState.Waiting): TimerTransition =
    accepted(state.withSession(state.session.copy(demoted = true)), side = listOf(StopForeground, UpdateNotification))

/** This waiting state with [session]. */
internal fun TimerState.Waiting.withSession(session: TimerSession): TimerState.Waiting = when (this) {
    is TimerState.Ready -> copy(session = session)
    is TimerState.Paused -> copy(session = session)
    is TimerState.Finished -> copy(session = session)
}
