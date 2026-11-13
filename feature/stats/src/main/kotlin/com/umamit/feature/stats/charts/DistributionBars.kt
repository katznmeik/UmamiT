package com.umamit.feature.stats.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.motion.LocalReduceMotion
import com.umamit.feature.stats.R

private const val STAR_LEVELS = 5
private val STAR_LABEL_WIDTH = 48.dp
private val COUNT_MIN_WIDTH = 32.dp
private val ROW_BAR_HEIGHT = 12.dp
private val ROW_SPACING = 8.dp
private val BAR_RADIUS = 3.dp

/**
 * The rating distribution (SCR-18): five horizontal bars, 5 ★ at the top to 1 ★ at the bottom, each with its
 * count. [countsByStars] has five entries, index 0 = 1 ★ … index 4 = 5 ★. The counts are printed, so the
 * bars take no tap; they grow from the left on first show and as a fade under reduce motion.
 */
@Composable
fun DistributionBars(
    countsByStars: List<Int>,
    title: String,
    modifier: Modifier = Modifier,
    animateIn: Boolean = true,
) {
    val counts = List(STAR_LEVELS) { countsByStars.getOrElse(it) { 0 } }
    val resources = LocalResources.current
    val reduceMotion = LocalReduceMotion.current
    val reveal = rememberChartReveal(STAR_LEVELS, title, animateIn, reduceMotion)
    // The bars morph as fractions of the longest, so the lengths move with it.
    val morph = rememberChartMorph(counts, reduceMotion) { list ->
        val peak = (list.maxOrNull() ?: 0).coerceAtLeast(1)
        list.map { it.toFloat() / peak }
    }
    val bar = MaterialTheme.colorScheme.primary
    val description = "$title. " + (STAR_LEVELS downTo 1).joinToString(" ") { stars ->
        resources.getString(R.string.stats_chart_stars_axis, stars) + ": " +
            ChartNumbers.axis(counts[stars - 1].toDouble()) +
            "."
    }
    Column(
        modifier.trackReveal(reveal).graphicsLayer { alpha = reveal.alpha }.clearAndSetSemantics {
            contentDescription = description
            testTag = STATS_CHART_TAG
        },
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        (STAR_LEVELS downTo 1).forEachIndexed { row, stars ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.stats_chart_stars_axis, stars),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(STAR_LABEL_WIDTH),
                )
                // The bar is drawn, not laid out, so a frame of the grow never measures anything.
                Box(
                    Modifier.weight(1f).height(ROW_BAR_HEIGHT).drawBehind {
                        val width = size.width * morph.fraction(stars - 1) * reveal.fraction(row)
                        drawRoundRect(bar, Offset.Zero, Size(width, size.height), CornerRadius(BAR_RADIUS.toPx()))
                    },
                )
                Text(
                    ChartNumbers.axis(counts[stars - 1].toDouble()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = COUNT_MIN_WIDTH),
                )
            }
        }
    }
}
