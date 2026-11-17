package com.umamit.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TasteSet
import com.umamit.core.model.TastingLimits
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Test tag of the drawn radar; absent while fewer than three axes are set. */
const val TASTE_RADAR_TAG = "taste_radar"

/** The pure part of `TasteRadar` (03 §3.8, JRN-03), so it is tested without a screen. */
object TasteRadarShape {
    /** The radar is drawn from this many set axes on. */
    const val MIN_AXES = 3

    private const val TENTHS = 10.0
    private const val FULL_TURN_DEGREES = 360.0
    private const val QUARTER_TURN_DEGREES = 90.0

    /** Whether [values] (set axes only) have enough axes for the radar. */
    fun isDrawn(values: Map<TasteAxis, Double>): Boolean = values.size >= MIN_AXES

    /**
     * The point of [axis] at [value] (0–5; null = unset, at the centre) around the centre (0, 0) at full [radius]:
     * the first axis points straight up, the others follow clockwise at equal angles. Screen coordinates (y down).
     */
    fun point(axis: TasteAxis, value: Double?, radius: Float, set: TasteSet = TasteSet.GREEN): Pair<Float, Float> {
        val angle = Math.toRadians(
            -QUARTER_TURN_DEGREES + FULL_TURN_DEGREES / set.axes.size * set.axes.indexOf(axis),
        )
        val reach = radius * ((value ?: 0.0) / TastingLimits.MAX_TASTE).toFloat()
        return reach * cos(angle).toFloat() to reach * sin(angle).toFloat()
    }

    /** „4" or „3,5": a value as TalkBack says it (averages have a decimal). */
    fun valueText(value: Double): String {
        val rounded = Math.round(value * TENTHS) / TENTHS
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else "%.1f".format(Locale.GERMAN, rounded)
    }
}

/** [TasteProfile]'s axes of [set] as the radar takes them; every other axis is left out (D-128 Q16). */
fun TasteProfile.radarValues(set: TasteSet = TasteSet.GREEN): Map<TasteAxis, Double> =
    set.axes.mapNotNull { axis -> get(axis)?.let { axis to it.toDouble() } }.toMap()

private val RADAR_HEIGHT = 240.dp
private val LABEL_GAP = 6.dp
private val POINT_RADIUS = 3.dp
private val STROKE = 2.dp
private val RIM_STROKE = 1.5.dp
private val HAIRLINE = 1.dp

/** The radar's slots whose labels sit above and below the rim: the first axis (top) and the fourth (bottom). */
private val VERTICAL_SLOTS = setOf(0, 3)
private const val FILL_ALPHA = 0.22f
private const val MAX_FONT_SCALE = 2f
private const val HEIGHT_PER_FONT_SCALE = 0.4f
private const val RIM_RINGS = 5

/** cos 30°: how far the four side axes reach sideways. */
private const val SIDE_REACH = 0.866f

/**
 * The taste radar (03 §3.8, JRN-03): the six axes of [set] (Grüntee: Umami, Süße, Bitterkeit, Adstringenz, Körper,
 * Aroma; D-128), clockwise from the top in the set's order, 0–5, drawn once three axes are set; an unset axis lies at
 * the centre. Read-only, a plain Canvas in the washi/matcha palette: [values] holds the set axes only, a session's
 * whole numbers or a tea's averages (TEA-13); an axis outside [set] is left out. Under three axes it shows nothing.
 * TalkBack reads the set values as a list.
 */
@Composable
fun TasteRadar(values: Map<TasteAxis, Double>, modifier: Modifier = Modifier, set: TasteSet = TasteSet.GREEN) {
    val axes = set.axes
    val shown = values.filterKeys { it in axes }
    if (!TasteRadarShape.isDrawn(shown)) return
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val labels = axes.associateWith { it.tasteLabel() }
    val spoken = axes.filter { it in shown }.map {
        stringResource(
            R.string.ui_taste_radar_item,
            labels.getValue(it),
            TasteRadarShape.valueText(shown.getValue(it)),
        )
    }.joinToString(", ")
    val description = stringResource(R.string.ui_taste_radar_label, spoken)
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, MAX_FONT_SCALE)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RADAR_HEIGHT * (1f + (fontScale - 1f) * HEIGHT_PER_FONT_SCALE))
            .testTag(TASTE_RADAR_TAG)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val layouts = labels.mapValues { (_, text) -> measurer.measure(text, labelStyle) }
            drawRadar(set, layouts, shown, grid, accent)
        }
    }
}

private fun DrawScope.drawRadar(
    set: TasteSet,
    layouts: Map<TasteAxis, TextLayoutResult>,
    values: Map<TasteAxis, Double>,
    grid: Color,
    accent: Color,
) {
    val gap = LABEL_GAP.toPx()
    // The side labels sit at 0,87 of the radius, the top and bottom ones above and below the rim.
    val maxSideWidth = layouts.filterKeys { set.axes.indexOf(it) !in VERTICAL_SLOTS }.values.maxOf { it.size.width }
    val labelHeight = layouts.values.maxOf { it.size.height }
    val centre = Offset(size.width / 2f, size.height / 2f)
    val radius = min(
        (size.width / 2f - maxSideWidth) / SIDE_REACH - gap,
        size.height / 2f - labelHeight - gap,
    ).coerceAtLeast(0f)
    fun at(axis: TasteAxis, value: Double?, r: Float = radius): Offset {
        val (x, y) = TasteRadarShape.point(axis, value, r, set)
        return centre + Offset(x, y)
    }
    fun polygon(value: (TasteAxis) -> Double?): Path = Path().apply {
        set.axes.forEachIndexed { i, axis ->
            val p = at(axis, value(axis))
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    for (ring in 1..RIM_RINGS) {
        val width = if (ring == RIM_RINGS) RIM_STROKE.toPx() else HAIRLINE.toPx()
        drawPath(polygon { ring.toDouble() }, grid, style = Stroke(width = width))
    }
    set.axes.forEach { drawLine(grid, centre, at(it, TastingLimits.MAX_TASTE.toDouble()), HAIRLINE.toPx()) }
    val shape = polygon { values[it] }
    drawPath(shape, accent.copy(alpha = FILL_ALPHA), style = Fill)
    drawPath(shape, accent, style = Stroke(width = STROKE.toPx()))
    values.keys.forEach { drawCircle(accent, POINT_RADIUS.toPx(), at(it, values[it])) }
    set.axes.forEachIndexed { slot, axis ->
        val layout = layouts.getValue(axis)
        val anchor = at(axis, TastingLimits.MAX_TASTE.toDouble(), radius + gap)
        val width = layout.size.width.toFloat()
        val height = layout.size.height.toFloat()
        // By slot, not by axis: top, two on the right, bottom, two on the left, whichever set it is.
        val topLeft = when (slot) {
            0 -> Offset(anchor.x - width / 2f, anchor.y - height)
            3 -> Offset(anchor.x - width / 2f, anchor.y)
            1, 2 -> Offset(anchor.x, anchor.y - height / 2f)
            else -> Offset(anchor.x - width, anchor.y - height / 2f)
        }
        drawText(layout, topLeft = topLeft)
    }
}
