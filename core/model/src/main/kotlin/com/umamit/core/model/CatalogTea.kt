package com.umamit.core.model

/**
 * A catalog tea as the catalog list shows it (SCR-19, CAT-10): a Tee-Kontor green tea of the
 * last refresh, keyed by the shop's base article number (05 §4.1).
 *
 * @property name the shop's name; the article number when the page showed none.
 * @property teaType the *Sorte* as the shop names it (Sencha, Gyokuro, …), if any.
 * @property basePriceCentsPer100g the price of 100 g in euro cents, if the shop showed one.
 * @property available in stock at the refresh; null when the page didn't say.
 * @property thumbnailPath the list thumbnail's file (CAT-08), if the tea has a photo.
 * @property originCountry the growing countries as the shop names them („Japan", „Japan, China").
 * @property umamiLeaves the shop's umami scale, 0–5 leaves, if it shows one.
 * @property recipeCheckReason why its derived recipe should be checked (CAT-05): the list's
 *   „Rezept prüfen" badge; null when it came straight from the shop's grid.
 * @property isNew first seen in the latest successful refresh (CAT-10 „Neu", D-083).
 * @property owned a collection tea links to it by `catalog_key` („im Besitz", CAT-10).
 */
data class CatalogTeaSummary(
    val key: String,
    val name: String,
    val secondaryName: String?,
    val teaType: String?,
    val basePriceCentsPer100g: Long?,
    val available: Boolean?,
    val thumbnailPath: String? = null,
    val originCountry: String? = null,
    val isOrganic: Boolean = false,
    val isFlavored: Boolean = false,
    val umamiLeaves: Int? = null,
    val recipeCheckReason: RecipeCheckReason? = null,
    val isNew: Boolean = false,
    val owned: Boolean = false,
) {
    /** The growing countries one by one (the shop joins several with „, "). */
    val originCountries: List<String>
        get() = originCountry.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** The umami level of the shop's scale (08 §2.6: 0 none, 1 low, 2 medium, 3 high, 4–5 very high). */
    val umamiLevel: UmamiLevel?
        get() = umamiLeaves?.let { leaves ->
            when {
                leaves <= 0 -> UmamiLevel.NONE
                leaves == 1 -> UmamiLevel.LOW
                leaves == 2 -> UmamiLevel.MEDIUM
                leaves == UMAMI_HIGH_LEAVES -> UmamiLevel.HIGH
                else -> UmamiLevel.VERY_HIGH
            }
        }

    private companion object {
        const val UMAMI_HIGH_LEAVES = 3
    }
}

/** The catalog list's content: the teas by name and when the last successful refresh finished (CAT-07 AC6). */
data class CatalogOverview(val teas: List<CatalogTeaSummary>, val lastRefreshAtMillis: Long?) {
    /** Never loaded or emptied: the list shows SCR-19's empty state. */
    val isEmpty: Boolean get() = teas.isEmpty()
}

/**
 * A catalog tea as its detail shows it (SCR-21, CAT-11): every field of 05 §4.1 the shop showed,
 * as found; null or empty where it showed none.
 *
 * @property name the shop's name; the article number when the page showed none.
 * @property productUrl the shop's product page (canonical URL).
 * @property photoPath the photo's file (1200 px, CAT-08), if downloaded.
 * @property tasteScales the shop's taste scales (Umami, Bitterstoffe, …) in the shop's order.
 * @property descriptionHtml the description, sanitised to basic formatting and links (08 §2.8).
 * @property brewTable the shop's brewing grid, verbatim.
 * @property brewText the description's paragraphs about brewing, verbatim.
 * @property recipes the timer recipes derived from the grid and text (CAT-04), the default first.
 * @property recipeCheckReason why the default recipe should be checked (CAT-05); null when it
 *   came straight from the shop's grid.
 * @property packages the package sizes (05 §4.2), from #67 on.
 */
data class CatalogTeaDetail(
    val key: String,
    val name: String,
    val secondaryName: String? = null,
    val productUrl: String,
    val teaType: String? = null,
    val shopCategory: String? = null,
    val originCountry: String? = null,
    val originRegion: String? = null,
    val cultivar: String? = null,
    val harvest: String? = null,
    val shading: String? = null,
    val steaming: String? = null,
    val isOrganic: Boolean = false,
    val isFlavored: Boolean = false,
    val coldBrewSuitable: Boolean = false,
    val aroma: String? = null,
    val tasteNotes: String? = null,
    val manufacturer: String? = null,
    val season: String? = null,
    val matchaGrade: String? = null,
    val tasteScales: List<TasteScale> = emptyList(),
    val ingredients: String? = null,
    val descriptionHtml: String? = null,
    val brewTable: List<BrewTableRow> = emptyList(),
    val brewText: String? = null,
    val recipes: List<RecipeTemplate> = emptyList(),
    val recipeCheckReason: RecipeCheckReason? = null,
    val basePriceCentsPer100g: Long? = null,
    val available: Boolean? = null,
    val discontinued: Boolean = false,
    val photoPath: String? = null,
    val packages: List<CatalogPackage> = emptyList(),
)

/** One of the shop's taste scales: [label] („Umami") and its filled [leaves] of 5 (SCR-21). */
data class TasteScale(val label: String, val leaves: Int) {
    companion object {
        const val MAX_LEAVES = 5
    }
}

/**
 * One row of the shop's brewing grid, verbatim (05 §4.1 `brew_table_raw`).
 *
 * @property kind the shop's method: `japan` (Kyusu), `westlich` (western) or `china` (Gongfu, D-089).
 */
data class BrewTableRow(val kind: String, val cells: List<BrewTableCell>) {
    companion object {
        const val KIND_JAPAN = "japan"
        const val KIND_WESTERN = "westlich"
        const val KIND_CHINA = "china"
    }
}

/** A brewing grid cell: its column's [label] („Menge", „1. Aufguss") and its [text] („~4g/100ml"). */
data class BrewTableCell(val label: String?, val text: String)

/**
 * A package size of a catalog tea (05 §4.2).
 *
 * @property grams the size in grams.
 * @property variantUrl the shop page of this size, if known.
 */
data class CatalogPackage(
    val articleNumber: String,
    val label: String,
    val grams: Double,
    val priceCents: Long?,
    val priceIsEstimate: Boolean,
    val savingsPercent: Int?,
    val variantUrl: String?,
    val available: Boolean?,
)
