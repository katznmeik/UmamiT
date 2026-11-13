package com.umamit.feature.stats

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.stats.OverviewStats
import com.umamit.core.domain.stats.Streak
import com.umamit.core.model.StatsRange
import com.umamit.feature.stats.charts.STATS_CHART_TAG
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
    fun `sessions, cups and teas sit in one row`() {
        show(OverviewFixtures.data())

        val tops = listOf("42", "96", "24").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertThat(tops.distinct()).hasSize(1)
    }

    @Test
    fun `no plus under Gesamt`() {
        show(OverviewFixtures.data(teasAdded = null), StatsRange.ALL)

        compose.onNodeWithText("Tees").assertExists()
        assertThat(compose.onAllNodesWithText("im Zeitraum", substring = true).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `the Tees donut follows Tee-Sorten with a TalkBack list`() {
        show(OverviewFixtures.data())

        val sorten = compose.onNodeWithTag(statsSectionTag("overview.sorten")).fetchSemanticsNode().boundsInRoot.top
        val tees = compose.onNodeWithTag(statsSectionTag("overview.tees")).performScrollTo()
            .fetchSemanticsNode().boundsInRoot.top
        assertThat(tees).isGreaterThan(sorten)
        compose.onNodeWithText("TEES").assertExists()
        val description = compose.onAllNodesWithTag(STATS_CHART_TAG)
            .fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.ContentDescription].joinToString() }
            .single { it.startsWith("Tees.") }
        assertThat(description).contains("Hisui Gyokuro: 29 %, 12 Sitzungen.")
        assertThat(description).contains("Andere: 11 %, 5 Sitzungen.")
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

        compose.onNodeWithText("Keine Serie gerade · längste 21 Tage").performScrollTo().assertExists()
    }

    @Test
    fun `no current streak and no longest reads Keine Serie gerade alone`() {
        show(OverviewFixtures.data(streak = Streak(0, 0)))

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

        assertThat(compose.onAllNodesWithText("In diesem Zeitraum nichts").fetchSemanticsNodes()).hasSize(5)
        assertThat(compose.onAllNodesWithText("0").fetchSemanticsNodes()).hasSize(2)
        compose.onNodeWithText("Keine Serie gerade · längste 21 Tage").performScrollTo().assertExists()
    }

    @Test
    fun `nothing is drawn before the first read`() {
        show(null)

        assertThat(compose.onAllNodesWithText("Sitzungen").fetchSemanticsNodes()).isEmpty()
    }
}
