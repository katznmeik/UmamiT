package com.umamit.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.DefaultExtendedColors
import com.umamit.core.designsystem.theme.SteepingColorScheme
import com.umamit.core.designsystem.theme.SteepingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `TimerRing` behaviour (03 §3.6, §3.8, §6): the liquor fill rises from the
 * bottom, long steps glide to a new level unless motion is reduced, and
 * TalkBack hears the step and the remaining time.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class TimerRingTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val liquor = DefaultExtendedColors.liquor.green
    private val background = SteepingColorScheme.background

    private var progress by mutableFloatStateOf(0f)

    private fun show(initial: Float, longStep: Boolean = false, reduceMotion: Boolean = false) {
        progress = initial
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SteepingTheme(reduceMotion = reduceMotion) {
                Box(Modifier.background(background)) {
                    TimerRing(
                        progress = { progress },
                        remainingSeconds = 72,
                        caption = "Zieht\u00A0…",
                        liquorColor = liquor,
                        stepDescription = "Aufguss 2 von 4",
                        longStep = longStep,
                        modifier = Modifier.testTag(TAG),
                    )
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun updateProgress(value: Float) {
        progress = value
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    /** The ring's colour at a height through its centre column: 0 = top inner edge, 1 = bottom inner edge. */
    private fun colorAtHeight(fraction: Float): Color {
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag(TAG).fetchSemanticsNode().boundsInWindow
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        // Sample just left of the centre column, clear of the leaf, digits and caption.
        val x = bounds.left + bounds.width * SAMPLE_X
        val inset = bounds.height * INNER_INSET
        val y = bounds.top + inset + (bounds.height - 2 * inset) * fraction
        return Color(bitmap.getPixel(x.toInt(), y.toInt()))
    }

    private fun Color.isTintedByLiquor(): Boolean = green - background.green > LIQUOR_TINT

    @Test
    fun `liquor fill rises from the bottom of the ring`() {
        show(initial = 0.5f)
        assertThat(colorAtHeight(BOTTOM).isTintedByLiquor()).isTrue()
        assertThat(colorAtHeight(TOP).isTintedByLiquor()).isFalse()
    }

    @Test
    fun `an empty ring has no liquor`() {
        show(initial = 0f)
        assertThat(colorAtHeight(BOTTOM).isTintedByLiquor()).isFalse()
    }

    @Test
    fun `short steps follow the progress on the next frame`() {
        show(initial = 0f)
        updateProgress(1f)
        assertThat(colorAtHeight(TOP).isTintedByLiquor()).isTrue()
    }

    @Test
    fun `long steps glide to a new level instead of jumping`() {
        show(initial = 0f, longStep = true)
        updateProgress(NEARLY_DONE)
        assertThat(colorAtHeight(TOP).isTintedByLiquor()).isFalse()
        compose.mainClock.advanceTimeBy(GLIDE_MILLIS)
        assertThat(colorAtHeight(TOP).isTintedByLiquor()).isTrue()
    }

    @Test
    fun `a long step's last update snaps, so no glide runs into the alert`() {
        show(initial = 0.5f, longStep = true)
        updateProgress(1f)
        assertThat(colorAtHeight(TOP).isTintedByLiquor()).isTrue()
    }

    @Test
    fun `under reduce motion long steps jump without a glide`() {
        show(initial = 0f, longStep = true, reduceMotion = true)
        // Not 1: the update to 1 snaps in both modes, so it would not prove the missing glide.
        updateProgress(NEARLY_DONE)
        assertThat(colorAtHeight(TOP).isTintedByLiquor()).isTrue()
    }

    @Test
    fun `TalkBack hears the step, the remaining time and the caption`() {
        show(initial = 0.3f)
        val description = compose.onNodeWithTag(TAG).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
        assertThat(description).isEqualTo("Aufguss 2 von 4, noch 1 Minute 12 Sekunden, Zieht\u00A0…")
    }

    private companion object {
        const val TAG = "ring"
        const val TOP = 0.08f
        const val BOTTOM = 0.92f
        const val SAMPLE_X = 0.3f
        const val INNER_INSET = 0.1f
        const val LIQUOR_TINT = 0.1f
        const val GLIDE_MILLIS = 700L
        const val NEARLY_DONE = 0.95f
    }
}
