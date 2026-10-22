package com.umamit.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.motion.UmamiMotion
import com.umamit.core.designsystem.motion.UnscaledMotion
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.withContext
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/** Colours of a [TimerRing]; see [TimerRingDefaults]. */
@Immutable
data class TimerRingColors(
    val track: Color,
    val progress: Color,
    val marker: Color,
    val glow: Color,
    val content: Color,
    val caption: Color,
    /** Opacity of the liquor fill; 0 on the alert surface, so nothing inside the ring softens the pulse. */
    val liquorAlpha: Float,
)

/** Defaults of [TimerRing] (03 §3.8, §3.1 „Steeping"). */
object TimerRingDefaults {
    /** The ring on the timer screen. */
    val Size: Dp = 264.dp

    /** The 3 dp ring with a round cap. */
    val StrokeWidth: Dp = 3.dp

    /**
     * Steeping colours: `outline` track, `primary` progress (03 §3.1). On an
     * [AlertSurface] these are the [alertColors], so the ring stays readable there.
     */
    @Composable
    fun colors(): TimerRingColors = if (LocalOnAlertSurface.current) alertColors() else steepingColors()

    @Composable
    private fun steepingColors(): TimerRingColors = TimerRingColors(
        track = MaterialTheme.colorScheme.outline,
        progress = MaterialTheme.colorScheme.primary,
        marker = MaterialTheme.colorScheme.onSurfaceVariant,
        glow = MaterialTheme.colorScheme.primary,
        content = MaterialTheme.colorScheme.onSurface,
        // Not onSurfaceVariant: the caption sits on the liquor and must stay ≥ 4.5:1 there.
        caption = MaterialTheme.colorScheme.onSurface,
        liquorAlpha = LIQUOR_ALPHA,
    )

    /** On the [AlertSurface]: everything in the alert content colour, readable on washi and matcha. */
    @Composable
    fun alertColors(): TimerRingColors {
        val ink = UmamiTheme.extendedColors.alert.content
        return TimerRingColors(
            track = ink.copy(alpha = ALERT_TRACK_ALPHA),
            progress = ink,
            marker = ink,
            glow = ink,
            content = ink,
            caption = ink,
            liquorAlpha = 0f,
        )
    }
}

/** The liquor tints the inside of the ring; the digits stay ≥ 4.5:1 on it. */
private const val LIQUOR_ALPHA = 0.32f
private const val ALERT_TRACK_ALPHA = 0.3f
private const val FULL_CIRCLE = 360f
private const val TOP_ANGLE = -90f
private const val BOTTOM_ANGLE = 90f
private const val DEGREES_TO_RADIANS = (Math.PI / 180).toFloat()
private const val RADIANS_TO_DEGREES = (180 / Math.PI).toFloat()

/** The digits are 80 sp already, far above „large text"; they keep their size at any font scale. */
private const val DIGITS_MAX_FONT_SCALE = 1f

/**
 * The caption grows with the font scale up to 24 sp, which still fits two lines
 * inside the ring; TalkBack reads it in full either way.
 */
private const val CAPTION_MAX_FONT_SCALE = 1.5f

private const val SECONDS_PER_HOUR = 3_600
private val HoursDigitsSize = 56.sp
private val HoursDigitsLineHeight = 64.sp

/** Gap between the ring and the liquor inside it. */
private val LiquorInset = 6.dp
private val MarkerLength = 12.dp
private val MarkerGlowRadius = 14.dp
private const val MARKER_SWELL = 0.8f
private const val GLOW_ALPHA = 0.7f
private const val GLOW_MIN_RADIUS = 0.4f

/**
 * The timer ring (03 §3.8, §3.6): a 3 dp ring with a round cap, the leaf glyph,
 * the digits and a caption, and the pre-alert point as a short tick on the ring.
 *
 * Motion (03 §3.6 „Motion tokens"):
 * - **Liquor fill:** the inside fills from the bottom in [liquorColor] as
 *   [progress] grows.
 * - **Short steps** follow [progress] per frame, without easing. [progress] is
 *   read only while drawing, so a per-frame value redraws the ring without
 *   recomposing it; only [remainingSeconds] (1 Hz) recomposes.
 * - **Long steps** ([longStep]): the caller updates [progress] about every
 *   `UmamiMotion.longStepUpdateIntervalMillis`; ring and fill glide there with
 *   `longStepGlide`. The last update to 1 snaps, so no glide runs into the alert.
 * - **Pre-alert glow:** each new [preAlertGlowTick] (the caller passes one per
 *   pre-alert tick, e.g. the tick's time) makes the marker swell once with
 *   `preAlertGlow`, unscaled so it stays in sync with the 0.8 s tick. A glow is
 *   never cut short, and the same tick never plays twice, also not after a
 *   configuration change.
 * - **Reduce motion:** no glow; the fill still shows the level but jumps.
 *
 * TalkBack reads one sentence: [stepDescription], the remaining time in words
 * and the caption. It is not a live region: the screen announces state changes.
 *
 * @param progress elapsed fraction of the step, 0 (ready) to 1 (done); read in the draw phase.
 * @param remainingSeconds remaining time as the engine shows it (rounded up).
 * @param preAlertPosition the pre-alert point as a fraction of the step; `null` without a pre-alert.
 * @param preAlertGlowTick identity of the latest pre-alert tick; `null` before the first.
 * @param colors the default follows the surface: [TimerRingDefaults.alertColors] on the [AlertSurface].
 */
