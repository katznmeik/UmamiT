package com.umamit.feature.collection.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.collection.LiterAverage
import com.umamit.core.domain.collection.TeaCost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** VORRAT's cost lines (INV-08, SCR-09, D-104; spec #171 seam 2): price, estimate, average, and their absence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailCostScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(cost: TeaCost?) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(
                    TeaDetailUiState.Ready(TeaDetailScreenTest.sample().copy(cost = cost)),
                    TeaDetailActions(),
                    photo = { null },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a priced tea shows its price per 100 g, the recipe estimate and the average`() {
        show(TeaCost(pricePer100gCents = 1240, recipeLiterCents = 240, average = LiterAverage(270, 5)))

        compose.onNodeWithText("12,40 € / 100 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ca. 2,40 € pro Liter (Rezept)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ø 2,70 € pro Liter aus 5 Sitzungen").performScrollTo().assertIsDisplayed()
        // TalkBack reads „Durchschnitt", not the sign.
        compose.onNodeWithContentDescription("Durchschnittlich 2,70 € pro Liter aus 5 Sitzungen").assertExists()
    }

    @Test
    fun `one priced session reads in the singular`() {
        show(TeaCost(pricePer100gCents = 1240, recipeLiterCents = 240, average = LiterAverage(310, 1)))

        compose.onNodeWithText("Ø 3,10 € pro Liter aus 1 Sitzung").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `before the first priced session only the price and the estimate show`() {
        show(TeaCost(pricePer100gCents = 1240, recipeLiterCents = 240))

        compose.onNodeWithText("12,40 € / 100 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ca. 2,40 € pro Liter (Rezept)").assertExists()
        compose.onNodeWithText("pro Liter aus", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a tea without a price shows none of the three lines`() {
        show(cost = null)

        compose.onNodeWithTag(TEA_DETAIL_COST_TAG).assertDoesNotExist()
        compose.onNodeWithText("/ 100 g", substring = true).assertDoesNotExist()
        compose.onNodeWithText("pro Liter", substring = true).assertDoesNotExist()
    }
}
