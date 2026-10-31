package com.umamit.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.AlertLength
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCatalogRefreshRunner
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.timer.alert.AlertTester
import com.umamit.core.timer.alert.TonePreview
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The timer settings (SCR-25): every M1 row persists in the settings (SET-09…11, 14, 17, 25), the
 * *Timer-Zuverlässigkeit* card follows the checks (SET-24), *Alarm testen* plays through the
 * player (SET-31), no dialog stays over an alert (rule 8), and *Katalog* shows the last refresh
 * with *Jetzt aktualisieren* (SET-23).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private class FakeAlertTester : AlertTester {
        var tests = 0
        var stops = 0

        override fun testAlert() {
            tests++
        }

        override fun stopTestAlert() {
            stops++
        }
    }

    private class FakeTonePreview : TonePreview {
        val played = mutableListOf<String>()
        val preAlertsPlayed = mutableListOf<String>()

        /** The *Lautstärke* of every preview, in order (SET-27). */
        val volumes = mutableListOf<Int>()

        /** *Bei „Nicht stören" klingeln* of every preview, in order (SET-32). */
        val ringUnderDnd = mutableListOf<Boolean>()
        var stops = 0

        override fun play(toneKey: String, volumePercent: Int, ringUnderDnd: Boolean) {
            played += toneKey
            volumes += volumePercent
            this.ringUnderDnd += ringUnderDnd
        }

        override fun playPreAlert(preAlertKey: String, volumePercent: Int, ringUnderDnd: Boolean) {
            preAlertsPlayed += preAlertKey
            volumes += volumePercent
            this.ringUnderDnd += ringUnderDnd
        }

        override fun stop() {
            stops++
        }
    }

    private val preview = FakeTonePreview()
    private val settings = FakeSettingsRepository()
    private val tester = FakeAlertTester()
    private val reliability = FakeTimerReliability()
    private var alertKey by mutableStateOf<String?>(null)
    private var aboutOpened = 0
    private var visible by mutableStateOf(true)
    private val catalog = FakeCatalogRepository()
    private val catalogRefresh = FakeCatalogRefreshRunner()

    private fun show(): SettingsViewModel {
        val viewModel = SettingsViewModel(settings, tester, reliability, preview)
        val catalogViewModel = CatalogSettingsViewModel(catalog, catalogRefresh)
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                if (visible) {
                    SettingsRoute(
                        onBack = {},
                        onOpenAbout = { aboutOpened++ },
                        alertKey = alertKey,
                        viewModel = viewModel,
                        catalogViewModel = catalogViewModel,
                    )
                }
            }
        }
        return viewModel
    }

    private fun stored(): UserSettings = runBlocking { settings.settings.first() }

    private fun clickRow(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()

    /** A tap on [hour] on the time picker's 24 h dial (its items carry the hour as content description). */
    private fun pickHour(hour: Int) =
        compose.onNode(hasContentDescription("$hour ", substring = true) and hasClickAction()).performClick()

    @Test
    fun `the switches persist vibration, the screen kept on and the dimming`() {
        show()

        clickRow("Vibration")
        clickRow("Bildschirm beim Brühen anlassen")
        clickRow("Beim Ziehen abdunkeln")
        compose.waitForIdle()

        assertThat(stored()).isEqualTo(UserSettings(vibration = false, keepScreenOn = false, dimWhileSteeping = false))
    }

    @Test
    fun `Fotos laden in the Katalog section persists SET-22`() {
        show()
        assertThat(stored().catalogDownloadPhotos).isTrue()

        clickRow("Fotos laden")
        compose.waitForIdle()

        assertThat(stored()).isEqualTo(UserSettings(catalogDownloadPhotos = false))
        clickRow("Fotos laden")
        compose.waitForIdle()
        assertThat(stored().catalogDownloadPhotos).isTrue()
    }

    @Test
    fun `the tone picker shows the German names, stores the pick and plays it on Anhoeren`() {
        show()
        compose.onNodeWithText("Klangschale").assertIsDisplayed()

        clickRow("Alarmton")
        compose.onNodeWithText("Glockenspiel").performClick()
        compose.waitForIdle()
        assertThat(stored().alertSound).isEqualTo("glockenspiel")
        compose.onNodeWithText("Anhören").performClick()
        compose.onNodeWithText("Fertig").performClick()

        assertThat(preview.played).containsExactly("glockenspiel")
        assertThat(tester.tests).isEqualTo(0)
        compose.onNodeWithText("Glockenspiel").assertIsDisplayed()
    }

    @Test
    fun `the Vorwarnton picker offers the five sounds by name, stores the pick and plays it on Anhoeren`() {
        runBlocking { settings.update { it.copy(preAlertSound = "zimbel") } }
        show()
        compose.onNodeWithText("Vorwarnton").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Zimbel").assertIsDisplayed()

        clickRow("Vorwarnton")
        listOf("Glöckchen", "Klangstab", "Tiefe Schale", "Ting (leise)").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNode(hasText("Zimbel") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Tiefe Schale").performClick()
        compose.waitForIdle()
        assertThat(stored().preAlertSound).isEqualTo("tiefe_schale")
        compose.onNode(hasText("Tiefe Schale") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Anhören").performClick()
        compose.onNodeWithText("Fertig").performClick()
        compose.waitForIdle()

        assertThat(preview.preAlertsPlayed).containsExactly("tiefe_schale")
        assertThat(preview.played).isEmpty()
        assertThat(preview.stops).isAtLeast(1)
        assertThat(tester.tests).isEqualTo(0)
        compose.onNodeWithText("Tiefe Schale").assertIsDisplayed()
        // The alert tone stays as it was (SET-09).
        assertThat(stored().alertSound).isEqualTo(UserSettings.DEFAULT_ALERT_SOUND)
    }

    @Test
    fun `Ting (leise) can be picked again`() {
        show()

        clickRow("Vorwarnton")
        compose.onNodeWithText("Ting (leise)").performClick()
        compose.onNodeWithText("Fertig").performClick()
        compose.waitForIdle()

        assertThat(stored().preAlertSound).isEqualTo("ting")
        compose.onNodeWithText("Ting (leise)").assertIsDisplayed()
    }

    @Test
    fun `an alert closes the Vorwarnton picker and stops its preview`() {
        show()
        clickRow("Vorwarnton")
        compose.onNodeWithText("Anhören").performClick()
        val stopsBefore = preview.stops

        alertKey = "session-1:0"
        compose.waitForIdle()

        compose.onNodeWithText("Anhören").assertDoesNotExist()
        assertThat(preview.stops).isGreaterThan(stopsBefore)
        clickRow("Vorwarnton")
        compose.onNodeWithText("Ting (leise)").assertDoesNotExist()
    }

    @Test
    fun `the pre-alert and the alert length persist their choice`() {
        show()

        clickRow("Vorwarnung")
        compose.onNodeWithText("20 s vorher").performClick()
        compose.onNodeWithText("Fertig").performClick()
        clickRow("Alarmdauer")
        compose.onNodeWithText("Lang").performClick()
        compose.onNodeWithText("Fertig").performClick()
        compose.waitForIdle()

        assertThat(stored().preAlertSeconds).isEqualTo(20)
        assertThat(stored().alertLength).isEqualTo(AlertLength.LONG)
        compose.onNodeWithText("20 s vorher").assertIsDisplayed()
    }

    @Test
    fun `Knapp ab in Bruehen und Vorrat reads 15 g and steps by 5 g (SET-16)`() {
        show()
        compose.onNodeWithText("15 g").performScrollTo().assertIsDisplayed()

        clickRow("Knapp ab")
        compose.onNodeWithContentDescription("5 g mehr").performClick()
        compose.waitForIdle()
        assertThat(stored().lowStockGrams).isEqualTo(20.0)
        compose.onNodeWithContentDescription("5 g weniger").performClick()
        compose.onNodeWithContentDescription("5 g weniger").performClick()
        compose.waitForIdle()
        assertThat(stored().lowStockGrams).isEqualTo(10.0)
        compose.onNodeWithText("Fertig").performClick()

        compose.onNodeWithText("10 g").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the daily reminder is on at 18 00 and switching it off hides its time (SET-28, SET-29)`() {
        show()
        compose.onNode(hasText("Tägliche Vorrats-Erinnerung") and isToggleable()).performScrollTo().assertIsOn()
        compose.onNodeWithText("Listet jeden Tag alle knappen und leeren Tees.").assertIsDisplayed()
        compose.onNodeWithText("Uhrzeit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("18:00").assertIsDisplayed()

        clickRow("Tägliche Vorrats-Erinnerung")
        compose.waitForIdle()

        assertThat(stored()).isEqualTo(UserSettings(lowStockReminder = false))
        compose.onNode(hasText("Tägliche Vorrats-Erinnerung") and isToggleable()).assertIsOff()
        compose.onNodeWithText("Uhrzeit").assertDoesNotExist()

        clickRow("Tägliche Vorrats-Erinnerung")
        compose.waitForIdle()
        assertThat(stored().lowStockReminder).isTrue()
        compose.onNodeWithText("18:00").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Uhrzeit opens the 24 h time picker and stores the time picked`() {
        show()

        clickRow("Uhrzeit")
        pickHour(21)
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()

        assertThat(stored().lowStockReminderTime).isEqualTo(21 * 60)
        compose.onNodeWithText("21:00").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Abbrechen in the time picker keeps the time`() {
        show()

        clickRow("Uhrzeit")
        pickHour(21)
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()

        assertThat(stored().lowStockReminderTime).isEqualTo(18 * 60)
    }

    @Test
    fun `the reminder time reads in the German 24 h format`() {
        runBlocking { settings.update { it.copy(lowStockReminderTime = 7 * 60 + 5) } }
        show()

        compose.onNodeWithText("07:05").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `an alert closes the time picker`() {
        show()
        clickRow("Uhrzeit")
        compose.onNodeWithText("OK").assertIsDisplayed()

        alertKey = "session-1:0"
        compose.waitForIdle()

        compose.onNodeWithText("OK").assertDoesNotExist()
    }

    @Test
    fun `Knapp ab stops at 0 and 500 g`() {
        runBlocking { settings.update { it.copy(lowStockGrams = 500.0) } }
        show()

        clickRow("Knapp ab")
        compose.onNodeWithContentDescription("5 g mehr").assertIsNotEnabled()
        compose.onNodeWithText("Fertig").performClick()
        runBlocking { settings.update { it.copy(lowStockGrams = 0.0) } }
        clickRow("Knapp ab")
        compose.onNodeWithContentDescription("5 g weniger").assertIsNotEnabled()
        compose.onNodeWithContentDescription("5 g mehr").assertIsEnabled()
    }

    @Test
    fun `Lautstaerke below Vorwarnton reads 100 percent with its hint and stores a new value in steps of 10`() {
        // SET-27 (D-098): 10–100 % in steps of 10, default 100 %.
        show()
        compose.onNodeWithTag(SettingsTags.VOLUME).performScrollTo()
        compose.onNodeWithText("Lautstärke").assertIsDisplayed()
        compose.onNodeWithText("100 %").assertIsDisplayed()
        compose.onNodeWithText("Wirkt auf die Alarmlautstärke des Handys").assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.VOLUME)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "100 %"))

        compose.onNodeWithTag(SettingsTags.VOLUME).performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        compose.waitForIdle()

        assertThat(stored().alertVolumePercent).isEqualTo(30)
        compose.onNodeWithText("30 %").assertIsDisplayed()
    }

    @Test
    fun `Lautstaerke stops at 10 percent, so the app never silences a cue`() {
        runBlocking { settings.update { it.copy(alertVolumePercent = 20) } }
        show()
        compose.onNodeWithTag(SettingsTags.VOLUME).performScrollTo()

        compose.onNodeWithTag(SettingsTags.VOLUME).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.waitForIdle()

        assertThat(stored().alertVolumePercent).isEqualTo(10)
        compose.onNodeWithTag(SettingsTags.VOLUME)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "10 %"))
        val range = compose.onNodeWithTag(SettingsTags.VOLUME).fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
        assertThat(range.range).isEqualTo(10f..100f)
        assertThat(range.steps).isEqualTo(8)
    }

    @Test
    fun `Anhoeren plays at the stored Lautstaerke`() {
        runBlocking { settings.update { it.copy(alertVolumePercent = 40) } }
        show()

        clickRow("Alarmton")
        compose.onNodeWithText("Anhören").performClick()
        compose.onNodeWithText("Fertig").performClick()
        clickRow("Vorwarnton")
        compose.onNodeWithText("Anhören").performClick()

        assertThat(preview.volumes).containsExactly(40, 40)
    }

    @Test
    fun `Bei Nicht stoeren klingeln is on with its detail and switching it off persists`() {
        // SET-32 (D-099): default on; off, the phone's reported ringer mode decides under Do Not Disturb.
        show()
        val row = hasText("Bei „Nicht\u00A0stören“ klingeln") and isToggleable()
        compose.onNode(row).performScrollTo().assertIsOn()
        compose.onNodeWithText("Vorwarnung und Alarm klingen, wenn „Nicht\u00A0stören“ Alarme erlaubt")
            .assertIsDisplayed()

        compose.onNode(row).performClick()
        compose.waitForIdle()

        assertThat(stored()).isEqualTo(UserSettings(ringUnderDnd = false))
        compose.onNode(row).assertIsOff()
        compose.onNode(row).performClick()
        compose.waitForIdle()
        assertThat(stored().ringUnderDnd).isTrue()
    }

    @Test
    fun `Anhoeren follows Bei Nicht stoeren klingeln`() {
        // SET-32 (D-099): the preview sounds where the cues would.
        runBlocking { settings.update { it.copy(ringUnderDnd = false) } }
        show()

        clickRow("Alarmton")
        compose.onNodeWithText("Anhören").performClick()
        compose.onNodeWithText("Fertig").performClick()
        runBlocking { settings.update { it.copy(ringUnderDnd = true) } }
        compose.waitForIdle()
        clickRow("Vorwarnton")
        compose.onNodeWithText("Anhören").performClick()

        assertThat(preview.ringUnderDnd).containsExactly(false, true).inOrder()
    }

    @Test
    fun `the alarm volume at 0 shows its row with Ist aus and Beheben, which opens the sound settings`() {
        // TMR-31, SET-24 (D-098).
        reliability.set(ReliabilityCheck.AlarmVolume, CheckState.Missing)
        val viewModel = show()

        compose.onNodeWithText("Alarmlautstärke").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ist aus").assertIsDisplayed()
        compose.onNodeWithText("Beheben").performClick()
        assertThat(reliability.opened).containsExactly(ReliabilityCheck.AlarmVolume)

        // Raised in the sound settings: back on the screen the row reads OK again.
        reliability.grantAll()
        viewModel.refresh()
        compose.waitForIdle()
        compose.onNodeWithText("Ist aus").assertDoesNotExist()
        compose.onNodeWithText("Beheben").assertDoesNotExist()
        compose.onNodeWithText("Alarmlautstärke").assertIsDisplayed()
    }

    @Test
    fun `Alarm testen plays the tick and the alert through the player, and leaving stops the test`() {
        show()

        clickRow("Alarm testen")
        assertThat(tester.tests).isEqualTo(1)

        visible = false
        compose.waitForIdle()
        assertThat(tester.stops).isAtLeast(1)
    }

    @Test
    fun `the reliability card shows each check, and Beheben opens the missing one`() {
        reliability.set(ReliabilityCheck.ExactAlarms, CheckState.Missing)
        val viewModel = show()

        compose.onNodeWithText("Timer-Zuverlässigkeit").assertIsDisplayed()
        compose.onNodeWithText("Beheben").performClick()
        assertThat(reliability.opened).containsExactly(ReliabilityCheck.ExactAlarms)

        reliability.grantAll()
        viewModel.refresh()
        compose.waitForIdle()
        compose.onNodeWithText("Beheben").assertDoesNotExist()
        compose.onNodeWithText("Alles bereit", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an alert closes an open dialog at once, and none opens while it plays`() {
        show()
        clickRow("Alarmton")
        compose.onNodeWithText("Anhören").assertIsDisplayed()

        alertKey = "session-1:0"
        compose.waitForIdle()
        compose.onNodeWithText("Anhören").assertDoesNotExist()

        clickRow("Alarmdauer")
        compose.onNodeWithText("Bis zu 15 Sekunden").assertDoesNotExist()
        alertKey = null
        compose.waitForIdle()
        compose.onNodeWithText("Anhören").assertDoesNotExist()
        compose.onNodeWithText("Bis zu 15 Sekunden").assertDoesNotExist()
    }

    @Test
    fun `a failed write never crashes, says so, and the next write clears the note`() {
        show()
        settings.failUpdates = true

        clickRow("Vibration")
        compose.waitForIdle()
        compose.onNodeWithText("Konnte nicht gespeichert werden", substring = true).assertIsDisplayed()
        assertThat(stored().vibration).isTrue()

        settings.failUpdates = false
        clickRow("Vibration")
        compose.waitForIdle()
        compose.onNodeWithText("Konnte nicht gespeichert werden", substring = true).assertDoesNotExist()
        assertThat(stored().vibration).isFalse()
    }

    @Test
    fun `each switch tells TalkBack whether it is on`() {
        show()

        compose.onNode(hasText("Vibration") and isToggleable(), useUnmergedTree = false).assertIsOn()
        clickRow("Vibration")
        compose.waitForIdle()
        compose.onNode(hasText("Vibration") and isToggleable(), useUnmergedTree = false).assertIsOff()
    }

    @Test
    fun `the tone preview stops when the picker closes, and Stopp stops a test`() {
        show()
        clickRow("Alarmton")
        compose.onNodeWithText("Anhören").performClick()
        assertThat(preview.played).containsExactly("klangschale")

        compose.onNodeWithText("Fertig").performClick()
        compose.waitForIdle()
        assertThat(preview.stops).isAtLeast(1)

        clickRow("Alarm testen")
        clickRow("Stopp")
        assertThat(tester.stops).isAtLeast(1)
    }

    @Test
    fun `Ueber opens the about page`() {
        show()

        compose.onNodeWithTag(SettingsTags.ABOUT).performScrollTo().performClick()

        assertThat(aboutOpened).isEqualTo(1)
    }

    @Test
    fun `Katalog shows the last refresh, and Jetzt aktualisieren starts one`() {
        catalog.catalog.value = CatalogOverview(emptyList(), lastRefreshAtMillis = FakeClock.DEFAULT_WALL_MS)
        show()

        compose.onNodeWithText("Letzter Abruf").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("22. Okt. 2026").assertIsDisplayed()
        clickRow("Jetzt aktualisieren")

        assertThat(catalogRefresh.starts).isEqualTo(1)
    }

    @Test
    fun `before the first refresh Katalog says Noch nie`() {
        show()

        compose.onNodeWithText("Letzter Abruf").performScrollTo()
        compose.onNodeWithText("Noch nie").assertIsDisplayed()
        compose.onNodeWithText("Jetzt aktualisieren").assertIsEnabled()
    }

    @Test
    fun `while a refresh runs Katalog says so and starts no second one`() {
        catalog.catalog.value = CatalogOverview(emptyList(), lastRefreshAtMillis = FakeClock.DEFAULT_WALL_MS)
        catalogRefresh.state.value = CatalogRefreshState.Running(CatalogRefreshProgress.STARTING)
        show()

        compose.onNodeWithText("Letzter Abruf").performScrollTo()
        compose.onNodeWithText("Wird gerade aktualisiert …").assertIsDisplayed()
        compose.onNodeWithText("Jetzt aktualisieren").assertIsNotEnabled()
    }
}
