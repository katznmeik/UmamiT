package com.umamit.feature.stats.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.motion.LocalReduceMotion
import com.umamit.feature.stats.R
import kotlin.math.roundToInt

private val HABIT_BAR_MAX = 56.dp
private val HABIT_BAR_WIDTH = 20.dp
private val HABIT_BAR_RADIUS = 3.dp
private val HABIT_BAR_STUB = 2.dp
private val HABIT_GAP = 4.dp
private val HABIT_ROW_BAR = 14.dp
private val HABIT_ROW_GAP = 8.dp
private val HABIT_ROW_SPACING = 6.dp
private const val PERCENT = 100

/**
 * A row of small vertical bars with the percentage above each (SCR-18 Gewohnheiten): day part or weekday. The
 * tallest share fills the bar height; the percentages are printed, so the bars take no tap. [title] opens
 * the TalkBack list („Tageszeit. Morgen: 12 %. …").
 */
@Composable
fun HabitBars(entries: List<HabitShare>, title: String, modifier: Modifier = Modifier, animateIn: Boolean = true) {
    val reduceMotion = LocalReduceMotion.current
    val reveal = rememberChartReveal(entries.size, title, animateIn, reduceMotion)
    // The bars morph as fractions of the tallest share, so the heights move with it.
    val morph = rememberChartMorph(entries, reduceMotion) { list ->
        val peak = (list.maxOfOrNull { it.share } ?: 0.0).coerceAtLeast(Double.MIN_VALUE)
        list.map { (it.share / peak).toFloat().coerceIn(0f, 1f) }
    }
    val shown = morph.displayed
    val percents = shown.map { stringResource(R.string.stats_chart_percent, (it.share * PERCENT).roundToInt()) }
    val description = "$title. " + shown.indices.joinToString(" ") { shown[it].label + ": " + percents[it] + "." }
    val maxBar = with(LocalDensity.current) { HABIT_BAR_MAX.toPx() }
    val stub = with(LocalDensity.current) { HABIT_BAR_STUB.toPx() }
    val semantics = Modifier.trackReveal(reveal).graphicsLayer { alpha = reveal.alpha }.clearAndSetSemantics {
        contentDescription = description
        testTag = STATS_CHART_TAG
    }
    if (LocalDensity.current.fontScale >= BELOW_FONT_SCALE) {
        HabitRows(shown, percents, morph, reveal, modifier.then(semantics))
        return
    }
    Row(
        modifier.trackReveal(reveal).graphicsLayer { alpha = reveal.alpha }.clearAndSetSemantics {
            contentDescription = description
            testTag = STATS_CHART_TAG
        },
        verticalAlignment = Alignment.Bottom,
    ) {
        shown.forEachIndexed { index, entry ->
            val empty = entry.share <= 0.0
            val barColor = if (empty) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary
            val barHeight = { if (empty) stub else maxBar * morph.fraction(index) * reveal.fraction(index) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                // The layout stays put; the percentage rides down to the bar's top in the graphics layer.
                Text(
                    percents[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer { translationY = maxBar - barHeight() },
                )
                Spacer(Modifier.height(HABIT_GAP))
                Canvas(Modifier.size(HABIT_BAR_WIDTH, HABIT_BAR_MAX)) {
                    val height = barHeight()
                    val radius = CornerRadius(HABIT_BAR_RADIUS.toPx())
                    drawRoundRect(
                        barColor.copy(alpha = morph.alpha),
                        Offset(0f, size.height - height),
                        Size(size.width, height),
                        radius,
                    )
                }
                Spacer(Modifier.height(HABIT_GAP))
                Text(
                    entry.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The large-font layout: label, horizontal bar, percentage per row, so no label has to be cut. */
@Composable
private fun HabitRows(
    shown: List<HabitShare>,
    percents: List<String>,
    morph: ChartMorph<HabitShare>,
    reveal: ChartReveal,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val line = with(density) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
    val rowHeight = maxOf(line, HABIT_ROW_BAR)
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    // Three columns keep the bars aligned whatever width each label needs.
    Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(HABIT_ROW_GAP)) {
        Column(verticalArrangement = Arrangement.spacedBy(HABIT_ROW_SPACING)) {
            shown.forEach {
                Box(Modifier.heightIn(min = rowHeight), contentAlignment = Alignment.CenterStart) {
                    Text(
                        it.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HABIT_ROW_SPACING)) {
            shown.forEachIndexed { index, entry ->
                val isEmpty = entry.share <= 0.0
                val color = if (isEmpty) empty else primary
                Box(
                    Modifier.fillMaxWidth().height(rowHeight).drawBehind {
                        val fraction = if (isEmpty) 0f else morph.fraction(index) * reveal.fraction(index)
                        val width = maxOf(size.width * fraction, HABIT_BAR_STUB.toPx())
                        val h = HABIT_ROW_BAR.toPx()
                        drawRoundRect(
                            color.copy(alpha = morph.alpha),
                            Offset(0f, (size.height - h) / 2),
                            Size(width, h),
                            CornerRadius(HABIT_BAR_RADIUS.toPx()),
                        )
                    },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(HABIT_ROW_SPACING)) {
            percents.forEach {
                Box(Modifier.heightIn(min = rowHeight), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}
