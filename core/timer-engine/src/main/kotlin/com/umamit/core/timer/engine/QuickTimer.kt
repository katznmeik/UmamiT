package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopAlert
import com.umamit.core.timer.engine.TimerEffect.StopForeground
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification

/*
 * The quick timer of 04 §10 (TMR-22): the same states and step transitions as
 * a session with one INFUSION step, but no journal row. *Fertig* (`poured`)
 * and *Abbrechen* (`cancel`) end it; endStepNow, skipStep, addStep, editSteps,
 * finishSession and discard do not apply.
 */

internal fun startQuickTimer(event: TimerEvent.StartQuickTimer, now: ClockReading): TimerTransition {
    val session = TimerSession(
        kind = SessionKind.Quick,
        steps = listOf(SessionStep(StepKind.INFUSION, event.durationSeconds, null, StepStatus.PENDING, null)),
        preAlert = PreAlertSettings(sessionOverrideSeconds = null, inheritedSeconds = event.globalPreAlertSeconds),
        zone = now.zone,
        lastInteractionWallMs = now.wallMs,
    )
    return accepted(
        TimerState.Ready(session, stepIndex = 0, readySinceWallMs = now.wallMs),
        side = listOf(StartForeground, UpdateNotification),
    )
}

/** The quick-timer events: *Fertig* while alerting, *Abbrechen* and the inactivity cancel before that. */
internal fun onQuickEvent(state: TimerState.Active, event: TimerEvent, now: ClockReading): TimerTransition? =
    when (event) {
        TimerEvent.Poured -> if (state is TimerState.Alerting) endQuickTimer(state) else null

        TimerEvent.Cancel -> if (state is TimerState.Alerting) null else endQuickTimer(state)

        TimerEvent.QuickTimerTimeout -> if (isDueWall(dueTimesOf(state).quickCancelAtWallMs, now)) {
            endQuickTimer(state)
        } else {
            null
        }

        else -> null
    }

/** The quick timer ends: nothing is recorded; the alert and the service stop. */
private fun endQuickTimer(state: TimerState.Active): TimerTransition = accepted(
    TimerState.Idle,
    side = listOfNotNull(StopAlert.takeIf { state is TimerState.Alerting }, StopForeground),
)
