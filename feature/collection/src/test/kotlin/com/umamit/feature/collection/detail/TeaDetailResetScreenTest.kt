package com.umamit.feature.collection.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ⋮ *Aus Katalog zurücksetzen* and its confirmation on the stateless tea detail (PRF-06, CAT-13, SCR-09, D-104). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailResetScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = TeaDetailActions(
        toggleFavorite = { calls += "favorite" },
        archive = { calls += "archive" },
        resetFromCatalog = { calls += "reset" },
    )

    private var alertKey by mutableStateOf<String?>(null)

    private fun show(tea: TeaDetail) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(tea), actions, alertKey = alertKey, photo = { null })
            }
        }
        compose.waitForIdle()
    }

    private fun openMenu() = compose.onNodeWithContentDescription("Weitere Optionen").performClick()

    private fun offered(leftOut: Int = 0) = TeaDetailScreenTest.sample().copy(catalogReset = CatalogResetOffer(leftOut))

    @Test
    fun `the menu offers the reset only while the catalog holds the tea`() {
        show(TeaDetailScreenTest.sample().copy(catalogReset = null))
        openMenu()
        compose.onNodeWithText("Favorit").assertIsDisplayed()
        compose.onNodeWithText("Aus Katalog zurücksetzen").assertDoesNotExist()
    }

    @Test
    fun `the reset asks first and says what changes and what stays`() {
        show(offered())

        openMenu()
        compose.onNodeWithText("Aus Katalog zurücksetzen").performClick()

        compose.onNodeWithText("Aus Katalog zurücksetzen?").assertIsDisplayed()
        compose.onNodeWithText(
            "Beschreibung und Katalog-Rezepte werden durch die aktuellen aus dem Katalog ersetzt. Deine eigenen " +
                "Rezepte, Vorrat, Fotos, Notizen, Favorit und Journal bleiben, wie sie sind.",
        ).assertIsDisplayed()
        compose.onNodeWithText("werden weggelassen", substring = true).assertDoesNotExist()
        compose.onNodeWithText("wird weggelassen", substring = true).assertDoesNotExist()
        assertThat(calls).isEmpty()

        compose.onNodeWithText("Zurücksetzen").performClick()
        compose.onNodeWithTag(TEA_DETAIL_RESET_DIALOG_TAG).assertDoesNotExist()
        assertThat(calls).containsExactly("reset")
    }

    @Test
    fun `Abbrechen resets nothing`() {
        show(offered())

        openMenu()
        compose.onNodeWithText("Aus Katalog zurücksetzen").performClick()
        compose.onNodeWithText("Abbrechen").performClick()

        compose.onNodeWithTag(TEA_DETAIL_RESET_DIALOG_TAG).assertDoesNotExist()
        assertThat(calls).isEmpty()
    }

    @Test
    fun `the confirmation says which catalog recipes the limit leaves out`() {
        show(offered(leftOut = 2))
        openMenu()
        compose.onNodeWithText("Aus Katalog zurücksetzen").performClick()
        compose.onNodeWithText(
            "2 Katalog-Rezepte passen nicht mehr dazu (höchstens 10 Rezepte) und werden weggelassen.",
        ).assertIsDisplayed()
    }

    @Test
    fun `one recipe left out is said in the singular`() {
        show(offered(leftOut = 1))
        openMenu()
        compose.onNodeWithText("Aus Katalog zurücksetzen").performClick()
        compose.onNodeWithText(
            "Ein Katalog-Rezept passt nicht mehr dazu (höchstens 10 Rezepte) und wird weggelassen.",
        ).assertIsDisplayed()
    }

    @Test
    fun `during the tea's brew the reset stays offered`() {
        // Rule 8: the running session keeps its own steps; only Archivieren and Löschen wait for its end.
        show(offered().copy(inActiveBrew = true))
        openMenu()
        compose.onNode(hasText("Aus Katalog zurücksetzen") and hasClickAction()).assertIsEnabled()
    }

    @Test
    fun `a new alert closes the confirmation for good`() {
        show(offered())
        openMenu()
        compose.onNodeWithText("Aus Katalog zurücksetzen").performClick()
        compose.onNodeWithTag(TEA_DETAIL_RESET_DIALOG_TAG).assertIsDisplayed()

        alertKey = "brew-1"
        compose.waitForIdle()
        compose.onNodeWithTag(TEA_DETAIL_RESET_DIALOG_TAG).assertDoesNotExist()

        alertKey = null
        compose.waitForIdle()
        compose.onNodeWithTag(TEA_DETAIL_RESET_DIALOG_TAG).assertDoesNotExist()
        assertThat(calls).isEmpty()
    }
}
