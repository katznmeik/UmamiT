package com.umamit.feature.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * The title that scrolls away with a tab's content (SCR-18): the content takes an upward scroll first from the
 * title, then scrolls itself; a downward scroll shows the title again only once the content is at its top.
 */
internal class CollapsingHeader {
    private var height = 0f
    private var offset by mutableFloatStateOf(0f)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            return Offset(0f, moveBy(available.y))
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f) return Offset.Zero
            return Offset(0f, moveBy(available.y))
        }
    }

    /** Moves the title by [dy] within its height; returns what it took. */
    private fun moveBy(dy: Float): Float {
        val moved = (offset + dy).coerceIn(-height, 0f)
        val taken = moved - offset
        offset = moved
        return taken
    }

    /** The title in full again (the Statistik tab was selected again). */
    fun reveal() {
        offset = 0f
    }

    /** Lays the title out [offset] up, taking only the height that is left. */
    fun modifier(): Modifier = Modifier
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            height = placeable.height.toFloat()
            val visible = (placeable.height + offset).roundToInt().coerceAtLeast(0)
            layout(placeable.width, visible) { placeable.place(0, offset.roundToInt()) }
        }
}

internal fun Modifier.collapsing(header: CollapsingHeader): Modifier = then(header.modifier())
