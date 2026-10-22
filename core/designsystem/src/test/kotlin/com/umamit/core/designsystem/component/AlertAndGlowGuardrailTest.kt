package com.umamit.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.DefaultExtendedColors
import com.umamit.core.designsystem.theme.SteepingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The rule-8 guardrails of 03 §3.6 „Unscaled alert and glow" and „Reduce
 * motion", at the normal animator scale and at a developer scale of 10x:
 * the alert pulse keeps its 1.5 s cycle, the pre-alert glow keeps its 0.8 s
 * shape (peak at 200 ms, gone at 800 ms), and the glow is off under reduce motion.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AlertAndGlowGuardrailTest(private val animatorScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "animatorScale={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf<Any>(1f), arrayOf<Any>(10f))

        private const val TAG = "subject"
        private const val HALF_CYCLE_MILLIS = 750L
        private const val GLOW_PEAK_MILLIS = 200L
        private const val GLOW_REST_OF_SWELL_MILLIS = 650L
        private const val SAME = 0.04f
        private const val VISIBLE_GLOW = 0.1f
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(
        effectContext = object : MotionDurationScale {
            override val scaleFactor: Float = animatorScale
        },
    )

    private val alert = DefaultExtendedColors.alert

    private fun alertColor(): Color {
        val bounds = compose.boundsOf(TAG)
        return compose.windowBitmap().colorAt(bounds.center.x, bounds.center.y)
    }

    @Test
    fun `the alert pulse keeps its cycle whatever the animator scale`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SteepingTheme(reduceMotion = false) {
                AlertSurface(modifier = Modifier.size(40.dp).testTag(TAG)) {}
            }
        }
        assertThat(alertColor().distanceTo(alert.matcha)).isLessThan(SAME)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(HALF_CYCLE_MILLIS)
        assertThat(alertColor().distanceTo(alert.washi)).isLessThan(SAME)
        compose.mainClock.advanceTimeBy(HALF_CYCLE_MILLIS)
        assertThat(alertColor().distanceTo(alert.matcha)).isLessThan(SAME)
    }

    private var tick by mutableStateOf<Long?>(null)

    private var ringShown by mutableStateOf(true)

    private fun showRing(reduceMotion: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SteepingTheme(reduceMotion = reduceMotion) {
                Box(Modifier.background(MaterialTheme.colorScheme.background).size(300.dp).testTag("host")) {
                    if (ringShown) {
                        TimerRing(
                            progress = { 0.25f },
                            remainingSeconds = 10,
                            caption = "Gleich fertig",
                            liquorColor = DefaultExtendedColors.liquor.green,
                            // A quarter turn: the marker sits on the right of the ring, on the horizontal axis.
                            preAlertPosition = 0.25f,
                            preAlertGlowTick = tick,
                            modifier = Modifier.testTag(TAG),
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    /** A point inside the glow's halo but clear of the ring and the tick. */
    private fun glowColor(): Color {
        val bounds = compose.boundsOf(TAG)
        val px = compose.density.density
        val ringRadius = bounds.width / 2 - 14 * px
        val marker = bounds.center + Offset(ringRadius, 0f)
        return compose.windowBitmap().colorAt(marker.x + 4 * px, marker.y + 5 * px)
    }

    private fun playTick() {
        tick = 1L
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `the pre-alert glow peaks at 200 ms and is gone at 800 ms whatever the animator scale`() {
        showRing(reduceMotion = false)
        val calm = glowColor()
        playTick()
        compose.mainClock.advanceTimeBy(GLOW_PEAK_MILLIS)
        assertThat(glowColor().distanceTo(calm)).isGreaterThan(VISIBLE_GLOW)
        compose.mainClock.advanceTimeBy(GLOW_REST_OF_SWELL_MILLIS)
        assertThat(glowColor().distanceTo(calm)).isLessThan(SAME)
    }

    @Test
    fun `a tick's glow never replays when the ring comes back`() {
        showRing(reduceMotion = false)
        val calm = glowColor()
        playTick()
        compose.mainClock.advanceTimeBy(GLOW_PEAK_MILLIS + GLOW_REST_OF_SWELL_MILLIS)
        ringShown = false
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        ringShown = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(GLOW_PEAK_MILLIS)
        assertThat(glowColor().distanceTo(calm)).isLessThan(SAME)
    }

    @Test
    fun `under reduce motion the pre-alert tick shows no glow`() {
        showRing(reduceMotion = true)
        val calm = glowColor()
        playTick()
        assertThat(glowColor().distanceTo(calm)).isLessThan(SAME)
        compose.mainClock.advanceTimeBy(GLOW_PEAK_MILLIS)
        assertThat(glowColor().distanceTo(calm)).isLessThan(SAME)
    }
}
