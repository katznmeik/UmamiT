package com.umamit.core.domain.collection

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.CollectionFilter
import com.umamit.core.model.CollectionRow
import com.umamit.core.model.CollectionSort
import com.umamit.core.model.StockLevel
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import org.junit.Test

/** *Meine Tees*' search, filters and sort (TEA-09–TEA-11, D-100), on synthetic teas. */
class CollectionBrowserTest {
    private fun tea(id: String, name: String = id, category: TeaCategory = TeaCategory.GREEN) =
        Tea(id = id, name = name, category = category, form = TeaForm.LOOSE_LEAF)

    private fun row(
        tea: Tea,
        added: Long = 0,
        brewed: Long? = null,
        grams: Double? = null,
        lowStockGrams: Double = 15.0,
    ) = CollectionRow.of(tea, added, brewed, grams, lowStockGrams)

    private fun ids(rows: List<CollectionRow>, filter: CollectionFilter = CollectionFilter(), sort: CollectionSort) =
        CollectionBrowser.listing(rows, filter, sort).rows.map { it.tea.id }

    private fun search(rows: List<CollectionRow>, text: String) =
        ids(rows, CollectionFilter(search = text), CollectionSort.NAME)

    // --- Search (TEA-09) ---

    @Test
    fun theSearchFindsATeaByEachOfItsFields() {
        val rows = listOf(
            row(tea("name", name = "Tanabata")),
            row(tea("secondary").copy(secondaryName = "かぶせ茶 Kabuse")),
            row(tea("vendor").copy(vendor = "Teehaus Nord")),
            row(tea("region").copy(originRegion = "Kagoshima")),
            row(tea("cultivar").copy(cultivar = "Saemidori")),
            row(tea("notes").copy(notes = "Mit Zitronenschale probieren")),
        )

        assertThat(search(rows, "tanab")).containsExactly("name")
        assertThat(search(rows, "kabuse")).containsExactly("secondary")
        assertThat(search(rows, "かぶせ茶")).containsExactly("secondary")
        assertThat(search(rows, "nord")).containsExactly("vendor")
        assertThat(search(rows, "kago")).containsExactly("region")
        assertThat(search(rows, "saemi")).containsExactly("cultivar")
        assertThat(search(rows, "zitronen")).containsExactly("notes")
    }

    @Test
    fun theSearchIgnoresCaseAndAccentsAndNeedsEveryWord() {
        val rows = listOf(
            row(tea("a", name = "Hōjicha Abend").copy(cultivar = "Yabukita")),
            row(tea("b", name = "Hojicha Morgen")),
        )

        assertThat(search(rows, "HOJI")).containsExactly("a", "b").inOrder()
        assertThat(search(rows, "hōjícha")).containsExactly("a", "b").inOrder()
        // Several words: each must start a word of any field.
        assertThat(search(rows, "hoji yabu")).containsExactly("a")
        assertThat(search(rows, "jicha")).isEmpty()
    }

    @Test
    fun theSearchDoesNotReadTheSorteOrDescription() {
        // TEA-09 names six fields; the Sorte has its own chip.
        val rows = listOf(row(tea("a").copy(teaType = "Gyokuro", description = "Ein Schattentee")))

        assertThat(search(rows, "gyokuro")).isEmpty()
        assertThat(search(rows, "schatten")).isEmpty()
        assertThat(search(rows, "  ")).containsExactly("a")
    }

    // --- Filters (TEA-10) ---

    @Test
    fun withoutFiltersEveryTeaThatIsNotArchivedShows() {
        val rows = listOf(row(tea("a")), row(tea("b").copy(isArchived = true)))

        assertThat(ids(rows, sort = CollectionSort.NAME)).containsExactly("a")
    }

    @Test
    fun favoritenShowsOnlyFavourites() {
        val rows = listOf(row(tea("a").copy(isFavorite = true)), row(tea("b")))

        assertThat(ids(rows, CollectionFilter(favoritesOnly = true), CollectionSort.NAME)).containsExactly("a")
    }

    @Test
    fun kategorieShowsTheChosenCategories() {
        val rows = listOf(
            row(tea("green")),
            row(tea("oolong", category = TeaCategory.OOLONG)),
            row(tea("black", category = TeaCategory.BLACK)),
        )
        val filter = CollectionFilter(categories = setOf(TeaCategory.OOLONG, TeaCategory.BLACK))

        assertThat(ids(rows, filter, CollectionSort.NAME)).containsExactly("black", "oolong").inOrder()
    }

    @Test
    fun sorteShowsTheChosenDisplaySorten() {
        val rows = listOf(
            row(tea("a").copy(teaType = "Gyokuro")),
            row(tea("b").copy(teaType = "Sencha")),
            row(tea("c")),
        )

        assertThat(ids(rows, CollectionFilter(sorten = setOf("Sencha")), CollectionSort.NAME)).containsExactly("b")
    }

    @Test
    fun vorratShowsKnappOrLeerByTheTeasOwnThresholdFirst() {
        val rows = listOf(
            row(tea("plenty"), grams = 80.0),
            row(tea("low"), grams = 15.0),
            row(tea("ownThreshold").copy(lowStockGrams = 50.0), grams = 40.0),
            row(tea("empty"), grams = 0.0),
            row(tea("untracked")),
        )

        assertThat(ids(rows, CollectionFilter(stock = setOf(StockLevel.LOW)), CollectionSort.NAME))
            .containsExactly("low", "ownThreshold").inOrder()
        assertThat(ids(rows, CollectionFilter(stock = setOf(StockLevel.EMPTY)), CollectionSort.NAME))
            .containsExactly("empty")
        assertThat(
            ids(rows, CollectionFilter(stock = setOf(StockLevel.LOW, StockLevel.EMPTY)), CollectionSort.NAME),
        ).containsExactly("low", "ownThreshold", "empty").inOrder()
    }

