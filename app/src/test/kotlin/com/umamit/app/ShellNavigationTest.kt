package com.umamit.app

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.umamit.core.designsystem.R as DesignSystemR
import com.umamit.feature.collection.R as CollectionR
import com.umamit.feature.home.R as HomeR
import com.umamit.feature.journal.R as JournalR
import com.umamit.feature.settings.R as SettingsR

/** Back stacks, re-select and settings entry of the navigation shell (M1-05, 03 §2.2). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ShellNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val brew = MutableStateFlow<ActiveBrew?>(null)

    private fun str(@StringRes id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun start() {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                UmamitApp(
                    activeBrewSource = object : ActiveBrewSource {
                        override val activeBrew: StateFlow<ActiveBrew?> = brew
                        override val progress: () -> Float = { 0.5f }
                    },
                )
            }
        }
    }

    private fun tab(tab: Tab) = compose.onNodeWithTag(tabTag(tab))

    private fun openSettingsFromStart() =
        compose.onNodeWithContentDescription(str(DesignSystemR.string.ds_settings)).performClick()

    @Test
    fun opensOnStart() {
        start()
        compose.onNodeWithText(str(HomeR.string.home_empty_title)).assertIsDisplayed()
        tab(Tab.Start).assertIsSelected()
    }

    @Test
    fun settingsOpensFromStartGear() {
        start()
        openSettingsFromStart()
        compose.onNodeWithText(str(SettingsR.string.settings_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.probe_entry)).assertIsDisplayed()
    }

    @Test
    fun settingsOpensFromOtherTabsMenu() {
        start()
        tab(Tab.Journal).performClick()
        compose.onNodeWithText(str(JournalR.string.journal_empty_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(DesignSystemR.string.ds_more_options)).performClick()
        compose.onNodeWithText(str(DesignSystemR.string.ds_settings)).performClick()
        compose.onNodeWithText(str(SettingsR.string.settings_empty_title)).assertIsDisplayed()
    }

    @Test
    fun eachTabKeepsItsBackStack() {
        start()
        openSettingsFromStart()
        tab(Tab.Journal).performClick()
        compose.onNodeWithText(str(JournalR.string.journal_empty_title)).assertIsDisplayed()
        tab(Tab.Start).performClick()
        compose.onNodeWithText(str(SettingsR.string.settings_empty_title)).assertIsDisplayed()
    }

    @Test
    fun reselectPopsToRoot() {
        start()
        openSettingsFromStart()
        tab(Tab.Start).performClick()
        compose.onNodeWithText(str(HomeR.string.home_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(str(SettingsR.string.settings_empty_title)).assertDoesNotExist()
    }

    @Test
    fun collectionRemembersLastSegment() {
        start()
        tab(Tab.Collection).performClick()
        compose.onNodeWithText(str(CollectionR.string.collection_my_teas_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(str(CollectionR.string.collection_segment_catalog)).performClick()
        tab(Tab.Stats).performClick()
        tab(Tab.Collection).performClick()
        compose.onNodeWithText(str(CollectionR.string.collection_catalog_empty_title)).assertIsDisplayed()
    }

    @Test
    fun activeBrewBarFollowsTheSource() {
        start()
        compose.onNodeWithContentDescription("Gyokuro", substring = true).assertDoesNotExist()
        brew.value = ActiveBrew("Gyokuro", "Aufguss 1/3", 45, Color(0xFF8DA36B))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Gyokuro", substring = true).assertIsDisplayed()
    }
}
