package com.umamit.core.model

/** The *Sammlung* segment (SCR-08, D-100), remembered as `collectionSegment` (05 §6). */
enum class CollectionSegment { MY_TEAS, CATALOG }

/**
 * How *Meine Tees* is ordered (TEA-11, D-100), remembered as `collectionSort` (05 §6): *Zuletzt gebrüht*
 * (the default), *Name*, *Zuletzt hinzugefügt*, *Vorrat* (low → high). Empty teas are last in each (INV-05).
 */
enum class CollectionSort { LAST_BREWED, NAME, RECENTLY_ADDED, STOCK }

/**
 * One tea of *Meine Tees* with what its search, filters and sort read (TEA-08–TEA-11, D-100). Build it with
 * [of] from the collection's flows: the tea, its row's `created_at`, the start of its latest session and the
 * Σ of its stock events (`InventoryDao.observeStockLevels`, which has no archived teas; null without events).
 *
 * @property addedAtMillis when the tea was added (`tea.created_at`), for *Zuletzt hinzugefügt*.
 * @property lastBrewedAtMillis the start of the tea's latest session; null when never brewed.
 * @property remainingGrams the remaining stock; null when the tea has no stock events.
 * @property stockLevel INV-05's *Leer* / *Knapp* / *Vorrätig*; null when the tea has no stock events.
 */
data class CollectionRow(
    val tea: Tea,
    val addedAtMillis: Long,
    val lastBrewedAtMillis: Long? = null,
    val remainingGrams: Double? = null,
    val stockLevel: StockLevel? = null,
) {
    /** *Leer* (INV-05): last in every order. */
    val isEmpty: Boolean get() = stockLevel == StockLevel.EMPTY

    companion object {
        /**
         * The row of [tea], its stock level by the tea's own `low_stock_grams`, else SET-16's [lowStockGrams]
         * („Knapp ab", INV-05).
         */
        fun of(
            tea: Tea,
            addedAtMillis: Long,
            lastBrewedAtMillis: Long?,
            remainingGrams: Double?,
            lowStockGrams: Double,
        ): CollectionRow = CollectionRow(
            tea = tea,
            addedAtMillis = addedAtMillis,
            lastBrewedAtMillis = lastBrewedAtMillis,
            remainingGrams = remainingGrams,
            stockLevel = remainingGrams?.let { StockLevel.of(it, tea.lowStockGrams ?: lowStockGrams) },
        )
    }
}

/**
 * The search and filter chips of *Meine Tees* (TEA-09, TEA-10, D-100); the defaults show every tea that is
 * not archived. Sets are „any of"; an empty set doesn't filter. Not stored: the list opens unfiltered (05 §6).
 *
 * @property search over name, secondary name, vendor, region, cultivar and notes (GEN-10); blank = none.
 * @property categories *Kategorie*.
 * @property sorten *Sorte*: the tea's display Sorte (`tea_type`).
 * @property stock *Vorrat*: [StockLevel.LOW] (*Knapp*) and/or [StockLevel.EMPTY] (*Leer*); a tea without
 *   stock events is neither.
 * @property archived *Archiviert*: only the archived teas instead of the others; the rest still applies.
 */
data class CollectionFilter(
    val search: String = "",
    val favoritesOnly: Boolean = false,
    val categories: Set<TeaCategory> = emptySet(),
    val sorten: Set<String> = emptySet(),
    val stock: Set<StockLevel> = emptySet(),
    val flavoredOnly: Boolean = false,
    val archived: Boolean = false,
) {
    /** A search or any chip is on: an empty result reads „Kein Tee passt zu deiner Suche." (A10). */
    val isActive: Boolean
        get() = search.isNotBlank() || favoritesOnly || categories.isNotEmpty() || sorten.isNotEmpty() ||
            stock.isNotEmpty() || flavoredOnly || archived
}

/**
 * *Meine Tees* as a [CollectionFilter] and [CollectionSort] show it (SCR-08).
 *
 * @property rows the teas that match, in order.
 * @property categories the *Kategorien* present among the teas of the current side (archived or not), in
 *   [TeaCategory] order, for the *Kategorie* chip.
 * @property sorten the *Sorten* present there, A–Z, for the *Sorte* chip.
 * @property ownedCount the teas that are not archived: none = „Noch keine Tees" (A7).
 */
data class CollectionListing(
    val rows: List<CollectionRow>,
    val categories: List<TeaCategory>,
    val sorten: List<String>,
    val ownedCount: Int,
)