@Composable
fun TimerRing(
    progress: () -> Float,
    remainingSeconds: Int,
    caption: String,
    liquorColor: Color,
    modifier: Modifier = Modifier,
    stepDescription: String? = null,
    preAlertPosition: Float? = null,
    preAlertGlowTick: Long? = null,
    longStep: Boolean = false,
    reduceMotion: Boolean = UmamiTheme.reduceMotion,
    colors: TimerRingColors = TimerRingDefaults.colors(),
    size: Dp = TimerRingDefaults.Size,
) {
    val motion = remember(reduceMotion) { UmamiMotion(reduceMotion) }
    val level = rememberRingLevel(progress, longStep, motion)
    val glow = rememberPreAlertGlow(preAlertGlowTick, motion)

    val spoken = listOfNotNull(
        stepDescription,
        stringResource(R.string.ds_timer_remaining_description, spokenDuration(remainingSeconds)),
        caption,
    ).joinToString(", ")

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        RingDrawing(level, liquorColor, preAlertPosition, glow, colors)
        RingContent(remainingSeconds, caption, colors)
    }
}

/**
 * The level to draw, read in the draw phase. Short steps (and long steps under
 * reduce motion) draw [progress] as given. Long steps glide to each coarse
 * update; reading [progress] in composition is fine there, it changes every 10 s.
 */
@Composable
internal fun rememberRingLevel(progress: () -> Float, longStep: Boolean, motion: UmamiMotion): () -> Float {
    if (!longStep || motion.reduceMotion) {
        return remember(progress) { { progress().coerceIn(0f, 1f) } }
    }
    val target = progress().coerceIn(0f, 1f)
    // The step's end is drawn at once: nothing may still be moving when the alert starts (rule 8).
    val done = target >= 1f
    val spec = if (done) snap() else motion.longStepGlide<Float>()
    val glided = animateFloatAsState(target, spec, label = "longStepProgress")
    return remember(glided, done) { { if (done) 1f else glided.value } }
}

@Composable
private fun rememberPreAlertGlow(tick: Long?, motion: UmamiMotion): Animatable<Float, *> {
    val glow = remember { Animatable(0f) }
    // Survives configuration changes, so returning to the ring never replays a tick's glow.
    var lastPlayed by rememberSaveable { mutableStateOf(tick) }
    val spec = motion.preAlertGlow
    LaunchedEffect(tick, spec) {
        if (tick == null || tick == lastPlayed) return@LaunchedEffect
        lastPlayed = tick
        if (spec == null) return@LaunchedEffect
        glow.snapTo(0f)
        withContext(UnscaledMotion) { glow.animateTo(0f, spec) }
    }
    return glow
}

@Composable
private fun RingContent(remainingSeconds: Int, caption: String, colors: TimerRingColors) {
    CompositionLocalProvider(LocalContentColor provides colors.content) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(UmamiIcons.Leaf),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(DIGITS_MAX_FONT_SCALE)),
            ) {
                val digits = UmamiTheme.textStyles.timerDigits
                Text(
                    text = clockText(remainingSeconds),
                    // „h:mm:ss" of long steps needs smaller digits to stay inside the ring.
                    style = if (remainingSeconds >= SECONDS_PER_HOUR) {
                        digits.copy(fontSize = HoursDigitsSize, lineHeight = HoursDigitsLineHeight)
                    } else {
                        digits
                    },
                    maxLines = 1,
                )
            }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(CAPTION_MAX_FONT_SCALE)),
            ) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.caption,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * The ring's drawing, skipped when only the digits recompose. Everything that
 * does not change per frame (strokes, the glow's gradient, the marker's place)
 * is built once per size in [drawWithCache], so a short step that redraws
 * every frame for minutes allocates nothing per frame.
 */
