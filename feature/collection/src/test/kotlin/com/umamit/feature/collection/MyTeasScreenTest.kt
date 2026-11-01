package com.umamit.feature.collection

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CollectionSort
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeCollectionList
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * *Meine Tees* (SCR-08; TEA-08–TEA-11, D-100) through its view model on fakes and Compose: search, the chips,
 * the remembered sort, *Archiviert* with *Wiederherstellen*, the FAB's choice, both empty states, a row's tap and
 * the TalkBack labels. Invented teas (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class MyTeasScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository(UserSettings(lowStockGrams = 20.0))
    private val sessions = FakeSessions(collection, settings)
    private val opened = mutableListOf<String>()
    private var fromCatalog = 0
    private var withoutCatalog = 0

    private fun show(vararg teas: Tea, stock: Map<String, Double> = emptyMap()) {
        runBlocking {
            teas.forEach { collection.teas.saveTea(it) }
            stock.forEach { (teaId, grams) ->
                collection.stock.addEvent(
                    InventoryEvent("e-$teaId", teaId, InventoryEventType.PURCHASE, grams, occurredAtMillis = 1L),
                )
            }
        }
        val vm = MyTeasViewModel(
            FakeCollectionList(sessions),
            settings,
            FakeTeaArchive(sessions),
            PhotoFiles(folder.newFolder()),
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                MyTeasRoute(
                    onOpenTea = { opened += it },
                    onAddFromCatalog = { fromCatalog++ },
                    onAddWithoutCatalog = { withoutCatalog++ },
                    viewModel = vm,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun row(id: String) = compose.onNodeWithTag(myTeaRowTag(id), useUnmergedTree = true)

    @Test
    fun theSearchNarrowsTheList() {
        show(tea("a", "Asamushi"), tea("b", "Bancha"))

        compose.onNodeWithTag(MY_TEAS_SEARCH_TAG).performTextInput("bAn")
        compose.waitForIdle()

        row("b").assertExists()
        row("a").assertDoesNotExist()
    }

    @Test
    fun favoritenShowsOnlyTheFavourites() {
        show(tea("a", "Asamushi", isFavorite = true), tea("b", "Bancha"))

        compose.onNodeWithText("Favoriten").performClick()
        compose.waitForIdle()

        row("a").assertExists()
        row("b").assertDoesNotExist()
    }

    @Test
    fun kategorieOffersThePresentCategoriesInAMenu() {
        show(tea("a", "Asamushi"), tea("o", "Oriental", category = TeaCategory.OOLONG))

        compose.onNodeWithText("Kategorie").performClick()
        // The menu's entry, after the row's meta line „Oolong".
        compose.onAllNodesWithText("Oolong").onLast().performClick()
        compose.waitForIdle()

        row("o").assertExists()
        row("a").assertDoesNotExist()
        // A category the collection doesn't hold is not offered.
        compose.onNodeWithText("Weißer Tee").assertDoesNotExist()
    }

    @Test
    fun sorteFiltersByTheTeasSorte() {
        show(tea("a", "Asamushi", teaType = "Sencha"), tea("b", "Bancha", teaType = "Bancha"))

        compose.onNodeWithText("Sorte").performClick()
        // The menu's entry, in its popup after the rows' meta line „Sencha".
        compose.onAllNodesWithText("Sencha").onLast().performClick()
        compose.waitForIdle()

        row("a").assertExists()
        row("b").assertDoesNotExist()
    }

    @Test
    fun vorratShowsTheTeasThatAreKnappOrLeer() {
        show(
            tea("a", "Asamushi"),
            tea("b", "Bancha").copy(isFlavored = true),
            stock = mapOf("a" to 8.0, "b" to 100.0),
        )

        compose.onNodeWithText("Vorrat").performClick()
        // The menu's entry, after the row's stock chip „Knapp".
        compose.onAllNodesWithText("Knapp").onLast().performClick()
        compose.waitForIdle()
        row("a").assertExists()
        row("b").assertDoesNotExist()
    }

    @Test
    fun aromatisiertShowsOnlyFlavouredTeas() {
        show(tea("a", "Asamushi"), tea("b", "Bancha").copy(isFlavored = true))

        compose.onNodeWithText("Aromatisiert").performClick()
        compose.waitForIdle()

        row("b").assertExists()
        row("a").assertDoesNotExist()
    }

    @Test
    fun theChosenSortIsRemembered() {
        show(tea("a", "Asamushi"), tea("b", "Bancha"))

        compose.onNodeWithTag(MY_TEAS_SORT_TAG).performClick()
        compose.onNodeWithText("Name").performClick()
        compose.waitForIdle()

        assertThat(runBlocking { settings.settings.first().collectionSort }).isEqualTo(CollectionSort.NAME)
        compose.onNodeWithContentDescription("Sortierung: Name").assertExists()
    }

    @Test
    fun archiviertListsArchivedTeasWithWiederherstellen() {
        show(tea("a", "Asamushi"), tea("x", "Alter Tee").copy(isArchived = true))
        row("x").assertDoesNotExist()

        compose.onNodeWithText("Archiviert").performClick()
        compose.waitForIdle()
        row("a").assertDoesNotExist()
        compose.onNodeWithText("Wiederherstellen").performClick()
        compose.waitForIdle()

        assertThat(runBlocking { collection.teas.getTea("x")!!.isArchived }).isFalse()
    }

    @Test
    fun theFabOffersAusDemKatalogAndOhneKatalog() {
        show(tea("a", "Asamushi"))

        compose.onNodeWithTag(MY_TEAS_ADD_TAG).performClick()
        compose.onNodeWithText("Aus dem Katalog").performClick()
        compose.waitForIdle()
        assertThat(fromCatalog).isEqualTo(1)

        compose.onNodeWithTag(MY_TEAS_ADD_TAG).performClick()
        compose.onNodeWithText("Ohne Katalog").performClick()
        compose.waitForIdle()
        assertThat(withoutCatalog).isEqualTo(1)
    }

    @Test
    fun noTeasIsNochKeineTeesWithImKatalogStoebern() {
        show()

        compose.onNodeWithText("Noch keine Tees").assertExists()
        compose.onNodeWithText("Im Katalog stöbern").performClick()

        assertThat(fromCatalog).isEqualTo(1)
    }

    @Test
    fun aSearchWithoutHitsIsKeinTeePasstAndCanBeReset() {
        show(tea("a", "Asamushi"))

        compose.onNodeWithTag(MY_TEAS_SEARCH_TAG).performTextInput("zzz")
        compose.waitForIdle()
        compose.onNodeWithText("Kein Tee passt zu deiner Suche.").assertExists()

        compose.onNodeWithText("Filter zurücksetzen").performClick()
        compose.waitForIdle()
        row("a").assertExists()
    }

    @Test
    fun aRowTapOpensTheTea() {
        show(tea("a", "Asamushi"))

        row("a").performClick()

        assertThat(opened).containsExactly("a")
    }

    @Test
    fun rowsChipsAndStarsHaveTalkBackLabels() {
        show(
            tea("a", "Asamushi", isFavorite = true),
            tea("b", "Bancha"),
            stock = mapOf("a" to 38.0, "b" to 8.0),
        )

        compose.onNode(hasContentDescription("Favorit"), useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("38 g übrig"), useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Knapp, 8 g übrig"), useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Sortierung: Zuletzt gebrüht").assertExists()
        compose.onNodeWithText("Favoriten").performClick()
        compose.onNodeWithText("Favoriten").assertIsSelected()
    }

    private fun tea(
        id: String,
        name: String,
        teaType: String? = null,
        isFavorite: Boolean = false,
        category: TeaCategory = TeaCategory.GREEN,
    ) = Tea(
        id = id,
        name = name,
        category = category,
        form = TeaForm.LOOSE_LEAF,
        teaType = teaType,
        isFavorite = isFavorite,
    )
}
