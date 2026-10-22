package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.timer.engine.TimerEffect.CancelAlarms
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.RecordStep
import com.umamit.core.timer.engine.TimerEffect.ScheduleAlarms
import com.umamit.core.timer.engine.TimerEffect.StartAlert
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopAlert
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification

/*
 * The step lifecycle transitions of 04 §3.2, called by BrewTimerEngine.reduce
 * only for the states that allow them. Every user event updates
 * lastInteractionWallMs; the deadline does not.
 */

internal fun startSession(event: TimerEvent.StartSession, now: ClockReading): TimerTransition {
    val session = TimerSession(
        sessionId = event.sessionId,
        steps = event.steps.map { SessionStep(it.kind, it.plannedSeconds, it.tempC) },
        preAlert = event.preAlert,
        zone = now.zone,
        lastInteractionWallMs = now.wallMs,
    )
    return TimerTransition(
        TimerState.Ready(session, stepIndex = 0, readySinceWallMs = now.wallMs),
        listOf(Persist, StartForeground, UpdateNotification),
    )
}

internal fun start(state: TimerState.Ready, now: ClockReading): TimerTransition {
    val running = TimerState.Running(
        session = state.session.touched(now).withStep(state.stepIndex) { it.copy(status = StepStatus.RUNNING) },
        stepIndex = state.stepIndex,
        stepStartedWallMs = now.wallMs,
        pausedTotalMs = 0,
        deadlineElapsedMs = now.elapsedMs + state.plannedMs,
        deadlineWallMs = now.wallMs + state.plannedMs,
    )
    return TimerTransition(running, listOf(Persist, running.alarms(), UpdateNotification))
}

internal fun pause(state: TimerState.Running, now: ClockReading): TimerTransition {
    val paused = TimerState.Paused(
        session = state.session.touched(now),
        stepIndex = state.stepIndex,
        stepStartedWallMs = state.stepStartedWallMs,
        pausedTotalMs = state.pausedTotalMs,
        remainingMs = state.deadlineElapsedMs - now.elapsedMs,
        pausedSinceElapsedMs = now.elapsedMs,
        pausedSinceWallMs = now.wallMs,
        preAlertPlayed = state.preAlertPlayed,
    )
    return TimerTransition(paused, listOf(Persist, CancelAlarms, UpdateNotification))
}

internal fun resume(state: TimerState.Paused, now: ClockReading): TimerTransition {
    val running = TimerState.Running(
        session = state.session.touched(now),
        stepIndex = state.stepIndex,
        stepStartedWallMs = state.stepStartedWallMs,
        pausedTotalMs = state.pausedTotalMs + (now.elapsedMs - state.pausedSinceElapsedMs),
        deadlineElapsedMs = now.elapsedMs + state.remainingMs,
        deadlineWallMs = now.wallMs + state.remainingMs,
        preAlertPlayed = state.preAlertPlayed,
    )
    return TimerTransition(running, listOf(Persist, running.alarms(), UpdateNotification))
}

internal fun deadlineReached(state: TimerState.Running): TimerTransition {
    val alerting = TimerState.Alerting(
        session = state.session,
        stepIndex = state.stepIndex,
        stepStartedWallMs = state.stepStartedWallMs,
        pausedTotalMs = state.pausedTotalMs,
        deadlineElapsedMs = state.deadlineElapsedMs,
        deadlineWallMs = state.deadlineWallMs,
    )
    return TimerTransition(alerting, listOf(Persist, StartAlert, UpdateNotification))
}

internal fun poured(state: TimerState.Alerting, now: ClockReading): TimerTransition = moveOn(
    state.session.touched(now),
    state.stepIndex,
    StepStatus.DONE,
    pouredRecord(state, now),
    now,
    leaveEffects = listOf(StopAlert),
)

internal fun endStepNow(state: TimerState.StepStarted, now: ClockReading): TimerTransition = moveOn(
    state.session.touched(now),
    state.stepIndex,
    StepStatus.DONE,
    endedRecord(state, now),
    now,
    leaveEffects = if (state is TimerState.Running) listOf(CancelAlarms) else emptyList(),
)

internal fun skipStep(state: TimerState.Ready, now: ClockReading): TimerTransition = moveOn(
    state.session.touched(now),
    state.stepIndex,
    StepStatus.SKIPPED,
    skippedRecord(state),
    now,
    leaveEffects = emptyList(),
)

/**
 * Ends step [stepIndex] with [status] and [record], then waits in Ready for
 * the next pending step, or in Finished when none is left.
 */
@Suppress("LongParameterList") // the transition's inputs; a holder class would only rename them
private fun moveOn(
    touchedSession: TimerSession,
    stepIndex: Int,
    status: StepStatus,
    record: StepRecord,
    now: ClockReading,
    leaveEffects: List<TimerEffect>,
): TimerTransition {
    val session = touchedSession.withStep(stepIndex) { it.copy(status = status, record = record) }
    val next = session.steps.indexOfFirst { it.status == StepStatus.PENDING }
    val nextState = if (next >= 0) {
        TimerState.Ready(session, next, readySinceWallMs = now.wallMs)
    } else {
        TimerState.Finished(session, readySinceWallMs = now.wallMs)
    }
    val recordEffect = session.sessionId?.let { RecordStep(it, stepIndex, record) }
    return TimerTransition(nextState, listOfNotNull(recordEffect, Persist) + leaveEffects + UpdateNotification)
}

private fun TimerState.Running.alarms() = ScheduleAlarms(deadlineElapsedMs, preAlertAtElapsedMs)
