package com.umamit.feature.stats.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.motion.LocalReduceMotion
import com.umamit.feature.stats.R
import kotlin.math.min

private val BAR_WIDTH_MAX = 28.dp
private val BAR_CORNER = 3.dp
private const val BAR_WIDTH_SHARE = 0.62f
private const val EARLIER_ALPHA = 0.7f

/**
 * Bars per period (SCR-18, INS-08): `primary`, the last period in full and earlier ones at 70 %, a
 * baseline, three y labels, thinned x labels. [scrollable] (Gesamt) gives every bar a fixed width and opens at
 * the end. A tap in a bar's column (even a zero bar) shows „KW 44 · 6 Sitzungen"; a second tap, or a tap
 * elsewhere, hides it. [title] opens the
 * TalkBack text („Sitzungen pro Woche"); [unit] formats the values. [animateIn] false skips the first-show
 * grow (a tab already shown in this visit); [selectionResetKey] hides the value label when it changes.
 */
@Composable
fun PeriodBarChart(
    entries: List<PeriodValue>,
    unit: ChartUnit,
    title: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    animateIn: Boolean = true,
    selectionResetKey: Any? = null,
) {
    val resources = LocalResources.current
    val reduceMotion = LocalReduceMotion.current
    val reveal = rememberChartReveal(entries.size, title, animateIn, reduceMotion)
    // The bars morph as fractions of their axis, so a new axis never re-reads the old heights.
    val morph = rememberChartMorph(entries, reduceMotion) { list ->
        val max = niceAxis(list.maxOfOrNull { it.value } ?: 0.0).max
        list.map { (it.value / max).toFloat().coerceIn(0f, 1f) }
    }
    val shown = morph.displayed
    val axis = remember(shown) { niceAxis(shown.maxOfOrNull { it.value } ?: 0.0) }
    val primary = MaterialTheme.colorScheme.primary
    val noValue = stringResource(R.string.stats_chart_no_data)
    val description = if (shown.isEmpty()) {
        "$title. $noValue"
    } else {
        "$title. " + shown.joinToString(" ") { "${it.fullLabel}: ${unit.format(resources, it.value)}." }
    }
    val spec = XYChartSpec(
        count = shown.size,
        xLabels = shown.map { it.label },
        yTicks = axis.labels.map { YTick(it, unit.axisLabel(it)) },
        yMin = 0.0,
        yMax = axis.max,
        description = description,
        scrollable = scrollable,
        selectionKey = listOf(selectionResetKey, shown),
        alpha = { reveal.alpha },
        tooltip = { "${shown[it].fullLabel} · ${unit.format(resources, shown[it].value)}" },
        // Any tap in a bar's column counts, a zero or short bar included.
        hitTest = { tap, geo -> geo.slotAt(tap.x) },
        anchor = { index, geo -> Offset(geo.centerX(index), geo.yOf(shown[index].value)) },
        plotHeight = BAR_PLOT_HEIGHT,
    )
    val path = remember { Path() }
    XYChartFrame(spec, modifier.trackReveal(reveal)) { geo, selected ->
        val barWidth = min(geo.slot * BAR_WIDTH_SHARE, BAR_WIDTH_MAX.toPx())
        val corner = CornerRadius(min(BAR_CORNER.toPx(), barWidth / 2f))
        val fade = morph.alpha
        for (index in 0 until geo.count) {
            val height = (geo.baseline - geo.top) * morph.fraction(index) * reveal.fraction(index)
            if (height < 1f) continue
            val left = geo.centerX(index) - barWidth / 2f
            val full = index == geo.count - 1 || index == selected
            path.rewind()
            path.addRoundRect(
                RoundRect(
                    left,
                    geo.baseline - height,
                    left + barWidth,
                    geo.baseline,
                    corner,
                    corner,
                    CornerRadius.Zero,
                    CornerRadius.Zero,
                ),
            )
            drawPath(path, primary.copy(alpha = (if (full) 1f else EARLIER_ALPHA) * fade))
        }
    }
}
