package com.umamit.core.timer.diagnostics

import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.TimerAlarmKind
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
    /** The variant `two-brews` (D-108, #202): a second brew session beside every step; null otherwise. */
    val twoBrews: SecondBrew? = null,
) {
    init {
        require(alongside == null || twoBrews == null) { "one variant at a time: $alongside, $twoBrews" }
    }
}

/**
 * The second brew session of the accuracy run's variant `two-brews` (04 §12, 09 §4a; D-108, #202): steps of
 * [stepSeconds] with a pre-alert of [preAlertSeconds], started with each of the first session's steps. Its pre-alert
 * comes between the first's pre-alert and alert, and its deadline while the first's alert sounds, so it is held until
 * that sound has ended and then rings with its overtime (04 §9.3, D-109).
 */
data class SecondBrew(val stepSeconds: Int, val preAlertSeconds: Int)

/**
 * The second brew's alert in the variant `two-brews` (D-108), held behind the first brew's ringing alert (04 §9.3):
 * when the controller [handled] its deadline (as the hold), and when its sound started after the release
 * ([released]), timed from the release with the time it was held. Its lateness after the deadline is the designed
 * hold, never a latency: the report shows the hold and the release's own delay apart, and neither is gated.
 */
data class HeldCueTimes(val handled: FireTime, val released: FireTime) {
    init {
        require(released.source == FireTime.PLAYER) { "not a sound start: $released" }
        require(released.heldMs != null) { "not a released alert's sound: $released" }
        require(handled.kind == TimerAlarmKind.DEADLINE && released.kind == TimerAlarmKind.DEADLINE) {
            "not an alert: $handled, $released"
        }
        require(handled.slot == released.slot) { "the sound $released is not that of $handled" }
    }

    /** How long the alert was held after its deadline (04 §9.3), ms. */
    val heldMs: Long get() = released.heldMs ?: 0

    /**
     * One line for the run's log and summary, e.g.
     * `30 ms after its release, held 6001 ms (deadline handled 15 ms, WAKE_UP; queued 1 ms, …)`.
     */
    fun summary(): String = "${released.deltaMs} ms after its release, held $heldMs ms " +
        "(deadline handled ${handled.deltaMs} ms, ${handled.source}${released.start?.let { "; $it" } ?: ""})"
}

/** The second brew's cues of one step of the variant `two-brews` (1-based [step]; D-108). */
data class SecondBrewStepFireTimes(val step: Int, val preAlert: CueTimes, val alert: HeldCueTimes) {
    init {
        require(preAlert.handled.slot == TimerSlot.BREW_2 && alert.handled.slot == TimerSlot.BREW_2) {
            "not the second brew's cues: $preAlert, $alert"
        }
    }
}

/**
 * The second brew's cues over the run's [steps] (D-108): its pre-alerts, gated like every unheld cue, and its held
 * alerts, reported apart (04 §9.3): how long they were held ([held]), how long the sound took after the release
 * ([release]) and the handled deadline ([alertHandled]).
 */
class SecondBrewAccuracy(val steps: List<SecondBrewStepFireTimes>) {
    val preAlert: DelayStats? = DelayStats.of(steps.map { it.preAlert.played.deltaMs })
    val preAlertHandled: DelayStats? = DelayStats.of(steps.map { it.preAlert.handled.deltaMs })
    val held: DelayStats? = DelayStats.of(steps.map { it.alert.heldMs })
    val release: DelayStats? = DelayStats.of(steps.map { it.alert.released.deltaMs })
    val alertHandled: DelayStats? = DelayStats.of(steps.map { it.alert.handled.deltaMs })

