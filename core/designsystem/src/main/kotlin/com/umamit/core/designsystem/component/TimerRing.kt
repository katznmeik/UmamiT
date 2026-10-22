package com.umamit.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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

    /** Steeping colours: `outline` track, `primary` progress (03 §3.1). */
    @Composable
    fun colors(): TimerRingColors = TimerRingColors(
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
private const val DEGREES_TO_RADIANS = (Math.PI / 180).toFloat()

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
 * @param colors [TimerRingDefaults.alertColors] on the [AlertSurface].
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = ringRadius()
            val current = level()
            drawLiquor(
                current,
                liquorColor.copy(alpha = colors.liquorAlpha),
                radius,
                strokePx() / 2 + LiquorInset.toPx(),
            )
            drawRing(current, colors.track, colors.progress, radius, strokePx())
            if (preAlertPosition != null) drawPreAlertMarker(preAlertPosition, glow.value, colors)
        }
        RingContent(remainingSeconds, caption, colors)
    }
}

/**
 * The level to draw, read in the draw phase. Short steps (and long steps under
 * reduce motion) draw [progress] as given. Long steps glide to each coarse
 * update; reading [progress] in composition is fine there, it changes every 10 s.
 */
@Composable
private fun rememberRingLevel(progress: () -> Float, longStep: Boolean, motion: UmamiMotion): () -> Float {
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
 * The liquor (colour with its fill opacity) inside a ring of [radius], [inset]
 * inside it, rising from the bottom. Shared with the mini ring.
 */
internal fun DrawScope.drawLiquor(level: Float, liquorColor: Color, radius: Float, inset: Float) {
    if (level <= 0f || liquorColor.alpha == 0f) return
    val diameter = 2 * (radius - inset)
    val topLeft = Offset(center.x - diameter / 2, center.y - diameter / 2)
    val circle = Path().apply { addOval(Rect(topLeft, Size(diameter, diameter))) }
    clipPath(circle) {
        // The level rises from the bottom, like tea poured into a cup (03 §3.6).
        val height = diameter * level
        drawRect(
            color = liquorColor,
            topLeft = Offset(topLeft.x, topLeft.y + diameter - height),
            size = Size(diameter, height),
        )
    }
}

/** Track and progress arc from the top, clockwise, with a round cap. Shared with the mini ring. */
internal fun DrawScope.drawRing(level: Float, track: Color, progress: Color, radius: Float, stroke: Float) {
    val arcTopLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    drawCircle(track, radius = radius, style = Stroke(width = stroke))
    if (level > 0f) {
        drawArc(
            color = progress,
            startAngle = TOP_ANGLE,
            sweepAngle = FULL_CIRCLE * level,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawPreAlertMarker(position: Float, glow: Float, colors: TimerRingColors) {
    val stroke = strokePx()
    val radius = ringRadius()
    val angle = (TOP_ANGLE + FULL_CIRCLE * position.coerceIn(0f, 1f)) * DEGREES_TO_RADIANS
    val direction = Offset(cos(angle), sin(angle))
    val pointOnRing = center + direction * radius
    if (glow > 0f) {
        val glowRadius = MarkerGlowRadius.toPx() * (GLOW_MIN_RADIUS + (1f - GLOW_MIN_RADIUS) * glow)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.glow.copy(alpha = GLOW_ALPHA * glow), Color.Transparent),
                center = pointOnRing,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = pointOnRing,
        )
    }
    // The tick crosses the ring; it swells a little with the glow.
    val half = MarkerLength.toPx() / 2 * (1f + MARKER_SWELL * glow)
    drawLine(
        color = colors.marker,
        start = pointOnRing - direction * half,
        end = pointOnRing + direction * half,
        strokeWidth = stroke * (1f + MARKER_SWELL * glow),
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.strokePx(): Float = TimerRingDefaults.StrokeWidth.toPx()

/** The ring sits inside the canvas with room for the marker's glow. */
private fun DrawScope.ringRadius(): Float = size.minDimension / 2 - MarkerGlowRadius.toPx()
