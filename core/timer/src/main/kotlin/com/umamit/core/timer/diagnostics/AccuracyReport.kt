package com.umamit.core.timer.diagnostics

import com.umamit.core.model.TimerSlot
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
    /** The variant `alongside` (D-098, #137): the quick timer beside every step; null for the standard run. */
    val alongside: QuickTimerAlongside? = null,
)

/**
 * The quick timer of the accuracy run's variant `alongside` (04 §12, 09 §4a; D-098, #137), beside every brew step:
 * one of [seconds] with a pre-alert of [preAlertSeconds] started with the step, whose cues are measured; after it,
 * one of [acrossSeconds] without a pre-alert, so a quick timer runs across the step's pre-alert and alert too (the
 * next step's quick timer replaces it).
 */
data class QuickTimerAlongside(val seconds: Int, val preAlertSeconds: Int, val acrossSeconds: Int)

/**
 * One cue of a step, the pre-alert or the alert (D-068): when the controller [handled] its
 * time-based event, and when the alert player started its sound ([played]), both against the
 * same scheduled time. The gate measures [played]: the persist, the alarms, the service command
 * and the player's queue lie between the two.
 */
data class CueTimes(val handled: FireTime, val played: FireTime) {
    init {
        require(played.source == FireTime.PLAYER) { "not a sound start: $played" }
        require(
            handled.kind == played.kind && handled.slot == played.slot &&
                handled.scheduledMonoMs == played.scheduledMonoMs,
        ) {
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

/**
 * The cues of one step of the run (1-based [step]): the brew's, or in the variant `alongside` the quick timer's that
 * ran beside that step.
 */
data class StepFireTimes(val step: Int, val preAlert: CueTimes, val alert: CueTimes) {
    init {
        require(preAlert.handled.slot == alert.handled.slot) { "the cues of two timers: $preAlert, $alert" }
    }

    /** Whose cues they are (D-098). */
    val slot: TimerSlot get() = preAlert.handled.slot
}

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
 * One timer's cues over the run's [steps] and their distributions (04 §12, TMR-30): the sound starts the gate
 * measures (D-068), and the handled events for comparison only (D-067).
 */
class TimerAccuracy(val steps: List<StepFireTimes>) {
    val preAlert: DelayStats? = DelayStats.of(steps.map { it.preAlert.played.deltaMs })
    val alert: DelayStats? = DelayStats.of(steps.map { it.alert.played.deltaMs })
    val preAlertHandled: DelayStats? = DelayStats.of(steps.map { it.preAlert.handled.deltaMs })
    val alertHandled: DelayStats? = DelayStats.of(steps.map { it.alert.handled.deltaMs })

    /** Both sound distributions are within the target. */
    val onTarget: Boolean get() = preAlert?.onTarget == true && alert?.onTarget == true
}

/**
 * The result of the accuracy run (04 §12, TMR-30; #52): the pre-alert and the alert (deadline)
 * of every completed step, their distributions, and the verdict. The delay that counts is the
 * one up to the start of the sound (D-068); the delay up to the handled event is shown next to
 * it, so a slow persist or player queue shows where the time went. The run passes only when
 * every planned step completed and both sound distributions are on target. [csv] and
 * [markdown] are the workflow's artifact and job summary.
 *
 * In the variant `alongside` (D-098, #137) [quickSteps] are the cues of the quick timer beside each step, with
 * the same target: the run passes only when both timers completed every step on target, and the summary and the
 * CSV name each row's timer. The standard run's summary and CSV are those of the runs before, so they compare.
 */
class AccuracyReport(
    val run: AccuracyRun,
    val steps: List<StepFireTimes>,
    val quickSteps: List<StepFireTimes> = emptyList(),
) {
    init {
        require(steps.all { it.slot == TimerSlot.BREW }) { "not the brew's cues: $steps" }
        require(quickSteps.all { it.slot == TimerSlot.QUICK }) { "not the quick timer's cues: $quickSteps" }
        require(run.alongside != null || quickSteps.isEmpty()) { "the standard run has no quick timer" }
    }

    /** The brew's cues. */
    val brew = TimerAccuracy(steps)

    /** The quick timer's cues in the variant `alongside`; null in the standard run. */
    val quick: TimerAccuracy? = run.alongside?.let { TimerAccuracy(quickSteps) }

    val preAlert: DelayStats? get() = brew.preAlert
    val alert: DelayStats? get() = brew.alert

    /** The delays up to the handled event, for comparison only (D-067). */
    val preAlertHandled: DelayStats? get() = brew.preAlertHandled
    val alertHandled: DelayStats? get() = brew.alertHandled

    val complete: Boolean
        get() = steps.size == run.plannedSteps && (quick == null || quickSteps.size == run.plannedSteps)

    val passed: Boolean get() = complete && brew.onTarget && quick?.onTarget != false

    /**
     * One row per cue, in the order they happened: the handled event, then the sound start. In the variant
     * `alongside` the second column names the timer (`BREW`, `QUICK`).
     */
    fun csv(): String = buildString {
        val alongside = run.alongside != null
        appendLine(
            "step,${if (alongside) "timer," else ""}event,source,scheduled_wall,scheduled_mono_ms," +
                "handled_wall,handled_mono_ms,handled_delta_ms,played_wall,played_mono_ms,played_delta_ms",
        )
        val cues = if (alongside) {
            // Per step, both timers' cues by the time they were handled: the quick timer's come first.
            (steps + quickSteps).groupBy { it.step }.toSortedMap().values.flatMap { timers ->
                timers.flatMap(::cuesOf).sortedBy { (_, cue) -> cue.handled.actualMonoMs }
            }
        } else {
            steps.flatMap(::cuesOf)
        }
        cues.forEach { (step, cue) -> appendLine(csvRow(step, cue, alongside)) }
    }

    private fun cuesOf(step: StepFireTimes): List<Pair<Int, CueTimes>> =
        listOf(step.step to step.preAlert, step.step to step.alert)

    private fun csvRow(step: Int, cue: CueTimes, withTimer: Boolean): String {
        val (handled, played) = cue
        return listOfNotNull(
            step,
            handled.slot.name.takeIf { withTimer },
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
        ).joinToString(",")
    }

    /** A short Markdown summary for `$GITHUB_STEP_SUMMARY` and the artifact. */
    fun markdown(): String = buildString {
        val alongside = run.alongside
        appendLine("### Timer accuracy · API ${run.apiLevel}${if (alongside != null) " · alongside" else ""}")
        appendLine()
        appendLine(
            "${run.plannedSteps} × ${run.stepSeconds} s steps with the screen off, " +
                "pre-alert ${run.preAlertSeconds} s (04 §12, TMR-30)${alongside?.let(::describe).orEmpty()}. " +
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
                "target p95 ≤ ${AccuracyTarget.P95_MS} ms, max ≤ ${AccuracyTarget.MAX_MS} ms" +
                if (alongside != null) ", for both timers" else "",
        )
        appendLine()
        table().forEach(::appendLine)
        // Each note a paragraph of its own.
        notes().forEach { appendLine().appendLine(it) }
    }

    /** The table: one row per cue (the pre-alert, the alert), and in the variant `alongside` per timer. */
    private fun table(): List<String> {
        val quick = quick ?: return listOf(
            "| Event | Steps | p50 | p95 | max | Handled p95 / max | Fired by | On target |",
            "|---|---:|---:|---:|---:|---:|---|---|",
        ) + rows(brew, timer = null)
        return listOf(
            "| Timer | Event | Steps | p50 | p95 | max | Handled p95 / max | Fired by | On target |",
            "|---|---|---:|---:|---:|---:|---:|---|---|",
        ) + rows(brew, BREW) + rows(quick, QUICK_TIMER)
    }

    /** Below the table: each timer's slowest pre-alert and alert, then what did not complete. */
    private fun notes(): List<String> {
        val slowest = if (quick == null) {
            listOf(slowest("pre-alert", steps) { it.preAlert }, slowest("alert", steps) { it.alert })
        } else {
            listOf(
                slowest("brew pre-alert", steps) { it.preAlert },
                slowest("brew alert", steps) { it.alert },
                slowest("quick timer pre-alert", quickSteps) { it.preAlert },
                slowest("quick timer alert", quickSteps) { it.alert },
            )
        }
        val incomplete = buildList {
            if (steps.size != run.plannedSteps) {
                add(
                    "Only ${steps.size} of ${run.plannedSteps} steps completed; " +
                        "see the test failure for the step that did not.",
                )
            }
            if (quick != null && quickSteps.size != run.plannedSteps) {
                add(
                    "Only ${quickSteps.size} of ${run.plannedSteps} quick timers completed; " +
                        "see the test failure for the one that did not.",
                )
            }
        }
        return slowest.filterNotNull() + incomplete
    }

    /** The quick timer beside each step, as the summary's first paragraph names it. */
    private fun describe(alongside: QuickTimerAlongside): String =
        ", each with a quick timer alongside (D-098): one of ${alongside.seconds} s with pre-alert " +
            "${alongside.preAlertSeconds} s from the step's start, " +
            "then one of ${alongside.acrossSeconds} s across the step's pre-alert and alert"

    /** The table's rows of one timer's pre-alert and alert; [timer] is its first column in the variant `alongside`. */
    private fun rows(accuracy: TimerAccuracy, timer: String?): List<String> {
        val preAlerts = accuracy.steps.map { it.preAlert.handled }
        val alerts = accuracy.steps.map { it.alert.handled }
        return listOf(
            row(timer, "Pre-alert", accuracy.preAlert, accuracy.preAlertHandled, preAlerts),
            row(timer, "Alert", accuracy.alert, accuracy.alertHandled, alerts),
        )
    }

    /** The step whose [cue] started its sound latest, and where the time went (D-092); null without steps. */
    private fun slowest(label: String, steps: List<StepFireTimes>, cue: (StepFireTimes) -> CueTimes): String? =
        steps.maxByOrNull { abs(cue(it).played.deltaMs) }
            ?.let { "Slowest $label: step ${it.step}, ${cue(it).summary()}" }

    private fun row(
        timer: String?,
        label: String,
        stats: DelayStats?,
        handled: DelayStats?,
        fires: List<FireTime>,
    ): String {
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
        return "| ${timer?.let { "$it | " }.orEmpty()}$label | ${fires.size} / ${run.plannedSteps} | " +
            "${values.joinToString(" | ")} | $handledValues | $sources | $onTarget |"
    }

    private companion object {
        /** The timers' names in the variant `alongside`'s table. */
        const val BREW = "Brew"
        const val QUICK_TIMER = "Quick timer"
    }
}
