package com.umamit.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Einstellungen (SCR-25) with a missing check and with all ready, and Über (SCR-27), in light and
 * dark at font scale 1.0 and 2.0 (03 §6). Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SettingsScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private val LIVE_UPDATES_MISSING = ReliabilityStatus(
            ReliabilityCheck.entries.associateWith { CheckState.Ok } +
                (ReliabilityCheck.LiveUpdates to CheckState.Missing),
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { UmamiTheme(darkTheme = dark, reduceMotion = true) { content() } }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/${name}_$variant.png")
    }

    @Test
    fun settingsWithMissingCheck() = capture("Settings_missingCheck") {
        SettingsScreen(
            settings = UserSettings(alertSound = "glocke", preAlertSeconds = 15),
            status = LIVE_UPDATES_MISSING,
            alerting = false,
            onBack = {},
            actions = SettingsActions(),
        )
    }

    @Test
    fun settingsAllReady() = capture("Settings_allReady") {
        SettingsScreen(
            settings = UserSettings(),
            status = ReliabilityStatus.AllOk,
            alerting = false,
            onBack = {},
            actions = SettingsActions(),
        )
    }

    @Test
    fun about() = capture("About") {
        AboutScreen(versionName = "0.1.0-rc.1", onBack = {}, onRerunSetup = {}, onOpenDiagnostics = {})
    }
}
