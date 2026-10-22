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

/** Global pre-alert values the settings offer (SET-10): off, or 5–30 s. */
private val PRE_ALERT_SECONDS = 5..30

/**
 * Starts the quick timer, or rejects a duration outside 5 s – 24 h or a
 * global pre-alert other than 0 or 5–30 s with [RejectionReason.InvalidQuickTimer].
 */
internal fun startQuickTimer(event: TimerEvent.StartQuickTimer, now: ClockReading): TimerTransition {
    val valid = event.durationSeconds in MIN_STEP_SECONDS..MAX_STEP_SECONDS &&
        (event.globalPreAlertSeconds == 0 || event.globalPreAlertSeconds in PRE_ALERT_SECONDS)
    if (!valid) {
        return TimerTransition(
            TimerState.Idle,
            emptyList(),
            TransitionOutcome.Rejected(RejectionReason.InvalidQuickTimer),
        )
    }
    val session = TimerSession(
        kind = SessionKind.Quick,
        steps = listOf(SessionStep(StepKind.INFUSION, event.durationSeconds, null, StepStatus.PENDING, null)),
        preAlert = PreAlertSettings(sessionOverrideSeconds = null, inheritedSeconds = event.globalPreAlertSeconds),
        zone = now.zone,
        lastInteractionWallMs = now.wallMs,
        bootCount = now.bootCount,
        demoted = false,
        longStepsOnly = false,
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
internal fun endQuickTimer(state: TimerState.Active): TimerTransition = accepted(
    TimerState.Idle,
    side = listOfNotNull(StopAlert.takeIf { state is TimerState.Alerting }, StopForeground),
)
