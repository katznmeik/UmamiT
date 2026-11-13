package com.umamit.core.domain.catalog

import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogKind
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
     * The list of [teas] as [filter] shows it: the teas of the filter's catalog kind (D-108), such as the
     * *Kräutertee* view's Kräutertees (v0.2.1, D-093); search hits of the other kinds count as
     * hidden (CAT-10). Without a kind (v0.6.1, D-114) every tea, grouped by kind in the chip order, the sort applying
     * inside each kind group. In the *Gemerkt* view (CAT-18, D-097) every marked tea, of either kind and
     * whatever its stock, the discontinued ones included, grouped by *Sorte*; only the search
     * applies there, and nothing counts as hidden.
     *
     * @param teas the catalog's teas: the current range, and the teas kept as „Nicht mehr im
     *   Sortiment" ([CatalogTeaSummary.discontinued]), which only the *Gemerkt* view shows.
     * @param searchHits the keys the search found, or null without a search ([CatalogFilter.search] blank).
     */
    fun listing(
        teas: List<CatalogTeaSummary>,
        filter: CatalogFilter,
        searchHits: Set<String>?,
        lastRefreshAtMillis: Long?,
    ): CatalogListing {
        val current = teas.filterNot { it.discontinued }
        if (filter.marked) return markedListing(teas, current, filter, searchHits, lastRefreshAtMillis)
        val range = current.filter { filter.shows(it) }
        val found = if (searchHits == null) current else current.filter { it.key in searchHits }
        val shown = found.filter { filter.shows(it) && filter.admits(it) }
        val sections = if (filter.kind == null) {
            val byKind = shown.groupBy { it.kind }
            // The chip order (D-108 point 5); a kind outside it, if any, after the known ones.
            (CatalogKind.ALL + (byKind.keys - CatalogKind.ALL.toSet())).flatMap { kind ->
                sorted(byKind[kind].orEmpty(), filter.sort).map { it.copy(kind = kind) }
            }
        } else {
            sorted(shown, filter.sort)
        }.filter { it.teas.isNotEmpty() }
        return CatalogListing(
            sections = sections,
            grouped = filter.sort == CatalogSort.TYPE,
            byKind = filter.kind == null,
            totalCount = range.size,
            hiddenMatches = if (searchHits == null) 0 else found.size - shown.size,
            types = range.mapNotNull { it.teaType }.distinct().sortedWith(collator),
            origins = range.flatMap { it.originCountries }.distinct().sortedWith(collator),
            lastRefreshAtMillis = lastRefreshAtMillis,
            catalogCount = current.size,
            hiddenInOtherKinds = if (searchHits == null) {
                emptyMap()
            } else {
                found.filterNot { filter.shows(it) }.groupingBy { it.kind }.eachCount()
            },
            tasteNotes = CatalogTaste.notes(range),
            tasteScales = CatalogTaste.scales(range),
            flavoursByKind = current.groupBy { it.kind }.mapValues { (_, members) -> CatalogTaste.flavours(members) },
        )
    }

    /** The *Gemerkt* view: the marked [teas], green and herbal, grouped as the default view (D-073). */
    private fun markedListing(
        teas: List<CatalogTeaSummary>,
        current: List<CatalogTeaSummary>,
        filter: CatalogFilter,
        searchHits: Set<String>?,
        lastRefreshAtMillis: Long?,
    ): CatalogListing {
        val marked = teas.filter { it.marked }
        val shown = if (searchHits == null) marked else marked.filter { it.key in searchHits }
        val range = current.filter { filter.shows(it) }
        return CatalogListing(
            sections = bySorte(shown),
            grouped = true,
            totalCount = marked.size,
            hiddenMatches = 0,
            types = range.mapNotNull { it.teaType }.distinct().sortedWith(collator),
            origins = range.flatMap { it.originCountries }.distinct().sortedWith(collator),
            lastRefreshAtMillis = lastRefreshAtMillis,
            catalogCount = current.size,
        )
    }

    /** [teas] in the order of [sort]: under a section per *Sorte* (D-073), else in one section. */
    private fun sorted(teas: List<CatalogTeaSummary>, sort: CatalogSort): List<CatalogSection> = when (sort) {
        CatalogSort.TYPE -> bySorte(teas)

        CatalogSort.NAME -> listOf(CatalogSection(null, teas.sortedWith(byName)))

        CatalogSort.PRICE -> listOf(
            CatalogSection(
                null,
                teas.sortedWith(compareBy(nullsLast(), CatalogTeaSummary::basePriceCentsPer100g).then(byName)),
            ),
        )
    }

    /** [teas] under a section per *Sorte* by name, teas without one last (D-073). */
    private fun bySorte(teas: List<CatalogTeaSummary>): List<CatalogSection> = teas.groupBy { it.teaType }
        .toSortedMap(nullsLast(Comparator { a: String, b: String -> collator.compare(a, b) }))
        .map { (type, members) -> CatalogSection(type, members.sortedWith(byName)) }

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

    /** The tea belongs to the view's kind; without a kind every tea does (D-114). */
    private fun CatalogFilter.shows(tea: CatalogTeaSummary): Boolean = kind == null || tea.kind == kind

    private fun CatalogFilter.admits(tea: CatalogTeaSummary): Boolean = admitsFlavour(tea) &&
        admitsUmami(tea) &&
        CatalogTaste.admits(this, tea) &&
        (types.isEmpty() || tea.teaType in types) &&
        (origins.isEmpty() || tea.originCountries.any { it in origins }) &&
        admitsSwitches(tea)

    /** „Pur" applies in the Grüntee view only (D-093 Q14, D-114 Q13). */
    private fun CatalogFilter.admitsFlavour(tea: CatalogTeaSummary): Boolean = !isGreen || when (flavour) {
        FlavourFilter.ALL -> true
        FlavourFilter.PURE -> !tea.isFlavored
        FlavourFilter.FLAVOURED -> tea.isFlavored
    }

    /** Umami applies in the Grüntee view only: hidden in every other view (D-093 Q19, D-114 Q13). */
    private fun CatalogFilter.admitsUmami(tea: CatalogTeaSummary): Boolean {
        val min = minUmami?.takeIf { isGreen } ?: return true
        return tea.umamiLevel?.let { it >= min } ?: false
    }

    /** The on/off filters; stock unknown (null) counts as available: the page didn't say otherwise (D-077). */
    private fun CatalogFilter.admitsSwitches(tea: CatalogTeaSummary): Boolean = (!organicOnly || tea.isOrganic) &&
        (!ownedOnly || tea.owned) &&
        (!availableOnly || tea.available != false) &&
        (!newOnly || tea.isNew)
}
