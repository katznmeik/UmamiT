package com.umamit.feature.stats.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.motion.LocalReduceMotion
import com.umamit.core.designsystem.theme.TeaTypeColors
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.stats.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

private val DONUT_SIZE = 148.dp
private val RING_WIDTH = 26.dp
private val RING_SELECTED_EXTRA = 6.dp
private val SLICE_GAP = 2.dp
private val HIT_SLACK = 12.dp
private val LEGEND_DOT = 10.dp
private val LEGEND_SPACING = 6.dp
private val DONUT_LEGEND_SPACING = 16.dp
private const val FULL_CIRCLE = 360f
private const val QUARTER_TURN = 90f
private const val BELOW_FONT_SCALE = 1.5f
private const val MAX_SLICES = 6

/**
 * The type colours still free for a Sorte without its own: the order of 03 §3.1, a green series. Colours
 * a slice claimed itself are left out, so no two slices share one.
 */
private fun TeaTypeColors.palette(): List<Color> = listOf(
    gyokuro, sencha, matcha, shincha, genmaicha, hojicha, flavoured, kukichaKarigane, chinaKoreaGreen, fukamushi,
    kabusecha, other,
)

/**
 * One colour per slice, never repeated (SCR-18): a slice keeps its own colour unless an earlier slice
 * holds it; the rest take the next unused colour of [palette]; „Andere" is [otherColor] and takes none.
 */
internal fun assignSliceColors(slices: List<DonutSlice>, palette: List<Color>, otherColor: Color): List<Color> {
    val result = arrayOfNulls<Color>(slices.size)
    val used = mutableSetOf<Color>()
    slices.forEachIndexed { index, slice ->
        when {
            slice.isOther -> result[index] = otherColor

            slice.color != null && slice.color !in used -> {
                result[index] = slice.color
                used += slice.color
            }
        }
    }
    val free = palette.filter { it !in used }.iterator()
    return slices.indices.map { index ->
        result[index]
            ?: if (free.hasNext()) free.next().also { used += it } else palette.last()
    }
}

/**
 * The share of sessions per Sorte as a ring (SCR-18): at most six slices (five Sorten and „Andere"), 2 dp
 * gaps, sweeping from 12 o'clock once on first show; the legend sits right of the ring, below it at font scale
 * ≥ 1.5, with „42 %". A tap on a slice shows „Sencha · 42 % · 21 Sitzungen"; a second tap or a tap elsewhere hides it.
 * [title] opens the TalkBack list.
 */
@Composable
fun SortenDonut(
    slices: List<DonutSlice>,
    title: String,
    modifier: Modifier = Modifier,
    animateIn: Boolean = true,
    selectionResetKey: Any? = null,
) {
    val shown = slices.take(MAX_SLICES).filter { it.count > 0 }
    val resources = LocalResources.current
    val reduceMotion = LocalReduceMotion.current
    val colors =
        assignSliceColors(shown, UmamiTheme.extendedColors.teaTypes.palette(), MaterialTheme.colorScheme.outlineVariant)
    val percents = percentages(shown.map { it.count })
    val reveal = rememberChartReveal(1, animateIn, reduceMotion)
    val counts = rememberMorphed(shown.map { it.count.toFloat() }, reduceMotion)
    var selected by remember(selectionResetKey, shown) { mutableStateOf<Int?>(null) }
    val sessionsText = { count: Int ->
        resources.getQuantityString(R.plurals.stats_chart_sessions, count, ChartNumbers.axis(count.toDouble()))
    }
    val description = "$title. " + shown.indices.joinToString(" ") { index ->
        "${shown[index].label}: ${percents[index]} %, ${sessionsText(shown[index].count)}."
    }
    val below = LocalDensity.current.fontScale >= BELOW_FONT_SCALE
    val geometry = DonutGeometry(DONUT_SIZE, RING_WIDTH, HIT_SLACK, LocalDensity.current.density)

    Box(
        modifier
            .graphicsLayer { alpha = reveal.alpha }
            .pointerInput(selectionResetKey, shown) {
                detectTapGestures { tap ->
                    val hit = geometry.sliceAt(tap, shown.map { it.count })
                    selected = if (hit == selected) null else hit
                }
            },
    ) {
        val ring = @Composable {
            Canvas(
                Modifier.size(DONUT_SIZE).clearAndSetSemantics {
                    contentDescription = description
                    testTag = STATS_CHART_TAG
                },
            ) {
                drawRing(counts, colors, selected, reveal.fraction(0))
            }
        }
        if (below) {
            Column(verticalArrangement = Arrangement.spacedBy(DONUT_LEGEND_SPACING)) {
                ring()
                Legend(shown, colors, percents, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(DONUT_LEGEND_SPACING), verticalAlignment = Alignment.Top) {
                ring()
                Legend(shown, colors, percents, Modifier.weight(1f))
            }
        }
        selected?.let { index ->
            val text = "${shown[index].label} · ${percents[index]} % · ${sessionsText(shown[index].count)}"
            ChartTooltip(
                text,
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val anchor = geometry.outerPoint(index, shown.map { it.count })
                    val x = (anchor.x - placeable.width / 2f).coerceIn(
                        0f,
                        max(
                            0f,
                            (
                                constraints.maxWidth -
                                    placeable.width
                                ).toFloat(),
                        ),
                    )
                    val y = max(0f, anchor.y - placeable.height)
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x.toInt(), y.toInt()) }
                },
            )
        }
    }
}

