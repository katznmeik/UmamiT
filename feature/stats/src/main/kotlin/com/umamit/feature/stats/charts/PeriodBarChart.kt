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
private val HIT_SLACK_ABOVE = 16.dp
private const val BAR_WIDTH_SHARE = 0.62f
private const val EARLIER_ALPHA = 0.7f

/**
 * Bars per period (SCR-18, INS-08): `primary`, the last period in full and earlier ones at 70 %, a
 * baseline, three y labels, thinned x labels. [scrollable] (Gesamt) gives every bar a fixed width and opens at
 * the end. A tap on a bar shows „KW 44 · 6 Sitzungen"; a second tap, or a tap elsewhere, hides it. [title] opens the
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
    val axis = remember(entries) { niceAxis(entries.maxOfOrNull { it.value } ?: 0.0) }
    val reveal = rememberChartReveal(entries.size, animateIn, reduceMotion)
    val values = rememberMorphed(entries.map { it.value.toFloat() }, reduceMotion)
    val primary = MaterialTheme.colorScheme.primary
    val noValue = stringResource(R.string.stats_chart_no_data)
    val description = if (entries.isEmpty()) {
        "$title. $noValue"
    } else {
        "$title. " + entries.joinToString(" ") { "${it.fullLabel}: ${unit.format(resources, it.value)}." }
    }
    val spec = XYChartSpec(
        count = entries.size,
        xLabels = entries.map { it.label },
        yTicks = axis.labels.map { YTick(it, ChartNumbers.axis(it)) },
        yMin = 0.0,
        yMax = axis.max,
        description = description,
        scrollable = scrollable,
        selectionKey = listOf(selectionResetKey, entries),
        alpha = reveal.alpha,
        tooltip = { "${entries[it].fullLabel} · ${unit.format(resources, entries[it].value)}" },
        hitTest = { tap, geo ->
            geo.slotAt(tap.x)?.takeIf { index ->
                tap.y <= geo.baseline &&
                    tap.y >= geo.yOf(values.getOrElse(index) { 0f }.toDouble()) - HIT_SLACK_ABOVE.toPx()
            }
        },
        anchor = { index, geo -> Offset(geo.centerX(index), geo.yOf(entries[index].value)) },
    )
    XYChartFrame(spec, modifier) { geo, selected ->
        val barWidth = min(geo.slot * BAR_WIDTH_SHARE, BAR_WIDTH_MAX.toPx())
        val corner = CornerRadius(min(BAR_CORNER.toPx(), barWidth / 2f))
        values.forEachIndexed { index, value ->
            val top = geo.baseline - (geo.baseline - geo.yOf(value.toDouble())) * reveal.fraction(index)
            if (geo.baseline - top < 1f) return@forEachIndexed
            val left = geo.centerX(index) - barWidth / 2f
            val full = index == values.lastIndex || index == selected
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left,
                        top,
                        left + barWidth,
                        geo.baseline,
                        corner,
                        corner,
                        CornerRadius.Zero,
                        CornerRadius.Zero,
                    ),
                )
            }
            drawPath(path, primary.copy(alpha = if (full) 1f else EARLIER_ALPHA))
        }
    }
}
