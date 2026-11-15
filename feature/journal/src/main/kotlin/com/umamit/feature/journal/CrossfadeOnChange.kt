package com.umamit.feature.journal

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.umamit.core.designsystem.theme.UmamiTheme

/**
 * Cross-fades the content once each time [changes] increases (03 §3.6, D-117): the frame drawn before the change
 * stays on top and fades out over the new content with `exitFade` (200 ms CalmEaseOut; 100 ms under reduce motion),
 * the new content already fully in place underneath (A-09, NFR-PERF-12). Only the drawing is faded: the content is
 * composed once, so a list keeps its scroll position and its focus, and nothing moves. A first composition, and every
 * frame without a change, draws the content as it is.
 */
@Composable
internal fun Modifier.crossfadeOnChange(changes: Int): Modifier {
    val first = rememberGraphicsLayer()
    val second = rememberGraphicsLayer()
    val layers = remember(first, second) { Layers(first, second, changes) }
    // A new change starts with the old frame fully on top, in the change's own first frame.
    val ghost = remember(changes) { Animatable(if (changes == layers.drawnChanges) 0f else 1f) }
    val spec = UmamiTheme.motion.exitFade<Float>()
    LaunchedEffect(ghost) { if (ghost.value > 0f) ghost.animateTo(0f, spec) }
    return drawWithContent {
        if (changes != layers.drawnChanges) layers.swap(changes)
        layers.live.record { this@drawWithContent.drawContent() }
        drawLayer(layers.live)
        val alpha = ghost.value
        if (alpha > 0f) {
            layers.old.alpha = alpha
            drawLayer(layers.old)
        }
    }
}

/** The layer the content is recorded into each frame, and the last frame before a change. Not state: only drawing. */
private class Layers(var live: GraphicsLayer, var old: GraphicsLayer, var drawnChanges: Int) {
    fun swap(changes: Int) {
        val last = live
        live = old
        old = last
        drawnChanges = changes
    }
}
