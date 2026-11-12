package com.umamit.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The full-screen photo viewer (SCR-28, D-114 Q9) and the hero's way into it: the viewer opens on the photo it is
 * given, swipes to its neighbours and tells which one it shows; a tap on a hero opens it on the photo shown, a hero
 * without photos opens nothing, and a hero follows the photo the viewer ended on. Placeholder painters only (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class PhotoViewerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<Triple<String, List<String>, Int>>()
    private var viewed by mutableStateOf<Pair<String, Int>?>(null)

    private val access = object : PhotoViewerAccess {
        override fun open(owner: String, sources: List<String>, index: Int) {
            opened += Triple(owner, sources, index)
        }

        override fun viewed(owner: String): Int? = viewed?.takeIf { it.first == owner }?.second
        override fun report(owner: String, index: Int) = Unit
        override fun consume(owner: String) {
            viewed = null
        }
    }

    private fun counter() = compose.onNodeWithTag(PHOTO_VIEWER_COUNTER_TAG, useUnmergedTree = true)

    private fun showViewer(start: Int, onBack: () -> Unit = {}, onPage: (Int) -> Unit = {}) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                PhotoViewerScreen(
                    sources = listOf("a", "b", "c"),
                    startIndex = start,
                    onBack = onBack,
                    onPageChange = onPage,
                    photo = { ColorPainter(Color.DarkGray) },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun showHero(photos: List<String>) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalPhotoViewer provides access) {
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
                        Text("unten")
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the viewer opens on the given photo and counts it`() {
        showViewer(start = 1)

        counter().assertTextEquals("2 / 3")
        compose.onNodeWithContentDescription("Foto 2 von 3").assertIsDisplayed()
    }

    @Test
    fun `a swipe moves to the next photo and reports it`() {
        val pages = mutableListOf<Int>()
        showViewer(start = 0, onPage = { pages += it })

        compose.onNodeWithTag(PHOTO_VIEWER_PAGER_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        counter().assertTextEquals("2 / 3")
        assertThat(pages.last()).isEqualTo(1)
    }

    @Test
    fun `the arrow closes the viewer`() {
        var closed = false
        showViewer(start = 0, onBack = { closed = true })

        compose.onNodeWithTag(PHOTO_VIEWER_BACK_TAG).performClick()

        assertThat(closed).isTrue()
    }

    @Test
    fun `a tap on the hero opens the viewer on the photo shown`() {
        showHero(listOf("a", "b", "c"))
        compose.onNodeWithTag(HERO).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Foto b").performClick()

        assertThat(opened).hasSize(1)
        assertThat(opened.single().second).containsExactly("a", "b", "c").inOrder()
        assertThat(opened.single().third).isEqualTo(1)
    }

    @Test
    fun `a hero without photos opens nothing`() {
        showHero(emptyList())

        compose.onNodeWithTag(HERO).assertDoesNotExist()
        assertThat(opened).isEmpty()
    }

    @Test
    fun `the hero follows the photo the viewer ended on`() {
        showHero(listOf("a", "b", "c"))
        compose.onNodeWithContentDescription("Foto a").performClick()
        val owner = opened.single().first

        viewed = owner to 2
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Foto c").assertIsDisplayed()
        // Followed once: the viewer's answer is spent.
        assertThat(viewed).isNull()
    }

    private companion object {
        const val HERO = "hero_pager"
    }
}
