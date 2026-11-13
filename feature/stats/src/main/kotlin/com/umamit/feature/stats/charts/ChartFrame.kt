package com.umamit.feature.stats.charts

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.motion.LocalReduceMotion
import com.umamit.core.designsystem.motion.MotionTokens
import kotlin.math.max
import kotlin.math.roundToInt

/** The test tag of every chart's plot node (the one TalkBack node of the chart). */
const val STATS_CHART_TAG = "stats_chart"

private val PLOT_HEIGHT = 160.dp
private val SCROLL_SLOT_MIN = 36.dp
private val AXIS_LABEL_GAP = 8.dp
private val X_LABEL_GAP = 6.dp
private val X_MIN_GAP = 8.dp
private val TOOLTIP_GAP = 4.dp
private val TOOLTIP_PADDING_H = 8.dp
private val TOOLTIP_PADDING_V = 4.dp
private val TOOLTIP_RADIUS = 6.dp
private val BASELINE_WIDTH = 1.dp
private const val SAMPLE_X_LABEL = "KW 44"
private const val HALF = 0.5f
private const val SCROLL_GLIDE_MILLIS = 250

/** The pixel geometry of one chart's plot area: [count] equal slots over [width], values from [yMin] to [yMax]. */
internal class PlotGeometry(
    val width: Float,
    val top: Float,
    val baseline: Float,
    val count: Int,
    val yMin: Double,
    val yMax: Double,
) {
    val slot: Float get() = if (count > 0) width / count else width

    fun centerX(index: Int): Float = slot * (index + HALF)

    /** The y of [value]; values outside the range are clamped to it. */
    fun yOf(value: Double): Float {
        val fraction = ((value - yMin) / (yMax - yMin)).coerceIn(0.0, 1.0).toFloat()
        return baseline - (baseline - top) * fraction
    }

    /** The slot under [x], or null outside the plot. */
    fun slotAt(x: Float): Int? = if (x < 0f || x >= width ||
        count == 0
    ) {
        null
    } else {
        (x / slot).toInt().coerceIn(0, count - 1)
    }
}

/** One quiet y-axis label. */
internal data class YTick(val value: Double, val text: String)

/** What a chart supplies to [XYChartFrame]; the frame owns the axes, scrolling, taps and the value label. */
internal class XYChartSpec(
    val count: Int,
    val xLabels: List<String>,
    val yTicks: List<YTick>,
    val yMin: Double,
    val yMax: Double,
    val description: String,
    val scrollable: Boolean,
    val selectionKey: Any?,
    val alpha: () -> Float,
    val tooltip: (Int) -> String,
    /** The index selected by a tap at [Offset] (plot pixels), or null for a tap on nothing. */
    val hitTest: Density.(Offset, PlotGeometry) -> Int?,
    /** The point the value label sits above (plot pixels). */
    val anchor: Density.(Int, PlotGeometry) -> Offset,
)

/**
 * The frame shared by the bar and line charts: three quiet y labels in a fixed column, a thin baseline,
 * thinned x labels that never overlap, an optional sideways scroll that opens at the end (Gesamt), taps
 * that show and hide the value label (INS-08), and one TalkBack node reading [XYChartSpec.description].
 * [draw] paints the marks; it gets the geometry and the selected index.
 */