    /** The gated cues, the pre-alerts, are within the target. */
    val onTarget: Boolean get() = preAlert?.onTarget == true
}

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
        // A released alert measures the arbitration (04 §9.3), not the latency: never a cue of the run.
        require(played.heldMs == null) { "a held alert's sound is no cue: $played" }
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
 *
 * In the variant `two-brews` (D-108, #202) [secondSteps] are the cues of the second brew session beside each step:
 * its pre-alerts with the same target, its alerts held behind the first brew's by design (04 §9.3) and reported
 * apart, never gated. The run passes only when both brews completed every step and the first brew's cues and the
 * second's pre-alerts are on target; the CSV names each row's timer and the held alert's hold.
 */
class AccuracyReport(
    val run: AccuracyRun,
    val steps: List<StepFireTimes>,
    val quickSteps: List<StepFireTimes> = emptyList(),
    val secondSteps: List<SecondBrewStepFireTimes> = emptyList(),
) {
    init {
        require(steps.all { it.slot == TimerSlot.BREW }) { "not the brew's cues: $steps" }
        require(quickSteps.all { it.slot == TimerSlot.QUICK }) { "not the quick timer's cues: $quickSteps" }
        require(run.alongside != null || quickSteps.isEmpty()) { "only the variant alongside has a quick timer" }
        require(run.twoBrews != null || secondSteps.isEmpty()) { "only the variant two-brews has a second brew" }
    }

    /** The brew's cues; in the variant `two-brews` the first brew session's. */
    val brew = TimerAccuracy(steps)

    /** The quick timer's cues in the variant `alongside`; null otherwise. */
    val quick: TimerAccuracy? = run.alongside?.let { TimerAccuracy(quickSteps) }

    /** The second brew session's cues in the variant `two-brews`; null otherwise. */
    val second: SecondBrewAccuracy? = run.twoBrews?.let { SecondBrewAccuracy(secondSteps) }

    val preAlert: DelayStats? get() = brew.preAlert
    val alert: DelayStats? get() = brew.alert

    /** The delays up to the handled event, for comparison only (D-067). */
    val preAlertHandled: DelayStats? get() = brew.preAlertHandled
    val alertHandled: DelayStats? get() = brew.alertHandled

    val complete: Boolean
        get() = steps.size == run.plannedSteps &&
            (quick == null || quickSteps.size == run.plannedSteps) &&
            (second == null || secondSteps.size == run.plannedSteps)

    val passed: Boolean get() = complete && brew.onTarget && quick?.onTarget != false && second?.onTarget != false

    /**
     * One row per cue, in the order they happened: the handled event, then the sound start. In the variants
     * `alongside` and `two-brews` the second column names the timer (`BREW`, `QUICK`, `BREW_2`); in `two-brews` the
     * last column is the held alert's hold (empty for the other cues), whose sound start is timed from its release.
     */
    fun csv(): String = buildString {
        val withTimer = run.alongside != null || run.twoBrews != null
        val withHeld = run.twoBrews != null
        appendLine(
            "step,${if (withTimer) "timer," else ""}event,source,scheduled_wall,scheduled_mono_ms," +
                "handled_wall,handled_mono_ms,handled_delta_ms,played_wall,played_mono_ms,played_delta_ms" +
                if (withHeld) ",held_ms" else "",
        )
        val cues = steps.flatMap(::cuesOf) + quickSteps.flatMap(::cuesOf) + secondSteps.flatMap(::cuesOf)
        val ordered = if (withTimer) {
            // Per step, both timers' cues by the time they were handled (alongside: the quick timer's come first).
            cues.groupBy { it.step }.toSortedMap().values.flatMap { step -> step.sortedBy { it.handled.actualMonoMs } }
        } else {
            cues
        }
        ordered.forEach { appendLine(csvRow(it, withTimer, withHeld)) }
    }

    private fun csvRow(cue: CsvCue, withTimer: Boolean, withHeld: Boolean): String {
        val (step, handled, played) = cue
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
            cue.heldMs?.toString().orEmpty().takeIf { withHeld },
        ).joinToString(",")
    }

    /** A short Markdown summary for `$GITHUB_STEP_SUMMARY` and the artifact. */
    fun markdown(): String = buildString {
        appendLine("### Timer accuracy · API ${run.apiLevel}${run.variantTitle}")
        appendLine()
        appendLine(
            "${run.plannedSteps} × ${run.stepSeconds} s steps with the screen off, " +
                "pre-alert ${run.preAlertSeconds} s (04 §12, TMR-30)${run.besideEachStep}. " +
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
                "target p95 ≤ ${AccuracyTarget.P95_MS} ms, max ≤ ${AccuracyTarget.MAX_MS} ms${run.targetScope}",
        )
        appendLine()
        table().forEach(::appendLine)
        second?.let { second ->
            appendLine()
            appendLine(
                "Held alert of brew 2 (04 §9.3): its deadline comes while brew 1's alert sounds, so it is held " +
                    "until that sound has ended (D-109), by design, and rings then with its overtime. *Held* is " +
                    "that designed delay; " +
                    "*Release → sound* is the player's start after the release, shown against the target but " +
                    "not gated.",
            )
            appendLine()
            heldTable(second).forEach(::appendLine)
        }
        // Each note a paragraph of its own.
        notes().forEach { appendLine().appendLine(it) }
    }

    /** The table: one row per cue (the pre-alert, the alert), and in the variants with a second timer per timer. */
    private fun table(): List<String> {
        val header = listOf(
            "| Timer | Event | Steps | p50 | p95 | max | Handled p95 / max | Fired by | On target |",
            "|---|---|---:|---:|---:|---:|---:|---|---|",
        )
        val quick = quick
        val second = second
        return when {
            quick != null -> header + rows(brew, BREW) + rows(quick, QUICK_TIMER)

            second != null -> header + rows(brew, BREW_1) + row(
                BREW_2,
                "Pre-alert",
                second.preAlert,
                second.preAlertHandled,
                second.steps.map { it.preAlert.handled },
            )

            else -> listOf(
                "| Event | Steps | p50 | p95 | max | Handled p95 / max | Fired by | On target |",
                "|---|---:|---:|---:|---:|---:|---|---|",
            ) + rows(brew, timer = null)
        }
    }

    /** The second brew's held alerts (04 §9.3): the hold, the release's own delay and the handled deadline. */
    private fun heldTable(second: SecondBrewAccuracy): List<String> {
        val release = second.release
        val values = release?.let { listOf("${it.p50Ms} ms", "${it.p95Ms} ms", "${it.maxMs} ms") }
            ?: listOf("–", "–", "–")
        val held = second.held?.let { "${it.p50Ms} ms / ${it.maxMs} ms" } ?: "–"
        val handled = second.alertHandled?.let { "${it.p95Ms} ms / ${it.maxMs} ms" } ?: "–"
        val withinTarget = if (release?.onTarget == true) "yes (not gated)" else "no (not gated)"
        return listOf(
            "| Timer | Event | Steps | Held p50 / max | Release → sound p50 | p95 | max | " +
                "Deadline handled p95 / max | Fired by | Within target |",
            "|---|---|---:|---:|---:|---:|---:|---:|---|---|",
            "| $BREW_2 | Alert (held) | ${second.steps.size} / ${run.plannedSteps} | $held | " +
                "${values.joinToString(" | ")} | $handled | ${sources(second.steps.map { it.alert.handled })} | " +
                "$withinTarget |",
        )
    }

    /** Below the table: each timer's slowest pre-alert and alert, then what did not complete. */
    private fun notes(): List<String> {
        val second = second
        val slowest = when {
            quick != null -> listOf(
                slowest("brew pre-alert", steps) { it.preAlert },
                slowest("brew alert", steps) { it.alert },
                slowest("quick timer pre-alert", quickSteps) { it.preAlert },
                slowest("quick timer alert", quickSteps) { it.alert },
            )

            second != null -> listOf(
                slowest("brew 1 pre-alert", steps) { it.preAlert },
                slowest("brew 1 alert", steps) { it.alert },
                secondSteps.maxByOrNull { abs(it.preAlert.played.deltaMs) }
                    ?.let { "Slowest brew 2 pre-alert: step ${it.step}, ${it.preAlert.summary()}" },
                secondSteps.maxByOrNull { abs(it.alert.released.deltaMs) }
                    ?.let { "Slowest brew 2 held alert: step ${it.step}, ${it.alert.summary()}" },
            )

            else -> listOf(slowest("pre-alert", steps) { it.preAlert }, slowest("alert", steps) { it.alert })
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
            if (second != null && secondSteps.size != run.plannedSteps) {
                add(
                    "Only ${secondSteps.size} of ${run.plannedSteps} second brew steps completed; " +
                        "see the test failure for the one that did not.",
                )
            }
        }
        return slowest.filterNotNull() + incomplete
    }

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
        val firedBy = sources(fires)
        val values =
            stats?.let { listOf("${it.p50Ms} ms", "${it.p95Ms} ms", "${it.maxMs} ms") } ?: listOf("–", "–", "–")
        val handledValues = handled?.let { "${it.p95Ms} ms / ${it.maxMs} ms" } ?: "–"
        val onTarget = when {
            stats == null -> "no"
            stats.onTarget -> "yes"
            else -> "**no**"
        }
        return "| ${timer?.let { "$it | " }.orEmpty()}$label | ${fires.size} / ${run.plannedSteps} | " +
            "${values.joinToString(" | ")} | $handledValues | $firedBy | $onTarget |"
    }

    private companion object {
        /** The timers' names in the table of the variant `alongside`, then `two-brews`. */
        const val BREW = "Brew"
        const val QUICK_TIMER = "Quick timer"
        const val BREW_1 = "Brew 1"
        const val BREW_2 = "Brew 2"
    }
}

