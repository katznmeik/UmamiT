package com.umamit.feature.collection.detail

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.model.TasteSet
import com.umamit.core.model.TeaRating
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The tea detail's rating, GESCHMACK and LETZTE SESSIONS (TEA-13, D-112, #232): what shows with and without data,
 * and what a row and *Alle anzeigen* do, also above the setup where they open nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailTastingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private var shownAll = 0

    private fun show(tea: TeaDetail, withSessionPages: Boolean = true) {
        val actions = if (withSessionPages) {
            TeaDetailActions(openSession = { opened += it }, showAllSessions = { shownAll++ })
        } else {
            TeaDetailActions()
        }
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(tea), actions, photo = { null })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `without a rating or sessions the header has no rating and the sections are absent`() {
        show(TeaDetailScreenTest.sample().copy(today = TODAY))

        compose.onNodeWithTag(TEA_DETAIL_RATING_TAG).assertDoesNotExist()
        compose.onNodeWithTag(TEA_DETAIL_TASTE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(TEA_DETAIL_RECENT_TAG).assertDoesNotExist()
        compose.onNodeWithText("GESCHMACK").assertDoesNotExist()
        compose.onNodeWithText("LETZTE SESSIONS").assertDoesNotExist()
    }

    @Test
    fun `the header shows the average with its count and TalkBack reads it`() {
        show(RATED)

        compose.onNodeWithTag(TEA_DETAIL_RATING_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("★ 4,5 (12)", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(TEA_DETAIL_RATING_TAG).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Bewertung 4,5 von 5, aus 12 Sessions"),
            ),
        )
    }

    @Test
    fun `the Zubereitung line sits below the header and TalkBack reads it`() {
        show(RATED.copy(brewingRating = TeaRating(3.8, 10)))

        compose.onNodeWithTag(TEA_DETAIL_BREWING_RATING_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Zubereitung Ø 3,8 (10)", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(TEA_DETAIL_BREWING_RATING_TAG).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Zubereitung im Schnitt 3,8 von 5, aus 10 Sessions"),
            ),
        )
        val header = compose.onNodeWithTag(TEA_DETAIL_RATING_TAG).fetchSemanticsNode().boundsInRoot
        val line = compose.onNodeWithTag(TEA_DETAIL_BREWING_RATING_TAG).fetchSemanticsNode().boundsInRoot
        assertThat(line.top).isAtLeast(header.bottom)
    }

    @Test
    fun `without a Zubereitung rating the line is hidden`() {
        show(RATED)

        compose.onNodeWithTag(TEA_DETAIL_BREWING_RATING_TAG).assertDoesNotExist()
    }

    @Test
    fun `a single rated session reads in the singular`() {
        show(RATED.copy(rating = TeaRating(4.0, 1)))

        compose.onNodeWithText("★ 4,0 (1)", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(TEA_DETAIL_RATING_TAG).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Bewertung 4,0 von 5, aus 1 Session"),
            ),
        )
    }

    @Test
    fun `GESCHMACK shows the radar and the descriptors with their counts`() {
        show(TASTED)

        compose.onNodeWithText("GESCHMACK").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nori · 4").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Honig · 2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("taste_radar").assertExists()
    }

    @Test
    fun `GESCHMACK comes after FRISCHE and before LETZTE SESSIONS`() {
        show(TASTED.copy(freshness = DetailFreshness(com.umamit.core.model.Freshness.FRESH, null, null)))

        val order = listOf("Frische", "Geschmack", "Letzte Sessions").map { title ->
            compose.onNode(hasContentDescription(title)).fetchSemanticsNode().boundsInRoot.top
        }
        // Scrolled content keeps its layout order, so the tops grow down the page.
        assertThat(order).isInOrder()
    }

    @Test
    fun `descriptors alone show GESCHMACK without a radar`() {
        show(
            TASTED.copy(
                taste = DetailTasting(descriptors = listOf(DetailDescriptor(TasteDescriptor.MARINE_NORI, 1))),
            ),
        )

        compose.onNodeWithText("Nori · 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("taste_radar").assertDoesNotExist()
    }

    @Test
    fun `the rows read date, temperature, time and infusions with the rating when rated`() {
        show(TASTED)

        compose.onNodeWithText("8. Juni · 55 °C · 1:00 · 3 Aufgüsse").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tee ★ 4,5 ·").performScrollTo()
        compose.onNodeWithText("Tee ★ 4,5 ·").assertIsDisplayed()
        compose.onAllNodesWithText("Zubereitung ★ 3,5")[0].assertIsDisplayed()
        compose.onNodeWithText("21. Oktober · 70 °C · 0:45 · 2 von 3 Aufgüssen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(teaDetailSessionTag("s1")).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("8. Juni, 55 °C, 1 Minute, 3 Aufgüsse, Tee 4,5 von 5, Zubereitung 3,5 von 5"),
            ),
        )
    }

    @Test
    fun `a session of an earlier year names its year`() {
        show(TASTED.copy(today = LocalDate.of(2027, 1, 5)))

        compose.onNodeWithText("8. Juni 2026 · 55 °C · 1:00 · 3 Aufgüsse").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a row opens its session page`() {
        show(TASTED)

        compose.onNodeWithTag(teaDetailSessionTag("s2")).performScrollTo().assertHasClickAction().performClick()

        assertThat(opened).containsExactly("s2")
    }

    @Test
    fun `Alle anzeigen opens the journal of the tea`() {
        show(TASTED)

        // By its action: scrolled to the viewport's lower edge, the pencil FAB covers the button's centre.
        compose.onNodeWithContentDescription("Alle anzeigen: Letzte Sessions").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(shownAll).isEqualTo(1)
        assertThat(opened).isEmpty()
    }

    @Test
    fun `three sessions or fewer have no Alle anzeigen`() {
        show(TASTED.copy(sessionCount = 2, recentSessions = TASTED.recentSessions.take(2)))

        compose.onNodeWithTag(TEA_DETAIL_RECENT_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Alle anzeigen: Letzte Sessions").assertDoesNotExist()
    }

    @Test
    fun `above the setup the rows open nothing and Alle anzeigen is hidden`() {
        show(TASTED, withSessionPages = false)

        compose.onNodeWithTag(TEA_DETAIL_RECENT_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(teaDetailSessionTag("s1")).assertHasNoClickAction()
        compose.onNodeWithContentDescription("Alle anzeigen: Letzte Sessions").assertDoesNotExist()
        assertThat(opened).isEmpty()
        assertThat(shownAll).isEqualTo(0)
    }

    @Test
    fun `a herbal tea's radar reads its six axes in the set's order`() {
        show(TASTED_HERBAL)

        compose.onNodeWithTag("taste_radar").performScrollTo().assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf(
                    "Geschmacksprofil: Süße 3 von 5, Säure 4 von 5, Frische 2 von 5, Würze 1 von 5, " +
                        "Bitterkeit 0,5 von 5, Körper 2 von 5",
                ),
            ),
        )
    }

    @Test
    fun `a green tea's radar is as before`() {
        show(TASTED)

        compose.onNodeWithTag("taste_radar").performScrollTo().assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Geschmacksprofil: Umami 4,5 von 5, Süße 3 von 5, Bitterkeit 1,5 von 5, Körper 3,5 von 5"),
            ),
        )
    }

    companion object {
        private val TODAY = LocalDate.of(2026, 11, 8)

        val RATED = TeaDetailScreenTest.sample().copy(today = TODAY, rating = TeaRating(4.5, 12))

        /** Rated, with a radar, descriptors and four finished sessions (three listed). */
        val TASTED = RATED.copy(
            sessionCount = 4,
            brewingRating = TeaRating(3.8, 10),
            taste = DetailTasting(
                averages = mapOf(
                    TasteAxis.UMAMI to 4.5,
                    TasteAxis.SWEETNESS to 3.0,
                    TasteAxis.BITTERNESS to 1.5,
                    TasteAxis.BODY to 3.5,
                ),
                descriptors = listOf(
                    DetailDescriptor(TasteDescriptor.MARINE_NORI, 4),
                    DetailDescriptor(TasteDescriptor.SWEET_HONIG, 2),
                ),
            ),
            recentSessions = listOf(
                RecentSession("s1", LocalDate.of(2026, 6, 8), 55, 60, 3, 3, 4.5, 3.5),
                RecentSession("s2", LocalDate.of(2026, 10, 21), 70, 45, 2, 3, null, 3.5),
                RecentSession("s3", LocalDate.of(2026, 10, 19), 70, 45, 3, 3, 4.0),
            ),
        )

        /** [TASTED] for a black tea (Schwarz · Oolong · Pu-Erh set, D-128). */
        val TASTED_OXIDISED = TASTED.copy(
            taste = TASTED.taste.copy(
                averages = mapOf(
                    TasteAxis.ROAST to 4.0,
                    TasteAxis.SWEETNESS to 2.0,
                    TasteAxis.BITTERNESS to 1.0,
                    TasteAxis.ASTRINGENCY to 3.0,
                    TasteAxis.BODY to 3.5,
                    TasteAxis.AROMA to 4.5,
                ),
                tasteSet = TasteSet.OXIDISED,
            ),
        )

        /** [TASTED] for a herbal tea (Kräuter · Rotbusch · Früchte set, D-128). */
        val TASTED_HERBAL = TASTED.copy(
            taste = TASTED.taste.copy(
                averages = mapOf(
                    TasteAxis.SWEETNESS to 3.0,
                    TasteAxis.ACIDITY to 4.0,
                    TasteAxis.FRESHNESS to 2.0,
                    TasteAxis.SPICE to 1.0,
                    TasteAxis.BITTERNESS to 0.5,
                    TasteAxis.BODY to 2.0,
                ),
                tasteSet = TasteSet.HERBAL,
            ),
        )
    }
}
