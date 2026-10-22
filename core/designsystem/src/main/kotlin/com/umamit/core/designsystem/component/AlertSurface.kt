package com.umamit.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.umamit.core.designsystem.motion.UmamiMotion
import com.umamit.core.designsystem.motion.UnscaledMotion
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.withContext

/**
 * The ALERTING surface (03 §3.6, D-041): a strong, slow pulse washi ↔ matcha,
 * one cycle per 1.5 s, that is visible across the room even in silent mode.
 *
 * Alert first (CLAUDE.md rule 8):
 * - the first frame is full matcha; the pulse then runs matcha → washi → matcha;
 * - it runs with [UnscaledMotion], so a developer animator scale cannot slow it;
 * - under reduce motion it is a static matcha surface;
 * - the content is never faded, moved or hidden: it sits on top in
 *   `AlertColors.content`, readable on both washi and matcha.
 *
 * Compose it only while ALERTING; it starts pulsing at full matcha when it enters
 * the composition and again each time the app returns to the foreground.
 */
@Composable
fun AlertSurface(
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = UmamiTheme.reduceMotion,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = UmamiTheme.extendedColors.alert
    val pulseSpec = remember(reduceMotion) { UmamiMotion(reduceMotion) }.alertPulse
    // The window's frame clock pauses while the app is in the background. Coming back
    // to a running alert restarts the pulse at full matcha instead of a random phase.
    val started = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
        .isAtLeast(Lifecycle.State.STARTED)
    // 0 = washi, 1 = matcha. Until the pulse runs (and under reduce motion) the surface is full matcha.
    val pulse = remember { Animatable(0f) }
    // Set on the pulse's first frame: before that the Animatable still holds its start value 0 (washi).
    var pulsing by remember { mutableStateOf(false) }
    LaunchedEffect(pulseSpec, started) {
        pulsing = false
        if (pulseSpec != null && started) {
            pulse.snapTo(0f)
            withContext(UnscaledMotion) { pulse.animateTo(1f, pulseSpec) { pulsing = true } }
        }
    }
    Box(
        modifier = modifier.drawBehind {
            val matchaFraction = if (pulsing && pulseSpec != null) pulse.value else 1f
            drawRect(lerp(colors.washi, colors.matcha, matchaFraction))
        },
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            content()
        }
    }
}
