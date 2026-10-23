package com.umamit.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.rules.TestRule
import kotlin.math.abs

/**
 * Draws the window into a bitmap. `captureToImage()` waits for a redraw that
 * never comes while the test clock is paused, so the motion tests draw it themselves.
 */
internal fun AndroidComposeTestRule<out TestRule, out ComponentActivity>.windowBitmap(): Bitmap {
    waitForIdle()
    val view = activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    return bitmap
}

internal fun AndroidComposeTestRule<out TestRule, out ComponentActivity>.boundsOf(tag: String): Rect =
    onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow

internal fun Bitmap.colorAt(x: Float, y: Float): Color = Color(getPixel(x.toInt(), y.toInt()))

/** The largest difference of the red, green and blue channels. */
internal fun Color.distanceTo(other: Color): Float =
    maxOf(abs(red - other.red), abs(green - other.green), abs(blue - other.blue))
