package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.umamit.core.designsystem.R as DesignSystemR

/**
 * The tour from *Einstellungen* › *Hilfe* › *Einführung* (SET-36, ONB-07): it opens alone above the shell, and
 * *Fertig*, *Überspringen* and back each return to the settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TourNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int) = compose.activity.getString(id)

    private fun openTourFromSettings() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(),
                    alertKey = MutableStateFlow(null),
                    onboardingCompleted = MutableStateFlow(true),
                    timerScreen = { _, _, _, _ -> Text("TIMER") },
                    startScreen = { Text("START") },
                    settings = testSettingsScreens(),
                )
            }
        }
        // The test's Start has no gear; the Journal tab's ⋮ opens the same settings.
        compose.onNodeWithText(str(R.string.tab_journal)).performClick()
        compose.onNodeWithContentDescription(str(DesignSystemR.string.ds_more_options)).performClick()
        compose.onNodeWithText(str(DesignSystemR.string.ds_settings)).performClick()
        compose.onNodeWithText("HILFE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Einführung").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun assertBackInSettings() {
        compose.onNodeWithText("Start und Timer").assertDoesNotExist()
        compose.onNodeWithText("Einführung").performScrollTo().assertIsDisplayed()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `Einfuehrung opens the tour alone, on its first page`() {
        openTourFromSettings()

        compose.onNodeWithText("Start und Timer").assertIsDisplayed()
        compose.onNodeWithText("Überspringen").assertIsDisplayed()
        // Alone: no navigation bar over it.
        compose.onNodeWithText(str(R.string.tab_journal)).assertDoesNotExist()
    }

    @Test
    fun `Fertig on the last page returns to the settings`() {
        openTourFromSettings()

        repeat(5) { compose.onNodeWithText("Weiter").performClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Sicherung und Rezepte teilen").assertIsDisplayed()
        compose.onNodeWithText("Fertig").performClick()
        compose.waitForIdle()

        assertBackInSettings()
    }

    @Test
    fun `Ueberspringen returns to the settings`() {
        openTourFromSettings()

        compose.onNodeWithText("Überspringen").performClick()
        compose.waitForIdle()

        assertBackInSettings()
    }

    @Test
    fun `back on the first page returns to the settings`() {
        openTourFromSettings()

        back()

        assertBackInSettings()
    }

    @Test
    fun `back on a later page goes to the page before, then leaves`() {
        openTourFromSettings()
        compose.onNodeWithText("Weiter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Zwei Timer und Schnelltimer").assertIsDisplayed()

        back()
        compose.onNodeWithText("Start und Timer").assertIsDisplayed()

        back()
        assertBackInSettings()
    }
}
