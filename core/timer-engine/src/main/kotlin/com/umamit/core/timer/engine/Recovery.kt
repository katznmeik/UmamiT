package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.timer.engine.TimerEffect.StartAlert
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification

/*
 * Recovery on process start (04 §7.2, §7.3, §7.5). The controller loads the
 * persisted state and sends `recover` before any other event. After a reboot
 * the elapsed clock restarted, so the elapsed values are first rebased from
 * the wall values; on the same boot the monotonic values still hold. Then the
 * recovery table applies. Its timeout rows are silent: no alert, no saved
 * sheet. Every row tries to restart the foreground service ("ensure").
 */

internal fun recover(state: TimerState.Active, now: ClockReading): TimerTransition {
    val rebased = state.rebasedTo(now)
    return when (rebased) {
        // #31 adds the midnight row (a waiting session past its midnight threshold) here.
        is TimerState.Ready, is TimerState.Paused, is TimerState.Finished -> restored(rebased)

        is TimerState.Running -> when {
            now.elapsedMs < rebased.deadlineElapsedMs -> restored(rebased)
            isOvertimeTimedOut(rebased.deadlineElapsedMs, now) -> overtimeTimedOut(rebased, now)
            else -> deadlineReached(rebased)
        }

        is TimerState.Alerting -> when {
            isOvertimeTimedOut(rebased.deadlineElapsedMs, now) -> overtimeTimedOut(rebased, now)
            rebased.alertPlayed -> restored(rebased)
            else -> accepted(rebased, side = listOf(StartForeground, StartAlert, UpdateNotification))
        }
    }
}

/** The state as it was: alarms re-armed from its due times, the service and the notification restored. */
private fun restored(state: TimerState.Active): TimerTransition =
    accepted(state, side = listOf(StartForeground, UpdateNotification))

/**
 * This state on the boot of [now]. Unchanged on the same boot. After a reboot
 * each elapsed value is moved to the new elapsed clock at the same distance
 * from now as its wall value (04 §5: wall values serve reboot recovery).
 */
private fun TimerState.Active.rebasedTo(now: ClockReading): TimerState.Active {
    if (session.bootCount == now.bootCount) return this
    val session = session.copy(bootCount = now.bootCount)
    fun elapsedAt(wallMs: Long) = now.elapsedMs + (wallMs - now.wallMs)
    return when (this) {
        is TimerState.Ready -> copy(session = session)

        is TimerState.Finished -> copy(session = session)

        is TimerState.Running -> {
            val deadline = elapsedAt(deadlineWallMs)
            copy(
                session = session,
                deadlineElapsedMs = deadline,
                preAlertAtElapsedMs = preAlertAtElapsedMs?.let { it - deadlineElapsedMs + deadline },
            )
        }

        is TimerState.Paused -> copy(session = session, pausedSinceElapsedMs = elapsedAt(pausedSinceWallMs))

        is TimerState.Alerting -> copy(session = session, deadlineElapsedMs = elapsedAt(deadlineWallMs))
    }
}
