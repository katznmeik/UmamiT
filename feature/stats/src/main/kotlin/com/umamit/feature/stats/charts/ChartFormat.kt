package com.umamit.feature.stats.charts

import android.content.res.Resources
import com.umamit.core.ui.GermanFormat
import com.umamit.feature.stats.R
import com.umamit.feature.stats.consumptionGrams
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** What a bar chart counts; decides the value label („6 Sitzungen", „38 g", „64,80 €") and the TalkBack text. */
enum class ChartUnit {
    SESSIONS,
    GRAMS,
    CUPS,
    EUROS,
    ;

    /** [value] with its unit in German format, for the value label and TalkBack. */
    fun format(resources: Resources, value: Double): String = when (this) {
        SESSIONS -> resources.getQuantityString(R.plurals.stats_chart_sessions, value.roundToInt(), count(value))
        CUPS -> resources.getQuantityString(R.plurals.stats_chart_cups, value.roundToInt(), count(value))
        GRAMS -> resources.getString(R.string.stats_chart_grams, consumptionGrams(value))
        EUROS -> resources.getString(R.string.stats_chart_euros, GermanFormat.euros((value * CENTS).roundToLong()))
    }

    private fun count(value: Double) = ChartNumbers.axis(value.roundToInt().toDouble())

    private companion object {
        const val CENTS = 100
    }
}

/** German number helpers shared by the charts. */
internal object ChartNumbers {
    private const val MAX_AXIS_DECIMALS = 1

    /** A quiet axis number: German separators, at most one decimal („1.200", „2,5"). */
    fun axis(value: Double): String = NumberFormat.getNumberInstance(Locale.GERMANY).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = MAX_AXIS_DECIMALS
    }.format(value)

    /** A rating to one decimal with a comma („4,2"). */
    fun rating(value: Double): String = GermanFormat.gramsOneDecimal(value)
}

/** The y axis of the bar charts: the three label values 0, [step], 2 × [step]. */
internal data class NiceAxis(val step: Double) {
    val max: Double get() = step * 2
    val labels: List<Double> get() = listOf(0.0, step, max)
}

private const val AXIS_FALLBACK_STEP = 1.0
private const val STEP_2 = 2.0
private const val STEP_2_5 = 2.5
private const val STEP_5 = 5.0
private const val STEP_10 = 10.0
private const val STEP_TOLERANCE = 1e-9
private val STEPS = listOf(1.0, STEP_2, STEP_5, STEP_10)
private val STEPS_WITH_2_5 = listOf(1.0, STEP_2, STEP_2_5, STEP_5, STEP_10)

/**
 * The smallest 1/2/(2.5)/5 × 10^k step whose double covers [maxValue], so the three y labels
 * (0, step, 2 × step) are round and the tallest bar stays inside the plot.
 */
internal fun niceAxis(maxValue: Double): NiceAxis {
    if (maxValue <= 0.0 || maxValue.isNaN()) return NiceAxis(AXIS_FALLBACK_STEP)
    val needed = maxValue / 2
    val base = STEP_10.pow(floor(log10(needed)))
    // 2.5 × 10^k only from 25 up, where the labels (25, 250 …) stay whole numbers.
    val multipliers = if (base >= STEP_10) STEPS_WITH_2_5 else STEPS
    val step = multipliers.map { it * base }.first { it >= needed * (1 - STEP_TOLERANCE) }
    return NiceAxis(step)
}

/**
 * Shows every k-th label, counted back from the last one, so the labels never overlap: [slot] is the
 * width of one period, [labelWidth] the widest label, [gap] the least space between two labels (all in px).
 */
internal fun labelStride(slot: Float, labelWidth: Float, gap: Float): Int =
    if (slot <= 0f) 1 else ceil((labelWidth + gap) / slot).toInt().coerceAtLeast(1)

/** Whether period [index] of [count] carries an x label at [stride] (the last one always does). */
internal fun showsLabel(index: Int, count: Int, stride: Int): Boolean = (count - 1 - index) % stride == 0

private const val PERCENT = 100.0

/** Percentages for [counts] that add up to 100 (largest remainder), so a legend never reads 99 % or 101 %. */
internal fun percentages(counts: List<Int>): List<Int> {
    val total = counts.sum()
    if (total <= 0) return counts.map { 0 }
    val exact = counts.map { it * PERCENT / total }
    val result = exact.map { floor(it).toInt() }.toMutableList()
    var missing = PERCENT.toInt() - result.sum()
    for (index in exact.indices.sortedByDescending { exact[it] - result[it] }) {
        if (missing <= 0) break
        result[index]++
        missing--
    }
    return result
}
