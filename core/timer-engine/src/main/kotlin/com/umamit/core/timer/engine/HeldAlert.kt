package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.timer.engine.TimerEffect.StartAlert
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopAlert
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification

/*
 * The held alert of 04 §3.3 and §9.3 (D-098): only a quick timer is held, while the brew alerts. A held alert
 * has no sound, no alert notification and no silent end; its overtime still runs from its deadline. The
 * controller sends `alertHold` and `alertRingStart` ([AlertArbitration]); neither is a user interaction.
 */

/** `alertHold` and `alertRingStart` of a quick timer in [state] at [now]; null where they do not apply. */
internal fun onArbitration(state: TimerState.Active, event: TimerEvent, now: ClockReading): TimerTransition? =
    when (event) {
        TimerEvent.AlertHold -> when (state) {
            is TimerState.Running -> holdAtDeadline(state).takeIf { isDue(state.deadlineElapsedMs, now) }
            is TimerState.Alerting -> if (state.held) null else holdAgain(state)
            else -> null
        }

        TimerEvent.AlertRingStart -> (state as? TimerState.Alerting)?.takeIf { it.held }?.let { ringStart(it, now) }

        else -> null
    }

/**
 * The quick deadline while the brew alerts: ALERTING held, however late (a quick timer that could not ring is
 * never ended silently). The service is ensured as for any alert, and the notification shows it waiting.
 */
internal fun holdAtDeadline(state: TimerState.Running): TimerTransition = accepted(
    alertingOf(state, ringingSinceElapsedMs = null, ringingSinceWallMs = null),
    side = listOf(StartForeground, UpdateNotification),
)

/** Preemption: the brew alert takes over, so the ringing quick alert stops and waits again, not reported played. */
internal fun holdAgain(state: TimerState.Alerting): TimerTransition = accepted(
    state.copy(alertPlayed = false, ringingSinceElapsedMs = null, ringingSinceWallMs = null),
    side = listOf(StopAlert, UpdateNotification),
)

/**
 * Release: the brew left ALERTING, so the held alert rings now, its full length (SET-14) and its silent end
 * 10 min from now; the notification takes the screen (full-screen intent) as at a deadline.
 */
internal fun ringStart(state: TimerState.Alerting, now: ClockReading): TimerTransition = accepted(
    state.copy(alertPlayed = false, ringingSinceElapsedMs = now.elapsedMs, ringingSinceWallMs = now.wallMs),
    side = listOf(StartForeground, StartAlert, UpdateNotification),
)

/** The step of [state] alerting since its deadline, ringing since the given time, or held (null). */
internal fun alertingOf(state: TimerState.Running, ringingSinceElapsedMs: Long?, ringingSinceWallMs: Long?) =
    TimerState.Alerting(
        session = state.session,
        stepIndex = state.stepIndex,
        stepStartedWallMs = state.stepStartedWallMs,
        pausedTotalMs = state.pausedTotalMs,
        deadlineElapsedMs = state.deadlineElapsedMs,
        deadlineWallMs = state.deadlineWallMs,
        alertPlayed = false,
        ringingSinceElapsedMs = ringingSinceElapsedMs,
        ringingSinceWallMs = ringingSinceWallMs,
    )
