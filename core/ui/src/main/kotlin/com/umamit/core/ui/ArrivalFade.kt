package com.umamit.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.delay

/**
 * How long a composition holds for its data before it shows fresh (03 §3.6 "hold, then show fresh": at most ≈ 150 ms,
 * A-09, D-117 "Arrival fade"), counted from the composition's first frame. Data ready within it is drawn in place, at
 * full opacity; only later data fades. Shared by [fadeInOnArrival] and the Journal's list hold.
 */
const val ARRIVAL_HOLD_MS = 150L

/**
 * Hold, then show fresh (03 §3.6, D-117 "Arrival fade", A-12, NFR-PERF-12): content whose data first [arrived] within
 * [ARRIVAL_HOLD_MS] of this composition's first frame is drawn at once, at full opacity, inside the route's own enter;
 * only a later first arrival fades in with `fade` (reduce motion: its 100 ms `fade`), instead of popping in part-way
 * through. Nothing moves; only the opacity changes. A composition that starts with the data (a tab that comes back, a
 * screenshot) draws it at once. Apply it to the container that holds the content only once it [arrived]. [onFadeEnd]
 * runs once the content is at full opacity after it arrived, for what must stay underneath until then (the tea
 * detail's loading bar, A-12).
 */
@Composable
fun Modifier.fadeInOnArrival(arrived: Boolean, onFadeEnd: () -> Unit = {}): Modifier {
    val alpha = rememberArrivalAlpha(arrived, onFadeEnd)
    return graphicsLayer { this.alpha = alpha.value }
}

/** The opacity [fadeInOnArrival] draws its content with; read it only in the draw phase. */
@Composable
internal fun rememberArrivalAlpha(arrived: Boolean, onFadeEnd: () -> Unit): State<Float> {
    val alpha = remember { Animatable(if (arrived) 1f else 0f) }
    val spec = UmamiTheme.motion.fade<Float>()
    val fadeEnd by rememberUpdatedState(onFadeEnd)
    var holding by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(ARRIVAL_HOLD_MS)
        holding = false
    }
    LaunchedEffect(arrived) {
        if (arrived) {
            if (alpha.value < 1f) {
                if (holding) alpha.snapTo(1f) else alpha.animateTo(1f, spec)
            }
            fadeEnd()
        }
    }
    return alpha.asState()
}
