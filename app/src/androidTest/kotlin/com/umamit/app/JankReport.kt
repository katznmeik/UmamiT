package com.umamit.app

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale

/**
 * The limit of NFR-PERF-12 (D-117 build note „Jank gate") and the switch that makes it fail the run.
 *
 * The gate is **relative** to the emulator's own speed: every test also measures [ReferenceTransition], and per
 * transition the median run may have no stall (longest frame ≤ [STALL_FACTOR] × the reference's typical frame) and
 * no more frames over the budget than the reference's, scaled to the transition's frame count, plus
 * [JANKY_ALLOWANCE]. The factors are calibrated on three CI runs (`docs/plans/v0.7.1-motion-audit.md` §7) before the
 * gate goes on. The absolute limit of D-117 Q16 (at most [MAX_JANKY_FRAMES] frames over the display's budget, none
 * over [MAX_FRAME_MS]) is still reported, as `absolute=`, but does not gate.
 *
 * The gate is off until then (#285): the test only reports. It is on when the instrumentation argument `jankGate` is
 * `true` (`-Pandroid.testInstrumentationRunnerArguments.jankGate=true`), or when [GATE_DEFAULT] is flipped to true,
 * and then only from API [GATE_MIN_API] on: API 35 always reports only. `jankRuns` overrides the [DEFAULT_RUNS] runs
 * per transition (7, so the median holds against a flaky run).
 *
 * Each line also says where the cost sits (#285): the median first frame, its ratio to the reference's typical frame,
 * the frame count and the 0-based index of the longest frame per run (`longestAt=[0,0,3,…]`), so a first-frame stall
 * reads apart from rendering cost in mid-transition. These are reported only; the verdicts do not use them.
 */
internal object JankGate {
    const val GATE_DEFAULT = false
    const val GATE_MIN_API = 36
    const val DEFAULT_RUNS = 7
    const val MAX_JANKY_FRAMES = 1
    const val MAX_FRAME_MS = 50.0

    /** A stall: the median run's longest frame over this many reference typical frames. */
    const val STALL_FACTOR = 3.0

    /** The janky frames a transition may have beyond the reference's, scaled to its frame count. */
    const val JANKY_ALLOWANCE = 1

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

    /** The run's typical frame: the median of its frames' durations. */
    val medianFrameMs: Double get() = median(frameNanos.map { it.toDouble() }) / NANOS_PER_MS

    /** The 0-based index, in frame order, of the run's longest frame (the first of equals); -1 without frames. */
    val longestIndex: Int get() = frameNanos.indexOf(frameNanos.maxOrNull())

    /** The run's first frame in ms; null without frames. */
    val firstFrameMs: Double? get() = frameNanos.firstOrNull()?.let { it / NANOS_PER_MS }

    override fun toString() = "${frameNanos.size}f/${janky}j/${format(maxFrameMs)}ms"
}

/** All runs of one transition: the median run's janky count, longest frame and frame count, and its verdicts. */
internal data class JankReport(val transition: String, val runs: List<JankRun>) {
    val medianJanky: Int get() = median(runs.map { it.janky.toDouble() }).toInt()
    val medianMaxFrameMs: Double get() = median(runs.map { it.maxFrameMs })
    val worstMaxFrameMs: Double get() = runs.maxOfOrNull { it.maxFrameMs } ?: 0.0
    val medianFrames: Double get() = median(runs.map { it.frameNanos.size.toDouble() })

    /** The median first frame over the runs with frames; reported only. */
    val medianFirstFrameMs: Double get() = median(runs.mapNotNull { it.firstFrameMs })

    /** Per run, the index of its longest frame, e.g. `[0,0,3,0,0]`; reported only. */
    val longestAt: String get() = runs.joinToString(",", "[", "]") { it.longestIndex.toString() }

    /** Per run, its frame count; reported only. */
    val framesPerRun: String get() = runs.joinToString(",", "[", "]") { it.frameNanos.size.toString() }

    /** The median first frame in reference typical frames; null without a usable reference. Reported only. */
    fun firstFrameRatio(reference: JankReference?): Double? =
        reference?.takeIf { it.usable }?.let { medianFirstFrameMs / it.typicalFrameMs }

    /** The first-frame and longest-frame fields of a report line, shared with the reference's line. */
    fun whereFields(reference: JankReference?): String = "medianFirstFrame=${format(medianFirstFrameMs)}ms " +
        "firstFrame=${firstFrameRatio(reference)?.let(::format) ?: "-"}x " +
        "frames=$framesPerRun longestAt=$longestAt"

