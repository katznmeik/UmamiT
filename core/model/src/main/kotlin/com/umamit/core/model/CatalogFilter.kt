package com.umamit.core.model

/** How the catalog list is ordered (CAT-10): grouped by *Sorte* (the default, D-073), by name or by price per 100 g. */
enum class CatalogSort { TYPE, NAME, PRICE }

/** The flavoured / pure filter (CAT-10): all teas, pure ones only („Pur", the default, D-073) or flavoured ones. */
enum class FlavourFilter { ALL, PURE, FLAVOURED }

/**
 * What the catalog list shows (CAT-10, SCR-19). The defaults are the list's default view
 * (D-073): the green teas grouped by *Sorte*, available teas only, „Pur" on.
 *
 * @property search the search text over name, secondary name, description, cultivar and region; blank = no search.
 * @property types the *Sorten* to show; empty = every one.
 * @property origins the growing countries to show; empty = every one.
 * @property minUmami only teas whose umami scale reaches this level; null = no umami filter.
 * @property kind the catalog kind shown (D-108): the green teas by default, or another one such as the
 *   *Kräutertee* view (v0.2.1, D-093). [flavour] („Pur", Q14) and [minUmami] (Q19) apply to the green kind only.
 * @property marked the *Gemerkt* view (v0.2.2, CAT-18, D-097): every marked tea, green and herbal,
 *   sold out or no longer in the range, grouped by *Sorte*; only [search] applies in it. The other
 *   filters are kept for when the view is left.
 */
data class CatalogFilter(
    val search: String = "",
    val types: Set<String> = emptySet(),
    val origins: Set<String> = emptySet(),
    val organicOnly: Boolean = false,
    val flavour: FlavourFilter = FlavourFilter.PURE,
    val minUmami: UmamiLevel? = null,
    val ownedOnly: Boolean = false,
    val availableOnly: Boolean = true,
    val newOnly: Boolean = false,
    val sort: CatalogSort = CatalogSort.TYPE,
    val kind: CatalogKind = CatalogKind.GREEN,
    val marked: Boolean = false,
) {
    /** The default kind is shown: the green-only rules („Pur", Umami) apply. */
    val isGreen: Boolean get() = kind == CatalogKind.GREEN

    /**
     * The filters that differ from the default view, without search and sort (the filter button's
     * count); a kind chip other than the default counts, „Pur" and Umami don't in its view (SCR-19).
     */
    val changedFilters: Int
        get() = listOf(
            types.isNotEmpty(),
            origins.isNotEmpty(),
            organicOnly,
            isGreen && flavour != FlavourFilter.PURE,
            isGreen && minUmami != null,
            ownedOnly,
            !availableOnly,
            newOnly,
            !isGreen,
        ).count { it }

    /** These filters without any: every tea of the current range of the kind, same search and sort. */
    fun withoutFilters(): CatalogFilter = CatalogFilter(
        search = search,
        flavour = FlavourFilter.ALL,
        availableOnly = false,
        sort = sort,
        kind = kind,
    )

    /**
     * A kind chip (SCR-19, D-093, D-108): the teas of [kind] instead of the current kind; the *Sorten*
     * chosen belong to the other kind and are dropped, the rest is kept.
     */
    fun withKind(kind: CatalogKind): CatalogFilter =
        if (kind == this.kind) this else copy(kind = kind, types = emptySet())

    companion object {
        /** The list's default view (D-073). */
        val DEFAULT = CatalogFilter()
    }
}

/** One group of the list: the teas of one *Sorte* ([teaType]; null = none), or the whole list when not grouped. */
data class CatalogSection(val teaType: String?, val teas: List<CatalogTeaSummary>)

/**
 * The catalog list as a [CatalogFilter] shows it (SCR-19).
 *
 * @property sections the teas that match, in order; one section per *Sorte* when grouped, else one.
 * @property grouped the sections are *Sorten* (sort by type).
 * @property totalCount every tea of the current range of the shown kind, for the
 *   header („274 Grüntees", „38 Kräutertees", CAT-07 AC6).
 * @property hiddenMatches search hits the filters hide („searchable but hidden", D-073), those of the
 *   other kind included (CAT-10); 0 without a search.
 * @property types every *Sorte* of the shown kind, for the filter sheet.
 * @property origins every growing country of the shown kind, for the filter sheet.
 * @property lastRefreshAtMillis when the last successful refresh finished (CAT-07 AC6).
 * @property catalogCount every tea of the current range, green and herbal: none = never loaded.
 * @property hiddenInOtherKinds the [hiddenMatches] of each other kind, shown by that kind's view; kinds
 *   without a hidden hit are left out.
 */
data class CatalogListing(
    val sections: List<CatalogSection>,
    val grouped: Boolean,
    val totalCount: Int,
    val hiddenMatches: Int,
    val types: List<String>,
    val origins: List<String>,
    val lastRefreshAtMillis: Long?,
    val catalogCount: Int = totalCount,
    val hiddenInOtherKinds: Map<CatalogKind, Int> = emptyMap(),
) {
    /** The [hiddenMatches] that belong to any other kind. */
    val hiddenInOtherView: Int get() = hiddenInOtherKinds.values.sum()

    val teas: List<CatalogTeaSummary> get() = sections.flatMap { it.teas }

    companion object {
        val EMPTY = CatalogListing(emptyList(), grouped = true, 0, 0, emptyList(), emptyList(), null)
    }
}
