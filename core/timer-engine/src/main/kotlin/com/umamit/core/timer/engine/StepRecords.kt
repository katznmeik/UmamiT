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
 * now − deadline, and the actual time includes it. From 10 min of overtime on
 * it is the overtime timeout's auto-ack record, as on recovery.
 */
internal fun endedRecord(state: TimerState.StepStarted, now: ClockReading): StepRecord = when (state) {
    is TimerState.Alerting -> pouredRecord(state, now)

    is TimerState.Running -> if (now.elapsedMs >= state.deadlineElapsedMs) {
        pouredRecord(state, now)
    } else {
        state.record(state.plannedMs - (state.deadlineElapsedMs - now.elapsedMs), state.pausedTotalMs, 0, now.wallMs)
    }

    is TimerState.Paused -> state.record(
        activeMs = state.plannedMs - state.remainingMs,
        pausedMs = state.pausedTotalMs + state.openPauseMs(now),
        overtimeMs = 0,
        endedAtWallMs = now.wallMs,
    )
}

/**
 * The current pause of this step until [now]; never negative, so a reading
 * older than the pause (04 §4 forbids it) adds nothing (D-054).
 */
internal fun TimerState.Paused.openPauseMs(now: ClockReading): Long =
    (now.elapsedMs - pausedSinceElapsedMs).coerceAtLeast(0)

/** A paused step ended where its pause began: the open pause is not counted (midnight timeout, D-053). */
internal fun pauseEndedRecord(state: TimerState.Paused): StepRecord =
    state.record(state.plannedMs - state.remainingMs, state.pausedTotalMs, 0, state.pausedSinceWallMs)

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

/**
 * The overtime timeout's record (04 §5.1): poured on time at the deadline,
 * so actual = planned and overtime 0, flagged as auto-acknowledged.
 */
internal fun autoAckRecord(state: TimerState.WithDeadline): StepRecord =
    state.record(state.plannedMs, state.pausedTotalMs, 0, state.deadlineWallMs).copy(overtimeAutoAck = true)

/** Poured at [now], after the deadline; from 10 min of overtime on, the overtime timeout's record (D-053). */
private fun pouredRecord(state: TimerState.WithDeadline, now: ClockReading): StepRecord {
    if (isOvertimeTimedOut(state.deadlineElapsedMs, now)) return autoAckRecord(state)
    val overtimeMs = now.elapsedMs - state.deadlineElapsedMs
    return state.record(state.plannedMs + overtimeMs, state.pausedTotalMs, overtimeMs, now.wallMs)
}

private fun TimerState.StepStarted.record(activeMs: Long, pausedMs: Long, overtimeMs: Long, endedAtWallMs: Long) =
    StepRecord(
        plannedSeconds = session.steps[stepIndex].plannedSeconds,
        actualSeconds = roundToSeconds(activeMs),
        pausedSeconds = roundToSeconds(pausedMs),
        overtimeSeconds = roundToSeconds(overtimeMs),
        startedAtWallMs = stepStartedWallMs,
        endedAtWallMs = endedAtWallMs,
        skipped = false,
        overtimeAutoAck = false,
    )

/** Display rounding of a remaining time: up, so „0:01" never means done (04 §5). */
internal fun ceilSeconds(ms: Long): Long = (ms.coerceAtLeast(0) + MS_PER_SECOND - 1) / MS_PER_SECOND

/** Display rounding of overtime: down (04 §5). */
internal fun floorSeconds(ms: Long): Long = ms.coerceAtLeast(0) / MS_PER_SECOND

/** Rounds a duration to whole seconds, half up (04 §5.1: „rounded to 1 s"); negative values count as 0. */
private fun roundToSeconds(ms: Long): Int = ((ms.coerceAtLeast(0) + HALF_SECOND_MS) / MS_PER_SECOND).toInt()
