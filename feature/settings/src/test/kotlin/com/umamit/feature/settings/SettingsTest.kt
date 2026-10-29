package com.umamit.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        var stops = 0

        override fun play(toneKey: String) {
            played += toneKey
        }

        override fun playPreAlert(preAlertKey: String) {
            preAlertsPlayed += preAlertKey
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
