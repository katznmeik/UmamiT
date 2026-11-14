package com.umamit.app

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale

/**
 * The limit of NFR-PERF-12 (D-117 „Metric test") and the switch that makes it fail the run.
 *
 * Per transition the median of the runs may have at most [MAX_JANKY_FRAMES] frames over the
 * display's frame budget (60 Hz: 16,7 ms) and no frame over [MAX_FRAME_MS]. The gate is off until
 * the audit's fixes land (#285): the test then only reports. It is on when the instrumentation
 * argument `jankGate` is `true` (`-Pandroid.testInstrumentationRunnerArguments.jankGate=true`), or
 * when [GATE_DEFAULT] is flipped to true, and then only from API [GATE_MIN_API] on: API 35 always
 * reports only. `jankRuns` overrides the [DEFAULT_RUNS] runs per transition (7 against a flake).
 */
internal object JankGate {
    const val GATE_DEFAULT = false
    const val GATE_MIN_API = 36
    const val DEFAULT_RUNS = 5
    const val MAX_JANKY_FRAMES = 1
    const val MAX_FRAME_MS = 50.0

    private val arguments get() = InstrumentationRegistry.getArguments()

    /** Whether a transition over the limit fails the test on this device. */
    val active: Boolean
        get() {
            val switchedOn = arguments.getString("jankGate")?.toBooleanStrictOrNull() ?: GATE_DEFAULT
            return switchedOn && Build.VERSION.SDK_INT >= GATE_MIN_API
        }

    val runs: Int get() = arguments.getString("jankRuns")?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_RUNS

    /** „on", „off" or why it is off on this device, for the report line. */
    val describe: String
        get() = when {
            active -> "on"
            Build.VERSION.SDK_INT < GATE_MIN_API -> "report-only (API ${Build.VERSION.SDK_INT})"
            else -> "off"
        }
}

/** One run of a transition: its frames' durations in ns, judged against [budgetNs]. */
internal data class JankRun(val frameNanos: List<Long>, val budgetNs: Long) {
    val janky: Int get() = frameNanos.count { it > budgetNs }
    val maxFrameMs: Double get() = (frameNanos.maxOrNull() ?: 0L) / NANOS_PER_MS

    override fun toString() = "${frameNanos.size}f/${janky}j/${format(maxFrameMs)}ms"
}

/** All runs of one transition and its verdict: the median run's janky count and longest frame. */
internal data class JankReport(val transition: String, val runs: List<JankRun>) {
    val medianJanky: Int get() = median(runs.map { it.janky.toDouble() }).toInt()
    val medianMaxFrameMs: Double get() = median(runs.map { it.maxFrameMs })
    val worstMaxFrameMs: Double get() = runs.maxOfOrNull { it.maxFrameMs } ?: 0.0

    val passes: Boolean
        get() = runs.isNotEmpty() &&
            medianJanky <= JankGate.MAX_JANKY_FRAMES &&
            medianMaxFrameMs <= JankGate.MAX_FRAME_MS

    /** One line per transition; CI collects the lines that start with [LINE_PREFIX] from the logcat. */
    fun line(budgetNs: Long, windows: Int): String =
        "$LINE_PREFIX ${transition.padEnd(TRANSITION_COLUMN)} api=${Build.VERSION.SDK_INT} " +
            "budget=${format(budgetNs / NANOS_PER_MS)}ms runs=${runs.size} " +
            "medianJanky=$medianJanky medianMaxFrame=${format(medianMaxFrameMs)}ms " +
            "worstMaxFrame=${format(worstMaxFrameMs)}ms windows=$windows " +
            "verdict=${if (passes) "PASS" else "OVER"} gate=${JankGate.describe} perRun=$runs"

    companion object {
        const val LINE_PREFIX = "TransitionJank:"
        private const val TRANSITION_COLUMN = 28
    }
}

private const val NANOS_PER_MS = 1_000_000.0

private fun format(value: Double) = String.format(Locale.ROOT, "%.1f", value)

/** The median; of an even count the mean of the middle two (never with the 5 or 7 runs used). */
private fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}
