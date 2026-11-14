package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.umamit.feature.collection.R as CollectionR

/**
 * Tabs the user left stay composed while hidden (A-10, NFR-PERF-12): no nodes, no back handling, their stack as it
 * was; a switch the app makes disposes the tab it leaves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class KeptTabsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var startDisposals = 0

    private fun str(@StringRes id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun start() = compose.setContent {
        UmamiTheme(darkTheme = false, reduceMotion = false) {
            UmamitApp(
                activeBrewSource = NoActiveBrewSource,
                journalSources = testJournalSources(),
                settings = testSettingsScreens(),
                startScreen = { tab ->
                    DisposableEffect(Unit) { onDispose { startDisposals++ } }
                    Column {
                        Text("START")
                        TextButton(onClick = tab.showJournal) { Text("ZUM JOURNAL") }
                    }
                },
                catalogScreen = { tab -> TextButton(onClick = { tab.openTea("k") }) { Text("OPEN-TEA") } },
                catalogScreens = CatalogStackScreens(tea = { Text("TEA-DETAIL") }),
            )
        }
    }

    private fun tab(tab: Tab) = compose.onNodeWithTag(tabTag(tab))

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `a hidden tab has no nodes and does not take back, and its stack stays`() {
        start()
        tab(Tab.Collection).performClick()
        compose.onNodeWithText(str(CollectionR.string.collection_segment_catalog)).performClick()
        compose.onNodeWithText("OPEN-TEA").performClick()
        compose.onNodeWithText("TEA-DETAIL").assertIsDisplayed()

        tab(Tab.Journal).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("TEA-DETAIL").assertDoesNotExist()

        // Back from the Journal's root returns to Start; the hidden Sammlung's detail is not popped.
        pressBack()
        tab(Tab.Start).assertIsSelected()
        compose.onNodeWithText("START").assertIsDisplayed()
        tab(Tab.Collection).performClick()
        compose.onNodeWithText("TEA-DETAIL").assertIsDisplayed()
    }

    @Test
    fun `the tab bar keeps the tab it leaves, a switch the app makes disposes it`() {
        start()
        compose.onNodeWithText("START").assertIsDisplayed()

        tab(Tab.Journal).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("START").assertDoesNotExist()
        assertThat(startDisposals).isEqualTo(0)

        tab(Tab.Start).performClick()
        compose.onNodeWithText("ZUM JOURNAL").performClick()
        compose.waitForIdle()
        assertThat(startDisposals).isEqualTo(1)
    }
}
