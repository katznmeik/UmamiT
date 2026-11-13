package com.umamit.feature.stats

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.stats.RatingsStats
import com.umamit.core.model.StatsRange
import com.umamit.core.ui.TASTE_RADAR_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** SCR-18 *Bewertungen* (INS-02, INS-04) with fixed results. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class RatingsTabTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun show(stats: RatingsStats) = compose.setContent {
        UmamiTheme(reduceMotion = true) {
            RatingsTab(
                TabFigures(StatsRange.DAYS_90, stats),
                rememberScrollState(),
                onOpenTea = { opened += it },
                thumbnail = { null },
            )
        }
    }

    @Test
    fun averageAndCountInGermanFormat() {
        show(RatingsFixtures.withData)
        compose.onNodeWithText("Ø 4,2 ★").assertIsDisplayed()
        compose.onNodeWithText("18 von 25 Sitzungen bewertet").assertIsDisplayed()
    }

    @Test
    fun topRatedRowsAndTaps() {
        show(RatingsFixtures.withData)
        compose.onNodeWithText("Ø 4,6 ★ · 5 Bewertungen").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Gyokuro Hisui", substring = true)[0].performScrollTo().performClick()
        assertThat(opened).containsExactly("tea-1")
    }

    @Test
    fun deletedTeaIsNotTappable() {
        show(RatingsFixtures.withData)
        compose.onNodeWithText("Gelöschter Tee").performScrollTo().performClick()
        assertThat(opened).isEmpty()
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
    fun radarWithThreeAxesAndInfusionCount() {
        show(RatingsFixtures.withData)
        compose.onNodeWithTag(TASTE_RADAR_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("aus 34 Aufgüssen").assertExists()
        compose.onAllNodesWithTag(RATINGS_TASTE_SUMMARY_TAG).assertCountEquals(0)
    }

    @Test
    fun summaryLineBelowThreeAxes() {
        show(RatingsFixtures.withoutRatings)
        compose.onNodeWithTag(RATINGS_TASTE_SUMMARY_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Umami 4 · Süße 3,5").assertExists()
        compose.onAllNodesWithTag(TASTE_RADAR_TAG).assertCountEquals(0)
    }

    @Test
    fun descriptorChipsWithCounts() {
        show(RatingsFixtures.withData)
        compose.onNodeWithText("Nori · 12×").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noRatingAtAll() {
        show(RatingsFixtures.withoutRatings)
        compose.onAllNodesWithTag(RATINGS_NO_RATING_TAG).assertCountEquals(3)
        compose.onAllNodesWithText("Noch keine Bewertung in diesem Zeitraum").assertCountEquals(3)
    }

    @Test
    fun emptyRangeSaysNothing() {
        show(RatingsFixtures.emptyRange)
        compose.onAllNodes(hasText("In diesem Zeitraum nichts")).assertCountEquals(5)
        compose.onAllNodesWithTag(RATINGS_NO_RATING_TAG).assertCountEquals(0)
    }
}
