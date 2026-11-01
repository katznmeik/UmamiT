package com.umamit.feature.collection.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.StockLevel
import com.umamit.core.model.UmamiLevel
import com.umamit.core.ui.STOCK_SHEET_FIELD_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The stateless tea detail (SCR-09, D-100): sections, ⋮, the delete question, the archived state, alerts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = TeaDetailActions(
        back = { calls += "back" },
        brew = { calls += "brew:$it" },
        editTea = { calls += "editTea" },
        editRecipe = { calls += "editRecipe:$it" },
        addRecipe = { calls += "addRecipe" },
        toggleFavorite = { calls += "favorite" },
        archive = { calls += "archive" },
        restore = { calls += "restore" },
        delete = { calls += "delete" },
        correctStock = { calls += "stock:$it" },
        openShop = { calls += "shop:$it" },
    )

    private var alertKey by mutableStateOf<String?>(null)

    private fun show(tea: TeaDetail = sample()) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(tea), actions, alertKey = alertKey, photo = null)
            }
        }
        compose.waitForIdle()
    }

    private fun openMenu() = compose.onNodeWithContentDescription("Weitere Optionen").performClick()

    private fun scrollTo(text: String) = compose.onNodeWithText(text, substring = true).performScrollTo()

    @Test
    fun `the top shows names, Sorte, umami and sessions, last brew and stock`() {
        show()

        compose.onNodeWithText("Morgennebel").assertIsDisplayed()
        compose.onNodeWithText("Asagiri").assertIsDisplayed()
        compose.onNodeWithText("Sencha").assertIsDisplayed()
        compose.onNodeWithContentDescription("Umami: hoch").assertExists()
        compose.onNodeWithContentDescription("12 Sitzungen").assertExists()
        compose.onNodeWithContentDescription("Zuletzt gebrüht am 21. Okt. 2026").assertExists()
        compose.onNodeWithContentDescription("Vorrat: 38\u00A0g").assertExists()
    }

    @Test
    fun `a tea never brewed says so`() {
        show(sample().copy(sessionCount = 0, lastBrewed = null))

        compose.onNodeWithContentDescription("0 Sitzungen").assertExists()
        compose.onNodeWithContentDescription("Noch nie gebrüht").assertExists()
    }

    @Test
    fun `REZEPTE shows the key values and the default, a tap brews it, the pencil edits it`() {
        show()

        scrollTo("Kyusu 250 ml · 5\u00A0g · 250\u00A0ml · 70\u00A0°C · 3 Aufgüsse").assertIsDisplayed()
        compose.onNodeWithText("Standard").assertExists()
        compose.onNodeWithTag(teaDetailRecipeTag("kalt")).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Kyusu bearbeiten").performScrollTo().performClick()
        compose.onNodeWithTag(TEA_DETAIL_ADD_RECIPE_TAG).performScrollTo().performClick()

        assertThat(calls).containsExactly("brew:kalt", "editRecipe:standard", "addRecipe").inOrder()
    }

    @Test
    fun `Rezept hinzufuegen is hidden at 10 recipes`() {
        val recipe = sample().recipes[1]
        show(sample().copy(recipes = (0 until 10).map { recipe.copy(id = "r$it") }))

        compose.onNodeWithTag(TEA_DETAIL_ADD_RECIPE_TAG).assertDoesNotExist()
    }

    @Test
    fun `Bruehen opens the setup with the default recipe, the pencil FAB the tea editor`() {
        show()

        compose.onNodeWithTag(TEA_DETAIL_BREW_TAG).performClick()
        compose.onNodeWithTag(TEA_DETAIL_EDIT_TAG).performClick()

        assertThat(calls).containsExactly("brew:null", "editTea").inOrder()
    }

    @Test
    fun `VORRAT shows the stock, Bestand korrigieren opens the sheet and saves`() {
        show()

        scrollTo("38\u00A0g übrig").assertIsDisplayed()
        compose.onNodeWithTag(TEA_DETAIL_CORRECT_STOCK_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(STOCK_SHEET_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithText("Speichern").performClick()

        assertThat(calls).containsExactly("stock:38.0")
        compose.onNodeWithTag(STOCK_SHEET_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun `a low stock reads knapp and reorders with a filled button`() {
        show(sample().copy(stockGrams = 12.0, stockLevel = StockLevel.LOW, reorder = Reorder(URL, prominent = true)))

        scrollTo("12\u00A0g übrig · knapp").assertIsDisplayed()
        compose.onNodeWithTag(TEA_DETAIL_REORDER_TAG).performScrollTo().performClick()

        assertThat(calls).containsExactly("shop:$URL")
    }

    @Test
    fun `an empty tea reads Leer`() {
        show(sample().copy(stockGrams = 0.0, stockLevel = StockLevel.EMPTY, reorder = Reorder(URL, prominent = true)))

        compose.onNodeWithContentDescription("Vorrat: Leer").assertExists()
        compose.onNodeWithTag(TEA_DETAIL_REORDER_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `without a reorder link nothing is offered`() {
        show(sample().copy(reorder = null))

        compose.onNodeWithTag(TEA_DETAIL_REORDER_TAG).assertDoesNotExist()
    }

    @Test
    fun `BESCHREIBUNG, NOTIZEN and DETAILS show their content, and are left out without it`() {
        show()
        scrollTo("Ein erfundener Sencha").assertIsDisplayed()
        scrollTo("Zweiter Aufguss").assertIsDisplayed()
        scrollTo("Japan, Shizuoka").assertIsDisplayed()
        scrollTo("Erste Pflückung 2026").assertIsDisplayed()
    }

    @Test
    fun `empty sections are left out`() {
        show(sample().copy(description = null, notes = null, details = TeaFacts()))

        compose.onNodeWithContentDescription("Beschreibung").assertDoesNotExist()
        compose.onNodeWithContentDescription("Notizen").assertDoesNotExist()
        compose.onNodeWithContentDescription("Details").assertDoesNotExist()
    }

    @Test
    fun `the menu offers Favorit, Archivieren and Loeschen`() {
        show()

        openMenu()
        compose.onNodeWithText("Favorit").performClick()
        openMenu()
        compose.onNodeWithText("Archivieren").performClick()

        assertThat(calls).containsExactly("favorite", "archive").inOrder()
    }

    @Test
    fun `Loeschen asks first and offers Stattdessen archivieren`() {
        show()

        openMenu()
        compose.onNodeWithText("Löschen").performClick()
        compose.onNodeWithText("Morgennebel löschen?").assertIsDisplayed()
        compose.onNodeWithText("Deine 12 Journal-Einträge bleiben erhalten.").assertIsDisplayed()
        compose.onNodeWithText("Stattdessen archivieren").performClick()
        assertThat(calls).containsExactly("archive")

        openMenu()
        compose.onNodeWithText("Löschen").performClick()
        // The menu has closed: the only „Löschen" left is the dialog's.
        compose.onNodeWithText("Löschen").performClick()
        compose.onNodeWithText("Morgennebel löschen?").assertDoesNotExist()
        assertThat(calls).containsExactly("archive", "delete").inOrder()
    }

    @Test
    fun `an archived tea's delete question has no Stattdessen archivieren`() {
        show(sample().copy(isArchived = true, sessionCount = 1))

        openMenu()
        compose.onNodeWithText("Löschen").performClick()

        compose.onNodeWithText("Dein Journal-Eintrag bleibt erhalten.").assertIsDisplayed()
        compose.onNodeWithText("Stattdessen archivieren").assertDoesNotExist()
    }

    @Test
    fun `during the tea's brew Archivieren and Loeschen are greyed out`() {
        show(sample().copy(inActiveBrew = true))

        openMenu()

        compose.onNode(hasText("Archivieren", substring = true) and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasText("Löschen", substring = true) and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasText("Favorit") and hasClickAction()).assertIsEnabled()
        assertThat(compose.onAllNodes(hasText("Erst die Sitzung beenden")).fetchSemanticsNodes()).hasSize(2)
    }

    @Test
    fun `an archived tea greys out Bruehen and offers Wiederherstellen`() {
        show(sample().copy(isArchived = true))

        compose.onNodeWithTag(TEA_DETAIL_BREW_TAG).assertIsNotEnabled()
        compose.onNodeWithText("Tee ist archiviert").assertIsDisplayed()
        openMenu()
        compose.onNodeWithText("Archivieren").assertDoesNotExist()
        compose.onNodeWithText("Wiederherstellen").performClick()

        assertThat(calls).containsExactly("restore")
    }

    @Test
    fun `a new alert closes the delete question for good`() {
        show()
        openMenu()
        compose.onNodeWithText("Löschen").performClick()
        compose.onNodeWithText("Morgennebel löschen?").assertIsDisplayed()

        alertKey = "brew-1"
        compose.waitForIdle()
        compose.onNodeWithText("Morgennebel löschen?").assertDoesNotExist()

        alertKey = null
        compose.waitForIdle()
        compose.onNodeWithText("Morgennebel löschen?").assertDoesNotExist()
        assertThat(calls).isEmpty()
    }

    @Test
    fun `a new alert closes the stock sheet without saving`() {
        show()
        compose.onNodeWithTag(TEA_DETAIL_CORRECT_STOCK_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(STOCK_SHEET_FIELD_TAG).assertIsDisplayed()

        alertKey = "brew-1"
        compose.waitForIdle()

        compose.onNodeWithTag(STOCK_SHEET_FIELD_TAG).assertDoesNotExist()
        assertThat(calls).isEmpty()
    }

    companion object {
        const val URL = "https://shop.example/morgennebel/XY0100-50"

        fun sample() = TeaDetail(
            id = "morgennebel",
            name = "Morgennebel",
            secondaryName = "Asagiri",
            teaType = "Sencha",
            styleKey = "sencha",
            umamiLevel = UmamiLevel.HIGH,
            sessionCount = 12,
            lastBrewed = LocalDate.of(2026, 10, 21),
            stockGrams = 38.0,
            stockLevel = StockLevel.AVAILABLE,
            recipes = listOf(
                RecipeSummary("standard", "Kyusu", true, BrewMethod.KYUSU, "Kyusu 250 ml", 5.0, 250, 70, 3),
                RecipeSummary("kalt", "Kaltaufguss", false, BrewMethod.COLD_BREW, null, 10.0, 1000, 5, 1),
            ),
            reorder = Reorder(URL, prominent = false),
            description = "Ein erfundener Sencha mit grasiger Frische und weichem Nachklang.",
            notes = "Zweiter Aufguss etwas kürzer.",
            details = TeaFacts(
                originCountry = "Japan",
                originRegion = "Shizuoka",
                cultivar = "Yabukita",
                harvestYear = 2026,
                harvestSeason = HarvestSeason.FIRST_FLUSH,
                vendor = "Teehaus Beispiel",
            ),
        )
    }
}
