package com.umamit.feature.stats.charts

import android.content.res.Resources
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.motion.LocalReduceMotion
import com.umamit.feature.stats.R

private const val RATING_MIN = 1.0
private const val RATING_MAX = 5.0
private const val RATING_MID = 3.0
private val LINE_WIDTH = 2.dp
private val POINT_RADIUS = 4.dp
private val SELECTED_HALO_RADIUS = 9.dp
private val HIT_RADIUS = 28.dp
private const val HALO_ALPHA = 0.25f

/**
 * The average rating per period (SCR-18): a 2 dp `primary` line with 4 dp points, y from 1 to 5 ★, points
 * only where a value exists and the line broken across periods without one. Tap, motion and TalkBack as in
 * [PeriodBarChart]; the line grows from the baseline.
 */
@Composable
fun RatingLineChart(
    entries: List<RatingPoint>,
    title: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    animateIn: Boolean = true,
    selectionResetKey: Any? = null,
) {
    val resources = LocalResources.current
    val reduceMotion = LocalReduceMotion.current
    val reveal = rememberChartReveal(entries.size, animateIn, reduceMotion)
    // A missing rating morphs as 0 (below the axis, so it stays on the baseline); the draw skips it.
    val values = rememberMorphed(entries.map { (it.rating ?: 0.0).toFloat() }, reduceMotion)
    val color = MaterialTheme.colorScheme.primary
    val ticks = listOf(RATING_MIN, RATING_MID, RATING_MAX).map {
        YTick(it, resources.getString(R.string.stats_chart_stars_axis, it.toInt()))
    }
    val noRating = stringResource(R.string.stats_chart_no_rating)
    val noData = stringResource(R.string.stats_chart_no_data)
    val description = "$title. " + if (entries.isEmpty()) {
        noData
    } else {
        entries.joinToString(" ") { "${it.fullLabel}: ${it.rating?.let { r -> starsText(resources, r) } ?: noRating}." }
    }
    val spec = XYChartSpec(
        count = entries.size,
        xLabels = entries.map { it.label },
        yTicks = ticks,
        yMin = RATING_MIN,
        yMax = RATING_MAX,
        description = description,
        scrollable = scrollable,
        selectionKey = listOf(selectionResetKey, entries),
        alpha = reveal.alpha,
        tooltip = { index ->
            "${entries[index].fullLabel} · " + starsText(resources, entries[index].rating ?: 0.0)
        },
        hitTest = { tap, geo ->
            val distance = { index: Int ->
                (Offset(geo.centerX(index), geo.yOf(entries[index].rating!!)) - tap).getDistance()
            }
            entries.indices.filter { entries[it].rating != null }.minByOrNull(distance)
                ?.takeIf { distance(it) <= HIT_RADIUS.toPx() }
        },
        anchor = { index, geo ->
            Offset(
                geo.centerX(index),
                geo.yOf(entries[index].rating ?: RATING_MIN) - POINT_RADIUS.toPx(),
            )
        },
    )
    XYChartFrame(spec, modifier) { geo, selected ->
        val points = entries.indices.map { index ->
            entries[index].rating?.let {
                val y = geo.baseline - (geo.baseline - geo.yOf(values[index].toDouble())) * reveal.fraction(index)
                Offset(geo.centerX(index), y)
            }
        }
        drawRatingLine(points, selected, color)
    }
}

private fun starsText(resources: Resources, rating: Double): String =
    resources.getString(R.string.stats_chart_stars_value, ChartNumbers.rating(rating))

/** The line over [points] (null = no rating), broken at every gap, then the points and the selected halo. */
private fun DrawScope.drawRatingLine(points: List<Offset?>, selected: Int?, color: Color) {
    val stroke = Stroke(LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val runs = mutableListOf<MutableList<Offset>>()
    points.forEachIndexed { index, offset ->
        when {
            offset == null -> Unit
            index > 0 && points[index - 1] != null -> runs.last() += offset
            else -> runs += mutableListOf(offset)
        }
    }
    runs.filter { it.size > 1 }.forEach { run ->
        val path = Path().apply {
            moveTo(run.first().x, run.first().y)
            run.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = stroke)
    }
    points.forEachIndexed { index, offset ->
        if (offset == null) return@forEachIndexed
        if (index == selected) drawCircle(color.copy(alpha = HALO_ALPHA), SELECTED_HALO_RADIUS.toPx(), offset)
        drawCircle(color, POINT_RADIUS.toPx(), offset)
    }
}
