package com.umamit.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.lowStockCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Start's low-stock card (INV-10, SCR-02, D-097): a quiet card with *Nachbestellen* and *Ausblenden*. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class HomeLowStockCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val reordered = mutableListOf<String>()
    private var hidden = 0

    private fun show(state: HomeUiState) {
        val actions = HomeActions(reorder = { reordered += it }, hideLowStock = { hidden++ })
        compose.setContent {
            UmamiTheme(reduceMotion = true) { HomeScreen(state = state, nowElapsedMs = { 0L }, actions = actions) }
        }
    }

    @Test
    fun `a Knapp tea reads wird knapp with its stock, Nachbestellen opens INV-09's page`() {
        show(HomeUiState(loading = false, lowStock = lowStockCard(grams = 14.0)))

        compose.onNodeWithTag(HOME_LOW_STOCK_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tanabata wird knapp · 14\u00A0g übrig").assertIsDisplayed()
        compose.onNodeWithText("Nachbestellen").performClick()

        assertThat(reordered).containsExactly("https://example.invalid/tanabata-50")
    }

    @Test
    fun `Ausblenden hides the card`() {
        show(HomeUiState(loading = false, lowStock = lowStockCard()))

        compose.onNodeWithText("Ausblenden").performScrollTo().performClick()

        assertThat(hidden).isEqualTo(1)
    }

    @Test
    fun `an empty tea reads ist leer`() {
        show(HomeUiState(loading = false, lowStock = lowStockCard(grams = 0.0)))

        compose.onNodeWithText("Tanabata ist leer").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a broken gram amount keeps its decimal`() {
        show(HomeUiState(loading = false, lowStock = lowStockCard(grams = 12.5)))

        compose.onNodeWithText("Tanabata wird knapp · 12,5\u00A0g übrig").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `without INV-09's link the card offers only Ausblenden`() {
        show(HomeUiState(loading = false, lowStock = lowStockCard(reorderUrl = null)))

        compose.onNodeWithText("Ausblenden").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nachbestellen").assertDoesNotExist()
    }

    @Test
    fun `no card without a Knapp tea`() {
        show(HomeUiState(loading = false))

        compose.onNodeWithTag(HOME_LOW_STOCK_TAG).assertDoesNotExist()
    }
}