/** A CSV row's cue: its handled event and its sound start, with the hold of a released alert. */
private data class CsvCue(val step: Int, val handled: FireTime, val played: FireTime, val heldMs: Long? = null)

private fun cuesOf(step: StepFireTimes): List<CsvCue> = listOf(step.preAlert, step.alert)
    .map { CsvCue(step.step, it.handled, it.played) }

private fun cuesOf(step: SecondBrewStepFireTimes): List<CsvCue> = listOf(
    CsvCue(step.step, step.preAlert.handled, step.preAlert.played),
    CsvCue(step.step, step.alert.handled, step.alert.released, step.alert.heldMs),
)

/** What delivered [fires], e.g. `ALARM 48, WAKE_UP 2`. */
private fun sources(fires: List<FireTime>): String = fires.groupingBy { it.source }.eachCount().toSortedMap()
    .entries
    .joinToString(", ") { (source, count) -> "$source $count" }
    .ifEmpty { "–" }

/** The variant at the end of the summary's heading: none for the standard run. */
private val AccuracyRun.variantTitle: String
    get() = when {
        alongside != null -> " · alongside"
        twoBrews != null -> " · two brews"
        else -> ""
    }

/** The second timer beside each step, as the summary's first paragraph names it; empty for the standard run. */
private val AccuracyRun.besideEachStep: String
    get() = alongside?.let {
        ", each with a quick timer alongside (D-098): one of ${it.seconds} s with pre-alert " +
            "${it.preAlertSeconds} s from the step's start, " +
            "then one of ${it.acrossSeconds} s across the step's pre-alert and alert"
    } ?: twoBrews?.let {
        ", each with a second brew session alongside (D-108): steps of ${it.stepSeconds} s with pre-alert " +
            "${it.preAlertSeconds} s started with the first's, so its alert comes while the first's sounds " +
            "and is held until that sound has ended (04 §9.3, D-109)"
    }.orEmpty()

/** What the verdict's target covers, after the target; empty for the standard run. */
private val AccuracyRun.targetScope: String
    get() = when {
        alongside != null -> ", for both timers"

        twoBrews != null ->
            ", for both brews' pre-alerts and the first brew's alerts; " +
                "the second brew's held alert is reported apart, not gated"

        else -> ""
    }
