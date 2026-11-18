package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.settings.SettingsTags
import com.umamit.feature.settings.backup.ImportedBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.umamit.core.designsystem.R as DesignSystemR

/**
 * *Einstellungen › Daten & Sicherung* (SET-30, SCR-26) as a route of a tab's stack, and a finished import (BAK-02
 * AC6): Start at its root with the import's snackbar, from whichever tab the settings were opened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class DataBackupNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<ActiveBrew?> = MutableStateFlow(null)
        override val progress: () -> Float = { 0f }
    }

    private fun show() {
        val settings = SettingsScreens(
            screen = testSettingsScreens().screen,
            dataBackup = { host ->
                Button(onClick = { host.onImported(ImportedBackup(sessions = 142, teas = 23)) }) {
                    Text("IMPORT-FERTIG")
                }
            },
            importedSnackbar = { imported, _, _ ->
                if (imported != null) Text("EINGESPIELT ${imported.sessions}/${imported.teas}")
            },
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = source,
                    journalSources = testJournalSources(),
                    alertKey = MutableStateFlow(null),
                    timerScreen = { _, _, _, _ -> Text("TIMER") },
                    settings = settings,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun str(id: Int) = compose.activity.getString(id)

    private fun openDataBackup() {
        compose.onNodeWithTag(SettingsTags.DATA_BACKUP).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("IMPORT-FERTIG").assertIsDisplayed()
    }

    private fun importAndExpectStart() {
        compose.onNodeWithText("IMPORT-FERTIG").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(tabTag(Tab.Start)).assertIsSelected()
        compose.onNodeWithText("IMPORT-FERTIG").assertDoesNotExist()
        compose.onNodeWithTag(SettingsTags.DATA_BACKUP).assertDoesNotExist()
        compose.onNodeWithText("EINGESPIELT 142/23").assertIsDisplayed()
    }

    @Test
    fun anImportFromStartsSettingsReturnsToStartWithItsSnackbar() {
        show()
        compose.onNodeWithContentDescription(str(DesignSystemR.string.ds_settings)).performClick()
        compose.waitForIdle()
        openDataBackup()

        importAndExpectStart()
    }

    @Test
    fun anImportFromAnotherTabsSettingsTurnsToStart() {
        show()
        compose.onNodeWithTag(tabTag(Tab.Stats)).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(str(DesignSystemR.string.ds_more_options)).performClick()
        compose.onNodeWithText(str(DesignSystemR.string.ds_settings)).performClick()
        compose.waitForIdle()
        openDataBackup()

        importAndExpectStart()
        // The tab left closed its settings (03 §2.2): Statistik shows its own screen again.
        compose.onNodeWithTag(tabTag(Tab.Stats)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.DATA_BACKUP).assertDoesNotExist()
    }
}
