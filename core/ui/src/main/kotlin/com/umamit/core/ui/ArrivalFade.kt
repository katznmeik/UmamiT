package com.umamit.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.umamit.core.designsystem.theme.UmamiTheme

/**
 * Fades the content in with `fade` (03 §3.6) when its data first [arrived] in this composition, instead of letting it
 * pop in part-way through a route's enter (A-03, A-12, NFR-PERF-12). Nothing moves; only the opacity changes. A
 * composition that starts with the data (a tab that comes back, a screenshot) draws it at once, at full opacity.
 * Apply it to the container that holds the content only once it [arrived].
 */
@Composable
fun Modifier.fadeInOnArrival(arrived: Boolean): Modifier {
    val alpha = remember { Animatable(if (arrived) 1f else 0f) }
    val spec = UmamiTheme.motion.fade<Float>()
    LaunchedEffect(arrived) { if (arrived && alpha.value < 1f) alpha.animateTo(1f, spec) }
    return graphicsLayer { this.alpha = alpha.value }
}
