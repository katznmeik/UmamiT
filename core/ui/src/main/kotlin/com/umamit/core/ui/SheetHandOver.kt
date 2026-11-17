package com.umamit.core.ui

import android.graphics.Picture
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.first

const val SHEET_STAND_IN_TAG = "sheet_stand_in"

/** Frames over which the scrim passes from the sheet's window to the stand-in, after the stand-in's first frame. */
private const val SCRIM_STEPS = 2

/** Frames over which the sheet itself then fades out over the stand-in. */
private const val SHEET_STEPS = 2

/** How long the stand-in waits for the next route to cover the screen it was frozen over. */
private const val MAX_UNCOVERED_NANOS = 500_000_000L

/**
 * A modal sheet's hand-over to the route it opens (03 SCR-03, D-129). A Material modal sheet draws in a window of
 * its own above the activity's, so the next route's fade cannot cover it. At the tap the sheet [freeze]s its frame;
 * [SheetStandIn] draws that frame in the activity's window, below the next route, which starts its fade at once; and
 * the sheet's own window stays ([holdsSheet]). From the stand-in's second frame the scrim passes over in a few frames:
 * the sheet window's fades out ([sheetScrimShare]) while the stand-in's rises, so both together dim as the sheet's
 * did. Then the sheet itself fades out over a few frames ([sheetAlpha]), revealing the stand-in with the route
 * already fading in over it. The two windows never change in one frame, but whichever shows its frame first, the
 * screen shows a blend of the sheet and the route over its frozen frame, never what the sheet covered. Then the
 * sheet's window, invisible now, goes.
 *
 * It holds nothing saveable: a rotation or a process recreation leaves neither the sheet nor the stand-in. The
 * owner hides both in the first frame of an alert and then [clear]s it (rule 8).
 */
@Stable
class SheetHandOver {
    private var frame by mutableStateOf<FrozenSheet?>(null)
    private var fadeStep by mutableIntStateOf(0)
    private var sheetReleased by mutableStateOf(false)

    /** Whether a hand-over runs: from the tap until the stand-in has gone. */
    val active: Boolean get() = frame != null

    /** Whether the sheet's own window must still stay: the stand-in has not taken over yet. */
    val holdsSheet: Boolean get() = frame != null && !sheetReleased

    /** The share of its scrim the sheet's window still draws: 1, then down to 0 as the stand-in takes it over. */
    val sheetScrimShare: Float
        get() = 1f - fadeStep.coerceAtMost(SCRIM_STEPS).toFloat() / SCRIM_STEPS

    /**
     * The alpha of the sheet in its window (without its scrim): 1, then down to 0 over the stand-in once the scrim
     * has passed over, so the sheet's scrim never shows through it.
     */
    val sheetAlpha: Float
        get() = 1f - (fadeStep - SCRIM_STEPS).coerceIn(0, SHEET_STEPS).toFloat() / SHEET_STEPS

    /**
     * Freezes the frame of the sheet in [sheetView]'s window: its whole window, of which the stand-in shows only the
     * sheet, [sheetLeft] to [sheetRight] from [sheetTop] down to the window's bottom (where a keyboard may stand,
     * drawn in [container]), clipped to [shape]; the scrim the stand-in draws itself, in [scrim]. It records the
     * frame now, in the tap, without waiting for a frame. False (and nothing held) if a hand-over runs already or the
     * frame cannot be recorded: then the sheet closes as it would without one.
     */
    @Suppress("LongParameterList") // the sheet's geometry, its shape and its two colours
    fun freeze(
        sheetView: View,
        sheetLeft: Float,
        sheetTop: Float,
        sheetRight: Float,
        shape: Shape,
        density: Density,
        layoutDirection: LayoutDirection,
        container: Color,
        scrim: Color,
    ): Boolean {
        val root = sheetView.rootView
        val picture = (if (frame == null && root.width > 0 && root.height > 0) record(root) else null) ?: return false
        val bounds = Rect(sheetLeft, sheetTop, sheetRight, root.height.toFloat())
        val outline = shape.createOutline(bounds.size, layoutDirection, density)
        val path = Path().apply {
            addOutline(outline)
            translate(bounds.topLeft)
        }
        val origin = IntArray(2).also(root::getLocationOnScreen)
        fadeStep = 0
        sheetReleased = false
        frame = FrozenSheet(
            picture = picture,
            windowOrigin = Offset(origin[0].toFloat(), origin[1].toFloat()),
            sheet = path,
            bounds = bounds,
            container = container,
            scrim = scrim,
        )
        return true
    }

