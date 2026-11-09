package com.umamit.core.domain.catalog

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.CatalogTeaSummary
import com.umamit.core.model.FlavourFilter
import com.umamit.core.model.UmamiLevel
import org.junit.Test

/**
 * The catalog kind (v0.5.0, #199, D-108, CAT-10): derived from the style; a new kind is a new list entry only.
 * Invented teas (D-016).
 */
class CatalogKindTest {
    private val oolong = CatalogKind("oolong", setOf("oolong"))
    private val kinds = CatalogKind.ALL + oolong

    private fun tea(key: String, name: String, styleKey: String?, type: String? = "Sorte", flavored: Boolean = false) =
        CatalogTeaSummary(
            key = key,
            name = name,
            secondaryName = null,
            teaType = type,
            basePriceCentsPer100g = 1000,
            available = true,
            isFlavored = flavored,
            umamiLeaves = 4,
            kind = CatalogKind.of(styleKey, kinds),
        )

    private val teas = listOf(
        tea("A1", "Gruen Rein", "sencha"),
        tea("A2", "Gruen Aroma", "sencha", flavored = true),
        tea("A3", "Gruen Ohne Style", null),
        tea("H1", "Wiese", "herbal", "Mischungen", flavored = true),
        tea("O1", "Berg", "oolong", "Oolong"),
    )

    private fun list(filter: CatalogFilter, hits: Set<String>? = null) =
        CatalogBrowser.listing(teas, filter, hits, null)

    @Test
    fun aTeasKindComesFromItsStyleAndGreenIsTheDefault() {
        assertThat(CatalogKind.of("herbal")).isEqualTo(CatalogKind.HERBAL)
        assertThat(CatalogKind.of("sencha")).isEqualTo(CatalogKind.GREEN)
        assertThat(CatalogKind.of(null)).isEqualTo(CatalogKind.GREEN)
        assertThat(CatalogKind.of("oolong")).isEqualTo(CatalogKind.GREEN)
        assertThat(CatalogKind.of("oolong", kinds)).isEqualTo(oolong)
        assertThat(CatalogKind.ALL).containsExactly(
            CatalogKind.GREEN,
            CatalogKind.BLACK,
            CatalogKind.WHITE,
            CatalogKind.OOLONG,
            CatalogKind.DARK,
            CatalogKind.HERBAL,
            CatalogKind.ROOIBOS,
            CatalogKind.FRUIT,
            CatalogKind.CHAI,
        ).inOrder()
        assertThat(CatalogKind.of("rooibos")).isEqualTo(CatalogKind.ROOIBOS)
        assertThat(CatalogKind.of("fruit")).isEqualTo(CatalogKind.FRUIT)
        assertThat(CatalogKind.of("chai")).isEqualTo(CatalogKind.CHAI)
        assertThat(CatalogKind.of("flavored_white")).isEqualTo(CatalogKind.WHITE)
        assertThat(CatalogKind.of("oolong_generic")).isEqualTo(CatalogKind.OOLONG)
        assertThat(CatalogKind.of("sheng")).isEqualTo(CatalogKind.DARK)
        assertThat(CatalogKind.of("shou")).isEqualTo(CatalogKind.DARK)
        assertThat(CatalogFilter.DEFAULT.kind).isEqualTo(CatalogKind.GREEN)
    }

    @Test
    fun aThirdKindBrowsesWithoutAnyChangeToTheBrowser() {
        val listing = list(CatalogFilter(kind = oolong))
        assertThat(listing.teas.map { it.name }).containsExactly("Berg")
        assertThat(listing.totalCount).isEqualTo(1)
        assertThat(listing.types).containsExactly("Oolong")
        assertThat(list(CatalogFilter()).teas.map { it.name }).containsExactly("Gruen Rein", "Gruen Ohne Style")
        // Like herbal, a non-green kind ignores „Pur" and Umami.
        val filter = CatalogFilter(kind = oolong, flavour = FlavourFilter.FLAVOURED, minUmami = UmamiLevel.HIGH)
        assertThat(list(filter).teas.map { it.name }).containsExactly("Berg")
    }

    @Test
    fun hiddenSearchHitsAreCountedPerOtherKind() {
        val listing = list(CatalogFilter(search = "x"), hits = setOf("A1", "A2", "H1", "O1"))
        assertThat(listing.hiddenMatches).isEqualTo(2 + 1)
        assertThat(listing.hiddenInOtherKinds).containsExactly(CatalogKind.HERBAL, 1, oolong, 1)
        assertThat(listing.hiddenInOtherView).isEqualTo(2)
    }

    @Test
    fun switchingKindDropsTheSortenAndKeepsTheRest() {
        val filter = CatalogFilter(search = "x", types = setOf("Sencha"), organicOnly = true).withKind(oolong)
        assertThat(filter).isEqualTo(CatalogFilter(search = "x", organicOnly = true, kind = oolong))
        assertThat(filter.changedFilters).isEqualTo(2)
        assertThat(filter.withoutFilters().kind).isEqualTo(oolong)
        assertThat(filter.withKind(CatalogKind.GREEN).changedFilters).isEqualTo(1)
    }
}
