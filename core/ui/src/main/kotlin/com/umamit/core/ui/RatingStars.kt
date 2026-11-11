package com.umamit.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.model.TastingLimits
import java.util.Locale
import kotlin.math.roundToInt

/** Test tag of the row; the stars are [ratingStarTag] 1 … 5. */
const val RATING_STARS_TAG = "rating_stars"

/** Test tag of star [n] (1–5). */
fun ratingStarTag(n: Int): String = "rating_star_$n"

private const val STAR_COUNT = 5
private val STAR_TARGET = 48.dp
private val STAR_SIZE = 30.dp
private const val EMPTY_ALPHA = 0.45f
private const val HALF_FILL = 0.5f

/** The nine values between 0 and 5 in half steps, as `ProgressBarRangeInfo.steps` counts them. */
private const val STEP_COUNT = 9

/** The tap cycle of `RatingStars` (03 §3.8, JRN-02): pure, so it is tested without a screen. */
object RatingCycle {
    private const val STEP = TastingLimits.RATING_STEP

    /**
     * The rating after a tap on star [star] (1–5): another star sets that many whole stars; the same star again
     * takes half off (n → n − 0,5); once more clears (null).
     */
    fun afterTap(current: Double?, star: Int): Double? {
        require(star in 1..STAR_COUNT) { "star $star" }
        val whole = star.toDouble()
        return when (current) {
            whole -> whole - STEP
            whole - STEP -> null
            else -> whole
        }
    }

    /** One TalkBack step up or down in half stars, clamped at 5,0; below 0,5 it clears. */
    fun step(current: Double?, up: Boolean): Double? {
        val next = (current ?: 0.0) + if (up) STEP else -STEP
        return if (next < TastingLimits.MIN_RATING) null else next.coerceAtMost(TastingLimits.MAX_RATING)
    }

    /** The nearest half step to a TalkBack [progress] target; below a quarter star it clears. */
    fun snap(progress: Float): Double? {
        val halves = (progress * 2f).roundToInt() / 2.0
        return if (halves < TastingLimits.MIN_RATING) null else halves.coerceAtMost(TastingLimits.MAX_RATING)
    }

    /** „3,5", „4": the value as TalkBack says it. */
    fun valueText(rating: Double): String =
        if (rating % 1.0 == 0.0) rating.toInt().toString() else "%.1f".format(Locale.GERMAN, rating)
}

/**
 * The one rating input (03 §3.8, JRN-02, D-112): five stars in 48 dp targets. A tap sets whole stars, a second tap on
 * the same star takes half off, a third clears ([RatingCycle]); a half star is drawn half filled; each change gives a
 * light tick. TalkBack reads „Bewertung, 3,5 von 5 Sternen" / „Bewertung, keine" (label plus state), adjusts in half
 * steps and offers „Bewertung entfernen". Stateless: [value] is shown as given, [onChange] gets the new rating (null
 * for none).
 */
@Composable
fun RatingStars(value: Double?, onChange: (Double?) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val currentValue = rememberUpdatedState(value)
    val currentOnChange = rememberUpdatedState(onChange)
    val change = { new: Double? ->
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        currentOnChange.value(new)
    }
    val label = stringResource(R.string.ui_rating_label)
    val state = if (value == null) {
        stringResource(R.string.ui_rating_none)
    } else {
        stringResource(R.string.ui_rating_value, RatingCycle.valueText(value))
    }
    val remove = stringResource(R.string.ui_rating_remove)
    Row(
        modifier = modifier
            .testTag(RATING_STARS_TAG)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = state
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = (value ?: 0.0).toFloat(),
                    range = 0f..TastingLimits.MAX_RATING.toFloat(),
                    steps = STEP_COUNT,
                )
                setProgress { target ->
                    val new = RatingCycle.snap(target)
                    if (new == currentValue.value) {
                        false
                    } else {
                        change(new)
                        true
                    }
                }
                if (value != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(remove) {
                            change(null)
                            true
                        },
                    )
                }
            },
    ) {
        for (star in 1..STAR_COUNT) {
            Star(
                fill = starFill(value, star),
                onTap = { change(RatingCycle.afterTap(currentValue.value, star)) },
                modifier = Modifier.testTag(ratingStarTag(star)),
            )
        }
    }
}

/** How much of star [star] is filled: 0, 0,5 or 1. */
private fun starFill(value: Double?, star: Int): Float = when {
    value == null -> 0f
    value >= star -> 1f
    value >= star - TastingLimits.RATING_STEP -> HALF_FILL
    else -> 0f
}

@Composable
private fun Star(fill: Float, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(STAR_TARGET)
            .clickable(interactionSource = null, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        val painter = painterResource(UmamiIcons.StarFilled)
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.outlineVariant.copy(alpha = EMPTY_ALPHA)),
            modifier = Modifier.size(STAR_SIZE),
        )
        if (fill > 0f) {
            Image(
                painter = painter,
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .size(STAR_SIZE)
                    .drawWithContent {
                        clipRect(right = size.width * fill) { this@drawWithContent.drawContent() }
                    },
            )
        }
    }
}
