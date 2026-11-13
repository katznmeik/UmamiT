package com.umamit.feature.stats.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.motion.LocalReduceMotion
import com.umamit.feature.stats.R
import kotlin.math.roundToInt

private val HABIT_BAR_MAX = 56.dp
private val HABIT_BAR_WIDTH = 20.dp
private val HABIT_BAR_RADIUS = 3.dp
private val HABIT_BAR_STUB = 2.dp
private val HABIT_GAP = 4.dp
private const val PERCENT = 100

/**
 * A row of small vertical bars with the percentage above each (SCR-18 Gewohnheiten): day part or weekday. The
 * tallest share fills the bar height; the percentages are printed, so the bars take no tap. [title] opens
 * the TalkBack list („Tageszeit. Morgen: 12 %. …").
 */
@Composable
fun HabitBars(entries: List<HabitShare>, title: String, modifier: Modifier = Modifier, animateIn: Boolean = true) {
    val reveal = rememberChartReveal(entries.size, animateIn, LocalReduceMotion.current)
    val shares = rememberMorphed(entries.map { it.share.toFloat() })
    val peak = (shares.maxOrNull() ?: 0f).coerceAtLeast(Float.MIN_VALUE)
    val percents = entries.map { stringResource(R.string.stats_chart_percent, (it.share * PERCENT).roundToInt()) }
    val description = "$title. " + entries.indices.joinToString(" ") { entries[it].label + ": " + percents[it] + "." }
    Row(
        modifier.graphicsLayer { alpha = reveal.alpha }.clearAndSetSemantics {
            contentDescription = description
            testTag = STATS_CHART_TAG
        },
        verticalAlignment = Alignment.Bottom,
    ) {
        entries.forEachIndexed { index, entry ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    percents[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(HABIT_GAP))
                val grown = (shares[index] / peak * reveal.fraction(index)).coerceIn(0f, 1f)
                val empty = entry.share <= 0.0
                Spacer(
                    Modifier
                        .width(HABIT_BAR_WIDTH)
                        .height(if (empty) HABIT_BAR_STUB else HABIT_BAR_MAX * grown)
                        .background(
                            if (empty) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(HABIT_BAR_RADIUS),
                        ),
                )
                Spacer(Modifier.height(HABIT_GAP))
                Text(
                    entry.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
