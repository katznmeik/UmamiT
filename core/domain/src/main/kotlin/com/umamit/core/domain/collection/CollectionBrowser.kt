package com.umamit.core.domain.collection

import com.umamit.core.domain.search.SearchQuery
import com.umamit.core.model.CollectionFilter
import com.umamit.core.model.CollectionListing
import com.umamit.core.model.CollectionRow
import com.umamit.core.model.CollectionSort
import com.umamit.core.model.Tea
import java.text.Collator
import java.util.Locale

/**
 * Finding teas in *Meine Tees* (SCR-08, D-100): the in-memory search (TEA-09), the filter chips (TEA-10)
 * and the sort (TEA-11) over the collection's rows. Pure, so the list's view model only gathers the rows.
 */
object CollectionBrowser {
    private val collator: Collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.SECONDARY }

    /** A–Z by name, ignoring case; the id keeps equal names in a stable order. */
    private val byName: Comparator<CollectionRow> =
        Comparator<CollectionRow> { a, b -> collator.compare(a.tea.name, b.tea.name) }.thenBy { it.tea.id }

    /**
     * *Meine Tees* as [filter] and [sort] show [rows]: the teas that are not archived, or with *Archiviert*
     * only the archived ones; then the search and the other chips; empty teas last in every order (INV-05).
     */
    fun listing(rows: List<CollectionRow>, filter: CollectionFilter, sort: CollectionSort): CollectionListing {
        val side = rows.filter { it.tea.isArchived == filter.archived }
        val search = SearchQuery(filter.search)
        val shown = side.filter { filter.admits(it) && (search.isEmpty || search.matches(it.tea.searchWords())) }
        return CollectionListing(
            rows = shown.sortedWith(comparator(sort)),
            categories = side.map { it.tea.category }.distinct().sorted(),
            sorten = side.mapNotNull { it.tea.teaType?.takeIf(String::isNotBlank) }.distinct().sortedWith(collator),
            ownedCount = rows.count { !it.tea.isArchived },
        )
    }

    /** [sort]'s order with the empty teas after all others (INV-05); ties by name, then id. */
    private fun comparator(sort: CollectionSort): Comparator<CollectionRow> {
        val order: Comparator<CollectionRow> = when (sort) {
            // The latest session first; teas never brewed follow, A–Z (D-100).
            CollectionSort.LAST_BREWED -> compareBy<CollectionRow, Long?>(nullsLast(reverseOrder())) {
                it.lastBrewedAtMillis
            }.then(byName)

            CollectionSort.NAME -> byName

            CollectionSort.RECENTLY_ADDED -> compareByDescending<CollectionRow> { it.addedAtMillis }.then(byName)

            // Low → high; teas without stock events follow the teas with stock (D-100).
            CollectionSort.STOCK -> compareBy<CollectionRow, Double?>(nullsLast()) { it.remainingGrams }.then(byName)
        }
        return compareBy<CollectionRow> { it.isEmpty }.then(order)
    }

    private fun CollectionFilter.admits(row: CollectionRow): Boolean {
        val tea = row.tea
        return (!favoritesOnly || tea.isFavorite) &&
            (!flavoredOnly || tea.isFlavored) &&
            (categories.isEmpty() || tea.category in categories) &&
            (sorten.isEmpty() || tea.teaType in sorten) &&
            (stock.isEmpty() || row.stockLevel in stock)
    }

    /** TEA-09's fields: name, secondary name, vendor, region, cultivar and notes. */
    private fun Tea.searchWords(): List<String> =
        listOfNotNull(name, secondaryName, vendor, originRegion, cultivar, notes).flatMap(SearchQuery::words)
}
