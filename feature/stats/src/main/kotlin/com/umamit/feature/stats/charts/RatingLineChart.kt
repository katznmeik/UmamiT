package com.umamit.feature.stats.charts

import android.content.res.Resources
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val reveal = rememberChartReveal(entries.size, title, animateIn, reduceMotion)
    // A missing rating morphs as the baseline; the draw skips it.
    val morph = rememberChartMorph(entries, reduceMotion) { list ->
        list.map { (((it.rating ?: RATING_MIN) - RATING_MIN) / (RATING_MAX - RATING_MIN)).toFloat() }
    }
    val shown = morph.displayed
    val color = MaterialTheme.colorScheme.primary
    val ticks = listOf(RATING_MIN, RATING_MID, RATING_MAX).map {
        YTick(it, resources.getString(R.string.stats_chart_stars_axis, it.toInt()))
    }
    val noRating = stringResource(R.string.stats_chart_no_rating)
    val noData = stringResource(R.string.stats_chart_no_data)
    val description = "$title. " + if (shown.isEmpty()) {
        noData
    } else {
        shown.joinToString(" ") { "${it.fullLabel}: ${it.rating?.let { r -> starsText(resources, r) } ?: noRating}." }
    }
    val spec = XYChartSpec(
        count = shown.size,
        xLabels = shown.map { it.label },
        yTicks = ticks,
        yMin = RATING_MIN,
        yMax = RATING_MAX,
        description = description,
        scrollable = scrollable,
        selectionKey = listOf(selectionResetKey, shown),
        alpha = { reveal.alpha },
        tooltip = { index ->
            "${shown[index].fullLabel} · " + starsText(resources, shown[index].rating ?: 0.0)
        },
        hitTest = { tap, geo ->
            val distance = { index: Int ->
                (Offset(geo.centerX(index), geo.yOf(shown[index].rating!!)) - tap).getDistance()
            }
            shown.indices.filter { shown[it].rating != null }.minByOrNull(distance)
                ?.takeIf { distance(it) <= HIT_RADIUS.toPx() }
        },
        anchor = { index, geo ->
            Offset(
                geo.centerX(index),
                geo.yOf(shown[index].rating ?: RATING_MIN) - POINT_RADIUS.toPx(),
            )
        },
    )
    val path = remember { Path() }
    XYChartFrame(spec, modifier.trackReveal(reveal)) { geo, selected ->
        val height = geo.baseline - geo.top
        val yAt = { index: Int -> geo.baseline - height * morph.fraction(index) * reveal.fraction(index) }
        drawRatingLine(shown, geo, yAt, selected, color.copy(alpha = morph.alpha), path)
    }
}

private fun starsText(resources: Resources, rating: Double): String =
    resources.getString(R.string.stats_chart_stars_value, ChartNumbers.rating(rating))

/** The line over the rated periods, broken at every gap, then the points and the selected halo. */
private fun DrawScope.drawRatingLine(
    entries: List<RatingPoint>,
    geo: PlotGeometry,
    yAt: (Int) -> Float,
    selected: Int?,
    color: Color,
    path: Path,
) {
    val stroke = Stroke(LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    var open = false
    var runLength = 0
    val flush = {
        if (runLength > 1) drawPath(path, color, style = stroke)
        path.rewind()
        open = false
        runLength = 0
    }
    for (index in entries.indices) {
        if (entries[index].rating == null) {
            flush()
            continue
        }
        val x = geo.centerX(index)
        val y = yAt(index)
        if (open) path.lineTo(x, y) else path.moveTo(x, y)
        open = true
        runLength++
    }
    flush()
    for (index in entries.indices) {
        if (entries[index].rating == null) continue
        val center = Offset(geo.centerX(index), yAt(index))
        if (index ==
            selected
        ) {
            drawCircle(color.copy(alpha = color.alpha * HALO_ALPHA), SELECTED_HALO_RADIUS.toPx(), center)
        }
        drawCircle(color, POINT_RADIUS.toPx(), center)
    }
}