private fun DrawScope.drawRing(counts: List<Float>, colors: List<Color>, selected: Int?, progress: Float) {
    val total = counts.sum()
    if (total <= 0f) return
    val extra = RING_SELECTED_EXTRA.toPx() / 2f
    val ring = RING_WIDTH.toPx()
    val radius = (size.minDimension - ring) / 2f - extra
    val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
    val gapDegrees = if (counts.count { it > 0f } >
        1
    ) {
        Math.toDegrees((SLICE_GAP.toPx() / radius).toDouble()).toFloat()
    } else {
        0f
    }
    val limit = FULL_CIRCLE * progress
    var start = 0f
    counts.forEachIndexed { index, count ->
        val sweep = FULL_CIRCLE * count / total
        val from = start + gapDegrees / 2f
        val to = minOf(start + sweep - gapDegrees / 2f, limit)
        start += sweep
        if (to <= from) return@forEachIndexed
        val width = if (index == selected) ring + RING_SELECTED_EXTRA.toPx() else ring
        drawArc(
            color = colors[index],
            startAngle = from - QUARTER_TURN,
            sweepAngle = to - from,
            useCenter = false,
            topLeft = topLeft,
            size = Size(radius * 2, radius * 2),
            style = Stroke(width),
        )
    }
}

/** The ring's hit-test and tooltip anchor, in the donut's own pixel space (the donut sits at the origin). */
private class DonutGeometry(val size: Dp, val ring: Dp, val slack: Dp, val density: Float) {
    private val sizePx get() = size.value * density
    private val outer get() = sizePx / 2f - RING_SELECTED_EXTRA.value * density / 2f + ring.value * density / 2f
    private val inner get() = outer - ring.value * density

    fun sliceAt(tap: Offset, counts: List<Int>): Int? {
        val center = Offset(sizePx / 2f, sizePx / 2f)
        val distance = hypot(tap.x - center.x, tap.y - center.y)
        val slack = slack.value * density
        val onRing = distance >= inner - slack && distance <= outer + slack
        val degrees = Math.toDegrees(atan2((tap.y - center.y).toDouble(), (tap.x - center.x).toDouble())).toFloat()
        val angle = (degrees + QUARTER_TURN + FULL_CIRCLE) % FULL_CIRCLE
        val total = counts.sum().toFloat()
        val ends = counts.runningFold(0f) { start, count -> start + FULL_CIRCLE * count / total }.drop(1)
        return if (onRing) ends.indexOfFirst { angle < it }.takeIf { it >= 0 } else null
    }

    fun outerPoint(index: Int, counts: List<Int>): Offset {
        val total = counts.sum().toFloat()
        val before = counts.take(index).sum()
        val middle = FULL_CIRCLE * (before + counts[index] / 2f) / total - QUARTER_TURN
        val radians = Math.toRadians(middle.toDouble())
        val center = sizePx / 2f
        return Offset(center + (outer * cos(radians)).toFloat(), center + (outer * sin(radians)).toFloat())
    }
}

@Composable
private fun Legend(slices: List<DonutSlice>, colors: List<Color>, percents: List<Int>, modifier: Modifier = Modifier) {
    Column(modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(LEGEND_SPACING)) {
        slices.forEachIndexed { index, slice ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LEGEND_SPACING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(LEGEND_DOT).background(colors[index], CircleShape))
                Text(
                    slice.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.stats_chart_percent, percents[index]),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
