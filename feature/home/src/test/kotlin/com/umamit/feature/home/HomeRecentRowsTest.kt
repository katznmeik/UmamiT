package com.umamit.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The rows of *Zuletzt gebrüht* brew their tea again, and TalkBack names the tap so (BRW-11, SCR-02, D-110). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class HomeRecentRowsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a tap on a row brews its tea again and TalkBack names the tap Nochmal bruehen`() {
        val brewed = mutableListOf<String>()
        val state = HomeUiState(
            loading = false,
            recent = listOf(
                RecentBrew("a", "s2", "Tee A", null, 0L, "Europe/Berlin", 60, 65, 3),
                RecentBrew("b", "s1", "Tee B", null, 0L, "Europe/Berlin", 45, 70, 2),
            ),
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                HomeScreen(state = state, nowElapsedMs = { 0L }, actions = HomeActions(brewAgain = { brewed += it }))
            }
        }

        // The tag is on the row's column; the merged row inside it holds the click.
        compose.onNodeWithTag(homeRecentTag("b")).onChildAt(0).assertHasClickAction()
        compose.onNodeWithTag(homeRecentTag("b")).performClick()

        assertThat(brewed).containsExactly("b")
        val labelled = SemanticsMatcher("a click action labelled Nochmal brühen") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == "Nochmal brühen"
        }
        compose.onNodeWithTag(homeRecentTag("a")).onChildAt(0).assert(labelled)
    }
}
