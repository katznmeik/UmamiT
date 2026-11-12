package com.umamit.core.domain.catalog

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.CatalogSort
import com.umamit.core.model.CatalogTeaSummary
import com.umamit.core.model.FlavourFilter
import com.umamit.core.model.UmamiLevel
import org.junit.Test

/** The Katalog without a kind (v0.6.1, #247, D-114 Q13–Q15, CAT-10, SCR-19). Invented teas (D-016). */
class CatalogNoKindTest {
    private fun tea(
        key: String,
        kind: CatalogKind,
        type: String?,
        flavored: Boolean = false,
        umami: Int? = null,
        price: Long = 1000,
    ) = CatalogTeaSummary(
        key = key,
        name = key,
        secondaryName = null,
        teaType = type,
        basePriceCentsPer100g = price,
        available = true,
        isFlavored = flavored,
        umamiLeaves = umami,
        kind = kind,
    )

    private val teas = listOf(
        tea("Herb", CatalogKind.HERBAL, "Mischungen", flavored = true),
        tea("Chai1", CatalogKind.CHAI, "Gewürz", flavored = true, price = 500),
        tea("Gyo", CatalogKind.GREEN, "Gyokuro", umami = 5, price = 3000),
        tea("Ban", CatalogKind.GREEN, "Bancha", umami = 1, price = 800),
        tea("Aroma", CatalogKind.GREEN, "Sencha", flavored = true, umami = 2),
        tea("Assam1", CatalogKind.BLACK, "Assam", flavored = true, price = 900),
    )

    private fun list(filter: CatalogFilter, hits: Set<String>? = null) =
        CatalogBrowser.listing(teas, filter, hits, null)

    @Test
    fun theDefaultViewHasNoKindAndShowsEveryTeaGroupedByKindInTheChipOrder() {
        assertThat(CatalogFilter.DEFAULT.kind).isNull()
        val listing = list(CatalogFilter.DEFAULT)
        assertThat(listing.byKind).isTrue()
        assertThat(listing.totalCount).isEqualTo(teas.size)
        // Green, Black, Herbal, Chai: the chip order; within each kind a section per *Sorte*.
        assertThat(listing.sections.map { it.kind to it.teaType }).containsExactly(
            CatalogKind.GREEN to "Bancha",
            CatalogKind.GREEN to "Gyokuro",
            CatalogKind.GREEN to "Sencha",
            CatalogKind.BLACK to "Assam",
            CatalogKind.HERBAL to "Mischungen",
            CatalogKind.CHAI to "Gewürz",
        ).inOrder()
        assertThat(listing.grouped).isTrue()
    }

    @Test
    fun aFlatSortAppliesInsideEachKindGroup() {
        val listing = list(CatalogFilter(sort = CatalogSort.PRICE))
        assertThat(listing.grouped).isFalse()
        assertThat(listing.sections.map { section -> section.kind to section.teas.map { it.key } }).containsExactly(
            CatalogKind.GREEN to listOf("Ban", "Aroma", "Gyo"),
            CatalogKind.BLACK to listOf("Assam1"),
            CatalogKind.HERBAL to listOf("Herb"),
            CatalogKind.CHAI to listOf("Chai1"),
        ).inOrder()
    }

    @Test
    fun aKindChipSelectsOneKindAndATapOnTheSelectedOneClearsIt() {
        val black = CatalogFilter.DEFAULT.toggleKind(CatalogKind.BLACK)
        assertThat(black.kind).isEqualTo(CatalogKind.BLACK)
        assertThat(black.toggleKind(CatalogKind.HERBAL).kind).isEqualTo(CatalogKind.HERBAL)
        // Never back to *Grüntee*.
        assertThat(black.toggleKind(CatalogKind.BLACK).kind).isNull()
        assertThat(CatalogFilter(kind = CatalogKind.GREEN).toggleKind(CatalogKind.GREEN).kind).isNull()
        val listing = list(black)
        assertThat(listing.byKind).isFalse()
        assertThat(listing.teas.map { it.key }).containsExactly("Assam1")
        assertThat(listing.sections.map { it.kind }).containsExactly(null)
    }

    @Test
    fun pureAndUmamiApplyInTheGreenViewOnly() {
        val strict = CatalogFilter(flavour = FlavourFilter.PURE, minUmami = UmamiLevel.HIGH)
        // Without a kind, flavoured teas and teas without umami show (D-114 Q13).
        assertThat(list(strict).teas).hasSize(teas.size)
        assertThat(strict.changedFilters).isEqualTo(0)
        val green = strict.withKind(CatalogKind.GREEN)
        assertThat(list(green).teas.map { it.key }).containsExactly("Gyo")
        assertThat(green.changedFilters).isEqualTo(1)
    }

    @Test
    fun theKindDoesNotCountAndAlleZeigenClearsIt() {
        val oolong = CatalogFilter(kind = CatalogKind.OOLONG, organicOnly = true)
        assertThat(oolong.changedFilters).isEqualTo(1)
        assertThat(oolong.withoutFilters().kind).isNull()
    }

    @Test
    fun withoutAKindNoSearchHitIsHiddenByKind() {
        val all = list(CatalogFilter(search = "x", newOnly = true), hits = setOf("Herb", "Gyo"))
        assertThat(all.hiddenMatches).isEqualTo(2)
        assertThat(all.hiddenInOtherKinds).isEmpty()
        val green = list(CatalogFilter(search = "x", kind = CatalogKind.GREEN), hits = setOf("Herb", "Gyo"))
        assertThat(green.hiddenMatches).isEqualTo(1)
        assertThat(green.hiddenInOtherKinds).containsExactly(CatalogKind.HERBAL, 1)
    }

    @Test
    fun theFilterSheetOffersEveryKindsSortenWithoutAKind() {
        assertThat(list(CatalogFilter.DEFAULT).types)
            .containsExactly("Assam", "Bancha", "Gewürz", "Gyokuro", "Mischungen", "Sencha").inOrder()
    }
}
