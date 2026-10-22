package com.umamit.feature.brew.timer

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-05 in every state, at font scale 1.0 and 2.0 (03 §6), inside the light
 * and the dark app theme: the timer is always dark, so both look the same.
 * The alert is captured in both pulse phases (matcha and washi) and with the
 * prominent overtime; the content must stay readable in each (D-055). The tea
 * photo is the placeholder illustration. Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerScreenScreenshotTest(private val appDark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "appDark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        /** Half an alert pulse cycle (03 §3.6 `alertPulse`): the washi phase. */
        private const val PULSE_HALF_CYCLE_MILLIS = 750L
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (appDark) "appDark" else "appLight"}_font${fontScale.toString().replace(
        '.',
        '_',
    )}"

    private fun capture(name: String, state: TimerUiState.Active, advanceMillis: Long = 0, photo: Boolean = true) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.mainClock.autoAdvance = false
        val start = compose.mainClock.currentTime
        compose.setContent {
            UmamiTheme(darkTheme = appDark, reduceMotion = false) {
                TimerScreen(
                    state = state,
                    nowElapsedMs = { NOW + compose.mainClock.currentTime - start },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    teaPhoto = if (photo) painterResource(UmamiIcons.LandscapePlaceholder) else null,
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        if (advanceMillis > 0) compose.mainClock.advanceTimeBy(advanceMillis)
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TimerScreen_${name}_$variant.png")
    }

    @Test
    fun ready() = capture("Ready", TimerStates.ready(stepIndex = 1))

    @Test
    fun running() = capture("Running", TimerStates.running(remainingMs = 48_000))

    @Test
    fun preAlert() = capture("PreAlert", TimerStates.running(remainingMs = 8_000, preAlertPlayed = true, stepIndex = 1))

    @Test
    fun paused() = capture("Paused", TimerStates.paused())

    @Test
    fun alertingMatchaPhase() = capture("Alerting_matchaPhase", TimerStates.alerting(overtimeMs = 7_000))

    @Test
    fun alertingWashiPhase() = capture(
        "Alerting_washiPhase",
        TimerStates.alerting(overtimeMs = 7_000),
        advanceMillis = PULSE_HALF_CYCLE_MILLIS,
    )

    @Test
    fun alertingProminentOvertimeWashiPhase() = capture(
        "Alerting_overtime_washiPhase",
        TimerStates.alerting(overtimeMs = 150_000),
        advanceMillis = PULSE_HALF_CYCLE_MILLIS,
    )

    @Test
    fun alertingRinse() = capture("Alerting_rinse", TimerStates.alerting(rinse = true, stepIndex = 0))

    @Test
    fun finished() = capture("Finished", TimerStates.finished())

    @Test
    fun quickTimerWithoutPhoto() = capture(
        "QuickTimer",
        TimerStates.running().copy(kind = com.umamit.core.timer.engine.SessionKind.Quick, teaName = null).let {
            it.copy(steps = it.steps.take(1))
        },
        photo = false,
    )
}
