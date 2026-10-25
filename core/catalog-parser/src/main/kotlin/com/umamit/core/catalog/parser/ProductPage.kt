package com.umamit.core.catalog.parser

/** One breadcrumb item of a product page: its [title] as the shop names it and its URL [path]. */
data class Crumb(val title: String, val path: String)

/** Whether a product belongs in the catalog (08 §2.4 item 3), by its breadcrumb. */
enum class CatalogScope {
    /** Under the green tea sections: kept. */
    GREEN,

    /** A breadcrumb outside them: remembered in the skip list (05 §4.2a). */
    NOT_GREEN,

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
) {
    /** The deepest category path, e.g. `/tee/gruener-tee/japan/sencha/` (05 §4.1 `breadcrumb`). */
    val categoryPath: String? get() = breadcrumb.lastOrNull()?.path

    /** The category titles, e.g. „Tee › Grüner Tee › Japan › Sencha" (05 §4.1 `shop_category`). */
    val shopCategory: String? get() = breadcrumb.takeIf { it.isNotEmpty() }?.joinToString(" › ") { it.title }

    /** Lacks a field the sanity check requires of every product (08 §2.9): name, price or article number. */
    val lacksRequiredField: Boolean get() = name == null || priceCents == null || articleNumber == null
}

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
