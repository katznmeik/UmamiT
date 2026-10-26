package com.umamit.core.catalog.parser

/** One breadcrumb item of a product page: its [title] as the shop names it and its URL [path]. */
data class Crumb(val title: String, val path: String)

/** Whether a product belongs in the catalog (08 §2.4 item 3), by its breadcrumb. */
enum class CatalogScope {
    /** Under the green tea sections: kept. */
    GREEN,

    /** A breadcrumb outside them: remembered in the skip list (05 §4.2a). */
    NOT_GREEN,

    /**
     * Under the green sections, but a set by its name or breadcrumb, without a set number
     * (D-091): remembered in the skip list as a set.
     */
    SET,

    /** No breadcrumb found: nothing can be said, so the product is neither kept nor skipped. */
    UNKNOWN,
}

/**
 * What the parser read from one product page (08 §2.3). Fields the page doesn't show, or that
 * the parser could not read, are null; later parser versions add fields here.
 *
 * @property articleNumber the order number as shown (`TG0557`, `TG0557-50`).
 * @property key the catalog key (05 §4.1): [articleNumber]'s base, else the base of the page URL's number.
 * @property productUrl the canonical URL, else the fetched URL.
 * @property name the title without its size suffix („– 100g") and without [secondaryName].
 * @property secondaryName the Japanese or Chinese part of the title (e.g. 「煎茶」), if any.
 * @property breadcrumb the category path without the home link.
 * @property teaType the *Sorte* as the shop names it: the breadcrumb's type level (Sencha, Gyokuro, …).
 * @property priceCents the price of the page's own package.
 * @property packageGrams the content of that package.
 * @property basePriceCentsPer100g the unit price per 100 g.
 * @property available in stock (true), out of stock (false) or not readable (null).
 * @property sizeOptions the configurator's package sizes in page order (08 §2.4 item 6), the page's own one selected.
 * @property photoUrl the product's main photo in full size (the gallery's `data-full-image`).
 * @property originCountry *Anbaugebiete*; several values joined with „, " (as are the other properties).
 * @property originRegion a known growing region named in the title, else in the description (08 §2.3).
 * @property cultivar *Kultivare*.
 * @property harvest the „Ernte: …" badge's value.
 * @property shading the „Beschattung: …" badge's value.
 * @property steaming the „Dämpfung: …" badge's value.
 * @property isOrganic the „Bio" badge.
 * @property isFlavored *Aroma* other than „naturbelassen", ingredients beyond tea, or the flavoured section (08 §2.3).
 * @property coldBrewSuitable the „Coldbrew geeignet" badge.
 * @property aroma *Aroma*.
 * @property tasteNotes *Geschmack*.
 * @property manufacturer *Hersteller …*.
 * @property season *Saison* (when to drink the tea, not the harvest).
 * @property matchaGrade *Matcha Qualitäten*.
 * @property tasteScales the shop's taste scales in page order.
 * @property ingredients the text after „Zutaten:".
 * @property descriptionHtml the description, sanitised to basic formatting and links (08 §2.8).
 * @property descriptionText the description as plain text, paragraphs separated by a blank line.
 * @property brewTable the brewing grid's rows as found.
 * @property brewText the description's paragraphs about brewing, as found.
 */
data class ProductPage(
    val articleNumber: String?,
    val key: String?,
    val productUrl: String,
    val name: String?,
    val secondaryName: String?,
    val breadcrumb: List<Crumb>,
    val scope: CatalogScope,
    val teaType: String?,
    val priceCents: Long?,
    val packageGrams: Double?,
    val basePriceCentsPer100g: Long?,
    val available: Boolean?,
    val sizeOptions: List<SizeOption> = emptyList(),
    val photoUrl: String? = null,
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
    val tasteScales: List<ShopScale> = emptyList(),
    val ingredients: String? = null,
    val descriptionHtml: String? = null,
    val descriptionText: String? = null,
    val brewTable: List<BrewGridRow> = emptyList(),
    val brewText: String? = null,
) {
    /** The deepest category path, e.g. `/tee/gruener-tee/japan/sencha/` (05 §4.1 `breadcrumb`). */
    val categoryPath: String? get() = breadcrumb.lastOrNull()?.path

    /** The category titles, e.g. „Tee › Grüner Tee › Japan › Sencha" (05 §4.1 `shop_category`). */
    val shopCategory: String? get() = breadcrumb.takeIf { it.isNotEmpty() }?.joinToString(" › ") { it.title }

    /** Lacks a field the sanity check requires of every product (08 §2.9): name, price or article number. */
    val lacksRequiredField: Boolean get() = name == null || priceCents == null || articleNumber == null
}

/** One of the shop's taste scales (08 §2.3): [label] („Umami", „Bitterstoffe") and its filled [leaves] of 5. */
data class ShopScale(val label: String, val leaves: Int)

/**
 * One row of the shop's brewing grid (08 §2.3), as found.
 *
 * @property kind the row's method as the shop's class names it: `japan` (Kyusu) or `westlich`.
 * @property cells the row's cells in order, without the method icon.
 */
data class BrewGridRow(val kind: String, val cells: List<BrewGridCell>)

/** A brewing grid cell: its column's [label] („Menge", „1. Aufguss") and its [text] („~4g/100ml", „-"). */
data class BrewGridCell(val label: String?, val text: String)

/** The result of parsing one product page. */
sealed interface ProductParse {
    data class Parsed(val page: ProductPage) : ProductParse

    data class Failed(val reason: ParseFailure) : ProductParse
}

/** Why a page gave no [ProductPage]. */
enum class ParseFailure {
    /** None of a product page's parts (name, order number, breadcrumb, price) was found. */
    NOT_A_PRODUCT_PAGE,
}