    /** Ends the hand-over: neither the sheet nor the stand-in is held any more. */
    fun clear() {
        frame = null
        fadeStep = 0
        sheetReleased = false
    }

    /** Lets the sheet's window go, whether or not the stand-in has taken over: never later than a bound (rule 8). */
    fun releaseSheet() {
        if (frame != null) sheetReleased = true
    }

    internal val frozen: FrozenSheet? get() = frame

    /** The stand-in's scrim alpha: with the sheet's window's, it dims as the sheet's scrim alone did. */
    internal fun standInScrimAlpha(frozen: FrozenSheet): Float {
        val full = frozen.scrim.alpha
        val live = if (sheetReleased) 0f else full * sheetScrimShare
        return (1f - (1f - full) / (1f - live)).coerceIn(0f, 1f)
    }

    /**
     * Runs the hand-over of [frozen], from the stand-in's first frame: after that frame the scrim passes over and the
     * sheet fades out, then, once the next route [covered] the screen, the sheet's window goes; when that route no
     * longer covers it (back), the stand-in goes too. Never covered in time (nothing opened): it ends, and the screen
     * shows as without a hand-over.
     */
    internal suspend fun run(frozen: FrozenSheet, covered: () -> Boolean) {
        // The stand-in's first frame keeps the sheet whole: its window may show that frame before the activity's.
        // Each change is made in the frame's callback, before that frame composes, so both windows draw it together.
        repeat(SCRIM_STEPS + SHEET_STEPS) { step -> withFrameNanos { fadeStep = step + 1 } }
        var start = -1L
        var isCovered = false
        while (!isCovered) {
            val now = withFrameNanos { now ->
                isCovered = covered()
                if (isCovered) sheetReleased = true
                now
            }
            if (start < 0) start = now
            if (!isCovered && now - start > MAX_UNCOVERED_NANOS) {
                if (frame === frozen) clear()
                return
            }
        }
        snapshotFlow { covered() }.first { !it }
        if (frame === frozen) clear()
    }

    // Whatever keeps the window from drawing in software (a hardware-only layer or bitmap, a test's stubbed graphics),
    // the frozen frame is presentation only: the sheet then closes as without a hand-over, and the tap never fails.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun record(root: View): Picture? {
        val picture = Picture()
        val canvas = picture.beginRecording(root.width, root.height)
        return try {
            root.draw(canvas)
            picture
        } catch (e: RuntimeException) {
            null
        } finally {
            picture.endRecording()
        }
    }
}

/** A sheet's window as frozen at the tap, in that window's coordinates, which start at [windowOrigin] on screen. */
internal class FrozenSheet(
    val picture: Picture,
    val windowOrigin: Offset,
    val sheet: Path,
    val bounds: Rect,
    val container: Color,
    val scrim: Color,
)

/**
 * The frozen frame of [handOver]'s sheet, in the activity's window: its scrim over the whole screen and the sheet
 * where it stood. Place it above what the sheet covered and below the route the sheet opens, so that route fades in
 * over it. It takes every touch, as the sheet did. [covered] tells whether that route covers the screen now.
 */
@Composable
fun SheetStandIn(handOver: SheetHandOver, covered: () -> Boolean, modifier: Modifier = Modifier) {
    val frozen = handOver.frozen ?: return
    val currentCovered by rememberUpdatedState(covered)
    LaunchedEffect(handOver, frozen) { handOver.run(frozen) { currentCovered() } }
    val place = remember(frozen) { ScreenPlace() }
    Box(
        modifier
            .fillMaxSize()
            .testTag(SHEET_STAND_IN_TAG)
            .onGloballyPositioned { place.origin = it.positionOnScreen() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .drawBehind { drawFrozen(frozen, handOver.standInScrimAlpha(frozen), place.origin) },
    )
}

/** Where the stand-in starts on screen, from its last layout; read in its draw. */
private class ScreenPlace(var origin: Offset = Offset.Zero)

private fun DrawScope.drawFrozen(frozen: FrozenSheet, scrimAlpha: Float, origin: Offset) {
    drawRect(color = frozen.scrim.copy(alpha = 1f), alpha = scrimAlpha)
    translate(frozen.windowOrigin.x - origin.x, frozen.windowOrigin.y - origin.y) {
        clipPath(frozen.sheet) {
            // The sheet's surface, also where a keyboard stood below it at the tap.
            drawRect(color = frozen.container, topLeft = frozen.bounds.topLeft, size = frozen.bounds.size)
            drawIntoCanvas { it.nativeCanvas.drawPicture(frozen.picture) }
        }
    }
}
