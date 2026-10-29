package com.umamit.feature.brew.timer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-05 on a short screen (384 × 832 dp) at font scale 2.0 with a two-line tea name: the band
 * and the seal give way, so the primary control lies whole on the screen (rule 8, D-096). The
 * seal shows a plain white synthetic photo (D-016). Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w384dp-h832dp-xhdpi", fontScale = 2.0f)
class TimerShortScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, state: TimerUiState.Active) {
        compose.mainClock.autoAdvance = false
        val start = compose.mainClock.currentTime
        compose.setContent {
            UmamiTheme(reduceMotion = false) {
                TimerScreen(
                    state = state.copy(teaName = TWO_LINE_NAME),
                    nowElapsedMs = { NOW + compose.mainClock.currentTime - start },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    teaPhoto = ColorPainter(Color.White),
                    teaStyleKey = "sencha",
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TimerScreen_short_${name}_font2_0.png")
    }

    @Test
    fun ready() = capture("Ready", TimerStates.ready(stepIndex = 1))

    @Test
    fun running() = capture("Running", TimerStates.running())

    @Test
    fun alerting() = capture("Alerting_matchaPhase", TimerStates.alerting(overtimeMs = 7_000))

    private companion object {
        /** A synthetic tea name (D-016) that takes two lines of the title here. */
        const val TWO_LINE_NAME = "Bio Japan Sencha Kagoshima Asatsuyu"
    }
}
