package com.umamit.core.designsystem.component

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.theme.UmamiTheme

/** A stop closer than this to the pin's centre counts as under the pin. */
private const val STOP_EPSILON_PX = 0.5f

/** The slim slider's measures (03 §3.8, D-113). */
private object SlimSliderSpec {
    val PinWidth = 3.dp
    val PinHeight = 20.dp
    val PinWidthPressed = 4.dp
    val PinHeightPressed = 24.dp
    val PinCorner = 1.5.dp
    val UnsetMarkWidth = 1.5.dp
    val UnsetMarkHeight = 10.dp
    val LineHeight = 2.dp
    val GapToPin = 4.dp
    val StopDiameter = 3.dp
    val TrackBox = 24.dp
}

/**
 * The slim slider (03 §3.8, D-113): a thin pin as the handle, a 2 dp line (primary up to the pin, outline
 * beyond it, a gap on each side of the pin) and dots at the stops beyond the pin. One look for the taste
 * axes (JRN-03) and the alert volume (SET-27). Whole steps are the caller's [steps]; the touch target is the
 * Slider's own 48 dp minimum.
 *
 * [unset] shows a short mark at the left end instead of the pin and no primary line (an unset taste axis).
 * [interactionSource] drives the pressed pin and can be supplied to drive it from outside (screenshots).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    unset: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        thumb = { SlimThumb(interactionSource, unset) },
        track = { state -> SlimTrack(state, unset) },
    )
}

@Composable
private fun SlimThumb(interactionSource: MutableInteractionSource, unset: Boolean) {
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val active = pressed || dragged
    val spec: FiniteAnimationSpec<Dp> = if (UmamiTheme.reduceMotion) snap() else UmamiTheme.motion.fade()
    val width by animateDpAsState(
        if (active) SlimSliderSpec.PinWidthPressed else SlimSliderSpec.PinWidth,
        spec,
        label = "pinWidth",
    )
    val height by animateDpAsState(
        if (active) SlimSliderSpec.PinHeightPressed else SlimSliderSpec.PinHeight,
        spec,
        label = "pinHeight",
    )
    val color = MaterialTheme.colorScheme.primary
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant
    // A fixed layout box (the widest pin), so the track does not shift while the pin grows.
    Canvas(Modifier.size(SlimSliderSpec.PinWidthPressed, SlimSliderSpec.PinHeightPressed)) {
        val pin = if (unset && !active) {
            PinShape(SlimSliderSpec.UnsetMarkWidth, SlimSliderSpec.UnsetMarkHeight, markColor, 0.dp)
        } else {
            PinShape(width, height, color, SlimSliderSpec.PinCorner)
        }
        val pw = pin.width.toPx()
        val ph = pin.height.toPx()
        // The layout box is centred on the value; the unset mark sits at the left end of the line.
        drawRoundRect(
            color = pin.color,
            topLeft = Offset((size.width - pw) / 2f, (size.height - ph) / 2f),
            size = Size(pw, ph),
            cornerRadius = CornerRadius(pin.corner.toPx()),
        )
    }
}

private data class PinShape(val width: Dp, val height: Dp, val color: Color, val corner: Dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlimTrack(state: SliderState, unset: Boolean) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outlineVariant
    val stop = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(SlimSliderSpec.TrackBox)) {
        val range = state.valueRange.endInclusive - state.valueRange.start
        val fraction = if (unset || range <= 0f) 0f else (state.value - state.valueRange.start) / range
        val line = SlimSliderSpec.LineHeight.toPx()
        val capRadius = line / 2f
        // The gap is measured from the pin's edge to the cap's tip.
        val gap = SlimSliderSpec.GapToPin.toPx() + SlimSliderSpec.PinWidth.toPx() / 2f + capRadius
        val handleX = size.width * fraction
        val y = size.height / 2f
        if (!unset && handleX - gap > 0f) {
            drawLine(active, Offset(0f, y), Offset(handleX - gap, y), line, StrokeCap.Round)
        }
        if (handleX + gap < size.width) {
            drawLine(inactive, Offset(handleX + gap, y), Offset(size.width, y), line, StrokeCap.Round)
        }
        val radius = SlimSliderSpec.StopDiameter.toPx() / 2f
        for (i in 0..state.steps + 1) {
            val x = size.width * i / (state.steps + 1)
            if (x > handleX + STOP_EPSILON_PX) drawCircle(stop, radius, Offset(x, y))
        }
    }
}
