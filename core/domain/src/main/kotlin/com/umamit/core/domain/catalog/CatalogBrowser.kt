package com.umamit.core.domain.catalog

import com.umamit.core.domain.search.SearchQuery
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogListing
import com.umamit.core.model.CatalogSection
import com.umamit.core.model.CatalogSort
import com.umamit.core.model.CatalogTeaSummary
import com.umamit.core.model.FlavourFilter
import com.umamit.core.model.KindFlavours
import com.umamit.core.model.TasteNoteOption
import com.umamit.core.model.TasteScaleOption
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
     * The list of [teas] as [filter] shows it: the teas of the filter's catalog kind (D-108): the green teas, or in the
     * *Kräutertee* view its Kräutertees (v0.2.1, D-093); search hits of the other kinds count as
     * hidden (CAT-10). In the *Gemerkt* view (CAT-18, D-097) every marked tea, of either kind and
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
        val range = current.filter { it.kind == filter.kind }
        val found = if (searchHits == null) current else current.filter { it.key in searchHits }
        val shown = found.filter { it.kind == filter.kind && filter.admits(it) }
        val grouped = filter.sort == CatalogSort.TYPE
        val sections = when (filter.sort) {
            CatalogSort.TYPE -> bySorte(shown)

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
            catalogCount = current.size,
            hiddenInOtherKinds = if (searchHits == null) {
                emptyMap()
            } else {
                found.filter { it.kind != filter.kind }.groupingBy { it.kind }.eachCount()
            },
            tasteNotes = tasteNotes(range),
            tasteScales = tasteScales(range),
            flavoursByKind = current.groupBy { it.kind }.mapValues { (_, members) -> flavours(members) },
        )
    }

    /**
     * The taste notes of [range] (D-108 point 8): one per [noteKey], most frequent first, ties by name; each shown
     * in its most frequent spelling, lower case.
     */
    private fun tasteNotes(range: List<CatalogTeaSummary>): List<TasteNoteOption> = range
        .flatMap { tea -> tea.tasteNotes.distinctBy(::noteKey) }
        .groupBy(::noteKey)
        .filterKeys { it.isNotEmpty() }
        .map { (key, spellings) ->
            val label = spellings.groupingBy { it.trim() }.eachCount().maxBy { it.value }.key
            TasteNoteOption(key, label.lowercase(Locale.GERMAN), spellings.size)
        }
        .sortedWith(
            compareByDescending<TasteNoteOption> { it.count }
                .thenComparator { a, b -> collator.compare(a.label, b.label) },
        )

    /** The taste scales of [range] but Umami (its own filter): most frequent first, ties by name, as the shop names. */
    private fun tasteScales(range: List<CatalogTeaSummary>): List<TasteScaleOption> = range
        .flatMap { tea -> tea.flavourScales().distinctBy { noteKey(it.label) } }
        .groupBy { noteKey(it.label) }
        .map { (key, scales) -> scales.size to TasteScaleOption(key, scales.first().label.trim()) }
        .sortedWith(
            compareByDescending<Pair<Int, TasteScaleOption>> { it.first }
                .thenComparator { a, b -> collator.compare(a.second.label, b.second.label) },
        )
        .map { it.second }

    private fun flavours(teas: List<CatalogTeaSummary>) = KindFlavours(
        notes = teas.flatMap { tea -> tea.tasteNotes.map(::noteKey) }.filterTo(mutableSetOf()) { it.isNotEmpty() },
        scales = teas.flatMap { tea -> tea.flavourScales().map { noteKey(it.label) } }.toSet(),
    )

    /** The tea's scales the scale filter offers: all but Umami, which stays its own filter (D-108 point 8). */
    private fun CatalogTeaSummary.flavourScales() = tasteScales.filter { scale ->
        noteKey(scale.label).let { key -> key.isNotEmpty() && key != UMAMI_KEY }
    }

    private const val UMAMI_KEY = "umami"

    /** A whole taste note normalised like search (GEN-10): lower case, no diacritics, single spaces. */
    fun noteKey(note: String): String = SearchQuery.words(note).joinToString(" ")

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
        val range = current.filter { it.kind == filter.kind }
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

    private fun CatalogFilter.admits(tea: CatalogTeaSummary): Boolean = admitsFlavour(tea) &&
        admitsUmami(tea) &&
        admitsTaste(tea) &&
        (types.isEmpty() || tea.teaType in types) &&
        (origins.isEmpty() || tea.originCountries.any { it in origins }) &&
        admitsSwitches(tea)

    /** „Pur" doesn't apply to Kräutertees (D-093 Q14). */
    private fun CatalogFilter.admitsFlavour(tea: CatalogTeaSummary): Boolean = !isGreen || when (flavour) {
        FlavourFilter.ALL -> true
        FlavourFilter.PURE -> !tea.isFlavored
        FlavourFilter.FLAVOURED -> tea.isFlavored
    }

    /** Kräutertees have no umami: the filter is hidden in their view (D-093 Q19) and doesn't apply. */
    private fun CatalogFilter.admitsUmami(tea: CatalogTeaSummary): Boolean {
        val min = minUmami?.takeIf { isGreen } ?: return true
        return tea.umamiLevel?.let { it >= min } ?: false
    }

    /** Every chosen note named by the tea (whole notes) and every scale minimum reached (D-108 point 8). */
    private fun CatalogFilter.admitsTaste(tea: CatalogTeaSummary): Boolean {
        if (notes.isEmpty() && minScales.isEmpty()) return true
        val teaNotes = tea.tasteNotes.mapTo(mutableSetOf(), ::noteKey)
        val leaves = tea.tasteScales.associate { noteKey(it.label) to it.leaves }
        return teaNotes.containsAll(notes) && minScales.all { (key, min) -> (leaves[key] ?: 0) >= min }
    }

    /** The on/off filters; stock unknown (null) counts as available: the page didn't say otherwise (D-077). */
    private fun CatalogFilter.admitsSwitches(tea: CatalogTeaSummary): Boolean = (!organicOnly || tea.isOrganic) &&
        (!ownedOnly || tea.owned) &&
        (!availableOnly || tea.available != false) &&
        (!newOnly || tea.isNew)
}
