package com.umamit.feature.brew.quick

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.feature.brew.setup.ActiveSessionQuestion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-07 before its *Start*, at font scale 1.0 and 2.0 (03 §6), inside the
 * light and the dark app theme (the screen is always dark): the chips, a
 * custom duration, the duration picker, the second-timer question, and the
 * *Vorwarnung* row with each kind of label and its menu (TMR-22, D-097):
 * „Auto · 10 s" (chips), „Auto · keine" (custom, a long step), „Aus", „20 s"
 * and „20 s · zu kurz". Its running states are the timer's
 * (`TimerScreen_Quick_*`). Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class QuickTimerScreenshotTest(private val appDark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "appDark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (appDark) "appDark" else "appLight"}_font${fontScale.toString().replace(
        '.',
        '_',
    )}"

    private fun capture(
        name: String,
        state: QuickTimerUiState,
        openPicker: Boolean = false,
        openPreAlertMenu: Boolean = false,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = appDark, reduceMotion = true) {
                QuickTimerScreen(state = state, actions = QuickTimerActions())
            }
        }
        if (openPicker) compose.onNodeWithTag(QUICK_RING_TAG).performClick()
        if (openPreAlertMenu) {
            compose.onNode(hasContentDescription("Vorwarnung:", substring = true)).performScrollTo().performClick()
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/QuickTimer_${name}_$variant.png")
    }

    @Test
    fun chips() = capture("chips", QuickTimerUiState(durationSeconds = 60))

    @Test
    fun customDuration() = capture("custom", QuickTimerUiState(durationSeconds = 4_500))

    @Test
    fun picker() = capture("picker", QuickTimerUiState(durationSeconds = 95), openPicker = true)

    @Test
    fun preAlertOff() = capture("preAlertOff", QuickTimerUiState(120, preAlertChoice = PreAlertChoice.Off))

    @Test
    fun preAlertExplicit() =
        capture("preAlertExplicit", QuickTimerUiState(120, preAlertChoice = PreAlertChoice.Seconds(20)))

    @Test
    fun preAlertTooShort() =
        capture("preAlertTooShort", QuickTimerUiState(20, preAlertChoice = PreAlertChoice.Seconds(20)))

    @Test
    fun preAlertMenu() = capture("preAlertMenu", QuickTimerUiState(120), openPreAlertMenu = true)

    @Test
    fun question() = capture(
        "question",
        QuickTimerUiState(durationSeconds = 120, question = ActiveSessionQuestion("Bio Uji Gyokuro Gokō")),
    )
}
