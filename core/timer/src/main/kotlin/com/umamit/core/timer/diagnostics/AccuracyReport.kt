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

/**
 * One cue of a step, the pre-alert or the alert (D-068): when the controller [handled] its
 * time-based event, and when the alert player started its sound ([played]), both against the
 * same scheduled time. The gate measures [played]: the persist, the alarms, the service command
 * and the player's queue lie between the two.
 */
data class CueTimes(val handled: FireTime, val played: FireTime) {
    init {
        require(played.source == FireTime.PLAYER) { "not a sound start: $played" }
        require(handled.kind == played.kind && handled.scheduledMonoMs == played.scheduledMonoMs) {
            "the sound $played is not that of $handled"
        }
    }

    /**
     * One line for the run's log and summary: the delay to the sound, the handled delay with
     * what delivered the event, and where the player's start spent the time (D-092), e.g.
     * `1178 ms (handled 26 ms, ALARM; queued 2 ms, settings 0 ms, outputs 1150 ms, sound loaded on play)`.
     */
    fun summary(): String =
        "${played.deltaMs} ms (handled ${handled.deltaMs} ms, ${handled.source}${played.start?.let { "; $it" } ?: ""})"
}

/** The cues of one step of the run (1-based [step]). */
data class StepFireTimes(val step: Int, val preAlert: CueTimes, val alert: CueTimes)

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
 * of every completed step, their distributions, and the verdict. The delay that counts is the
 * one up to the start of the sound (D-068); the delay up to the handled event is shown next to
 * it, so a slow persist or player queue shows where the time went. The run passes only when
 * every planned step completed and both sound distributions are on target. [csv] and
 * [markdown] are the workflow's artifact and job summary.
 */
class AccuracyReport(val run: AccuracyRun, val steps: List<StepFireTimes>) {
    val preAlert: DelayStats? = DelayStats.of(steps.map { it.preAlert.played.deltaMs })
    val alert: DelayStats? = DelayStats.of(steps.map { it.alert.played.deltaMs })

    /** The delays up to the handled event, for comparison only (D-067). */
    val preAlertHandled: DelayStats? = DelayStats.of(steps.map { it.preAlert.handled.deltaMs })
    val alertHandled: DelayStats? = DelayStats.of(steps.map { it.alert.handled.deltaMs })

    val complete: Boolean get() = steps.size == run.plannedSteps

    val passed: Boolean get() = complete && preAlert?.onTarget == true && alert?.onTarget == true

    /** One row per cue, in the order they happened: the handled event, then the sound start. */
    fun csv(): String = buildString {
        appendLine(
            "step,event,source,scheduled_wall,scheduled_mono_ms," +
                "handled_wall,handled_mono_ms,handled_delta_ms,played_wall,played_mono_ms,played_delta_ms",
        )
        steps.forEach { step ->
            listOf(step.preAlert, step.alert).forEach { (handled, played) ->
                appendLine(
                    listOf(
                        step.step,
                        handled.kind.name,
                        handled.source,
                        handled.scheduledWall,
                        handled.scheduledMonoMs,
                        handled.actualWall,
                        handled.actualMonoMs,
                        handled.deltaMs,
                        played.actualWall,
                        played.actualMonoMs,
                        played.deltaMs,
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
                "Delay = when the sound started − scheduled time, from the diagnostics log (D-060, D-068); " +
                "the controller's handling of the event is shown for comparison.",
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
        appendLine("| Event | Steps | p50 | p95 | max | Handled p95 / max | Fired by | On target |")
        appendLine("|---|---:|---:|---:|---:|---:|---|---|")
        appendLine(row("Pre-alert", preAlert, preAlertHandled, steps.map { it.preAlert.handled }))
        appendLine(row("Alert", alert, alertHandled, steps.map { it.alert.handled }))
        slowest("pre-alert") { it.preAlert }?.let { appendLine().appendLine(it) }
        slowest("alert") { it.alert }?.let { appendLine().appendLine(it) }
        if (!complete) {
            appendLine()
            appendLine(
                "Only ${steps.size} of ${run.plannedSteps} steps completed; " +
                    "see the test failure for the step that did not.",
            )
        }
    }

    /** The step whose [cue] started its sound latest, and where the time went (D-092); null without steps. */
    private fun slowest(label: String, cue: (StepFireTimes) -> CueTimes): String? =
        steps.maxByOrNull { abs(cue(it).played.deltaMs) }
            ?.let { "Slowest $label: step ${it.step}, ${cue(it).summary()}" }

    private fun row(label: String, stats: DelayStats?, handled: DelayStats?, fires: List<FireTime>): String {
        val sources = fires.groupingBy { it.source }.eachCount().toSortedMap().entries
            .joinToString(", ") { (source, count) -> "$source $count" }
            .ifEmpty { "–" }
        val values =
            stats?.let { listOf("${it.p50Ms} ms", "${it.p95Ms} ms", "${it.maxMs} ms") } ?: listOf("–", "–", "–")
        val handledValues = handled?.let { "${it.p95Ms} ms / ${it.maxMs} ms" } ?: "–"
        val onTarget = when {
            stats == null -> "no"
            stats.onTarget -> "yes"
            else -> "**no**"
        }
        return "| $label | ${fires.size} / ${run.plannedSteps} | ${values.joinToString(" | ")} | $handledValues | " +
            "$sources | $onTarget |"
    }
}
