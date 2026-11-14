package com.umamit.feature.stats

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.stats.RatingsStats
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsRankingBy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** SCR-18 *Bewertungen* (INS-02) with fixed results. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class RatingsTabTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private val rankingChoices = mutableListOf<StatsRankingBy>()

    private fun show(
        stats: RatingsStats,
        range: StatsRange = StatsRange.DAYS_90,
        by: StatsRankingBy = StatsRankingBy.TEA,
    ) = compose.setContent {
        var chosen by remember { mutableStateOf(by) }
        UmamiTheme(reduceMotion = true) {
            RatingsTab(
                TabFigures(range, stats),
                rememberScrollState(),
                onOpenTea = { opened += it },
                rankingBy = chosen,
                onRankingByChange = {
                    rankingChoices += it
                    chosen = it
                },
                thumbnail = { null },
            )
        }
    }

    @Test
    fun theSwitchStartsOnTeeAndRanksByZubereitungWhenChosen() {
        show(RatingsFixtures.withData)
        compose.onNodeWithTag(RATINGS_SWITCH_TEA_TAG).assertIsSelected()
        compose.onNodeWithText("4,6 ★ · 5×").performScrollTo().assertExists()

        compose.onNodeWithTag(RATINGS_SWITCH_BREWING_TAG).performScrollTo().performClick()

        assertThat(rankingChoices).containsExactly(StatsRankingBy.BREWING)
        compose.onNodeWithTag(RATINGS_SWITCH_BREWING_TAG).assertIsSelected()
        compose.onAllNodesWithTag(RATINGS_RANKING_ROW_TAG).assertCountEquals(3)
        compose.onNodeWithText("4,4 ★ · 3×").assertExists()
        compose.onNodeWithText("4,6 ★ · 5×").assertDoesNotExist()
    }

    @Test
    fun theFoldedRankingNamesTheTopTeaOfTheChosenKind() {
        show(RatingsFixtures.withData)
        compose.onNodeWithTag(statsSectionTag("ratings.ranking")).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Tee-Rangliste · Gyokuro Hisui").assertExists()
        compose.onNodeWithTag(statsSectionTag("ratings.ranking")).performClick()
        compose.onNodeWithTag(RATINGS_SWITCH_BREWING_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(statsSectionTag("ratings.ranking")).performClick()
        compose.onNodeWithContentDescription("Tee-Rangliste · Sencha Asahi").assertExists()
    }

    @Test
    fun aRememberedZubereitungChoiceShowsItsRanking() {
        show(RatingsFixtures.withData, by = StatsRankingBy.BREWING)
        compose.onNodeWithTag(RATINGS_SWITCH_BREWING_TAG).assertIsSelected()
        compose.onAllNodesWithTag(RATINGS_RANKING_ROW_TAG).assertCountEquals(3)
    }

    @Test
    fun ratingsOfOnlyTheOtherKindSayNoneOfThisKind() {
        show(RatingsFixtures.onlyBrewing)
        compose.onNodeWithTag(RATINGS_NONE_OF_KIND_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Keine Bewertung dieser Art im Zeitraum").assertExists()
        compose.onAllNodesWithTag(RATINGS_NO_RATING_TAG).assertCountEquals(0)
        compose.onNodeWithTag(RATINGS_SWITCH_BREWING_TAG).performScrollTo().performClick()
        compose.onAllNodesWithTag(RATINGS_NONE_OF_KIND_TAG).assertCountEquals(0)
    }

    @Test
    fun bestRecipeNamesItsRating() {
        show(RatingsFixtures.withData)
        compose.onNodeWithText("nach Zubereitung").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rankingRowsAndTaps() {
        show(RatingsFixtures.withData)
        compose.onNodeWithText("4,6 ★ · 5×").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Gyokuro Hisui", substring = true)[0].performScrollTo().performClick()
        assertThat(opened).containsExactly("tea-1")
    }

    @Test
    fun rankingShowsTenAndExpandsInPlace() {
        show(RatingsFixtures.withData)
        compose.onAllNodesWithTag(RATINGS_RANKING_ROW_TAG).assertCountEquals(10)
        compose.onNodeWithText("Alle anzeigen (12)").performScrollTo().performClick()
        compose.onAllNodesWithTag(RATINGS_RANKING_ROW_TAG).assertCountEquals(12)
        compose.onNodeWithTag(RATINGS_RANKING_MORE_TAG).assertDoesNotExist()
    }

    @Test
    fun shortRankingHasNoShowAll() {
        show(RatingsFixtures.withData.copy(teaRanking = RatingsFixtures.withData.teaRanking.take(3)))
        compose.onNodeWithTag(RATINGS_RANKING_MORE_TAG).assertDoesNotExist()
    }

    @Test
    fun deletedTeaIsNotTappable() {
        show(RatingsFixtures.withData)
        compose.onNodeWithText("Gelöschter Tee").performScrollTo().assertHasNoClickAction()
        compose.onNodeWithText("Gelöschter Tee").performClick()
        assertThat(opened).isEmpty()
    }

    @Test
    fun newlyDiscoveredShowsFiveAndExpands() {
        show(RatingsFixtures.withData)
        compose.onAllNodesWithTag(RATINGS_NEW_ROW_TAG).assertCountEquals(5)
        compose.onNodeWithText("ohne Bewertung").performScrollTo().assertExists()
        compose.onAllNodesWithText("Ø 4,6 ★", substring = true).assertCountEquals(1)
        compose.onNodeWithTag(RATINGS_NEW_MORE_TAG).performScrollTo()
        compose.onNodeWithText("Alle anzeigen (7)").performClick()
        compose.onAllNodesWithTag(RATINGS_NEW_ROW_TAG).assertCountEquals(7)
    }

    @Test
    fun newlyDiscoveredTapOpensTeaButNotADeletedOne() {
        show(RatingsFixtures.withData)
        compose.onNodeWithText("Neuling ohne Wertung").performScrollTo().performClick()
        compose.onNodeWithText("Gelöschter Neuling").performScrollTo().assertHasNoClickAction()
        assertThat(opened).containsExactly("new-2")
    }

    @Test
    fun newlyDiscoveredIsHiddenUnderGesamt() {
        show(RatingsFixtures.withData, StatsRange.ALL)
        compose.onNodeWithText("NEU ENTDECKT").assertDoesNotExist()
        compose.onAllNodesWithTag(RATINGS_NEW_ROW_TAG).assertCountEquals(0)
    }

    @Test
    fun sectionsComeInTheDocumentedOrder() {
        show(RatingsFixtures.withData)
        val keys = listOf("ratings.ranking", "ratings.new", "ratings.history", "ratings.best_recipe")
        val tops = keys.map {
            compose.onNodeWithTag(statsSectionTag(it)).performScrollTo().fetchSemanticsNode().positionInRoot.y
        }
        assertThat(tops).isInOrder()
    }

    @Test
    fun bestRecipeShownWithSnapshotNames() {
        show(RatingsFixtures.withData)
        compose.onNodeWithText("Kalt 4,6 ★ · Klassisch 4,0 ★").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun bestRecipeHiddenWhenNoneQualifies() {
        show(RatingsFixtures.withData.copy(bestRecipes = emptyList()))
        compose.onNodeWithText("BESTES REZEPT").assertDoesNotExist()
        compose.onAllNodesWithTag(RATINGS_BEST_RECIPE_TAG).assertCountEquals(0)
    }

    @Test
    fun noRatingAtAllSaysSoAboveNeuEntdeckt() {
        show(RatingsFixtures.withoutRatingsNew)
        compose.onAllNodesWithTag(RATINGS_NO_RATING_TAG).assertCountEquals(1)
        compose.onNodeWithText("In diesem Zeitraum nichts bewertet").assertExists()
        compose.onNodeWithText("TEE-RANGLISTE").assertDoesNotExist()
        compose.onNodeWithText("Neu entdeckt".uppercase()).assertExists()
        compose.onNodeWithText("BEWERTUNG IM VERLAUF").assertDoesNotExist()
    }

    @Test
    fun emptyRangeSaysNothing() {
        show(RatingsFixtures.emptyRange)
        compose.onAllNodes(hasText("In diesem Zeitraum nichts")).assertCountEquals(3)
        compose.onAllNodesWithTag(RATINGS_NO_RATING_TAG).assertCountEquals(0)
    }
}
