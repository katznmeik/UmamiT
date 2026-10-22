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

/** An alerting step acknowledged now: the actual time includes the overtime. */
internal fun pouredRecord(state: TimerState.Alerting, now: ClockReading): StepRecord {
    val overtimeMs = now.elapsedMs - state.deadlineElapsedMs
    return state.record(state.plannedMs + overtimeMs, state.pausedTotalMs, overtimeMs, now)
}

/**
 * The current step ended now: a running or paused step ends early, without
 * overtime (*Aufguss jetzt beenden*); an alerting one as poured.
 */
internal fun endedRecord(state: TimerState.StepStarted, now: ClockReading): StepRecord = when (state) {
    is TimerState.Running -> state.record(
        activeMs = state.plannedMs - (state.deadlineElapsedMs - now.elapsedMs),
        pausedMs = state.pausedTotalMs,
        overtimeMs = 0,
        now = now,
    )

    is TimerState.Paused -> state.record(
        activeMs = state.plannedMs - state.remainingMs,
        pausedMs = state.pausedTotalMs + (now.elapsedMs - state.pausedSinceElapsedMs),
        overtimeMs = 0,
        now = now,
    )

    is TimerState.Alerting -> pouredRecord(state, now)
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
)

private fun TimerState.StepStarted.record(activeMs: Long, pausedMs: Long, overtimeMs: Long, now: ClockReading) =
    StepRecord(
        plannedSeconds = session.steps[stepIndex].plannedSeconds,
        actualSeconds = roundToSeconds(activeMs),
        pausedSeconds = roundToSeconds(pausedMs),
        overtimeSeconds = roundToSeconds(overtimeMs),
        startedAtWallMs = stepStartedWallMs,
        endedAtWallMs = now.wallMs,
        skipped = false,
    )

/** Rounds a duration to whole seconds, half up (04 §5.1: „rounded to 1 s"); negative values count as 0. */
private fun roundToSeconds(ms: Long): Int = Math.floorDiv(ms.coerceAtLeast(0) + HALF_SECOND_MS, MS_PER_SECOND).toInt()
