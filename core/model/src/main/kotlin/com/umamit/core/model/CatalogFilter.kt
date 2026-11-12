package com.umamit.core.model

/** How the catalog list is ordered (CAT-10): grouped by *Sorte* (the default, D-073), by name or by price per 100 g. */
enum class CatalogSort { TYPE, NAME, PRICE }

/** The flavoured / pure filter (CAT-10): all teas, pure ones only („Pur", the default, D-073) or flavoured ones. */
enum class FlavourFilter { ALL, PURE, FLAVOURED }

/**
 * What the catalog list shows (CAT-10, SCR-19). The defaults are the list's default view
 * (D-073, D-114): no kind, so every tea grouped by kind, available teas only („Pur" stays on but applies to the
 * Grüntee view only).
 *
 * @property search the search text over name, secondary name, description, cultivar and region; blank = no search.
 * @property types the *Sorten* to show; empty = every one.
 * @property origins the growing countries to show; empty = every one.
 * @property minUmami only teas whose umami scale reaches this level; null = no umami filter.
 * @property kind the catalog kind shown (D-108), such as the *Kräutertee* view (v0.2.1, D-093); null = no kind
 *   (v0.6.1, D-114 Q13): every tea, grouped by kind. [flavour] („Pur", Q14) and [minUmami] (Q19) apply to the green
 *   kind only.
 * @property notes the taste notes (*Geschmack*) a tea must all name, as [TasteNoteOption.key]s (D-108 point 8);
 *   empty = no note filter. They apply in every kind's view.
 * @property minScales the shop's taste scales but Umami (Süße, Bitterstoffe, …) as [TasteScaleOption.key]s, each
 *   with the leaves a tea must at least show („mindestens N", 1–5, D-108 point 8); empty = no scale filter.
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
    val kind: CatalogKind? = null,
    val marked: Boolean = false,
    val notes: Set<String> = emptySet(),
    val minScales: Map<String, Int> = emptyMap(),
) {
    /** The Grüntee view is shown: the green-only rules („Pur", Umami) apply (D-114 Q13: not without a kind). */
    val isGreen: Boolean get() = kind == CatalogKind.GREEN

    /**
     * The filters that differ from the default view, without search and sort (the filter button's
     * count); the kind doesn't count (D-114), „Pur" and Umami count in the Grüntee view only (SCR-19). The taste notes
     * count once, like *Sorte*; each scale with a minimum counts, like Umami (D-108 point 8).
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
            notes.isNotEmpty(),
        ).count { it } + minScales.size

    /** These filters without any (*Alle zeigen*): every tea of the range, no kind (D-114), same search and sort. */
    fun withoutFilters(): CatalogFilter = CatalogFilter(
        search = search,
        flavour = FlavourFilter.ALL,
        availableOnly = false,
        sort = sort,
    )

    /**
     * A kind chip (SCR-19, D-093, D-108): the teas of [kind] instead of the current kind, null = no kind (D-114); the
     * *Sorten* chosen belong to the other view and are dropped, the rest is kept.
     */
    fun withKind(kind: CatalogKind?): CatalogFilter =
        if (kind == this.kind) this else copy(kind = kind, types = emptySet())

    /**
     * A kind chip's tap (D-114 Q13, Q15): narrows to [kind] (one at a time), or clears it when it is the kind shown;
     * never falls back to the green teas.
     */
    fun toggleKind(kind: CatalogKind): CatalogFilter = withKind(if (kind == this.kind) null else kind)

    /** A taste note chip (D-108 point 8): [key] chosen or no longer. */
    fun toggleNote(key: String): CatalogFilter = copy(notes = if (key in notes) notes - key else notes + key)

    /** A taste scale's choice (D-108 point 8): at least [min] leaves of the scale [key]; null = *Egal*. */
    fun withMinScale(key: String, min: Int?): CatalogFilter =
        copy(minScales = if (min == null) minScales - key else minScales + (key to min))

    /**
     * These filters with only the taste notes and scales that [listing] finds in this filter's kind, every kind's
     * without one (D-108 point 8, D-114): after a kind change the notes and scales the new view lacks are dropped, so
     * none hides the whole view.
     */
    fun keepingFlavoursOf(listing: CatalogListing): CatalogFilter {
        val flavours = kind?.let { listing.flavoursByKind[it] } ?: listing.flavoursByKind.values.fold(
            KindFlavours.NONE,
        ) { all, one -> KindFlavours(all.notes + one.notes, all.scales + one.scales) }
        val keptNotes = notes.filterTo(mutableSetOf()) { it in flavours.notes }
        val keptScales = minScales.filterKeys { it in flavours.scales }
        val unchanged = keptNotes == notes && keptScales == minScales
        return if (unchanged) this else copy(notes = keptNotes, minScales = keptScales)
    }

    companion object {
        /** The list's default view (D-073). */
        val DEFAULT = CatalogFilter()
    }
}

