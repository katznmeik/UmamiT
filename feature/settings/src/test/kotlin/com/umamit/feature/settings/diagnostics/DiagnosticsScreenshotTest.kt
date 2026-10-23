package com.umamit.feature.settings.diagnostics

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.data.diagnostics.DiagnosticsEntry
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * *Diagnose* (SCR-27, M1-20) in light and dark at font scale 1.0 and 2.0
 * (03 §6): a log of a step with a pre-alert and an alert, and the empty log.
 * Synthetic entries only. Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class DiagnosticsScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private const val HEADER = "UmamiT 0.1.0-rc.1 (10001) · Android 16 (API 36) · Samsung SM-S931B"

        private val ENTRIES = listOf(
            "10:01:00.212" to "alert ALERT: ringer NORMAL, in call no, DND blocks no -> sound + vibration | " +
                "tone klangschale, length NORMAL | mono 5060212 boot 1",
            "10:01:00.180" to "service: in the foreground with notification 2 | mono 5060180 boot 1",
            "10:01:00.040" to "event DeadlineReached from ALARM: RUNNING -> ALERTING accepted | " +
                "DEADLINE scheduled 10:01:00.000 (mono 5060000) actual 10:01:00.040 (mono 5060040) delta +40 ms",
            "10:00:50.080" to "event PreAlertReached from ALARM: RUNNING -> RUNNING accepted | " +
                "PRE_ALERT scheduled 10:00:50.000 (mono 5050000) actual 10:00:50.080 (mono 5050080) delta +80 ms",
            "10:00:00.000" to "event Start from APP: READY -> RUNNING accepted | mono 5000000 boot 1",
            "09:59:58.512" to "process start: notifications yes, live updates yes, full-screen alert yes, " +
                "exact alarms yes, battery unrestricted no | mono 4998512 boot 1",
        ).map { (time, message) -> DiagnosticsEntry("2026-10-22 $time +02:00", message) }
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, state: DiagnosticsUiState) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                DiagnosticsScreen(state = state, onBack = {})
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/Diagnostics_${name}_$variant.png")
    }

    @Test
    fun withEntries() = capture("withEntries", DiagnosticsUiState(entries = ENTRIES, header = HEADER))

    @Test
    fun empty() = capture("empty", DiagnosticsUiState(entries = emptyList(), header = HEADER))
}
