package com.umamit.feature.stats.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalViewConfiguration

/**
 * What a Statistik page tells its charts so that each grows once (INS-08): whether the page is the settled one,
 * where its viewport is, and which of its charts were revealed already. The pager disposes a page that is
 * swiped away, so the memory of what was revealed lives here, one level up, and survives it.
 */
@Stable
internal class ChartPageState(private val prefix: String, private val revealed: MutableList<String>, active: Boolean) {
    var active by mutableStateOf(active)

    /** The page's viewport in window pixels. */
    var viewport by mutableStateOf(Rect.Zero)

    /** How many grows started on this page; read by the tests. */
    var growStarts = 0

    fun isRevealed(key: String): Boolean = "$prefix:$key" in revealed

    fun markRevealed(key: String) {
        if (!isRevealed(key)) revealed += "$prefix:$key"
    }
}

internal val LocalChartPage = compositionLocalOf<ChartPageState?> { null }

/** Tells the charts under it where the page's viewport is. */
internal fun Modifier.chartViewport(page: ChartPageState): Modifier =
    onGloballyPositioned { page.viewport = it.boundsInWindow() }

/**
 * Which chart's value label is shown (at most one on the screen, INS-08): a tap on a chart's mark sets it, a
 * tap anywhere else or a scroll clears it ([ChartSelectionHost]).
 */
@Stable
internal class ChartSelectionHolder {
    private var owner by mutableStateOf<Any?>(null)
    private var index by mutableStateOf<Int?>(null)

    fun indexFor(token: Any): Int? = if (owner === token) index else null

    fun select(token: Any, value: Int?) {
        owner = if (value == null) null else token
        index = value
    }

    fun clear() = select(Any(), null)

    fun release(token: Any) {
        if (owner === token) clear()
    }
}

internal val LocalChartSelection = staticCompositionLocalOf<ChartSelectionHolder?> { null }

/** One chart's view of the selection: the screen's holder when there is one, else its own state. */
@Stable
internal class ChartSelection(private val holder: ChartSelectionHolder?) {
    private val token = Any()
    private var local by mutableStateOf<Int?>(null)

    val index: Int? get() = if (holder == null) local else holder.indexFor(token)

    fun select(value: Int?) = if (holder == null) local = value else holder.select(token, value)

    fun release() = holder?.release(token)
}

/** [ChartSelection] of a chart; a new [resetKey] hides its label. */
@Composable
internal fun rememberChartSelection(resetKey: Any?): ChartSelection {
    val holder = LocalChartSelection.current
    val selection = remember(holder, resetKey) { ChartSelection(holder) }
    DisposableEffect(selection) { onDispose { selection.release() } }
    return selection
}

/**
 * Hosts the one value label of the charts below it: a tap that no chart's mark took (on text, on empty space, on
 * another chart's blank) clears it. It only watches, never consumes.
 */
@Composable
internal fun ChartSelectionHost(
    holder: ChartSelectionHolder,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val slop = LocalViewConfiguration.current.touchSlop
    Box(
        modifier.pointerInput(holder, slop) {
            awaitPointerEventScope {
                var down: Offset? = null
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull() ?: continue
                    if (change.pressed && !change.previousPressed) down = change.position
                    val start = down
                    if (change.changedToUp() && start != null && !change.isConsumed &&
                        (change.position - start).getDistance() <= slop
                    ) {
                        holder.clear()
                    }
                    if (!change.pressed) down = null
                }
            }
        },
    ) {
        CompositionLocalProvider(LocalChartSelection provides holder, content = content)
    }
}
