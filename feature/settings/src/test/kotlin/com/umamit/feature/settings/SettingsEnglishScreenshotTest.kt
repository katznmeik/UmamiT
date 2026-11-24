package com.umamit.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
import com.umamit.core.timer.reliability.ReliabilityStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Einstellungen (SCR-25) in English (GEN-13, #359), light and dark at font scale 1.0: with a missing check, with
 * *Language* picked, and scrolled to *Timer & alarm*'s end. Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp-xhdpi")
class SettingsEnglishScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))

        private val LIVE_UPDATES_MISSING = ReliabilityStatus(
            ReliabilityCheck.entries.associateWith { CheckState.Ok } +
                (ReliabilityCheck.LiveUpdates to CheckState.Missing),
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = if (dark) "dark" else "light"

    private fun capture(
        name: String,
        status: ReliabilityStatus = ReliabilityStatus.AllOk,
        scrollToText: String? = null,
        clickText: String? = null,
        language: LanguageChoice = LanguageChoice.SYSTEM,
    ) {
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                SettingsScreen(
                    settings = UserSettings(alertSound = "glocke", preAlertSeconds = 15),
                    status = status,
                    alerting = false,
                    onBack = {},
                    actions = SettingsActions(),
                    language = language,
                )
            }
        }
        compose.waitForIdle()
        if (scrollToText != null) {
            compose.onNodeWithText(scrollToText).performScrollTo()
            compose.waitForIdle()
        }
        if (clickText != null) {
            compose.onNodeWithText(clickText).performScrollTo().performClick()
            compose.waitForIdle()
        }
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/SettingsEn_${name}_$variant.png")
    }

    @Test
    fun missingCheck() = capture("missingCheck", status = LIVE_UPDATES_MISSING)

    @Test
    fun languagePicker() = capture("language", clickText = "Language", language = LanguageChoice.ENGLISH)

    @Test
    fun brewingAndData() = capture("brewing", scrollToText = "Data & backup")
}
