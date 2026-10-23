package com.umamit.core.timer.notification

import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState

/** Whether [this] state runs with the foreground service: all but a demoted session and a long running step (D-053). */
internal val TimerUiState.Active.runsWithService: Boolean
    get() = !demoted && !(phase == TimerPhase.RUNNING && longStep)

/**
 * The ProgressStyle of the Live Update (04 §8.3): one segment per step (at most 30, PRF-03),
 * its length in tenths of a second of the planned time but at least a twentieth of the plan,
 * so a short step stays visible; a point at the pre-alert of the current step; the progress is
 * the session's elapsed share of the plan on that scale.
 */
internal data class LiveUpdateProgress(val segments: List<Segment>, val preAlertAt: Int?, val progress: Int) {
    data class Segment(val length: Int, val rinse: Boolean)

    companion object {
        private const val MAX_SEGMENTS = 30
        private const val MIN_SHARE = 20
        private const val UNITS_PER_SECOND = 10
        private const val MS_PER_SECOND = 1_000L

        fun of(state: TimerUiState.Active, nowElapsedMs: Long): LiveUpdateProgress {
            val steps = state.steps.take(MAX_SEGMENTS)
            val planned = steps.map { (it.plannedSeconds * UNITS_PER_SECOND).coerceAtLeast(1) }
            val min = (planned.sum() + MIN_SHARE - 1) / MIN_SHARE
            val lengths = planned.map { it.coerceAtLeast(min) }
            val segments = steps.mapIndexed { index, step -> Segment(lengths[index], step.kind == StepKind.RINSE) }
            val before = lengths.take(state.stepIndex.coerceAtMost(lengths.size)).sum()
            val current = lengths.getOrNull(state.stepIndex)
            val plannedMs = (state.currentStep?.plannedSeconds ?: 0) * MS_PER_SECOND
            if (current == null || plannedMs == 0L) return LiveUpdateProgress(segments, null, before)
            val elapsedMs = when (state.phase) {
                TimerPhase.RUNNING -> plannedMs - (checkNotNull(state.deadlineElapsedMs) - nowElapsedMs)
                TimerPhase.PAUSED -> plannedMs - checkNotNull(state.pausedRemainingMs)
                TimerPhase.ALERTING -> plannedMs
                TimerPhase.READY, TimerPhase.FINISHED -> 0L
            }.coerceIn(0, plannedMs)
            val preAlertAt = state.preAlertLeadSeconds?.takeUnless { state.longStep }?.let { lead ->
                before + (current * (plannedMs - lead * MS_PER_SECOND) / plannedMs).toInt()
            }
            return LiveUpdateProgress(segments, preAlertAt, before + (current * elapsedMs / plannedMs).toInt())
        }
    }
}
