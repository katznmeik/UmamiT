package com.umamit.feature.brew.quick

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TimerPhase
import com.umamit.feature.brew.timer.TimerScreen
import com.umamit.feature.brew.timer.TimerStates
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-07 on a short screen (384 × 832 dp) at font scale 2.0 in the light app theme, with the chips of v0.6.1
 * (1:00 … 15:00, D-114): before *Start* on the 1:00 it opens with and on the 10:00 chip, and the 10:00 quick timer
 * running. The chips wrap, the band gives way, and the ring and the pill stay where they were at *Start* (polish 2,
 * D-098; asserted in `TimerScreenLayoutTest`). Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w384dp-h832dp-xhdpi", fontScale = 2.0f)
class QuickTimerShortScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage(
            "${roborazziSystemPropertyOutputDirectory()}/QuickTimer_short_${name}_appLight_font2_0.png",
        )
    }

    private fun captureBeforeStart(name: String, state: QuickTimerUiState) {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                QuickTimerScreen(state = state, actions = QuickTimerActions())
            }
        }
        capture(name)
    }

    @Test
    fun chips() = captureBeforeStart("chips", QuickTimerUiState())

    @Test
    fun chipsTenMinutes() = captureBeforeStart("chips10", QuickTimerUiState(durationSeconds = 600))

    @Test
    fun running() {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                TimerScreen(
                    state = TimerStates.quick(TimerPhase.RUNNING, seconds = 600, remainingMs = 420_000),
                    nowElapsedMs = { NOW },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                )
            }
        }
        capture("running10")
    }
}
