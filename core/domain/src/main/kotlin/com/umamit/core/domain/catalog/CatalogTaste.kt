package com.umamit.core.domain.catalog

import com.umamit.core.domain.search.SearchQuery
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogTeaSummary
import com.umamit.core.model.KindFlavours
import com.umamit.core.model.TasteNoteOption
import com.umamit.core.model.TasteScaleOption
import java.text.Collator
import java.util.Locale

/**
 * The flavour filter's part of the catalog browser (v0.5.0, D-108 point 8, CAT-10): the shop's taste notes
 * (*Geschmack*) and taste scales a view offers, and whether a tea passes the ones chosen.
 */
internal object CatalogTaste {
    private const val UMAMI_KEY = "umami"
    private val collator: Collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.SECONDARY }

    /** A whole taste note or scale label normalised like search (GEN-10): lower case, no diacritics, single spaces. */
    fun key(text: String): String = SearchQuery.words(text).joinToString(" ")

    /**
     * The taste notes of [range]: one per [key], most frequent first, ties by name; each shown in its most frequent
     * spelling, lower case.
     */
    fun notes(range: List<CatalogTeaSummary>): List<TasteNoteOption> = range
        .flatMap { tea -> tea.tasteNotes.distinctBy(::key) }
        .groupBy(::key)
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
    fun scales(range: List<CatalogTeaSummary>): List<TasteScaleOption> = range
        .flatMap { tea -> tea.offeredScales().distinctBy { key(it.label) } }
        .groupBy { key(it.label) }
        .map { (key, scales) -> scales.size to TasteScaleOption(key, scales.first().label.trim()) }
        .sortedWith(
            compareByDescending<Pair<Int, TasteScaleOption>> { it.first }
                .thenComparator { a, b -> collator.compare(a.second.label, b.second.label) },
        )
        .map { it.second }

    /** The note and scale keys of [teas], for a kind change ([CatalogFilter.keepingFlavoursOf]). */
    fun flavours(teas: List<CatalogTeaSummary>) = KindFlavours(
        notes = teas.flatMap { tea -> tea.tasteNotes.map(::key) }.filterTo(mutableSetOf()) { it.isNotEmpty() },
        scales = teas.flatMap { tea -> tea.offeredScales().map { key(it.label) } }.toSet(),
    )

    /** Every chosen note named by [tea] (whole notes) and every scale minimum of [filter] reached. */
    fun admits(filter: CatalogFilter, tea: CatalogTeaSummary): Boolean {
        if (filter.notes.isEmpty() && filter.minScales.isEmpty()) return true
        val teaNotes = tea.tasteNotes.mapTo(mutableSetOf(), ::key)
        val leaves = tea.tasteScales.associate { key(it.label) to it.leaves }
        return teaNotes.containsAll(filter.notes) &&
            filter.minScales.all { (key, min) -> (leaves[key] ?: 0) >= min }
    }

    /** The tea's scales the scale filter offers: all but Umami. */
    private fun CatalogTeaSummary.offeredScales() = tasteScales.filter { scale ->
        key(scale.label).let { key -> key.isNotEmpty() && key != UMAMI_KEY }
    }
}
