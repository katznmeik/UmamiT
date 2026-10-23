package com.umamit.core.timer.diagnostics

import kotlin.math.abs
import kotlin.math.ceil

/** The target of 04 §12 and TMR-30: p95 ≤ 250 ms and max ≤ 1 s, for the pre-alert and the alert. */
object AccuracyTarget {
    const val P95_MS: Long = 250
    const val MAX_MS: Long = 1_000
}

/** How the accuracy run was set up (04 §12, #52). */
data class AccuracyRun(
    val apiLevel: Int,
    val plannedSteps: Int,
    val stepSeconds: Int,
    val preAlertSeconds: Int,
    /** A delay added to every pre-alert and alert to prove the gate bites; 0 in a real run. */
    val injectedDelayMs: Long = 0,
)

/** The fire times of one step of the run (1-based [step]). */
data class StepFireTimes(val step: Int, val preAlert: FireTime, val deadline: FireTime)

/**
 * The distribution of [count] delays (absolute values, ms) by the nearest-rank method: the
 * p-th percentile is the smallest delay that at least p % of the samples do not exceed.
 */
data class DelayStats(val count: Int, val p50Ms: Long, val p95Ms: Long, val maxMs: Long) {
    /** Within the target of 04 §12. */
    val onTarget: Boolean get() = p95Ms <= AccuracyTarget.P95_MS && maxMs <= AccuracyTarget.MAX_MS

    companion object {
        private const val P50 = 0.50
        private const val P95 = 0.95

        /** The statistics of [deltasMs] (signed; early counts like late); null when there is none. */
        fun of(deltasMs: List<Long>): DelayStats? {
            if (deltasMs.isEmpty()) return null
            val sorted = deltasMs.map(::abs).sorted()
            return DelayStats(sorted.size, sorted.rank(P50), sorted.rank(P95), sorted.last())
        }

        private fun List<Long>.rank(quantile: Double): Long =
            this[(ceil(quantile * size).toInt() - 1).coerceIn(indices)]
    }
}

/**
 * The result of the accuracy run (04 §12, TMR-30; #52): the pre-alert and the alert (deadline)
 * of every completed step, their distributions, and the verdict. It passes only when every
 * planned step completed and both distributions are on target. [csv] and [markdown] are the
 * workflow's artifact and job summary.
 */
class AccuracyReport(val run: AccuracyRun, val steps: List<StepFireTimes>) {
    val preAlert: DelayStats? = DelayStats.of(steps.map { it.preAlert.deltaMs })
    val alert: DelayStats? = DelayStats.of(steps.map { it.deadline.deltaMs })

    val complete: Boolean get() = steps.size == run.plannedSteps

    val passed: Boolean get() = complete && preAlert?.onTarget == true && alert?.onTarget == true

    /** One row per fire time, in the order they happened. */
    fun csv(): String = buildString {
        appendLine("step,event,source,scheduled_wall,scheduled_mono_ms,actual_wall,actual_mono_ms,delta_ms")
        steps.forEach { step ->
            listOf(step.preAlert, step.deadline).forEach { fire ->
                appendLine(
                    listOf(
                        step.step,
                        fire.kind.name,
                        fire.source,
                        fire.scheduledWall,
                        fire.scheduledMonoMs,
                        fire.actualWall,
                        fire.actualMonoMs,
                        fire.deltaMs,
                    ).joinToString(","),
                )
            }
        }
    }

    /** A short Markdown summary for `$GITHUB_STEP_SUMMARY` and the artifact. */
    fun markdown(): String = buildString {
        appendLine("### Timer accuracy · API ${run.apiLevel}")
        appendLine()
        appendLine(
            "${run.plannedSteps} × ${run.stepSeconds} s steps with the screen off, " +
                "pre-alert ${run.preAlertSeconds} s (04 §12, TMR-30). " +
                "Delay = actual − scheduled time of the event, from the diagnostics log (D-060).",
        )
        if (run.injectedDelayMs != 0L) {
            appendLine()
            appendLine("**Gate check:** every pre-alert and alert was delayed by ${run.injectedDelayMs} ms on purpose.")
        }
        appendLine()
        appendLine(
            "**Result: ${if (passed) "PASS" else "FAIL"}** · " +
                "target p95 ≤ ${AccuracyTarget.P95_MS} ms, max ≤ ${AccuracyTarget.MAX_MS} ms",
        )
        appendLine()
        appendLine("| Event | Steps | p50 | p95 | max | Fired by | On target |")
        appendLine("|---|---:|---:|---:|---:|---|---|")
        appendLine(row("Pre-alert", preAlert, steps.map { it.preAlert }))
        appendLine(row("Alert", alert, steps.map { it.deadline }))
        if (!complete) {
            appendLine()
            appendLine(
                "Only ${steps.size} of ${run.plannedSteps} steps completed; " +
                    "see the test failure for the step that did not.",
            )
        }
    }

    private fun row(label: String, stats: DelayStats?, fires: List<FireTime>): String {
        val sources = fires.groupingBy { it.source }.eachCount().toSortedMap().entries
            .joinToString(", ") { (source, count) -> "$source $count" }
            .ifEmpty { "–" }
        val values =
            stats?.let { listOf("${it.p50Ms} ms", "${it.p95Ms} ms", "${it.maxMs} ms") } ?: listOf("–", "–", "–")
        val onTarget = when {
            stats == null -> "no"
            stats.onTarget -> "yes"
            else -> "**no**"
        }
        return "| $label | ${fires.size} / ${run.plannedSteps} | ${values.joinToString(" | ")} | $sources | $onTarget |"
    }
}