@Composable
internal fun XYChartFrame(
    spec: XYChartSpec,
    modifier: Modifier = Modifier,
    draw: DrawScope.(PlotGeometry, Int?) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val labelHeight = remember(labelStyle, density) { measurer.measure(SAMPLE_X_LABEL, labelStyle).size.height }
    val topPad = labelHeight / 2f
    val bandPx = labelHeight + with(density) { X_LABEL_GAP.toPx() }
    val plotPx = with(density) { PLOT_HEIGHT.toPx() }
    val axisWidthPx = spec.yTicks.maxOfOrNull { measurer.measure(it.text, labelStyle).size.width }
        ?.plus(with(density) { AXIS_LABEL_GAP.toPx() }) ?: 0f
    val baseline = topPad + plotPx
    val geometry = { width: Float -> PlotGeometry(width, topPad, baseline, spec.count, spec.yMin, spec.yMax) }
    val selection = rememberChartSelection(spec.selectionKey)
    val selected = selection.index
    val reduceMotion = LocalReduceMotion.current

    Row(modifier.height(with(density) { (baseline + bandPx).toDp() }).graphicsLayer { alpha = spec.alpha() }) {
        YAxisLabels(spec, geometry, measurer, labelStyle, Modifier.width(with(density) { axisWidthPx.toDp() }))
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val viewport = constraints.maxWidth.toFloat()
            val content = if (spec.scrollable) {
                max(
                    viewport,
                    spec.count * with(density) {
                        SCROLL_SLOT_MIN.toPx()
                    },
                )
            } else {
                viewport
            }
            val scroll = rememberScrollState()
            if (spec.scrollable) {
                // Opens at the end; when the range changes later the view glides to the new end (snaps under reduce motion).
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(scroll.maxValue) {
                    if (shown && !reduceMotion) {
                        scroll.animateScrollTo(
                            scroll.maxValue,
                            tween(SCROLL_GLIDE_MILLIS, easing = MotionTokens.CalmEaseOut),
                        )
                    } else {
                        scroll.scrollTo(scroll.maxValue)
                    }
                    shown = true
                }
            }
            val scrollModifier = if (spec.scrollable) Modifier.horizontalScroll(scroll) else Modifier
            Box(Modifier.fillMaxSize().then(scrollModifier)) {
                PlotContent(
                    spec,
                    geometry,
                    measurer,
                    labelStyle,
                    selected,
                    selection::select,
                    draw,
                    Modifier.width(
                        with(density) {
                            content.toDp()
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun YAxisLabels(
    spec: XYChartSpec,
    geometry: (Float) -> PlotGeometry,
    measurer: TextMeasurer,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxHeight()) {
        val geo = geometry(size.width)
        spec.yTicks.forEach { tick ->
            val layout = measurer.measure(tick.text, style)
            drawText(
                measurer,
                tick.text,
                Offset(
                    size.width - layout.size.width - AXIS_LABEL_GAP.toPx(),
                    geo.yOf(tick.value) - layout.size.height / 2f,
                ),
                style,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun PlotContent(
    spec: XYChartSpec,
    geometry: (Float) -> PlotGeometry,
    measurer: TextMeasurer,
    style: TextStyle,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    draw: DrawScope.(PlotGeometry, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier.fillMaxHeight().pointerInput(spec.selectionKey, spec.count, selected) {
            detectTapGestures { tap ->
                val hit = with(spec) { hitTest(tap, geometry(size.width.toFloat())) }
                onSelect(if (hit == selected) null else hit)
            }
        },
    ) {
        Canvas(
            Modifier.fillMaxSize().clearAndSetSemantics {
                contentDescription = spec.description
                testTag = STATS_CHART_TAG
            },
        ) {
            val geo = geometry(size.width)
            drawLine(baselineColor, Offset(0f, geo.baseline), Offset(size.width, geo.baseline), BASELINE_WIDTH.toPx())
            drawXLabels(spec.xLabels, geo, measurer, style)
            draw(geo, selected)
        }
        selected?.let { index ->
            ChartTooltip(
                text = spec.tooltip(index),
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val anchor = with(spec) { anchor(index, geometry(constraints.maxWidth.toFloat())) }
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
                    val y = max(0f, anchor.y - placeable.height - TOOLTIP_GAP.toPx())
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(x.roundToInt(), y.roundToInt())
                    }
                },
            )
        }
    }
}

private fun DrawScope.drawXLabels(labels: List<String>, geo: PlotGeometry, measurer: TextMeasurer, style: TextStyle) {
    if (labels.isEmpty()) return
    val widest = labels.maxOf { measurer.measure(it, style).size.width }
    val stride = labelStride(geo.slot, widest.toFloat(), X_MIN_GAP.toPx())
    // From the last label backwards: the last one is pushed inside the plot, so a label that would
    // then touch it (or any later one) is left out.
    var limit = Float.MAX_VALUE
    for (index in labels.indices.reversed().filter { showsLabel(it, labels.size, stride) }) {
        val size = measurer.measure(labels[index], style).size
        val x = (geo.centerX(index) - size.width / 2f).coerceIn(0f, max(0f, geo.width - size.width))
        if (x + size.width + X_MIN_GAP.toPx() <= limit) {
            drawText(measurer, labels[index], Offset(x, geo.baseline + X_LABEL_GAP.toPx() / 2f), style)
            limit = x
        }
    }
}

/** The value label (INS-08, SCR-18): a small `inverseSurface` tooltip, announced politely to TalkBack. */
@Composable
internal fun ChartTooltip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.inverseOnSurface,
        maxLines = 1,
        modifier = modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(TOOLTIP_RADIUS))
            .padding(horizontal = TOOLTIP_PADDING_H, vertical = TOOLTIP_PADDING_V),
    )
}
