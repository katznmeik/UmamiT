package com.umamit.feature.stats.charts

import android.content.res.Resources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
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
private val DASH_ON = 6.dp
private val DASH_OFF = 4.dp
private val LEGEND_SAMPLE_WIDTH = 24.dp
private val LEGEND_GAP = 6.dp
private val LEGEND_SPACING = 12.dp
private val LEGEND_BOTTOM = 4.dp

/** The two series of the chart (D-117 Q6): *Tee* solid in `primary`, *Zubereitung* dashed in `tertiary`. */
private enum class Series { TEA, BREWING }

private fun RatingPoint.of(series: Series): Double? = when (series) {
    Series.TEA -> rating
    Series.BREWING -> brewingRating
}

private fun ratingFraction(rating: Double?): Float =
    (((rating ?: RATING_MIN) - RATING_MIN) / (RATING_MAX - RATING_MIN)).toFloat()

/**
 * The average rating per period (SCR-18): a 2 dp `primary` line for *Tee* and, from v0.7.1 (D-117), a dashed
 * `tertiary` line for *Zubereitung*, each with its own 4 dp points, y from 1 to 5 ★, points only where a value
 * exists and each line broken across periods without its rating; a legend above says which is which. Tap, motion and
 * TalkBack as in [PeriodBarChart]; the lines grow from the baseline.
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
    val morph = rememberChartMorph(entries, reduceMotion) { list -> list.map { ratingFraction(it.rating) } }
    val brewingMorph = rememberChartMorph(entries, reduceMotion) { list ->
        list.map { ratingFraction(it.brewingRating) }
    }
    val shown = morph.displayed
    val tea = MaterialTheme.colorScheme.primary
    val brewing = MaterialTheme.colorScheme.tertiary
    val ticks = listOf(RATING_MIN, RATING_MID, RATING_MAX).map {
        YTick(it, resources.getString(R.string.stats_chart_stars_axis, it.toInt()))
    }
    val noRating = stringResource(R.string.stats_chart_no_rating)
    val noData = stringResource(R.string.stats_chart_no_data)
    val description = "$title. " + if (shown.isEmpty()) {
        noData
    } else {
        shown.joinToString(" ") { "${it.fullLabel}: ${talkBackRatings(resources, it, noRating)}." }
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
        tooltip = { index -> "${shown[index].fullLabel} · " + tooltipRatings(resources, shown[index]) },
        hitTest = { tap, geo ->
            val distance = { index: Int, series: Series ->
                (Offset(geo.centerX(index), geo.yOf(shown[index].of(series) ?: RATING_MIN)) - tap).getDistance()
            }
            shown.indices
                .flatMap { index -> Series.entries.filter { shown[index].of(it) != null }.map { index to it } }
                .minByOrNull { (index, series) -> distance(index, series) }
                ?.takeIf { (index, series) -> distance(index, series) <= HIT_RADIUS.toPx() }
                ?.first
        },
        anchor = { index, geo ->
            val top = Series.entries.mapNotNull { shown[index].of(it) }.maxOrNull() ?: RATING_MIN
            Offset(geo.centerX(index), geo.yOf(top) - POINT_RADIUS.toPx())
        },
    )
    val path = remember { Path() }
    Column(modifier) {
        RatingLegend(tea, brewing)
        XYChartFrame(spec, Modifier.trackReveal(reveal)) { geo, selected ->
            val height = geo.baseline - geo.top
            val teaY = { index: Int -> geo.baseline - height * morph.fraction(index) * reveal.fraction(index) }
            val brewingY = { index: Int ->
                geo.baseline - height * brewingMorph.fraction(index) * reveal.fraction(index)
            }
            val alpha = morph.alpha
            drawRatingLine(shown.map { it.rating }, geo, teaY, selected, tea.copy(alpha = alpha), path, dashed = false)
            drawRatingLine(
                shown.map { it.brewingRating },
                geo,
                brewingY,
                selected,
                brewing.copy(alpha = alpha),
                path,
                dashed = true,
            )
        }
    }
}

/** „— Tee · - - Zubereitung" above the chart: the kind is told by the line's shape too, not by colour alone. */
@Composable
private fun RatingLegend(tea: Color, brewing: Color) {
    val textStyle = MaterialTheme.typography.labelMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        // The chart's own description names both kinds; the legend is for the eye.
        modifier = Modifier.padding(bottom = LEGEND_BOTTOM).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(LEGEND_SPACING),
    ) {
        LegendItem(stringResource(R.string.stats_chart_legend_tea), tea, dashed = false, textStyle, muted)
        LegendItem(stringResource(R.string.stats_chart_legend_brewing), brewing, dashed = true, textStyle, muted)
    }
}

