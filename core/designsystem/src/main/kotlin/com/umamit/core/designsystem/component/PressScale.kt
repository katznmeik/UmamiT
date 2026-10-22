package com.umamit.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import com.umamit.core.designsystem.theme.UmamiTheme

/** Pressed elements shrink a touch, so the press is felt at touch-down (03 §3.6). */
private const val PRESSED_SCALE = 0.97f

/**
 * Scale for press feedback, following [interactionSource]. It animates with the
 * press token and stays at 1 under reduce motion (the ripple still shows the press).
 */
@Composable
internal fun rememberPressScale(interactionSource: InteractionSource): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed && !UmamiTheme.reduceMotion) PRESSED_SCALE else 1f
    return animateFloatAsState(
        targetValue = target,
        animationSpec = UmamiTheme.motion.press(),
        label = "pressScale",
    )
}
