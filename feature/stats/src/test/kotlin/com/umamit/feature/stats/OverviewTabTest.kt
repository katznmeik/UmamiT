package com.umamit.feature.stats

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.stats.OverviewStats
import com.umamit.core.domain.stats.Streak
import com.umamit.core.model.StatsRange
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** *Überblick* (INS-01, INS-03, INS-06) with fixed results: German numerals, the plus, the streak, the row taps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class OverviewTabTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun show(stats: OverviewStats?, range: StatsRange = StatsRange.DAYS_90) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                OverviewTab(
                    figures = stats?.let { TabFigures(range, it) },
                    scrollState = rememberScrollState(),
                    onOpenTea = { opened += it },
                    thumbnail = { null },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the numerals read in German with the plus`() {
        show(OverviewFixtures.data())

        compose.onNodeWithText("42").assertExists()
        compose.onNodeWithText("Sitzungen").assertExists()
        compose.onNodeWithText("96").assertExists()
        compose.onNodeWithText("Tassen").assertExists()
        compose.onNodeWithText("24").assertExists()
        compose.onNodeWithText("Tees · +3 im Zeitraum").assertExists()
    }

    @Test
    fun `the plus is hidden at 0, a thousand is grouped and one is singular`() {
        show(OverviewFixtures.data(teasAdded = 0).copy(sessionCount = 1234, cupCount = 1))

        compose.onNodeWithText("Tees").assertExists()
        assertThat(compose.onAllNodesWithText("+", substring = true).fetchSemanticsNodes()).isEmpty()
        compose.onNodeWithText("1.234").assertExists()
        compose.onNodeWithText("Tasse").assertExists()
    }

    @Test
    fun `the streak reads neutrally`() {
        show(OverviewFixtures.data())

        compose.onNodeWithText("7 Tage in Folge · längste 21 Tage · seit Beginn").performScrollTo().assertExists()
    }

    @Test
    fun `one day is singular`() {
        show(OverviewFixtures.data(streak = Streak(1, 1)))

        compose.onNodeWithText("1 Tag in Folge · längste 1 Tag · seit Beginn").performScrollTo().assertExists()
    }

    @Test
    fun `no current streak reads Keine Serie gerade`() {
        show(OverviewFixtures.data(streak = Streak(0, 21)))

        compose.onNodeWithText("Keine Serie gerade").performScrollTo().assertExists()
    }

    @Test
    fun `a tea row opens its tea, a deleted tea's row is not clickable`() {
        show(OverviewFixtures.data())

        compose.onNodeWithText("12×").assertExists()
        compose.onNodeWithText("Hisui Gyokuro").performScrollTo().assertHasClickAction()
        compose.onNodeWithText("Hisui Gyokuro").performClick()
        assertThat(opened).containsExactly("t1")

        compose.onNodeWithText("Gelöschter Matcha").performScrollTo().assertHasNoClickAction()
        compose.onNodeWithText("Gelöschter Matcha").performClick()
        assertThat(opened).containsExactly("t1")
    }

    @Test
    fun `an empty range reads the quiet line in every section but the streak`() {
        show(OverviewFixtures.emptyRange())

        assertThat(compose.onAllNodesWithText("In diesem Zeitraum nichts").fetchSemanticsNodes()).hasSize(4)
        assertThat(compose.onAllNodesWithText("0").fetchSemanticsNodes()).hasSize(2)
        compose.onNodeWithText("Keine Serie gerade").performScrollTo().assertExists()
    }

    @Test
    fun `nothing is drawn before the first read`() {
        show(null)

        assertThat(compose.onAllNodesWithText("Sitzungen").fetchSemanticsNodes()).isEmpty()
    }
}