    @Test
    fun aromatisiertShowsOnlyFlavouredTeas() {
        val rows = listOf(row(tea("a").copy(isFlavored = true)), row(tea("b")))

        assertThat(ids(rows, CollectionFilter(flavoredOnly = true), CollectionSort.NAME)).containsExactly("a")
    }

    @Test
    fun archiviertShowsOnlyArchivedTeasAndTheRestStillApplies() {
        val rows = listOf(
            row(tea("kept").copy(isFavorite = true)),
            row(tea("gone fav", name = "Alt Gyokuro").copy(isArchived = true, isFavorite = true)),
            row(tea("gone", name = "Alt Sencha").copy(isArchived = true)),
        )

        assertThat(ids(rows, CollectionFilter(archived = true), CollectionSort.NAME))
            .containsExactly("gone fav", "gone").inOrder()
        assertThat(ids(rows, CollectionFilter(archived = true, favoritesOnly = true), CollectionSort.NAME))
            .containsExactly("gone fav")
        assertThat(ids(rows, CollectionFilter(archived = true, search = "sencha"), CollectionSort.NAME))
            .containsExactly("gone")
    }

    @Test
    fun theChipsOfferOnlyValuesPresentOnTheCurrentSide() {
        val rows = listOf(
            row(tea("a", category = TeaCategory.OOLONG).copy(teaType = "Tieguanyin")),
            row(tea("b").copy(teaType = "Sencha")),
            row(tea("c").copy(teaType = "Bancha")),
            row(tea("d")),
            row(tea("e", category = TeaCategory.BLACK).copy(teaType = "Assam", isArchived = true)),
        )

        // Even with filters on, the chips list what the side holds, so a choice can be widened again.
        val listing = CollectionBrowser.listing(
            rows,
            CollectionFilter(categories = setOf(TeaCategory.OOLONG)),
            CollectionSort.NAME,
        )
        assertThat(listing.categories).containsExactly(TeaCategory.GREEN, TeaCategory.OOLONG).inOrder()
        assertThat(listing.sorten).containsExactly("Bancha", "Sencha", "Tieguanyin").inOrder()
        assertThat(listing.ownedCount).isEqualTo(4)

        val archived = CollectionBrowser.listing(rows, CollectionFilter(archived = true), CollectionSort.NAME)
        assertThat(archived.categories).containsExactly(TeaCategory.BLACK)
        assertThat(archived.sorten).containsExactly("Assam")
        assertThat(archived.ownedCount).isEqualTo(4)
    }

    // --- Sort (TEA-11) ---

    @Test
    fun zuletztGebruehtShowsTheLatestBrewFirstThenTheNeverBrewedAToZThenTheEmpty() {
        val rows = listOf(
            row(tea("never b", name = "Bancha"), grams = 50.0),
            row(tea("old", name = "Zuerst"), brewed = 100, grams = 50.0),
            row(tea("new", name = "Yame"), brewed = 300),
            row(tea("empty brewed", name = "Asamushi"), brewed = 500, grams = 0.0),
            row(tea("never a", name = "aracha")),
            row(tea("empty never", name = "Benifuki"), grams = 0.0),
        )

        assertThat(ids(rows, sort = CollectionSort.LAST_BREWED))
            .containsExactly("new", "old", "never a", "never b", "empty brewed", "empty never").inOrder()
    }

    @Test
    fun nameSortsAToZIgnoringCaseAndAccentsWithEmptyTeasLast() {
        val rows = listOf(
            row(tea("c", name = "Ōita Kamairicha")),
            row(tea("a", name = "anji Bai Cha")),
            row(tea("empty", name = "Aaa Bancha"), grams = 0.0),
            row(tea("b", name = "Benifuki")),
        )

        assertThat(ids(rows, sort = CollectionSort.NAME)).containsExactly("a", "b", "c", "empty").inOrder()
    }

    @Test
    fun zuletztHinzugefuegtShowsTheNewestFirstWithEmptyTeasLast() {
        val rows = listOf(
            row(tea("first"), added = 1),
            row(tea("empty newest"), added = 9, grams = 0.0),
            row(tea("third"), added = 3),
            row(tea("second"), added = 2),
        )

        assertThat(ids(rows, sort = CollectionSort.RECENTLY_ADDED))
            .containsExactly("third", "second", "first", "empty newest").inOrder()
    }

    @Test
    fun vorratRunsLowToHighThenTeasWithoutStockEventsThenTheEmpty() {
        val rows = listOf(
            row(tea("untracked", name = "Bancha")),
            row(tea("plenty", name = "Aracha"), grams = 90.0),
            row(tea("empty", name = "Aaa"), grams = 0.0),
            row(tea("low", name = "Zuletzt"), grams = 4.5),
            row(tea("untracked2", name = "Aojiru")),
        )

        assertThat(ids(rows, sort = CollectionSort.STOCK))
            .containsExactly("low", "plenty", "untracked2", "untracked", "empty").inOrder()
    }

    @Test
    fun tiesBreakByNameThenId() {
        val rows = listOf(
            row(tea("z", name = "Sencha"), added = 5, brewed = 7, grams = 20.0),
            row(tea("y", name = "Sencha"), added = 5, brewed = 7, grams = 20.0),
            row(tea("x", name = "Bancha"), added = 5, brewed = 7, grams = 20.0),
            row(tea("e2", name = "Leer"), added = 5, grams = 0.0),
            row(tea("e1", name = "Leer"), added = 5, grams = 0.0),
        )

        for (sort in CollectionSort.entries) {
            assertThat(ids(rows, sort = sort)).containsExactly("x", "y", "z", "e1", "e2").inOrder()
        }
    }
}
