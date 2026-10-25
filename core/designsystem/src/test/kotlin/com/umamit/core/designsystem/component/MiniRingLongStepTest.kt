package com.umamit.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.designsystem.theme.WashiColorScheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The bar's mini ring follows the long-step mode of the timer ring (03 §3.6):
 * a long step glides to each coarse update, a short step follows at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class MiniRingLongStepTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var progress by mutableFloatStateOf(0f)

    private fun show(longStep: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = false) {
                ActiveBrewBar(
                    brew = ActiveBrew("Gyokuro", "Aufguss 1/1", 3_000, Color(0xFFC9D58F), longStep = longStep),
                    progress = { progress },
                    onClick = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(SETTLED_MILLIS)
    }

    private fun update(value: Float) {
        progress = value
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    /** Whether the liquor reaches near the top of the mini ring. */
    private fun topIsFilled(): Boolean {
        val bar = compose.onNodeWithContentDescription("Gyokuro", substring = true).fetchSemanticsNode().boundsInWindow
        val (ringCenterX, belowCenter) = compose.density.run { (RING_CENTER_X.toPx()) to (NEAR_TOP.toPx()) }
        val color = compose.windowBitmap().colorAt(bar.left + ringCenterX, bar.center.y - belowCenter)
        return color.distanceTo(WashiColorScheme.surfaceContainerHigh) > TINT
    }

    @Test
    fun `a long step's mini ring glides to the update`() {
        show(longStep = true)
        update(NEARLY_DONE)
        assertThat(topIsFilled()).isFalse()
        compose.mainClock.advanceTimeBy(GLIDE_MILLIS)
        assertThat(topIsFilled()).isTrue()
    }

    @Test
    fun `a short step's mini ring follows at once`() {
        show(longStep = false)
        update(NEARLY_DONE)
        assertThat(topIsFilled()).isTrue()
    }

    private companion object {
        const val SETTLED_MILLIS = 1_000L
        const val GLIDE_MILLIS = 700L
        const val NEARLY_DONE = 0.95f
        const val TINT = 0.08f

        /** 16 dp padding + half the 56 dp ring. */
        val RING_CENTER_X = 44.dp

        /** Inside the liquor circle (radius 21 dp), just below its top. */
        val NEAR_TOP = 15.dp
    }
}
