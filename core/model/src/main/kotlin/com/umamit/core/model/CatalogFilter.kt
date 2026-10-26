package com.umamit.core.model

/** How the catalog list is ordered (CAT-10): grouped by *Sorte* (the default, D-073), by name or by price per 100 g. */
enum class CatalogSort { TYPE, NAME, PRICE }

/** The flavoured / pure filter (CAT-10): all teas, pure ones only („Pur", the default, D-073) or flavoured ones. */
enum class FlavourFilter { ALL, PURE, FLAVOURED }

/**
 * What the catalog list shows (CAT-10, SCR-19). The defaults are the list's default view
 * (D-073): grouped by *Sorte*, available teas only, „Pur" on.
 *
 * @property search the search text over name, secondary name, description, cultivar and region; blank = no search.
 * @property types the *Sorten* to show; empty = every one.
 * @property origins the growing countries to show; empty = every one.
 * @property minUmami only teas whose umami scale reaches this level; null = no umami filter.
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
) {
    /** The filters that differ from the default view, without search and sort (the filter button's count). */
    val changedFilters: Int
        get() = listOf(
            types.isNotEmpty(),
            origins.isNotEmpty(),
            organicOnly,
            flavour != FlavourFilter.PURE,
            minUmami != null,
            ownedOnly,
            !availableOnly,
            newOnly,
        ).count { it }

    /** These filters without any: every tea of the current range, same search and sort. */
    fun withoutFilters(): CatalogFilter = CatalogFilter(
        search = search,
        flavour = FlavourFilter.ALL,
        availableOnly = false,
        sort = sort,
    )

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
 * @property totalCount every tea of the current range, for the header („274 Grüntees", CAT-07 AC6).
 * @property hiddenMatches search hits the filters hide („searchable but hidden", D-073); 0 without a search.
 * @property types every *Sorte* of the catalog, for the filter sheet.
 * @property origins every growing country of the catalog, for the filter sheet.
 * @property lastRefreshAtMillis when the last successful refresh finished (CAT-07 AC6).
 */
data class CatalogListing(
    val sections: List<CatalogSection>,
    val grouped: Boolean,
    val totalCount: Int,
    val hiddenMatches: Int,
    val types: List<String>,
    val origins: List<String>,
    val lastRefreshAtMillis: Long?,
) {
    val teas: List<CatalogTeaSummary> get() = sections.flatMap { it.teas }

    companion object {
        val EMPTY = CatalogListing(emptyList(), grouped = true, 0, 0, emptyList(), emptyList(), null)
    }
}
