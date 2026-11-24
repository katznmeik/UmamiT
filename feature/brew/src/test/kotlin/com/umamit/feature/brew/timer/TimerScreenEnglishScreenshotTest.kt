package com.umamit.feature.brew.timer

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-05 in English (GEN-13, #359), light and dark at font scale 1.0: READY with the stock, RUNNING with the quick
 * timer's row, the pre-alert, PAUSED, the alert, the rinse's alert, FINISHED and a cold brew's READY. The states are
 * [TimerStates]'s; the tea's name is data and stays as it is. No photo on the seal (D-095). Goldens:
 * `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp-xhdpi")
class TimerScreenEnglishScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = if (dark) "dark" else "light"

    private fun capture(
        name: String,
        state: TimerUiState.Active,
        stock: TimerStock? = TimerStock(38.0, StockLevel.AVAILABLE),
        other: TimerUiState.Active? = null,
    ) {
        compose.mainClock.autoAdvance = false
        val start = compose.mainClock.currentTime
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = false) {
                TimerScreen(
                    state = state,
                    nowElapsedMs = { NOW + compose.mainClock.currentTime - start },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    teaPhoto = null,
                    teaStyleKey = "gyokuro",
                    stock = stock,
                    other = other,
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TimerScreenEn_${name}_$variant.png")
    }

    @Test
    fun readyLowStock() =
        capture("Ready_lowStock", TimerStates.ready(stepIndex = 1), stock = TimerStock(14.0, StockLevel.LOW))

    @Test
    fun runningWithQuickRow() = capture(
        "Running_quickRow",
        TimerStates.running(),
        other = TimerStates.quick(TimerPhase.RUNNING, seconds = 300, remainingMs = 134_000),
    )

    @Test
    fun preAlert() = capture("PreAlert", TimerStates.running(remainingMs = 4_000, preAlertPlayed = true, stepIndex = 1))

    @Test
    fun paused() = capture("Paused", TimerStates.paused())

    @Test
    fun alerting() = capture("Alerting", TimerStates.alerting(overtimeMs = 150_000))

    @Test
    fun alertingRinse() = capture("Alerting_rinse", TimerStates.alerting(rinse = true, stepIndex = 0))

    @Test
    fun finished() = capture("Finished", TimerStates.finished())

    @Test
    fun readyColdBrew() = capture("Ready_coldBrew", TimerStates.coldReady(iceBrew = false))
}
