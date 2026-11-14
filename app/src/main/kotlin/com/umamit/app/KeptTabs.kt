package com.umamit.app

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The tabs of the shell, each kept composed once visited (A-10, NFR-PERF-12): a tab switch only fades layers, so its
 * first frame no longer composes the incoming tab (its NavHost, screen, first list layout and image requests) from
 * scratch. The fade-through is unchanged: the outgoing tab fades out with [outSpec] (`tabSwitchOut`), the incoming one
 * in with [inSpec] (`tabSwitchIn`), driven by the same [transition], so predictive back still seeks it.
 *
 * A hidden tab is not placed (it draws nothing and takes no input), has no semantics, and its lifecycle is held at
 * `CREATED`, so its collectors pause and its back handlers stay off; its state stays as it was.
 */
@Composable
internal fun <T> KeptTabs(
    transition: Transition<T>,
    tabs: List<T>,
    inSpec: FiniteAnimationSpec<Float>,
    outSpec: FiniteAnimationSpec<Float>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    // Plain, not state: it only grows, and only while the selection (read below) changes.
    val visited = remember { mutableSetOf<T>() }
    visited += transition.currentState
    visited += transition.targetState
    Box(modifier) {
        tabs.filter { it in visited }.forEach { tab ->
            key(tab) {
                val alpha by transition.animateFloat(
                    transitionSpec = { if (targetState == tab) inSpec else outSpec },
                    label = "tab alpha",
                ) { state -> if (state == tab) 1f else 0f }
                val shown = transition.currentState == tab || transition.targetState == tab
                TabLayer(shown = shown, alpha = { alpha }) { content(tab) }
            }
        }
    }
}

@Composable
private fun TabLayer(shown: Boolean, alpha: () -> Float, content: @Composable () -> Unit) {
    val parent = LocalLifecycleOwner.current.lifecycle
    val owner = remember(parent) { CappedLifecycleOwner(parent, shown) }
    SideEffect { owner.shown = shown }
    DisposableEffect(owner) {
        owner.attach()
        onDispose { owner.detach() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (shown) Modifier else Modifier.clearAndSetSemantics {})
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { if (shown) placeable.place(0, 0) }
            }
            .graphicsLayer { this.alpha = alpha() },
    ) {
        CompositionLocalProvider(LocalLifecycleOwner provides owner) { content() }
    }
}

/** [parent]'s lifecycle, held at `CREATED` while the tab is hidden. */
private class CappedLifecycleOwner(private val parent: Lifecycle, initiallyShown: Boolean) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    private val observer = LifecycleEventObserver { _, _ -> update() }

    override val lifecycle: Lifecycle get() = registry

    init {
        // In step with the parent from the first composition, before the effects run.
        update(initiallyShown)
    }

    var shown: Boolean = initiallyShown
        set(value) {
            if (field == value) return
            field = value
            update()
        }

    fun attach() {
        parent.addObserver(observer)
        update()
    }

    fun detach() = parent.removeObserver(observer)

    private fun update(shown: Boolean = this.shown) {
        val state = parent.currentState
        val capped = if (shown) state else minOf(state, Lifecycle.State.CREATED)
        // A registry never goes from INITIALIZED straight to DESTROYED.
        if (capped == Lifecycle.State.DESTROYED && registry.currentState == Lifecycle.State.INITIALIZED) return
        registry.currentState = capped
    }
}
