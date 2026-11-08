package com.umamit.feature.collection.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.TeaPurchase
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** *Nachkauf erfassen* on VORRAT (INV-03, SCR-09, D-104; spec #171 seam 2): the sheet's defaults and checks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailPurchaseScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val purchases = mutableListOf<TeaPurchase>()
    private val actions = TeaDetailActions(recordPurchase = { purchases += it })
    private var alertKey by mutableStateOf<String?>(null)
    private var tea by mutableStateOf(TeaDetailScreenTest.sample().copy(today = TODAY))

    private fun show() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(tea), actions, alertKey = alertKey, photo = { null })
            }
        }
        compose.waitForIdle()
    }

    private fun openSheet() {
        compose.onNodeWithTag(TEA_DETAIL_PURCHASE_TAG).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun save() = compose.onNodeWithTag(PURCHASE_SHEET_SAVE_TAG)

    @Test
    fun `the sheet opens with empty fields on today and saves nothing until the grams are set`() {
        show()
        openSheet()

        compose.onNodeWithTag(PURCHASE_SHEET_GRAMS_TAG).assertIsDisplayed()
        compose.onNodeWithTag(PURCHASE_SHEET_DATE_TAG).assertIsDisplayed()
        compose.onNodeWithText("8. Nov. 2026").assertIsDisplayed()
        save().assertIsNotEnabled()
    }

    @Test
    fun `grams, price and today are recorded and the sheet closes`() {
        show()
        openSheet()

        compose.onNodeWithTag(PURCHASE_SHEET_GRAMS_TAG).performTextInput("37,5")
        compose.onNodeWithTag(PURCHASE_SHEET_PRICE_TAG).performTextInput("12,40")
        save().assertIsEnabled().performClick()
        compose.waitForIdle()

        assertThat(purchases).containsExactly(TeaPurchase(37.5, 1240, TODAY))
        compose.onNodeWithTag(PURCHASE_SHEET_GRAMS_TAG).assertDoesNotExist()
    }

    @Test
    fun `without a price the purchase has none`() {
        show()
        openSheet()

        compose.onNodeWithTag(PURCHASE_SHEET_GRAMS_TAG).performTextInput("50")
        save().performClick()

        assertThat(purchases).containsExactly(TeaPurchase(50.0, null, TODAY))
    }

    @Test
    fun `malformed grams or price are said at the field and refused`() {
        show()
        openSheet()

        compose.onNodeWithTag(PURCHASE_SHEET_GRAMS_TAG).performTextInput("0")
        compose.onNodeWithText("Gib die Menge in g an, z. B. 50.").assertIsDisplayed()
        save().assertIsNotEnabled()

        compose.onNodeWithTag(PURCHASE_SHEET_GRAMS_TAG).performTextInput("5")
        compose.onNodeWithTag(PURCHASE_SHEET_PRICE_TAG).performTextInput("12,345")
        compose.onNodeWithText("Gib den Preis in Euro an, z. B. 12,40.").assertIsDisplayed()
        save().assertIsNotEnabled()
        assertThat(purchases).isEmpty()
    }

    @Test
    fun `a price of 0 is refused like any other malformed price (INV-08)`() {
        show()
        openSheet()

        compose.onNodeWithTag(PURCHASE_SHEET_GRAMS_TAG).performTextInput("50")
        compose.onNodeWithTag(PURCHASE_SHEET_PRICE_TAG).performTextInput("0")
        compose.onNodeWithText("Gib den Preis in Euro an, z. B. 12,40.").assertIsDisplayed()
        save().assertIsNotEnabled()
        assertThat(purchases).isEmpty()
    }

    @Test
    fun `a day after today is refused`() {
        show()
        openSheet()
        compose.onNodeWithTag(PURCHASE_SHEET_GRAMS_TAG).performTextInput("50")

        // The phone's clock went back a day while the sheet was open: its day now lies ahead.
        tea = tea.copy(today = TODAY.minusDays(1))
        compose.waitForIdle()

        compose.onNodeWithText("Das Datum liegt in der Zukunft.").assertIsDisplayed()
        save().assertIsNotEnabled()
        assertThat(purchases).isEmpty()
    }

    @Test
    fun `Abbrechen and a new alert close the sheet without saving`() {
        show()
        openSheet()
        compose.onNodeWithTag(PURCHASE_SHEET_GRAMS_TAG).performTextInput("50")
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PURCHASE_SHEET_GRAMS_TAG).assertDoesNotExist()

        openSheet()
        alertKey = "brew-1"
        compose.waitForIdle()

        compose.onNodeWithTag(PURCHASE_SHEET_GRAMS_TAG).assertDoesNotExist()
        assertThat(purchases).isEmpty()
    }

    @Test
    fun `a catalog tea keeps its reorder link beside the purchase, a tea of its own has the purchase too`() {
        show()
        compose.onNodeWithTag(TEA_DETAIL_PURCHASE_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TEA_DETAIL_REORDER_TAG).performScrollTo().assertIsDisplayed()

        tea = tea.copy(reorder = null, hasStock = false)
        compose.waitForIdle()

        compose.onNodeWithTag(TEA_DETAIL_PURCHASE_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TEA_DETAIL_REORDER_TAG).assertDoesNotExist()
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 8)
    }
}
