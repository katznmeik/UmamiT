package com.umamit.feature.brew.quick

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.feature.brew.timer.TimerScreen
import com.umamit.feature.brew.timer.TimerStates
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-07 in English (GEN-10, #359), light and dark at font scale 1.0: before its *Start* (the chips, the pre-alert
 * too short, the pre-alert menu) and running. Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp-xhdpi")
class QuickTimerEnglishScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = if (dark) "dark" else "light"

    private fun path(name: String) = "${roborazziSystemPropertyOutputDirectory()}/QuickTimerEn_${name}_$variant.png"

    private fun capture(name: String, state: QuickTimerUiState, openPreAlertMenu: Boolean = false) {
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                QuickTimerScreen(state = state, actions = QuickTimerActions())
            }
        }
        if (openPreAlertMenu) {
            compose.onNode(hasContentDescription("Pre-alert:", substring = true)).performScrollTo().performClick()
        }
        compose.waitForIdle()
        captureScreenRoboImage(path(name))
    }

    @Test
    fun chips() = capture("chips", QuickTimerUiState(durationSeconds = 60))

    @Test
    fun preAlertTooShort() =
        capture("preAlertTooShort", QuickTimerUiState(20, preAlertChoice = PreAlertChoice.Seconds(20)))

    @Test
    fun preAlertMenu() = capture("preAlertMenu", QuickTimerUiState(120), openPreAlertMenu = true)

    @Test
    fun running() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = false) {
                TimerScreen(
                    state = TimerStates.quick(TimerPhase.RUNNING),
                    nowElapsedMs = { NOW },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    teaPhoto = null,
                    teaStyleKey = null,
                    stock = null,
                    other = null,
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        captureScreenRoboImage(path("running"))
    }
}
