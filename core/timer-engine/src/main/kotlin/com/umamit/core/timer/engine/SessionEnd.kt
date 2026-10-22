package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.timer.engine.TimerEffect.CancelAlarms
import com.umamit.core.timer.engine.TimerEffect.CompleteSession
import com.umamit.core.timer.engine.TimerEffect.DiscardSession
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.RecordStep
import com.umamit.core.timer.engine.TimerEffect.ShowSavedSheet
import com.umamit.core.timer.engine.TimerEffect.StopAlert
import com.umamit.core.timer.engine.TimerEffect.StopForeground

/**
 * *Sitzung beenden* (TMR-12): a started step ends as by *Aufguss jetzt
 * beenden* (Running, Paused) or *Abgegossen* (Alerting). Then the session is
 * completed if a non-rinse step was completed, and discarded otherwise, in
 * which case nothing is recorded (the UI confirms the discard first).
 */
internal fun finishSession(state: TimerState.Active, now: ClockReading): TimerTransition {
    val ended = (state as? TimerState.StepStarted)?.let { it.stepIndex to endedRecord(it, now) }
    val session = ended?.let { (index, record) ->
        state.session.withStep(index) { it.copy(status = StepStatus.DONE, record = record) }
    } ?: state.session
    if (!session.hasCompletedBrewStep) return discard(state)

    val sessionId = session.sessionId
    val recordEffect = ended?.let { (index, record) -> sessionId?.let { RecordStep(it, index, record) } }
    val effects = listOfNotNull(recordEffect, sessionId?.let(::CompleteSession)) +
        sessionEndEffects(state) +
        listOfNotNull(sessionId?.let(::ShowSavedSheet))
    return TimerTransition(TimerState.Idle, effects)
}

/** Discard (TMR-13): the session and its stock deduction are deleted; nothing is recorded. */
internal fun discard(state: TimerState.Active): TimerTransition = TimerTransition(
    TimerState.Idle,
    listOfNotNull(state.session.sessionId?.let(::DiscardSession)) + sessionEndEffects(state),
)

/** Leaving an active state for Idle: clear `active_brew`, cancel the alarms, stop the alert and the service. */
private fun sessionEndEffects(state: TimerState.Active): List<TimerEffect> = listOfNotNull(
    Persist,
    CancelAlarms,
    StopAlert.takeIf { state is TimerState.Alerting },
    StopForeground,
)