    /** The absolute limit of D-117 Q16: reported, never gating. */
    val passesAbsolute: Boolean
        get() = runs.isNotEmpty() &&
            medianJanky <= JankGate.MAX_JANKY_FRAMES &&
            medianMaxFrameMs <= JankGate.MAX_FRAME_MS

    /** The median longest frame in reference typical frames; null without a usable reference. */
    fun stallRatio(reference: JankReference?): Double? =
        reference?.takeIf { it.usable }?.let { medianMaxFrameMs / it.typicalFrameMs }

    /** The janky frames allowed: the reference's, scaled by frame count, plus [JankGate.JANKY_ALLOWANCE]. */
    fun jankyAllowance(reference: JankReference?): Double? = reference?.takeIf { it.usable }?.let {
        it.medianJanky * (medianFrames / it.medianFrames) + JankGate.JANKY_ALLOWANCE
    }

    /** The relative verdict, the one that gates; never a pass without a usable reference. */
    fun passes(reference: JankReference?): Boolean {
        val stall = stallRatio(reference)
        val allowance = jankyAllowance(reference)
        return runs.isNotEmpty() && stall != null && allowance != null &&
            stall <= JankGate.STALL_FACTOR && medianJanky <= allowance
    }

    private fun verdict(reference: JankReference?): String = when {
        reference?.usable != true -> "NOREF"
        passes(reference) -> "PASS"
        else -> "OVER"
    }

    /** One line per transition; CI collects the lines that start with [LINE_PREFIX] from the logcat. */
    fun line(budgetNs: Long, windows: Int, reference: JankReference?): String =
        "$LINE_PREFIX ${transition.padEnd(TRANSITION_COLUMN)} api=${Build.VERSION.SDK_INT} " +
            "budget=${format(budgetNs / NANOS_PER_MS)}ms runs=${runs.size} " +
            "medianJanky=$medianJanky medianFrames=${format(medianFrames)} " +
            "medianMaxFrame=${format(medianMaxFrameMs)}ms worstMaxFrame=${format(worstMaxFrameMs)}ms " +
            "absolute=${if (passesAbsolute) "PASS" else "OVER"} " +
            "stall=${stallRatio(reference)?.let(::format) ?: "-"}x(max ${format(JankGate.STALL_FACTOR)}) " +
            "jankyAllowed=${jankyAllowance(reference)?.let(::format) ?: "-"} " +
            "${whereFields(reference)} " +
            "verdict=${verdict(reference)} gate=${JankGate.describe} windows=$windows perRun=$runs"

    companion object {
        const val LINE_PREFIX = "TransitionJank:"
        const val TRANSITION_COLUMN = 28
    }
}

/**
 * The runs of [ReferenceTransition] and what the relative gate divides by: its typical frame (the median across runs
 * of each run's median frame), its median janky count and its median frame count.
 */
internal data class JankReference(val report: JankReport) {
    val typicalFrameMs: Double get() = median(report.runs.map { it.medianFrameMs })
    val medianJanky: Int get() = report.medianJanky
    val medianFrames: Double get() = report.medianFrames
    val usable: Boolean get() = report.runs.isNotEmpty() && typicalFrameMs > 0.0 && medianFrames > 0.0

    /** The reference's own report line, printed before the transitions' lines. */
    fun line(budgetNs: Long, windows: Int): String =
        "${JankReport.LINE_PREFIX} ${report.transition.padEnd(JankReport.TRANSITION_COLUMN)} " +
            "api=${Build.VERSION.SDK_INT} budget=${format(budgetNs / NANOS_PER_MS)}ms runs=${report.runs.size} " +
            "medianJanky=$medianJanky medianFrames=${format(medianFrames)} " +
            "typicalFrame=${format(typicalFrameMs)}ms medianMaxFrame=${format(report.medianMaxFrameMs)}ms " +
            "worstMaxFrame=${format(report.worstMaxFrameMs)}ms ${report.whereFields(this)} " +
            "usable=$usable windows=$windows perRun=${report.runs}"
}

private const val NANOS_PER_MS = 1_000_000.0

private fun format(value: Double) = String.format(Locale.ROOT, "%.1f", value)

/** The median; of an even count the mean of the middle two. */
private fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}
