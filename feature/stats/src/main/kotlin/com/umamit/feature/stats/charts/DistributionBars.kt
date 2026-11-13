package com.umamit.feature.stats.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val reveal = rememberChartReveal(STAR_LEVELS, animateIn, LocalReduceMotion.current)
    val morphed = rememberMorphed(counts.map { it.toFloat() })
    val peak = (morphed.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val description = "$title. " + (STAR_LEVELS downTo 1).joinToString(" ") { stars ->
        resources.getString(R.string.stats_chart_stars_axis, stars) + ": " +
            ChartNumbers.axis(counts[stars - 1].toDouble()) +
            "."
    }
    Column(
        modifier.graphicsLayer { alpha = reveal.alpha }.clearAndSetSemantics {
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
                Box(Modifier.weight(1f)) {
                    val share = (morphed[stars - 1] / peak * reveal.fraction(row)).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth(share)
                            .height(ROW_BAR_HEIGHT)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(BAR_RADIUS)),
                    )
                }
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
