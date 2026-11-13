package com.umamit.feature.stats.charts

import androidx.compose.ui.graphics.Color

/** One period of a [PeriodBarChart]: [label] under the bar („KW 44", „Okt."), [value] its height. */
data class PeriodValue(
    val label: String,
    val value: Double,
    /** The period as the value label and TalkBack name it; defaults to [label]. */
    val fullLabel: String = label,
)

/** One period of a [RatingLineChart]: the average [rating] (1–5), or null when nothing was rated in it. */
data class RatingPoint(
    val label: String,
    val rating: Double?,
    /** The period as the value label and TalkBack name it; defaults to [label]. */
    val fullLabel: String = label,
)

/**
 * One Sorte of the [SortenDonut]: [count] sessions. [color] is its type colour (03 §3.1) or null when it
 * has none; [isOther] marks „Andere", which takes `outlineVariant`.
 */
data class DonutSlice(val label: String, val count: Int, val color: Color? = null, val isOther: Boolean = false)

/** One bar of [HabitBars]: [label] („Morgen", „Mo") and its [share] (0–1) of the range's sessions. */
data class HabitShare(val label: String, val share: Double)
