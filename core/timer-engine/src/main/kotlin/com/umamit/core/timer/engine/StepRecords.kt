package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading

/*
 * Recorded step data (04 §5.1). Active time is derived from the monotonic
 * deadline: while a step runs, active = planned − (deadline − now), so pauses
 * are excluded without a separate counter.
 */

internal const val MS_PER_SECOND = 1000L
private const val HALF_SECOND_MS = 500L

/** Planned duration of the current step in milliseconds. */
internal val TimerState.OnStep.plannedMs: Long
    get() = session.steps[stepIndex].plannedSeconds * MS_PER_SECOND

/**
 * The current step ended now. Before the deadline (Running, Paused) it ends
 * early, without overtime. At or after the deadline (Alerting, or Running
 * before the deadline event was processed) it counts as poured: overtime =
 * now − deadline, and the actual time includes it.
 */
internal fun endedRecord(state: TimerState.StepStarted, now: ClockReading): StepRecord = when (state) {
    is TimerState.Alerting -> pouredRecord(state, state.deadlineElapsedMs, now)

    is TimerState.Running -> if (now.elapsedMs >= state.deadlineElapsedMs) {
        pouredRecord(state, state.deadlineElapsedMs, now)
    } else {
        state.record(state.plannedMs - (state.deadlineElapsedMs - now.elapsedMs), state.pausedTotalMs, 0, now)
    }

    is TimerState.Paused -> state.record(
        activeMs = state.plannedMs - state.remainingMs,
        pausedMs = state.pausedTotalMs + (now.elapsedMs - state.pausedSinceElapsedMs),
        overtimeMs = 0,
        now = now,
    )
}

/** A step skipped in Ready: never started, so no times. */
internal fun skippedRecord(state: TimerState.Ready): StepRecord = StepRecord(
    plannedSeconds = state.session.steps[state.stepIndex].plannedSeconds,
    actualSeconds = 0,
    pausedSeconds = 0,
    overtimeSeconds = 0,
    startedAtWallMs = null,
    endedAtWallMs = null,
    skipped = true,
    overtimeAutoAck = false,
)

private fun pouredRecord(state: TimerState.StepStarted, deadlineElapsedMs: Long, now: ClockReading): StepRecord {
    val overtimeMs = now.elapsedMs - deadlineElapsedMs
    return state.record(state.plannedMs + overtimeMs, state.pausedTotalMs, overtimeMs, now)
}

private fun TimerState.StepStarted.record(activeMs: Long, pausedMs: Long, overtimeMs: Long, now: ClockReading) =
    StepRecord(
        plannedSeconds = session.steps[stepIndex].plannedSeconds,
        actualSeconds = roundToSeconds(activeMs),
        pausedSeconds = roundToSeconds(pausedMs),
        overtimeSeconds = roundToSeconds(overtimeMs),
        startedAtWallMs = stepStartedWallMs,
        endedAtWallMs = now.wallMs,
        skipped = false,
        overtimeAutoAck = false,
    )

/** Rounds a duration to whole seconds, half up (04 §5.1: „rounded to 1 s"); negative values count as 0. */
private fun roundToSeconds(ms: Long): Int = ((ms.coerceAtLeast(0) + HALF_SECOND_MS) / MS_PER_SECOND).toInt()
