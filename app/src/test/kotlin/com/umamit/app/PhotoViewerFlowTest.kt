package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.ui.PHOTO_VIEWER_COUNTER_TAG
import com.umamit.core.ui.PHOTO_VIEWER_PAGER_TAG
import com.umamit.core.ui.PhotoHero
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The full-screen photo viewer (SCR-28, TEA-05, D-114 Q9) through the real root: a tap on a hero opens it above the
 * shell on the photo shown; back returns and the hero shows the photo last viewed; a hero without photos opens
 * nothing; during an alert the alerting timer opens instead (rule 8). Placeholder painters only (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class PhotoViewerFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection, FakeSettingsRepository())
    private val alertKey = MutableStateFlow<String?>(null)

    private fun show(photos: List<String>) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(sessions, collection.teas),
                    alertKey = alertKey,
                    collectionScreens = CollectionStackScreens(
                        myTeas = { tab ->
                            Box(tab.modifier) { TextButton(onClick = { tab.openTea("t") }) { Text(ROW) } }
                        },
                        tea = { Hero(photos) },
                    ),
                    timerScreen = { _, _, _, _ -> Text(TIMER) },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ROW).performClick()
        compose.waitForIdle()
    }

    /** A stand-in for the tea detail: the shared hero over [photos], keyed by their name. */
    @Composable
    private fun Hero(photos: List<String>) {
        Column {
            PhotoHero(
                photos = photos,
                key = { it },
                path = { it },
                description = { item, _, _ -> "Foto $item" },
                pagerDescription = "Fotos",
                pagerTag = HERO,
                placeholderColor = Color.Gray,
                photo = { ColorPainter(Color.DarkGray) },
                controls = {},
            )
            Text(HERO_TEXT)
        }
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun counter() = compose.onNodeWithTag(PHOTO_VIEWER_COUNTER_TAG, useUnmergedTree = true)

    @Test
    fun `a tap on the hero opens the viewer on the photo shown, back returns`() {
        show(listOf("a", "b", "c"))

        compose.onNodeWithTag(HERO).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Foto b").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(PHOTO_VIEWER_PAGER_TAG).assertIsDisplayed()
        counter().assertTextEquals("2 / 3")

        back()
        compose.onNodeWithTag(PHOTO_VIEWER_PAGER_TAG).assertDoesNotExist()
        compose.onNodeWithText(HERO_TEXT).assertIsDisplayed()
    }

    @Test
    fun `the hero shows the photo last viewed when the viewer closes`() {
        show(listOf("a", "b", "c"))

        compose.onNodeWithContentDescription("Foto a").performClick()
        compose.waitForIdle()
        counter().assertTextEquals("1 / 3")
        compose.onNodeWithTag(PHOTO_VIEWER_PAGER_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        counter().assertTextEquals("2 / 3")

        back()

        // The hero's pager moved with it: the second photo is the one on screen.
        compose.onNodeWithContentDescription("Foto b").assertIsDisplayed()
    }

    @Test
    fun `a hero without photos opens nothing`() {
        show(emptyList())

        compose.onNodeWithText(HERO_TEXT).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(PHOTO_VIEWER_PAGER_TAG).assertDoesNotExist()
    }

    @Test
    fun `during an alert a tap opens the alerting timer, not the viewer`() {
        show(listOf("a", "b"))
        alertKey.value = "brew-1"
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Foto a").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(PHOTO_VIEWER_PAGER_TAG).assertDoesNotExist()
        compose.onNodeWithText(TIMER).assertIsDisplayed()
    }

    @Test
    fun `an alert that starts while the viewer is open gives the screen to the timer`() {
        show(listOf("a", "b"))
        compose.onNodeWithContentDescription("Foto a").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PHOTO_VIEWER_PAGER_TAG).assertIsDisplayed()

        alertKey.value = "brew-1"
        compose.waitForIdle()

        compose.onNodeWithTag(PHOTO_VIEWER_PAGER_TAG).assertDoesNotExist()
        compose.onNodeWithText(TIMER).assertIsDisplayed()
    }

    private companion object {
        const val ROW = "Tee öffnen"
        const val HERO = "hero_pager"
        const val HERO_TEXT = "Unter dem Hero"
        const val TIMER = "Timer (Attrappe)"
    }
}
