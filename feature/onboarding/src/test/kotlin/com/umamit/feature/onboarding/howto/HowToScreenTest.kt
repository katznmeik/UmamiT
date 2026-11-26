package com.umamit.feature.onboarding.howto

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.onboarding.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Anleitungen's list and topic page (SCR-35, SCR-36; ONB-08): rows, callbacks and what TalkBack reads. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HowToScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theListHasEightRowsAndATapOpensTheTopic() {
        var opened: HowToTopic? = null
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                HowToListScreen(onOpenTopic = { opened = it }, onBack = {})
            }
        }

        assertThat(HowToTopic.entries).hasSize(EIGHT)
        compose.onNodeWithText(context.getString(R.string.howto_list_title)).assertExists()
        HowToTopic.entries.forEach { compose.onNodeWithTag(HowToTags.row(it)).assertExists() }
        compose.onNodeWithTag(HowToTags.row(HowToTopic.Recipes)).performClick()

        assertThat(opened).isEqualTo(HowToTopic.Recipes)
    }

    @Test
    fun theTopicPageReadsTheStepsAndTheMockups() {
        val topic = HowToTopic.WithoutCatalog
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) { HowToTopicScreen(topic = topic, onBack = {}) }
        }

        compose.onNodeWithText(context.getString(topic.title)).assertExists()
        // Each step is one item: „Schritt 2 von 5: …".
        topic.steps.forEachIndexed { index, step ->
            val spoken = context.getString(
                R.string.howto_step_description,
                index + 1,
                topic.steps.size,
                context.getString(step.text),
            )
            compose.onNodeWithContentDescription(spoken).assertExists()
        }
        // Each mock-up reads its one description.
        topic.steps.mapNotNull { it.mockup }.forEach {
            compose.onAllNodesWithContentDescription(context.getString(it.description)).assertCountEquals(1)
        }
    }

    @Test
    fun theTitleIsAHeading() {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                HowToTopicScreen(topic = HowToTopic.ShopLink, onBack = {})
            }
        }

        val title = context.getString(HowToTopic.ShopLink.title)
        val node = compose.onNodeWithText(title).fetchSemanticsNode()
        assertThat(SemanticsProperties.Heading in node.config).isTrue()
    }

    @Test
    fun backCallsBack() {
        var back = 0
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                HowToTopicScreen(topic = HowToTopic.ShopLink, onBack = { back++ })
            }
        }

        compose.onNodeWithContentDescription(
            context.getString(com.umamit.core.designsystem.R.string.ds_back),
        ).performClick()

        assertThat(back).isEqualTo(1)
    }
}

private const val EIGHT = 8
