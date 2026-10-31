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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Einstellungen (SCR-25) with a missing check, with all ready, with the *Vorwarnton* picker
 * (SET-26), its *Lautstärke* (SET-27), *Bei „Nicht stören“ klingeln* (SET-32) and the alarm volume at 0
 * (TMR-31), scrolled to its *Katalog* section (SET-22, SET-23), and Über (SCR-27), in light and dark at font
 * scale 1.0 and 2.0 (03 §6).
 * Goldens: `src/test/screenshots/`.
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

        /** 22 Oct 2026, 12:00 UTC. */
        private const val LAST_REFRESH_MS = 1_792_670_400_000L

        private val LIVE_UPDATES_MISSING = ReliabilityStatus(
            ReliabilityCheck.entries.associateWith { CheckState.Ok } +
                (ReliabilityCheck.LiveUpdates to CheckState.Missing),
        )

        /** SET-32's title, with the no-break space that keeps „Nicht stören“ on one line. */
        private const val RING_UNDER_DND = "Bei „Nicht\u00A0stören“ klingeln"

        private val ALARM_VOLUME_OFF = ReliabilityStatus(
            ReliabilityCheck.entries.associateWith { CheckState.Ok } +
                (ReliabilityCheck.AlarmVolume to CheckState.Missing),
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(
        name: String,
        scrollToText: String? = null,
        clickText: String? = null,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { UmamiTheme(darkTheme = dark, reduceMotion = true) { content() } }
        compose.waitForIdle()
        if (scrollToText != null) {
            compose.onNodeWithText(scrollToText).performScrollTo()
            compose.waitForIdle()
        }
        if (clickText != null) {
            compose.onNodeWithText(clickText).performScrollTo().performClick()
            compose.waitForIdle()
        }
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

    /** The *Vorwarnton* picker (SET-26): the five sounds by name, Klangstab picked, *Anhören* and *Fertig*. */
    @Test
    fun settingsPreAlertSoundPicker() = capture("Settings_preAlertSound", clickText = "Vorwarnton") {
        SettingsScreen(
            settings = UserSettings(preAlertSound = "klangstab"),
            status = ReliabilityStatus.AllOk,
            alerting = false,
            onBack = {},
            actions = SettingsActions(),
        )
    }

    /** *Lautstärke* below *Vorwarnton* at 60 %, with its hint (SET-27, D-098). */
    @Test
    fun settingsVolume() = capture("Settings_volume", scrollToText = "Wirkt auf die Alarmlautstärke des Handys") {
        SettingsScreen(
            settings = UserSettings(alertVolumePercent = 60),
            status = ReliabilityStatus.AllOk,
            alerting = false,
            onBack = {},
            actions = SettingsActions(),
        )
    }

    /** „Bei „Nicht stören" klingeln" below *Alarmdauer*, on with its detail (SET-32, D-099). */
    @Test
    fun settingsRingUnderDnd() = capture("Settings_ringUnderDnd", scrollToText = RING_UNDER_DND) {
        SettingsScreen(
            settings = UserSettings(),
            status = ReliabilityStatus.AllOk,
            alerting = false,
            onBack = {},
            actions = SettingsActions(),
        )
    }

    /** The phone's alarm volume at 0: the card's row „Alarmlautstärke · Ist aus" with *Beheben* (TMR-31, D-098). */
    @Test
    fun settingsAlarmVolumeOff() = capture("Settings_alarmVolumeOff", scrollToText = "Ist aus") {
        SettingsScreen(
            settings = UserSettings(),
            status = ALARM_VOLUME_OFF,
            alerting = false,
            onBack = {},
            actions = SettingsActions(),
        )
    }

    /** The *Katalog* section: „Letzter Abruf" with *Jetzt aktualisieren* and *Fotos laden* (SET-22, SET-23). */
    @Test
    fun settingsCatalog() = capture("Settings_catalog", scrollToText = "Fotos laden") {
        SettingsScreen(
            settings = UserSettings(),
            status = ReliabilityStatus.AllOk,
            alerting = false,
            onBack = {},
            actions = SettingsActions(),
            // Midday UTC, so the date is the same in every time zone the tests run in.
            catalog = CatalogSettingsState(lastRefreshAtMillis = LAST_REFRESH_MS),
        )
    }

    /** *Brühen & Vorrat* between *Timer & Alarm* and *Katalog*: „Knapp ab" 15 g (SET-16, SET-30, D-097). */
    @Test
    fun settingsBrewing() = capture("Settings_brewing", scrollToText = "Knapp ab") {
        SettingsScreen(
            settings = UserSettings(),
            status = ReliabilityStatus.AllOk,
            alerting = false,
            onBack = {},
            actions = SettingsActions(),
            catalog = CatalogSettingsState(lastRefreshAtMillis = LAST_REFRESH_MS),
        )
    }

    /** The „Knapp ab" stepper at 15 g. */
    @Test
    fun settingsLowStockDialog() = capture("Settings_lowStock", clickText = "Knapp ab") {
        SettingsScreen(
            settings = UserSettings(),
            status = ReliabilityStatus.AllOk,
            alerting = false,
            onBack = {},
            actions = SettingsActions(),
        )
    }

    /** The daily low-stock reminder's rows: the switch on, „Uhrzeit · 18:00" (SET-28, SET-29, INV-11, D-098). */
    @Test
    fun settingsReminder() = capture("Settings_reminder", scrollToText = "Uhrzeit") {
        SettingsScreen(
            settings = UserSettings(),
            status = ReliabilityStatus.AllOk,
            alerting = false,
            onBack = {},
            actions = SettingsActions(),
            catalog = CatalogSettingsState(lastRefreshAtMillis = LAST_REFRESH_MS),
        )
    }

    /** Switched off: the time row is gone. */
    @Test
    fun settingsReminderOff() = capture("Settings_reminderOff", scrollToText = "Tägliche Vorrats-Erinnerung") {
        SettingsScreen(
            settings = UserSettings(lowStockReminder = false),
            status = ReliabilityStatus.AllOk,
            alerting = false,
            onBack = {},
            actions = SettingsActions(),
            catalog = CatalogSettingsState(lastRefreshAtMillis = LAST_REFRESH_MS),
        )
    }

    /** Material 3's 24 h time picker at 18:00. */
    @Test
    fun settingsReminderTimeDialog() = capture("Settings_reminderTime", clickText = "Uhrzeit") {
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
