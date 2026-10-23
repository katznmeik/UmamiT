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
 * Scale for press feedback, following [interactionSource]: press token on touch-down,
 * press-release spring on release, and 1 under reduce motion (the ripple still shows
 * the press). For pills and cards only; list rows keep just the ripple.
 */
@Composable
internal fun rememberPressScale(interactionSource: InteractionSource): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed && !UmamiTheme.reduceMotion) PRESSED_SCALE else 1f
    val motion = UmamiTheme.motion
    return animateFloatAsState(
        targetValue = target,
        // Asymmetric: the press eases in over 120 ms, the release springs back faster.
        animationSpec = if (pressed) motion.press() else motion.pressRelease(),
        label = "pressScale",
    )
}
