package com.umamit.core.catalog.parser

import java.net.URI
import java.net.URISyntaxException

/**
 * A shop article number (08 §2.2): parent `TG0557`, size variants `TG0557-50` (suffix = grams),
 * `TG0557M` (the parent's page under another number), lower-case suffixes such as `AT0220a`
 * (separate articles), sets `…SET` and sale duplicates `…SALE` (both out of scope).
 *
 * @property base the catalog key (05 §4.1): the number without a variant suffix and without `M`;
 *   a lower-case letter stays.
 * @property variant the part after the dash (`50`, `250mittel`), or null.
 * @property isSet a set, never fetched (08 §2.4 item 2): the letters after the digits start with
 *   „SET" in any case (`…SET`, `…Set`, `…SETc`, `…SETSALE`; D-091), or the variant is „SET".
 * @property isSale a reduced duplicate of a regular tea (`…SALE` in any case, D-091), never fetched.
 */
data class ArticleNumber(
    val text: String,
    val base: String,
    val variant: String?,
    val isSet: Boolean,
    val isSale: Boolean = false,
) {
    /** The grams of a size variant (`-250` → 250), or null. */
    val grams: Int? get() = variant?.toIntOrNull()

    companion object {
        /** Letters, digits, then optional letters (`XY0100`, `XY0100a`, `XY0100M`, `XY0100SET`). */
        private val HEAD = Regex("""[A-Z]{2,5}\d{2,7}[A-Za-z]*""")
        private val VARIANT = Regex("""[A-Za-z0-9]+""")

        /** The `M` of the parent's second number, after the digits or a lower-case suffix. */
        private val PARENT_ALIAS = Regex("""(.*\d[a-z]?)M""")

        /** [text] as an article number, or null when it has no article number's shape. */
        fun parse(text: String): ArticleNumber? {
            val head = text.substringBefore('-')
            val variant = if ('-' in text) text.substringAfter('-') else null
            if (!HEAD.matches(head) || (variant != null && !VARIANT.matches(variant))) return null
            val letters = head.takeLastWhile { it.isLetter() }
            val isSale = letters.endsWith(SALE_MARK, ignoreCase = true)
            val unsold = if (isSale) letters.dropLast(SALE_MARK.length) else letters
            val isSet = unsold.startsWith(SET_MARK, ignoreCase = true) || variant.equals(SET_MARK, ignoreCase = true)
            val base = PARENT_ALIAS.matchEntire(head)?.groupValues?.get(1)?.takeUnless { isSet } ?: head
            return ArticleNumber(text = text, base = base, variant = variant, isSet = isSet, isSale = isSale)
        }

        private const val SET_MARK = "SET"
        private const val SALE_MARK = "SALE"
    }
}

/**
 * The product pages of one base number in the sitemap (08 §2.4 item 2).
 *
 * @property pageUrl the page to fetch for it: the parent's own URL, else its `M` page, else the
 *   smallest size variant (each variant's page shows the same product).
 * @property variantUrls every variant URL by its article number (`TG0557-50`), for the package
 *   sizes (08 §2.4 item 6).
 */
data class ProductGroup(val base: String, val pageUrl: String, val variantUrls: Map<String, String>)

/**
 * The products of a sitemap: one [ProductGroup] per base number, sorted by it, and the base
 * numbers left out without a request: the [sets] and the [sales] (sale duplicates that are no
 * set, D-091).
 */
data class SitemapProducts(val groups: List<ProductGroup>, val sets: Int, val sales: Int = 0)

/** Finds the product URLs among a sitemap's URLs (08 §2.2): `/<slug>/<article number>`. */
object ProductUrls {
    private const val PRODUCT_SEGMENTS = 2

    /** Groups the product URLs among [urls] per base number; any other URL is ignored. */
    fun group(urls: List<String>): SitemapProducts {
        val numbered = urls.distinct().mapNotNull { url -> articleNumberOf(url)?.let { it to url } }
        val (sets, others) = numbered.partition { (number, _) -> number.isSet }
        // A sale duplicate (`…SALE` next to the regular number) is the same tea, reduced (D-091).
        val (sales, products) = others.partition { (number, _) -> number.isSale }
        val groups = products.groupBy { (number, _) -> number.base }.toSortedMap().map { (base, pages) ->
            ProductGroup(
                base = base,
                pageUrl = pageOf(base, pages),
                variantUrls = pages.filter { (number, _) -> number.variant != null }
                    .associate { (number, url) -> number.text to url }
                    .toSortedMap(),
            )
        }
        return SitemapProducts(
            groups,
            sets = sets.map { (number, _) -> number.base }.distinct().size,
            sales = sales.map { (number, _) -> number.base }.distinct().size,
        )
    }

    /** The article number of a product URL, or null for any other URL. */
    fun articleNumberOf(url: String): ArticleNumber? {
        val segments = pathOf(url)?.takeUnless { it.endsWith("/") }?.split('/')?.filter { it.isNotEmpty() }
        return segments?.takeIf { it.size == PRODUCT_SEGMENTS }?.let { ArticleNumber.parse(it.last()) }
    }

    private fun pathOf(url: String): String? = try {
        URI(url).path
    } catch (_: URISyntaxException) {
        null
    }

    /** The parent's own page, else its `M` page, else the smallest size variant. */
    private fun pageOf(base: String, pages: List<Pair<ArticleNumber, String>>): String {
        val bySize = compareBy<Pair<ArticleNumber, String>> { (number, _) -> number.grams ?: Int.MAX_VALUE }
            .thenBy { (number, _) -> number.text }
        val page = pages.firstOrNull { (number, _) -> number.text == base }
            ?: pages.firstOrNull { (number, _) -> number.variant == null }
            ?: pages.minWith(bySize)
        return page.second
    }
}
