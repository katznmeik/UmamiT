package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.onboarding.howto.HowToTags
import com.umamit.feature.onboarding.howto.HowToTopic
import com.umamit.feature.settings.SettingsTags
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.umamit.core.designsystem.R as DesignSystemR

/**
 * The entry points of the Anleitungen (SCR-36 „Entry points", SET-36, ONB-08 AC4): *Einstellungen* › *Hilfe* ›
 * *Anleitungen* and ⋮ *Hilfe* on *Meine Tees*, *Katalog*, the tea, *Rezept bearbeiten*, the Journal, the session page
 * and *Daten & Sicherung*. Each opens the right target alone above the shell, and back returns to where it was opened.
 * The screens of the other features are stand-ins that call the host's `onOpenHelp`; each feature's own tests cover
 * its ⋮ item.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class HowToNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int) = compose.activity.getString(id)

    private fun show() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(),
                    alertKey = MutableStateFlow(null),
                    onboardingCompleted = MutableStateFlow(true),
                    timerScreen = { _, _, _, _ -> Text("TIMER") },
                    startScreen = { Text("START") },
                    collectionScreens = CollectionStackScreens(
                        tea = { host ->
                            Column {
                                Text(TEA)
                                TextButton(onClick = host.onOpenHelp) { Text(TEA_HELP) }
                                TextButton(onClick = host.onAddRecipe) { Text(ADD_RECIPE) }
                                TextButton(onClick = { host.onOpenSession?.invoke("s1") }) { Text(OPEN_SESSION) }
                            }
                        },
                        recipeEditor = { host ->
                            Column {
                                Text(EDITOR)
                                TextButton(onClick = host.onOpenHelp) { Text(EDITOR_HELP) }
                            }
                        },
                        myTeas = { tab ->
                            Column(tab.modifier) { TextButton(onClick = { tab.openTea("t1") }) { Text(ROW) } }
                        },
                    ),
                    journalScreens = JournalStackScreens(
                        session = { host ->
                            Column {
                                Text(SESSION)
                                TextButton(onClick = host.onOpenHelp) { Text(SESSION_HELP) }
                            }
                        },
                    ),
                    settings = SettingsScreens(
                        screen = testSettingsScreens().screen,
                        dataBackup = { host ->
                            Column {
                                Text(BACKUP)
                                TextButton(onClick = host.onOpenHelp) { Text(BACKUP_HELP) }
                            }
                        },
                    ),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun tab(tab: Tab) {
        compose.onNodeWithTag(tabTag(tab)).performClick()
        compose.waitForIdle()
    }

    private fun overflow(item: String) {
        compose.onNodeWithContentDescription(str(DesignSystemR.string.ds_more_options)).performClick()
        compose.onNodeWithText(item).performClick()
        compose.waitForIdle()
    }

    private fun hilfe() = overflow(str(DesignSystemR.string.ds_help))

    private fun openSettings() {
        tab(Tab.Journal)
        overflow(str(DesignSystemR.string.ds_settings))
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun assertList() {
        compose.onNodeWithTag(HowToTags.LIST).assertIsDisplayed()
        // Alone: no navigation bar over it.
        compose.onNodeWithTag(tabTag(Tab.Collection)).assertDoesNotExist()
    }

    private fun assertTopic(topic: HowToTopic) {
        compose.onNodeWithTag(HowToTags.TOPIC).assertIsDisplayed()
        compose.onNodeWithText(str(topic.intro)).assertIsDisplayed()
        compose.onNodeWithTag(tabTag(Tab.Collection)).assertDoesNotExist()
    }

    private fun assertClosed() {
        compose.onNodeWithTag(HowToTags.LIST).assertDoesNotExist()
        compose.onNodeWithTag(HowToTags.TOPIC).assertDoesNotExist()
    }

    private fun openTea() {
        tab(Tab.Collection)
        tap(ROW)
        compose.onNodeWithText(TEA).assertIsDisplayed()
    }

    @Test
    fun `Anleitungen in the settings opens the list, a row its topic, and back returns step by step`() {
        show()
        openSettings()
        compose.onNodeWithText("HILFE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.HOWTOS).performScrollTo().performClick()
        compose.waitForIdle()
        assertList()

        compose.onNodeWithTag(HowToTags.row(HowToTopic.Brewing)).performClick()
        compose.waitForIdle()
        assertTopic(HowToTopic.Brewing)

        back()
        assertList()
        back()
        assertClosed()
        compose.onNodeWithTag(SettingsTags.HOWTOS).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Meine Tees Hilfe opens the list, and back returns to Sammlung`() {
        show()
        tab(Tab.Collection)

        hilfe()
        assertList()

        back()
        assertClosed()
        compose.onNodeWithTag(tabTag(Tab.Collection)).assertIsSelected()
        compose.onNodeWithText(ROW).assertIsDisplayed()
    }

    @Test
    fun `Katalog Hilfe opens Tee per Shop-Link hinzufuegen, and back returns to the Katalog segment`() {
        show()
        tab(Tab.Collection)
        tap(str(com.umamit.feature.collection.R.string.collection_segment_catalog))

        hilfe()
        assertTopic(HowToTopic.ShopLink)

        back()
        assertClosed()
        compose.onNodeWithTag(tabTag(Tab.Collection)).assertIsSelected()
        // Still on the Katalog segment: *Meine Tees*' stand-in row is not shown.
        compose.onNodeWithText(ROW).assertDoesNotExist()
    }

    @Test
    fun `the tea's Hilfe opens Vorrat und Kosten, and back returns to the tea`() {
        show()
        openTea()

        tap(TEA_HELP)
        assertTopic(HowToTopic.StockAndCost)

        back()
        assertClosed()
        compose.onNodeWithText(TEA).assertIsDisplayed()
    }

    @Test
    fun `Rezept bearbeiten Hilfe opens Rezepte, and back returns to the editor`() {
        show()
        openTea()
        tap(ADD_RECIPE)
        compose.onNodeWithText(EDITOR).assertIsDisplayed()

        tap(EDITOR_HELP)
        assertTopic(HowToTopic.Recipes)

        back()
        assertClosed()
        compose.onNodeWithText(EDITOR).assertIsDisplayed()
    }

    @Test
    fun `Journal Hilfe opens Journal und Geschmack, and back returns to the Journal`() {
        show()
        tab(Tab.Journal)

        hilfe()
        assertTopic(HowToTopic.JournalAndTaste)

        back()
        assertClosed()
        compose.onNodeWithTag(tabTag(Tab.Journal)).assertIsSelected()
    }

    @Test
    fun `the session page's Hilfe opens Journal und Geschmack, and back returns to the page`() {
        show()
        openTea()
        tap(OPEN_SESSION)
        compose.onNodeWithText(SESSION).assertIsDisplayed()

        tap(SESSION_HELP)
        assertTopic(HowToTopic.JournalAndTaste)

        back()
        assertClosed()
        compose.onNodeWithText(SESSION).assertIsDisplayed()
    }

    @Test
    fun `Daten und Sicherung Hilfe opens Daten und Sicherung, and back returns to it`() {
        show()
        openSettings()
        compose.onNodeWithTag(SettingsTags.DATA_BACKUP).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(BACKUP).assertIsDisplayed()

        tap(BACKUP_HELP)
        assertTopic(HowToTopic.DataAndBackup)

        back()
        assertClosed()
        compose.onNodeWithText(BACKUP).assertIsDisplayed()
    }

    private companion object {
        const val ROW = "Asahi"
        const val TEA = "TEE-DETAIL"
        const val TEA_HELP = "TEE-HILFE"
        const val ADD_RECIPE = "REZEPT-NEU"
        const val OPEN_SESSION = "SESSION-OEFFNEN"
        const val EDITOR = "REZEPT-EDITOR"
        const val EDITOR_HELP = "EDITOR-HILFE"
        const val SESSION = "SESSION-SEITE"
        const val SESSION_HELP = "SESSION-HILFE"
        const val BACKUP = "SICHERUNG"
        const val BACKUP_HELP = "SICHERUNG-HILFE"
    }
}