@Composable
@Suppress("LongParameterList") // the label, line colour and shape, text style and colour of one legend entry
private fun LegendItem(label: String, color: Color, dashed: Boolean, style: TextStyle, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(LEGEND_SAMPLE_WIDTH).height(LINE_WIDTH)) {
            val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(DASH_ON.toPx(), DASH_OFF.toPx())) else null
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = LINE_WIDTH.toPx(),
                cap = StrokeCap.Butt,
                pathEffect = effect,
            )
        }
        Spacer(Modifier.width(LEGEND_GAP))
        Text(label, style = style, color = textColor)
    }
}

/** „Tee 4,2, Zubereitung 3,8" for TalkBack (SCR-18); a period with neither reads [noRating]. */
private fun talkBackRatings(resources: Resources, point: RatingPoint, noRating: String): String {
    val parts = listOfNotNull(
        point.rating?.let { resources.getString(R.string.stats_chart_rating_tea, ChartNumbers.rating(it)) },
        point.brewingRating?.let { resources.getString(R.string.stats_chart_rating_brewing, ChartNumbers.rating(it)) },
    )
    return if (parts.isEmpty()) noRating else parts.joinToString(", ")
}

/** The value label's text: „Tee 4,5 ★ · Zubereitung 3,8 ★", each kind only where the period has it. */
private fun tooltipRatings(resources: Resources, point: RatingPoint): String = listOfNotNull(
    point.rating?.let { resources.getString(R.string.stats_chart_tooltip_tea, ChartNumbers.rating(it)) },
    point.brewingRating?.let { resources.getString(R.string.stats_chart_tooltip_brewing, ChartNumbers.rating(it)) },
).joinToString(" · ").ifEmpty { resources.getString(R.string.stats_chart_no_rating) }

/** One line over the rated periods, broken at every gap, then its points and the selected halo. */
@Suppress("LongParameterList") // the values, geometry, heights, selection, colour, scratch path and dash flag
private fun DrawScope.drawRatingLine(
    values: List<Double?>,
    geo: PlotGeometry,
    yAt: (Int) -> Float,
    selected: Int?,
    color: Color,
    path: Path,
    dashed: Boolean,
) {
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(DASH_ON.toPx(), DASH_OFF.toPx())) else null
    // Round caps would add half the width to each dash end and close the 6/4 gaps of the dashed line.
    val cap = if (dashed) StrokeCap.Butt else StrokeCap.Round
    val stroke = Stroke(LINE_WIDTH.toPx(), cap = cap, join = StrokeJoin.Round, pathEffect = effect)
    var open = false
    var runLength = 0
    val flush = {
        if (runLength > 1) drawPath(path, color, style = stroke)
        path.rewind()
        open = false
        runLength = 0
    }
    for (index in values.indices) {
        if (values[index] == null) {
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
    for (index in values.indices) {
        if (values[index] == null) continue
        val center = Offset(geo.centerX(index), yAt(index))
        if (index == selected) {
            drawCircle(color.copy(alpha = color.alpha * HALO_ALPHA), SELECTED_HALO_RADIUS.toPx(), center)
        }
        drawCircle(color, POINT_RADIUS.toPx(), center)
    }
}
