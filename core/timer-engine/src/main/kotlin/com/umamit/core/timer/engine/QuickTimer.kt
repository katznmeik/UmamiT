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

/** Pre-alert leads the settings (SET-10) and SCR-07's selector offer, besides 0 (off): 5–30 s. */
private val PRE_ALERT_SECONDS = 5..30

/** [seconds] is a pre-alert the settings or SCR-07's selector can hold: 0 (*Aus*) or 5–30 s. */
internal fun isQuickPreAlert(seconds: Int): Boolean = seconds == 0 || seconds in PRE_ALERT_SECONDS

/** A quick timer of [durationSeconds], global [globalSeconds] and choice [choiceSeconds] may start (04 §10). */
private fun isValidQuickTimer(durationSeconds: Int, globalSeconds: Int, choiceSeconds: Int?): Boolean =
    durationSeconds in MIN_STEP_SECONDS..MAX_STEP_SECONDS &&
        isQuickPreAlert(globalSeconds) &&
        (choiceSeconds?.let(::isQuickPreAlert) ?: true)

/** [BrewTimerEngine.quickTimerPreAlertLeadSeconds]: the lead of the one step [startQuickTimer] would plan. */
internal fun quickTimerPreAlertLeadOf(durationSeconds: Int, globalSeconds: Int, choiceSeconds: Int?): Int? =
    if (isValidQuickTimer(durationSeconds, globalSeconds, choiceSeconds)) {
        val step = PlannedStep(StepKind.INFUSION, durationSeconds, tempC = null, choiceSeconds)
        preAlertLeadSecondsOf(
            step,
            PreAlertSettings(globalSeconds),
            isLongStepOf(durationSeconds, longStepsOnly = false),
        )
    } else {
        null
    }

/**
 * Starts the quick timer, or rejects a duration outside 5 s – 24 h, or a
 * global pre-alert or a choice other than 0 or 5–30 s, with
 * [RejectionReason.InvalidQuickTimer] (04 §10, D-097).
 */
internal fun startQuickTimer(event: TimerEvent.StartQuickTimer, now: ClockReading): TimerTransition {
    if (!isValidQuickTimer(event.durationSeconds, event.globalPreAlertSeconds, event.preAlertSeconds)) {
        return TimerTransition(
            TimerState.Idle,
            emptyList(),
            TransitionOutcome.Rejected(RejectionReason.InvalidQuickTimer),
        )
    }
    val session = TimerSession(
        kind = SessionKind.Quick,
        // Its one step carries the choice of SCR-07; *Automatisch* uses B = the global setting (TMR-22, D-097).
        steps = listOf(
            SessionStep(
                StepKind.INFUSION,
                event.durationSeconds,
                null,
                StepStatus.PENDING,
                null,
                event.preAlertSeconds,
            ),
        ),
        preAlert = PreAlertSettings(inheritedSeconds = event.globalPreAlertSeconds),
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