/**
 * One group of the list: the teas of one *Sorte* ([teaType]; null = none), or the whole list when not grouped. Without
 * a kind (D-114) each section also names its [kind]: the kind groups are the runs of sections of one kind, in the
 * chip order.
 */
data class CatalogSection(val teaType: String?, val teas: List<CatalogTeaSummary>, val kind: CatalogKind? = null)

/**
 * The catalog list as a [CatalogFilter] shows it (SCR-19).
 *
 * @property sections the teas that match, in order; one section per *Sorte* when grouped, else one; without a kind
 *   ([byKind]) the same within each kind, the kinds in the chip order.
 * @property grouped the sections are *Sorten* (sort by type).
 * @property byKind the view without a kind (D-114): the sections come grouped by [CatalogSection.kind].
 * @property totalCount every tea of the current range of the shown kind, or every tea without a kind, for the
 *   header („274 Grüntees", „312 Tees", CAT-07 AC6).
 * @property hiddenMatches search hits the filters hide („searchable but hidden", D-073), those of the
 *   other kind included (CAT-10); 0 without a search.
 * @property types every *Sorte* of the shown kind (every kind's without one), for the filter sheet.
 * @property origins every growing country of the shown kind (every kind's without one), for the filter sheet.
 * @property lastRefreshAtMillis when the last successful refresh finished (CAT-07 AC6).
 * @property catalogCount every tea of the current range, green and herbal: none = never loaded.
 * @property hiddenInOtherKinds the [hiddenMatches] of each other kind, shown by that kind's view; kinds
 *   without a hidden hit are left out.
 * @property tasteNotes the taste notes of the shown kind's range, most frequent first, for the filter sheet
 *   (D-108 point 8).
 * @property tasteScales the taste scales but Umami of the shown kind's range, most frequent first, for the sheet.
 * @property flavoursByKind the note and scale keys of each kind's current range, for a kind change
 *   ([CatalogFilter.keepingFlavoursOf]).
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
    val tasteNotes: List<TasteNoteOption> = emptyList(),
    val tasteScales: List<TasteScaleOption> = emptyList(),
    val flavoursByKind: Map<CatalogKind, KindFlavours> = emptyMap(),
    val byKind: Boolean = false,
) {
    /** The [hiddenMatches] that belong to any other kind. */
    val hiddenInOtherView: Int get() = hiddenInOtherKinds.values.sum()

    val teas: List<CatalogTeaSummary> get() = sections.flatMap { it.teas }

    companion object {
        val EMPTY = CatalogListing(emptyList(), grouped = true, 0, 0, emptyList(), emptyList(), null)
    }
}

/**
 * A taste note the filter sheet offers (D-108 point 8): its [key] (the note normalised like search, GEN-10, which
 * [CatalogFilter.notes] holds), its [label] as shown and how many teas of the view's range name it ([count]).
 */
data class TasteNoteOption(val key: String, val label: String, val count: Int)

/** A taste scale the filter sheet offers (D-108 point 8): its [key], normalised like a note, and [label]. */
data class TasteScaleOption(val key: String, val label: String)

/** The taste note and scale keys found in one kind's range (D-108 point 8). */
data class KindFlavours(val notes: Set<String>, val scales: Set<String>) {
    companion object {
        val NONE = KindFlavours(emptySet(), emptySet())
    }
}