@Composable
private fun RingDrawing(
    level: () -> Float,
    liquorColor: Color,
    preAlertPosition: Float?,
    glow: Animatable<Float, *>,
    colors: TimerRingColors,
) {
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val stroke = TimerRingDefaults.StrokeWidth.toPx()
                val radius = size.minDimension / 2 - MarkerGlowRadius.toPx()
                val strokes = RingStrokes(stroke)
                val liquor = liquorColor.copy(alpha = colors.liquorAlpha)
                val liquorInset = stroke / 2 + LiquorInset.toPx()
                val marker = preAlertPosition?.let {
                    PreAlertMarker(it, size.center, radius, stroke, colors.glow, this)
                }
                onDrawBehind {
                    val current = level()
                    drawLiquor(current, liquor, radius, liquorInset)
                    drawRing(current, colors.track, colors.progress, radius, strokes)
                    marker?.draw(this, glow.value, colors.marker)
                }
            },
    )
}

/** The ring's two strokes, built once per size: the plain track and the progress arc with its round cap. */
internal class RingStrokes(width: Float) {
    val track = Stroke(width = width)
    val progress = Stroke(width = width, cap = StrokeCap.Round)
}

/**
 * The liquor (colour with its fill opacity) inside a ring of [radius], [inset]
 * inside it, rising from the bottom, like tea poured into a cup (03 §3.6). It is
 * the circular segment below the water line, drawn as an arc closed by its chord:
 * no path and no clip, so nothing is allocated per frame. Its height is linear in
 * [level]. Shared with the mini ring.
 */
internal fun DrawScope.drawLiquor(level: Float, liquorColor: Color, radius: Float, inset: Float) {
    if (level <= 0f || liquorColor.alpha == 0f) return
    val liquorRadius = radius - inset
    // The water line's half-angle, measured from the bottom of the circle: height = r·(1 − cos θ) = 2r·level.
    val halfAngle = acos(1f - 2f * level.coerceAtMost(1f)) * RADIANS_TO_DEGREES
    drawArc(
        color = liquorColor,
        startAngle = BOTTOM_ANGLE - halfAngle,
        sweepAngle = 2 * halfAngle,
        useCenter = false,
        topLeft = Offset(center.x - liquorRadius, center.y - liquorRadius),
        size = Size(2 * liquorRadius, 2 * liquorRadius),
    )
}

/** Track and progress arc from the top, clockwise, with a round cap. Shared with the mini ring. */
internal fun DrawScope.drawRing(level: Float, track: Color, progress: Color, radius: Float, strokes: RingStrokes) {
    drawCircle(track, radius = radius, style = strokes.track)
    if (level > 0f) {
        drawArc(
            color = progress,
            startAngle = TOP_ANGLE,
            sweepAngle = FULL_CIRCLE * level,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = strokes.progress,
        )
    }
}

/** The pre-alert tick on the ring and its glow, placed once per size. */
private class PreAlertMarker(
    position: Float,
    center: Offset,
    radius: Float,
    private val stroke: Float,
    glowColor: Color,
    density: Density,
) {
    private val angle = (TOP_ANGLE + FULL_CIRCLE * position.coerceIn(0f, 1f)) * DEGREES_TO_RADIANS
    private val direction = Offset(cos(angle), sin(angle))
    private val pointOnRing = center + direction * radius
    private val halfLength = with(density) { MarkerLength.toPx() } / 2
    private val glowRadius = with(density) { MarkerGlowRadius.toPx() }

    /** The glow at full size and opacity; each frame scales and fades it. */
    private val glowBrush = Brush.radialGradient(
        colors = listOf(glowColor, Color.Transparent),
        center = pointOnRing,
        radius = glowRadius,
    )

    fun draw(scope: DrawScope, glow: Float, markerColor: Color) = with(scope) {
        if (glow > 0f) {
            scale(GLOW_MIN_RADIUS + (1f - GLOW_MIN_RADIUS) * glow, pivot = pointOnRing) {
                drawCircle(glowBrush, radius = glowRadius, center = pointOnRing, alpha = GLOW_ALPHA * glow)
            }
        }
        // The tick crosses the ring; it swells a little with the glow.
        val half = halfLength * (1f + MARKER_SWELL * glow)
        drawLine(
            color = markerColor,
            start = pointOnRing - direction * half,
            end = pointOnRing + direction * half,
            strokeWidth = stroke * (1f + MARKER_SWELL * glow),
            cap = StrokeCap.Round,
        )
    }
}
