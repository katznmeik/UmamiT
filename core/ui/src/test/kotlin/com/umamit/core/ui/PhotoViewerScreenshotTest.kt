package com.umamit.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The photo viewer (SCR-28) on a portrait and a landscape placeholder, fitted whole on black, at font scale 1.0 and
 * 2.0 for the counter (03 §6). Light and dark do not matter: the viewer is black. Placeholders only (D-016).
 * Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class PhotoViewerScreenshotTest(private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "fontScale={0}")
        fun parameters(): List<Array<Any>> = listOf(1f, 2f).map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, start: Int) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                PhotoViewerScreen(
                    sources = listOf("portrait", "landscape"),
                    startIndex = start,
                    onBack = {},
                    photo = { if (it == "portrait") PORTRAIT else LANDSCAPE },
                )
            }
        }
        compose.waitForIdle()
        val scale = fontScale.toString().replace('.', '_')
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/PhotoViewer_${name}_font$scale.png")
    }

    @Test
    fun portrait() = capture("portrait", start = 0)

    @Test
    fun landscape() = capture("landscape", start = 1)
}

/** A synthetic photo of [width] × [height] with a frame and a diagonal, so cropping would show (D-016). */
private class PlaceholderPhoto(private val width: Float, private val height: Float, private val tint: Color) :
    Painter() {
    override val intrinsicSize: Size = Size(width, height)

    override fun DrawScope.onDraw() {
        drawRect(tint, size = size)
        drawRect(Color.White, size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12f))
        drawLine(Color.White, Offset.Zero, Offset(size.width, size.height), strokeWidth = 8f)
        drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 8f)
    }
}

private val PORTRAIT = PlaceholderPhoto(900f, 1200f, Color(0xFF5B7F5B))
private val LANDSCAPE = PlaceholderPhoto(1600f, 900f, Color(0xFF6A7FA8))
