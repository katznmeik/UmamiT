package com.umamit.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.onboarding.howto.HowToTags
import com.umamit.feature.onboarding.howto.HowToTopic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tour (SCR-33, ONB-07) on its own: *Überspringen* only before the last page, „Mehr dazu" → Anleitung → back to
 * the same page, *Fertig* closing the tour from the settings, and one button width on every page (D-137 Q11, Q12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TourTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var finished = 0
    private var skipped = 0

    private fun show() {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                TourScreen(onFinish = { finished++ }, onSkip = { skipped++ })
            }
        }
    }

    private fun toPage(page: Int) {
        repeat(page) {
            compose.onNodeWithTag(TourTags.NEXT).performClick()
            compose.waitForIdle()
        }
    }

    @Test
    fun `Ueberspringen on page 2 calls onSkip`() {
        show()
        toPage(1)
        compose.onNodeWithTag(TourTags.DOTS).assertContentDescriptionEquals("Seite 2 von 6")

        compose.onNodeWithTag(TourTags.SKIP).performClick()

        assertThat(skipped).isEqualTo(1)
        assertThat(finished).isEqualTo(0)
    }

    @Test
    fun `no Ueberspringen on page 6 but on page 5`() {
        show()
        toPage(4)
        compose.onNodeWithTag(TourTags.SKIP).assertIsDisplayed()

        toPage(1)

        compose.onNodeWithTag(TourTags.DOTS).assertContentDescriptionEquals("Seite 6 von 6")
        compose.onNodeWithTag(TourTags.SKIP).assertDoesNotExist()
        compose.onNodeWithText("Überspringen").assertDoesNotExist()
    }

    @Test
    fun `Mehr dazu opens the Anleitung and back lands on the same page`() {
        show()
        toPage(2)
        compose.onNodeWithTag(TourTags.DOTS).assertContentDescriptionEquals("Seite 3 von 6")

        compose.onNodeWithTag(TourTags.more(HowToTopic.ShopLink)).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(HowToTags.TOPIC).assertIsDisplayed()
        compose.onNodeWithTag(TourTags.PAGER).assertDoesNotExist()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithTag(HowToTags.TOPIC).assertDoesNotExist()
        compose.onNodeWithText("Sammlung und Katalog").assertIsDisplayed()
        compose.onNodeWithTag(TourTags.DOTS).assertContentDescriptionEquals("Seite 3 von 6")
        assertThat(skipped).isEqualTo(0)
    }

    @Test
    fun `Mehr dazu link names the topic`() {
        show()
        compose.onNodeWithText("Mehr dazu: Aufgießen im Detail").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mehr dazu: Zuverlässiger Alarm").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Fertig on the last page of the settings tour calls onClose`() {
        var closed = 0
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) { TourRoute(onClose = { closed++ }) }
        }
        toPage(5)

        compose.onNodeWithText("Fertig").performClick()

        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `the button keeps one width on page 1 and page 6`() {
        show()
        val first = compose.onNodeWithTag(TourTags.NEXT).getBoundsInRoot()
        toPage(5)
        val last = compose.onNodeWithTag(TourTags.NEXT).getBoundsInRoot()

        compose.onNodeWithText("Fertig").assertIsDisplayed()
        assertThat(last.right - last.left).isEqualTo(first.right - first.left)
    }
}
