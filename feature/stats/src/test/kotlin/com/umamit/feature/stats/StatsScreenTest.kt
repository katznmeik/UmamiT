package com.umamit.feature.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** SCR-18 (INS-07, INS-08): the shell's states, the selector's and the tabs' semantics, the pager. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class StatsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private var state by mutableStateOf<StatsUiState>(StatsUiState.Data(StatsRange.DAYS_90, StatsTab.OVERVIEW))
    private val ranges = mutableListOf<StatsRange>()
    private val tabs = mutableListOf<StatsTab>()

    private fun show() = compose.setContent {
        UmamiTheme(reduceMotion = true) {
            StatsContent(
                state = state,
                onRangeChange = { ranges += it },
                onTabChange = {
                    tabs += it
                    state = (state as StatsUiState.Data).copy(tab = it)
                },
                onOpenSettings = {},
            )
        }
    }

    @Test
    fun `loading draws nothing, not even the empty state`() {
        state = StatsUiState.Loading
        show()

        compose.onAllNodesWithText(str(R.string.stats_empty_title)).assertCountEquals(0)
        compose.onAllNodesWithText(str(R.string.stats_tab_overview)).assertCountEquals(0)
    }

    @Test
    fun `no finished session shows A9 with its texts and neither selector nor tabs`() {
        state = StatsUiState.Empty
        show()

        compose.onNodeWithText(str(R.string.stats_empty_title)).assertExists()
        compose.onNodeWithText(str(R.string.stats_empty_body)).assertExists()
        compose.onAllNodesWithText(str(R.string.stats_range_90)).assertCountEquals(0)
        compose.onAllNodesWithText(str(R.string.stats_tab_ratings)).assertCountEquals(0)
    }

    @Test
    fun `the selector reads the range for TalkBack and marks the chosen one`() {
        show()

        compose.onNodeWithContentDescription(str(R.string.stats_range_90_description)).assert(isSelected())
        compose.onNodeWithContentDescription(str(R.string.stats_range_30_description))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        assertThat(str(R.string.stats_range_90_description)).isEqualTo("Zeitraum: 90 Tage")
    }

    @Test
    fun `a tap on a range reports it`() {
        show()

        compose.onNodeWithContentDescription(str(R.string.stats_range_all_description)).performClick()

        assertThat(ranges).containsExactly(StatsRange.ALL)
    }

    @Test
    fun `the tabs are tabs, the chosen one is selected, a tap reports it`() {
        show()

        compose.onNode(hasText(str(R.string.stats_tab_overview)) and isTab()).assert(isSelected())
        compose.onNode(hasText(str(R.string.stats_tab_consumption)) and isTab()).performClick()

        assertThat(tabs).containsExactly(StatsTab.CONSUMPTION)
        compose.onNode(hasText(str(R.string.stats_tab_consumption)) and isTab()).assert(isSelected())
    }

    @Test
    fun `a swipe turns the page and reports the tab`() {
        show()

        compose.onNodeWithText(str(R.string.stats_section_empty)).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertThat(tabs).containsExactly(StatsTab.RATINGS)
    }

    @Test
    fun `an empty section says In diesem Zeitraum nichts`() {
        show()

        compose.onNodeWithText("In diesem Zeitraum nichts").assertExists()
    }

    private fun isTab() = SemanticsMatcher("is a tab") {
        it.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Tab
    }
}
