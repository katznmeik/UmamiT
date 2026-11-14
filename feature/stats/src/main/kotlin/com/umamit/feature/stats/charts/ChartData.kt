package com.umamit.feature.stats.charts

import androidx.compose.ui.graphics.Color

/** One period of a [PeriodBarChart]: [label] under the bar („KW 44", „Okt."), [value] its height. */
data class PeriodValue(
    val label: String,
    val value: Double,
    /** The period as the value label and TalkBack name it; defaults to [label]. */
    val fullLabel: String = label,
)

/**
 * One period of a [RatingLineChart]: the average *Tee* [rating] and *Zubereitung* [brewingRating] (1–5), each null
 * when nothing of that kind was rated in it (D-117).
 */
data class RatingPoint(
    val label: String,
    val rating: Double?,
    /** The period as the value label and TalkBack name it; defaults to [label]. */
    val fullLabel: String = label,
    val brewingRating: Double? = null,
)

/**
 * One Sorte of the [SortenDonut]: [count] sessions, [percent] of the range's sessions as the domain counted it
 * (the legend and the value label print it as it is). [color] is its type colour (03 §3.1) or null when it
 * has none; [isOther] marks „Andere", which takes `outlineVariant`. [key] identifies the slice across a range change
 * (the morph and the colours follow it), so two slices with the same [label] stay two; it defaults to the label.
 */
data class DonutSlice(
    val label: String,
    val count: Int,
    val percent: Int,
    val color: Color? = null,
    val isOther: Boolean = false,
    val key: String = label,
)

/** One bar of [HabitBars]: [label] („Morgen", „Mo") and its [share] (0–1) of the range's sessions. */
data class HabitShare(val label: String, val share: Double)
