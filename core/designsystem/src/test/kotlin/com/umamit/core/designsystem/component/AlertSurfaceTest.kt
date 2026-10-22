package com.umamit.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.DefaultExtendedColors
import com.umamit.core.designsystem.theme.SteepingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The alert surface (03 §3.6 `alertPulse`, CLAUDE.md rule 8): full matcha on the
 * first frame, washi half a cycle later, and static matcha under reduce motion.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AlertSurfaceTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val alert = DefaultExtendedColors.alert

    private fun show(reduceMotion: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SteepingTheme(reduceMotion = reduceMotion) {
                AlertSurface(modifier = Modifier.size(40.dp).testTag("alert")) {}
            }
        }
    }

    private fun surfaceColor(): Color {
        // captureToImage() waits for a redraw that never comes with a paused clock, so draw the window ourselves.
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("alert").fetchSemanticsNode().boundsInWindow
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return Color(bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt()))
    }

    private fun assertClose(actual: Color, expected: Color) {
        listOf(
            actual.red to expected.red,
            actual.green to expected.green,
            actual.blue to expected.blue,
        ).forEach { (a, e) -> assertThat(abs(a - e)).isLessThan(COLOR_TOLERANCE) }
    }

    @Test
    fun `alert surface is full matcha on the first frame`() {
        show(reduceMotion = false)
        assertClose(surfaceColor(), alert.matcha)
    }

    @Test
    fun `alert pulse reaches washi half a cycle after the alert starts`() {
        show(reduceMotion = false)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(HALF_CYCLE_MILLIS)
        assertClose(surfaceColor(), alert.washi)
    }

    @Test
    fun `alert pulse is back at matcha after one full cycle`() {
        show(reduceMotion = false)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(FULL_CYCLE_MILLIS)
        assertClose(surfaceColor(), alert.matcha)
    }

    @Test
    fun `under reduce motion the alert surface stays static matcha`() {
        show(reduceMotion = true)
        assertClose(surfaceColor(), alert.matcha)
        compose.mainClock.advanceTimeBy(HALF_CYCLE_MILLIS)
        assertClose(surfaceColor(), alert.matcha)
    }

    private companion object {
        const val HALF_CYCLE_MILLIS = 750L
        const val FULL_CYCLE_MILLIS = 1_500L
        const val COLOR_TOLERANCE = 0.04f
    }
}
