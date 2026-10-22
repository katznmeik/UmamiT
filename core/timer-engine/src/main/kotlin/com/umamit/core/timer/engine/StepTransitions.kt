package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.timer.engine.TimerEffect.CreateSession
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
 * lastInteractionWallMs; the deadline and the alert completion do not.
 */

/**
 * An accepted transition to [state]: the data effects [data], one [Persist],
 * one [ScheduleAlarms] with the due times of [state], then the [side] effects.
 */
internal fun accepted(
    state: TimerState,
    data: List<TimerEffect> = emptyList(),
    side: List<TimerEffect> = emptyList(),
): TimerTransition = TimerTransition(
    state,
    data + Persist + ScheduleAlarms(dueTimesOf(state)) + side,
    TransitionOutcome.Accepted,
)

internal fun startSession(event: TimerEvent.StartSession, now: ClockReading): TimerTransition {
    val session = TimerSession(
        kind = SessionKind.Brew(event.sessionId),
        steps = event.steps.map { SessionStep(it.kind, it.plannedSeconds, it.tempC, StepStatus.PENDING, null) },
        preAlert = event.preAlert,
        zone = now.zone,
        lastInteractionWallMs = now.wallMs,
    )
    return accepted(
        TimerState.Ready(session, stepIndex = 0, readySinceWallMs = now.wallMs),
        data = listOf(CreateSession(event.sessionId)),
        side = listOf(StartForeground, UpdateNotification),
    )
}

internal fun start(state: TimerState.Ready, now: ClockReading): TimerTransition = accepted(
    TimerState.Running(
        session = state.session.touched(now).withStep(state.stepIndex) { it.copy(status = StepStatus.STARTED) },
        stepIndex = state.stepIndex,
        stepStartedWallMs = now.wallMs,
        pausedTotalMs = 0,
        deadlineElapsedMs = now.elapsedMs + state.plannedMs,
        deadlineWallMs = now.wallMs + state.plannedMs,
        preAlertAtElapsedMs = null,
        preAlertPlayed = false,
    ),
    side = listOf(UpdateNotification),
)

internal fun pause(state: TimerState.Running, now: ClockReading): TimerTransition = accepted(
    TimerState.Paused(
        session = state.session.touched(now),
        stepIndex = state.stepIndex,
        stepStartedWallMs = state.stepStartedWallMs,
        pausedTotalMs = state.pausedTotalMs,
        remainingMs = state.deadlineElapsedMs - now.elapsedMs,
        pausedSinceElapsedMs = now.elapsedMs,
        pausedSinceWallMs = now.wallMs,
        preAlertPlayed = state.preAlertPlayed,
    ),
    side = listOf(UpdateNotification),
)

internal fun resume(state: TimerState.Paused, now: ClockReading): TimerTransition = accepted(
    TimerState.Running(
        session = state.session.touched(now),
        stepIndex = state.stepIndex,
        stepStartedWallMs = state.stepStartedWallMs,
        pausedTotalMs = state.pausedTotalMs + (now.elapsedMs - state.pausedSinceElapsedMs),
        deadlineElapsedMs = now.elapsedMs + state.remainingMs,
        deadlineWallMs = now.wallMs + state.remainingMs,
        preAlertAtElapsedMs = null,
        preAlertPlayed = state.preAlertPlayed,
    ),
    side = listOf(UpdateNotification),
)

/** The alert needs the service (the AlertPlayer lives there), so it is ensured first. */
internal fun deadlineReached(state: TimerState.Running): TimerTransition = accepted(
    TimerState.Alerting(
        session = state.session,
        stepIndex = state.stepIndex,
        stepStartedWallMs = state.stepStartedWallMs,
        pausedTotalMs = state.pausedTotalMs,
        deadlineElapsedMs = state.deadlineElapsedMs,
        deadlineWallMs = state.deadlineWallMs,
        alertPlayed = false,
    ),
    side = listOf(StartForeground, StartAlert, UpdateNotification),
)

internal fun alertCompleted(state: TimerState.Alerting): TimerTransition = accepted(state.copy(alertPlayed = true))

internal fun poured(state: TimerState.Alerting, now: ClockReading): TimerTransition =
    moveOn(state, StepStatus.DONE, endedRecord(state, now), now, leaveEffects = listOf(StopAlert))

internal fun endStepNow(state: TimerState.StepStarted, now: ClockReading): TimerTransition =
    moveOn(state, StepStatus.DONE, endedRecord(state, now), now, leaveEffects = emptyList())

internal fun skipStep(state: TimerState.Ready, now: ClockReading): TimerTransition =
    moveOn(state, StepStatus.SKIPPED, skippedRecord(state), now, leaveEffects = emptyList())

/**
 * Ends the current step of [state] with [status] and [record], then waits in
 * Ready for the next pending step, or in Finished when none is left.
 */
private fun moveOn(
    state: TimerState.OnStep,
    status: StepStatus,
    record: StepRecord,
    now: ClockReading,
    leaveEffects: List<TimerEffect>,
): TimerTransition {
    val session = state.session.touched(now).withStep(state.stepIndex) { it.copy(status = status, record = record) }
    val next = session.steps.indexOfFirst { it.status == StepStatus.PENDING }
    val nextState = if (next >= 0) {
        TimerState.Ready(session, next, readySinceWallMs = now.wallMs)
    } else {
        TimerState.Finished(session, readySinceWallMs = now.wallMs)
    }
    return accepted(
        nextState,
        data = listOfNotNull(session.brewSessionId?.let { RecordStep(it, state.stepIndex, record) }),
        side = leaveEffects + UpdateNotification,
    )
}
