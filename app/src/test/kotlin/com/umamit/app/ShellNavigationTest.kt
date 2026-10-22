package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
import com.umamit.feature.stats.R as StatsR

/** Back stacks, re-select and settings entry of the navigation shell (M1-05, 03 §2.2). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ShellNavigationTest {

    private companion object {
        /** Short enough that every placeholder screen scrolls. */
        const val SHORT_WINDOW = "w411dp-h360dp-xhdpi"
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val brew = MutableStateFlow<ActiveBrew?>(null)

    private fun str(@StringRes id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<ActiveBrew?> = brew
        override val progress: () -> Float = { 0.5f }
    }

    private fun start(reduceMotion: Boolean = true) {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = reduceMotion) {
                UmamitApp(activeBrewSource = source)
            }
        }
    }

    /** The scroll position of the screen on top, in pixels (the placeholders scroll in a short window). */
    private fun scrollPosition(): Float {
        compose.waitForIdle()
        return compose.onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
    }

    private fun scrollDown() {
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertThat(scrollPosition()).isGreaterThan(0f)
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
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
    @Config(qualifiers = SHORT_WINDOW)
    fun reselectScrollsFirstAndPopsToRootOnTheSecondTap() {
        start()
        openSettingsFromStart()
        scrollDown()

        tab(Tab.Start).performClick()
        assertThat(scrollPosition()).isEqualTo(0f)
        compose.onNodeWithText(str(SettingsR.string.settings_empty_title)).assertExists()

        tab(Tab.Start).performClick()
        // Start's empty state sits below the quick timer, under the fold of the short window.
        compose.onNodeWithText(str(HomeR.string.home_empty_title)).assertExists()
        compose.onNodeWithText(str(SettingsR.string.settings_empty_title)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = SHORT_WINDOW)
    fun reselectOnARootScreenScrollsEveryTime() {
        start()
        repeat(2) {
            scrollDown()
            tab(Tab.Start).performClick()
            assertThat(scrollPosition()).isEqualTo(0f)
        }
        compose.onNodeWithText(str(HomeR.string.home_empty_title)).assertExists()
    }

    @Test
    @Config(qualifiers = SHORT_WINDOW)
    fun returningFromAPushedScreenDoesNotReplayItsScroll() {
        start()
        tab(Tab.Journal).performClick()
        scrollDown()
        val journalPosition = scrollPosition()
        compose.onNodeWithContentDescription(str(DesignSystemR.string.ds_more_options)).performClick()
        compose.onNodeWithText(str(DesignSystemR.string.ds_settings)).performClick()
        // Settings scrolls to the top; the Journal underneath did not ask for it.
        tab(Tab.Journal).performClick()

        pressBack()

        compose.onNodeWithText(str(JournalR.string.journal_empty_title)).assertExists()
        assertThat(scrollPosition()).isEqualTo(journalPosition)
    }

    @Test
    @Config(qualifiers = SHORT_WINDOW)
    fun aScreenPushedAgainScrollsBeforeItPops() {
        start()
        openSettingsFromStart()
        tab(Tab.Start).performClick()
        pressBack()

        openSettingsFromStart()
        scrollDown()
        tab(Tab.Start).performClick()

        compose.onNodeWithText(str(SettingsR.string.settings_empty_title)).assertExists()
        assertThat(scrollPosition()).isEqualTo(0f)
    }

    @Test
    @Config(qualifiers = SHORT_WINDOW)
    fun reselectStillScrollsAfterAConfigurationChange() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) { UmamitApp(activeBrewSource = source) }
        }
        repeat(3) { tab(Tab.Start).performClick() }

        restoration.emulateSavedInstanceStateRestore()
        scrollDown()
        tab(Tab.Start).performClick()

        assertThat(scrollPosition()).isEqualTo(0f)
    }

    @Test
    @Config(qualifiers = SHORT_WINDOW)
    fun eachTabKeepsItsScrollPosition() {
        start()
        tab(Tab.Journal).performClick()
        scrollDown()
        val journalPosition = scrollPosition()

        tab(Tab.Stats).performClick()
        compose.onNodeWithText(str(StatsR.string.stats_empty_title)).assertExists()
        tab(Tab.Journal).performClick()

        compose.onNodeWithText(str(JournalR.string.journal_empty_title)).assertExists()
        assertThat(scrollPosition()).isEqualTo(journalPosition)
    }

    @Test
    fun backLeavesTheTabStackThenReturnsToStart() {
        start()
        tab(Tab.Journal).performClick()
        compose.onNodeWithContentDescription(str(DesignSystemR.string.ds_more_options)).performClick()
        compose.onNodeWithText(str(DesignSystemR.string.ds_settings)).performClick()
        compose.onNodeWithText(str(SettingsR.string.settings_empty_title)).assertIsDisplayed()

        pressBack()
        compose.onNodeWithText(str(JournalR.string.journal_empty_title)).assertIsDisplayed()

        pressBack()
        compose.onNodeWithText(str(HomeR.string.home_empty_title)).assertIsDisplayed()
        tab(Tab.Start).assertIsSelected()
    }

    @Test
    fun tabSwitchSettlesWithMotion() {
        start(reduceMotion = false)
        tab(Tab.Journal).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(str(JournalR.string.journal_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(str(HomeR.string.home_empty_title)).assertDoesNotExist()
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
