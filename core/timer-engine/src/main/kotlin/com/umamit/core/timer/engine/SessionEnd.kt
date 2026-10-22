package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.timer.engine.TimerEffect.CompleteSession
import com.umamit.core.timer.engine.TimerEffect.DiscardSession
import com.umamit.core.timer.engine.TimerEffect.NotifySessionSaved
import com.umamit.core.timer.engine.TimerEffect.RecordStep
import com.umamit.core.timer.engine.TimerEffect.ShowSavedSheet
import com.umamit.core.timer.engine.TimerEffect.StopAlert
import com.umamit.core.timer.engine.TimerEffect.StopForeground

/**
 * *Sitzung beenden* (TMR-12): a started step ends as by *Aufguss jetzt
 * beenden* (Running, Paused) or *Abgegossen* (Alerting). Then the session is
 * completed if a non-rinse step was completed, and discarded otherwise, in
 * which case nothing is recorded (the UI confirms the discard first, using
 * [BrewTimerEngine.finishWouldDiscard]).
 */
internal fun finishSession(state: TimerState.Active, now: ClockReading): TimerTransition {
    if (!finishCompletes(state)) return discard(state)
    val sessionId = state.session.brewSessionId
    val recordEffect = (state as? TimerState.StepStarted)?.let { started ->
        sessionId?.let { RecordStep(it, started.stepIndex, endedRecord(started, now)) }
    }
    return accepted(
        TimerState.Idle,
        data = listOfNotNull(recordEffect, sessionId?.let(::CompleteSession)),
        side = sessionEndEffects(state) + listOfNotNull(sessionId?.let(::ShowSavedSheet)),
    )
}

/** Discard (TMR-13): the session and its stock deduction are deleted; nothing is recorded. */
internal fun discard(state: TimerState.Active): TimerTransition = accepted(
    TimerState.Idle,
    data = listOfNotNull(state.session.brewSessionId?.let(::DiscardSession)),
    side = sessionEndEffects(state),
)

/** True when finishing [state] completes the session: a non-rinse step is done, counting a started one as done. */
internal fun finishCompletes(state: TimerState.Active): Boolean {
    val session = if (state is TimerState.StepStarted) {
        state.session.withStep(state.stepIndex) { it.copy(status = StepStatus.DONE) }
    } else {
        state.session
    }
    return session.hasCompletedBrewStep
}

/**
 * The midnight timeout (TMR-14): completes the session with the notification
 * „Sitzung gespeichert" if a non-rinse step was completed, a paused step
 * counting as completed, else discards it silently. A paused step ends where
 * its pause began, so the open pause is not recorded. No saved sheet.
 */
internal fun midnightTimeout(state: TimerState.Waiting): TimerTransition {
    val sessionId = state.session.brewSessionId
    if (sessionId == null || !finishCompletes(state)) return discard(state)
    val recordEffect = (state as? TimerState.Paused)?.let { RecordStep(sessionId, it.stepIndex, pauseEndedRecord(it)) }
    return accepted(
        TimerState.Idle,
        data = listOfNotNull(recordEffect, CompleteSession(sessionId)),
        side = listOf(StopForeground, NotifySessionSaved(sessionId)),
    )
}

/** Leaving an active state for Idle: stop the alert and the service (which removes the timer notifications). */
private fun sessionEndEffects(state: TimerState.Active): List<TimerEffect> =
    listOfNotNull(StopAlert.takeIf { state is TimerState.Alerting }, StopForeground)
