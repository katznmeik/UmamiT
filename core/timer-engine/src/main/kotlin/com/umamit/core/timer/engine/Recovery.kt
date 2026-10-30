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
 * sheet. Every restored state tries to restart the foreground service
 * ("ensure"), except a demoted session and a long running step, which run
 * without it (D-053).
 */

internal fun recover(state: TimerState.Active, now: ClockReading, holdAlert: Boolean = false): TimerTransition {
    val rebased = state.rebasedTo(now)
    return when (rebased) {
        is TimerState.Waiting -> recoverWaiting(rebased, now)

        is TimerState.Running -> when {
            now.elapsedMs < rebased.deadlineElapsedMs -> restored(rebased)

            // The brew alerts (04 §7.2 two slots): the quick timer waits for it, however late (D-098).
            holdAlert -> holdAtDeadline(rebased)

            isOvertimeTimedOut(rebased.deadlineElapsedMs, now) -> overtimeTimedOut(rebased, now)

            else -> deadlineReached(rebased)
        }

        is TimerState.Alerting -> when {
            holdAlert && !rebased.held -> accepted(
                rebased.copy(alertPlayed = false, ringingSinceElapsedMs = null, ringingSinceWallMs = null),
                side = listOf(StartForeground, UpdateNotification),
            )

            rebased.isTimedOut(now) -> overtimeTimedOut(rebased, now)

            // A held alert stays held; the controller releases it once the brew no longer alerts (04 §9.3).
            rebased.alertPlayed || rebased.held -> restored(rebased)

            else -> accepted(rebased, side = listOf(StartForeground, StartAlert, UpdateNotification))
        }
    }
}

/**
 * A waiting state applies the waiting timeouts that came while the process
 * was dead, the one that ends the session first: the quick-timer cancel, the
 * midnight timeout (TMR-14), then idle demote.
 */
private fun recoverWaiting(state: TimerState.Waiting, now: ClockReading): TimerTransition {
    val due = dueTimesOf(state)
    return when {
        isDueWall(due.quickCancelAtWallMs, now) -> endQuickTimer(state)
        isDueWall(due.midnightAtWallMs, now) -> midnightTimeout(state)
        isDueWall(due.idleDemoteAtWallMs, now) -> idleDemote(state)
        else -> restored(state)
    }
}

/**
 * The state as it was: alarms re-armed from its due times, the notification
 * restored, and the service where the state needs it.
 */
private fun restored(state: TimerState.Active): TimerTransition =
    accepted(state, side = listOfNotNull(StartForeground.takeIf { state.needsService }, UpdateNotification))

/**
 * This state on the boot of [now]. Unchanged on the same boot while it is
 * possible there. After a reboot, or when the state is impossible on this
 * elapsed clock (a reboot whose boot count did not change), each elapsed value
 * is moved to the new elapsed clock at the same distance from now as its wall
 * value (04 §5: wall values serve reboot recovery), within physical bounds:
 * every instant of the old boot lies before elapsed 0, so a pause started and
 * a deadline passed at or before 0, and a running step had at most its planned
 * time left at 0. The bounds catch a wall clock set back across the reboot; a
 * wall clock set forward cannot be told apart from a longer time off (D-048).
 */
private fun TimerState.Active.rebasedTo(now: ClockReading): TimerState.Active {
    if (session.bootCount == now.bootCount && isPossibleAt(now)) return this
    val session = session.copy(bootCount = now.bootCount)
    fun elapsedAt(wallMs: Long) = now.elapsedMs + (wallMs - now.wallMs)
    return when (this) {
        is TimerState.Ready -> copy(session = session)

        is TimerState.Finished -> copy(session = session)

        is TimerState.Running -> {
            val deadline = minOf(elapsedAt(deadlineWallMs), plannedMs)
            copy(
                session = session,
                deadlineElapsedMs = deadline,
                preAlertAtElapsedMs = preAlertAtElapsedMs?.let { it - deadlineElapsedMs + deadline },
            )
        }

        is TimerState.Paused -> copy(session = session, pausedSinceElapsedMs = minOf(elapsedAt(pausedSinceWallMs), 0))

        is TimerState.Alerting -> copy(
            session = session,
            deadlineElapsedMs = minOf(elapsedAt(deadlineWallMs), 0),
            ringingSinceElapsedMs = ringingSinceWallMs?.let { minOf(elapsedAt(it), 0) },
        )
    }
}

/**
 * False for elapsed values no state of this elapsed clock can have: a time after now (a deadline, a pause, a
 * ringing start), more than planned left.
 */
private fun TimerState.Active.isPossibleAt(now: ClockReading): Boolean = when (this) {
    is TimerState.Running -> deadlineElapsedMs - now.elapsedMs <= plannedMs
    is TimerState.Paused -> pausedSinceElapsedMs <= now.elapsedMs
    is TimerState.Alerting -> deadlineElapsedMs <= now.elapsedMs && (ringingSinceElapsedMs ?: 0) <= now.elapsedMs
    is TimerState.Ready, is TimerState.Finished -> true
}
