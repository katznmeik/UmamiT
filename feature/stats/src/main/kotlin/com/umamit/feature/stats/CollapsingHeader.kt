package com.umamit.feature.stats

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import com.umamit.core.designsystem.motion.MotionTokens
import kotlinx.coroutines.CancellationException

/** How long the title takes to settle to an end or to come back (the standard exit, quick). */
private const val SETTLE_MILLIS = 200

/**
 * The title that scrolls away with a tab's content (SCR-18): the content takes an upward scroll first from the
 * title, then scrolls itself; a downward scroll shows the title again only once the content is at its top. It
 * never rests half-way: when the scroll ends it settles to the nearer end in 200 ms (at once under
 * [reduceMotion]).
 */
internal class CollapsingHeader {
    var reduceMotion = false
    private var height = 0f
    private var generation = 0

    /** Where the title sits, from `-height` (gone) to 0 (shown); read in the graphics layer only. */
    internal var offset by mutableFloatStateOf(0f)
        private set

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            return Offset(0f, moveBy(available.y))
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f) return Offset.Zero
            return Offset(0f, moveBy(available.y))
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle()
            return Velocity.Zero
        }
    }

    /** Moves the title by [dy] within its height; returns what it took. A touch stops a running settle. */
    private fun moveBy(dy: Float): Float {
        generation++
        val moved = (offset + dy).coerceIn(-height, 0f)
        val taken = moved - offset
        offset = moved
        return taken
    }

    /** The title in full again (the Statistik tab was selected again, or a page that starts at its top came). */
    suspend fun reveal() = animateTo(0f)

    private suspend fun settle() {
        if (offset == 0f || offset == -height) return
        animateTo(if (offset < -height / 2f) -height else 0f)
    }

    private suspend fun animateTo(target: Float) {
        val token = ++generation
        if (offset == target) return
        if (reduceMotion) {
            offset = target
            return
        }
        try {
            animate(offset, target, animationSpec = tween(SETTLE_MILLIS, easing = MotionTokens.CalmEaseOut)) {
                    value,
                    _,
                ->
                if (token != generation) throw CancellationException("the user took the title over")
                offset = value
            }
        } catch (e: CancellationException) {
            // Only the hand-over is swallowed; the caller's own cancellation goes on.
            if (token == generation) throw e
        }
    }

    internal fun measured(heightPx: Int) {
        height = heightPx.toFloat()
    }

    internal fun translation(): Float = offset.coerceIn(-height, 0f)
}

/**
 * The Statistik frame: [title] on top, then the pinned [pinned] chrome, then [body] with the rest of the height. The
 * layout never changes while the title collapses: the three move together in their graphics layers and [body] is
 * measured at the full height it has once the title is gone, so a frame of the collapse only draws.
 */
@Composable
internal fun CollapsingColumn(
    header: CollapsingHeader,
    title: @Composable () -> Unit,
    pinned: @Composable () -> Unit,
    body: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            title()
            pinned()
            body()
        },
        modifier = modifier.clipToBounds().nestedScroll(header.connection),
    ) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val titlePlaceable = measurables[0].measure(loose)
        val pinnedPlaceable = measurables[1].measure(loose)
        header.measured(titlePlaceable.height)
        val bodyHeight = (constraints.maxHeight - pinnedPlaceable.height).coerceAtLeast(0)
        val bodyPlaceable = measurables[2].measure(Constraints.fixed(constraints.maxWidth, bodyHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            titlePlaceable.placeWithLayer(0, 0) { translationY = header.translation() }
            pinnedPlaceable.placeWithLayer(0, titlePlaceable.height) { translationY = header.translation() }
            bodyPlaceable.placeWithLayer(0, titlePlaceable.height + pinnedPlaceable.height) {
                translationY = header.translation()
            }
        }
    }
}
