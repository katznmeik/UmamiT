package com.umamit.core.domain.catalog

import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogListing
import com.umamit.core.model.CatalogSection
import com.umamit.core.model.CatalogSort
import com.umamit.core.model.CatalogTeaSummary
import com.umamit.core.model.FlavourFilter
import java.text.Collator
import java.util.Locale

/**
 * Finding teas in the catalog (CAT-10, SCR-19): filters, sort and grouping of the current range.
 * The search itself runs on the catalog's full-text index; this takes its hits.
 */
object CatalogBrowser {
    private val collator: Collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.SECONDARY }
    private val byName = Comparator<CatalogTeaSummary> { a, b -> collator.compare(a.name, b.name) }
        .thenBy { it.key }

    /**
     * The list of [teas] (the current range) as [filter] shows it: the green teas, or in the
     * *Kräutertee* view the Kräutertees (v0.2.1, D-093); search hits of the other kind count as
     * hidden (CAT-10).
     *
     * @param searchHits the keys the search found, or null without a search ([CatalogFilter.search] blank).
     */
    fun listing(
        teas: List<CatalogTeaSummary>,
        filter: CatalogFilter,
        searchHits: Set<String>?,
        lastRefreshAtMillis: Long?,
    ): CatalogListing {
        val range = teas.filter { it.isHerbal == filter.herbal }
        val found = if (searchHits == null) teas else teas.filter { it.key in searchHits }
        val shown = found.filter { it.isHerbal == filter.herbal && filter.admits(it) }
        val grouped = filter.sort == CatalogSort.TYPE
        val sections = when (filter.sort) {
            CatalogSort.TYPE -> shown.groupBy { it.teaType }
                .toSortedMap(nullsLast(Comparator { a: String, b: String -> collator.compare(a, b) }))
                .map { (type, members) -> CatalogSection(type, members.sortedWith(byName)) }

            CatalogSort.NAME -> listOf(CatalogSection(null, shown.sortedWith(byName)))

            CatalogSort.PRICE -> listOf(
                CatalogSection(
                    null,
                    shown.sortedWith(compareBy(nullsLast(), CatalogTeaSummary::basePriceCentsPer100g).then(byName)),
                ),
            )
        }.filter { it.teas.isNotEmpty() }
        return CatalogListing(
            sections = sections,
            grouped = grouped,
            totalCount = range.size,
            hiddenMatches = if (searchHits == null) 0 else found.size - shown.size,
            types = range.mapNotNull { it.teaType }.distinct().sortedWith(collator),
            origins = range.flatMap { it.originCountries }.distinct().sortedWith(collator),
            lastRefreshAtMillis = lastRefreshAtMillis,
            catalogCount = teas.size,
            hiddenInOtherView = if (searchHits == null) 0 else found.count { it.isHerbal != filter.herbal },
        )
    }

    /**
     * The FTS4 query for the search text [search] (CAT-10): every word as a prefix, all of them
     * required; anything but letters and digits separates words, so no FTS syntax gets through.
     * Null when the text has no word.
     */
    fun ftsQuery(search: String): String? = search
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ") { "$it*" }

    private fun CatalogFilter.admits(tea: CatalogTeaSummary): Boolean = admitsFlavour(tea) &&
        admitsUmami(tea) &&
        (types.isEmpty() || tea.teaType in types) &&
        (origins.isEmpty() || tea.originCountries.any { it in origins }) &&
        admitsSwitches(tea)

    /** „Pur" doesn't apply to Kräutertees (D-093 Q14). */
    private fun CatalogFilter.admitsFlavour(tea: CatalogTeaSummary): Boolean = herbal || when (flavour) {
        FlavourFilter.ALL -> true
        FlavourFilter.PURE -> !tea.isFlavored
        FlavourFilter.FLAVOURED -> tea.isFlavored
    }

    /** Kräutertees have no umami: the filter is hidden in their view (D-093 Q19) and doesn't apply. */
    private fun CatalogFilter.admitsUmami(tea: CatalogTeaSummary): Boolean {
        val min = minUmami?.takeUnless { herbal } ?: return true
        return tea.umamiLevel?.let { it >= min } ?: false
    }

    /** The on/off filters; stock unknown (null) counts as available: the page didn't say otherwise (D-077). */
    private fun CatalogFilter.admitsSwitches(tea: CatalogTeaSummary): Boolean = (!organicOnly || tea.isOrganic) &&
        (!ownedOnly || tea.owned) &&
        (!availableOnly || tea.available != false) &&
        (!newOnly || tea.isNew)
}
