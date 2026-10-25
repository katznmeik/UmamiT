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
        bootCount = now.bootCount,
        demoted = false,
        longStepsOnly = event.longStepsOnly,
    )
    return accepted(
        TimerState.Ready(session, stepIndex = 0, readySinceWallMs = now.wallMs),
        data = listOf(CreateSession(event.sessionId)),
        side = listOf(StartForeground, UpdateNotification),
    )
}

internal fun start(state: TimerState.Ready, now: ClockReading): TimerTransition {
    val session = state.session.touched(now).withStep(state.stepIndex) { it.copy(status = StepStatus.STARTED) }
    val deadline = now.elapsedMs + state.plannedMs
    return accepted(
        TimerState.Running(
            session = session,
            stepIndex = state.stepIndex,
            stepStartedWallMs = now.wallMs,
            pausedTotalMs = 0,
            deadlineElapsedMs = deadline,
            deadlineWallMs = now.wallMs + state.plannedMs,
            preAlertAtElapsedMs = preAlertAt(session, state.stepIndex, deadline, played = false, now),
            preAlertPlayed = false,
        ),
        side = listOf(serviceFor(session, state.stepIndex), UpdateNotification),
    )
}

/**
 * Freezes the step with its remaining time, within 1 ms and the planned time:
 * a reading older than the start (04 §4 forbids it) never stores more (D-054).
 */
internal fun pause(state: TimerState.Running, now: ClockReading): TimerTransition = accepted(
    TimerState.Paused(
        session = state.session.touched(now),
        stepIndex = state.stepIndex,
        stepStartedWallMs = state.stepStartedWallMs,
        pausedTotalMs = state.pausedTotalMs,
        remainingMs = (state.deadlineElapsedMs - now.elapsedMs).coerceIn(1, state.plannedMs),
        pausedSinceElapsedMs = now.elapsedMs,
        pausedSinceWallMs = now.wallMs,
        preAlertPlayed = state.preAlertPlayed,
    ),
    side = listOf(UpdateNotification),
)

/** A played pre-alert is not repeated; an unplayed one is rescheduled from the new deadline, or skipped if past. */
internal fun resume(state: TimerState.Paused, now: ClockReading): TimerTransition {
    val session = state.session.touched(now)
    val deadline = now.elapsedMs + state.remainingMs
    return accepted(
        TimerState.Running(
            session = session,
            stepIndex = state.stepIndex,
            stepStartedWallMs = state.stepStartedWallMs,
            pausedTotalMs = state.pausedTotalMs + state.openPauseMs(now),
            deadlineElapsedMs = deadline,
            deadlineWallMs = now.wallMs + state.remainingMs,
            preAlertAtElapsedMs = preAlertAt(session, state.stepIndex, deadline, state.preAlertPlayed, now),
            preAlertPlayed = state.preAlertPlayed,
        ),
        side = listOf(serviceFor(session, state.stepIndex), UpdateNotification),
    )
}

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
 * Ready for the next pending step, or in Finished when none is left. A user
 * event is an [interaction]; a timeout is not.
 */
internal fun moveOn(
    state: TimerState.OnStep,
    status: StepStatus,
    record: StepRecord,
    now: ClockReading,
    leaveEffects: List<TimerEffect>,
    interaction: Boolean = true,
): TimerTransition {
    val touched = if (interaction) state.session.touched(now) else state.session
    val session = touched.withStep(state.stepIndex) { it.copy(status = status, record = record) }
    return accepted(
        waitingFor(session, readySinceWallMs = now.wallMs),
        data = listOfNotNull(session.brewSessionId?.let { RecordStep(it, state.stepIndex, record) }),
        side = leaveEffects + UpdateNotification,
    )
}
