package com.umamit.core.catalog.parser

import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A package size the page's configurator offers (08 §2.3 „variants"): [grams], the savings the
 * shop notes for it („3% gespart") and whether it is the page's own size ([selected]).
 */
data class SizeOption(val grams: Double, val savingsPercent: Int?, val selected: Boolean)

/** Reads the configurator's size options from the product's own buy box (08 §2.3). */
internal object SizeOptions {
    /** „250g", „1kg", „0,5 kg" at the start of an option's label. */
    private val SIZE = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(kg|g)\b""", RegexOption.IGNORE_CASE)
    private val SAVINGS = Regex("""(\d+)\s*%\s*gespart""", RegexOption.IGNORE_CASE)
    private const val GRAMS_PER_KG = 1000

    /** The size options in page order; an option whose label names no size is left out. */
    fun read(buyBox: Element): List<SizeOption> =
        buyBox.select("form[data-variant-switch] .product-detail-configurator-option").mapNotNull { option ->
            val label = option.selectFirst("label") ?: return@mapNotNull null
            val text = normalisedSpace(label.text())
            val size = SIZE.find(text) ?: return@mapNotNull null
            SizeOption(
                grams = grams(size.groupValues[1], size.groupValues[2]) ?: return@mapNotNull null,
                savingsPercent = SAVINGS.find(text)?.groupValues?.get(1)?.toIntOrNull(),
                selected = option.selectFirst("input[checked]") != null,
            )
        }

    private fun grams(number: String, unit: String): Double? {
        val value = number.replace(',', '.').toBigDecimalOrNull() ?: return null
        val grams = if (unit.equals("kg", ignoreCase = true)) value.multiply(BigDecimal(GRAMS_PER_KG)) else value
        return grams.toDouble()
    }
}

/**
 * One package size of a catalog tea (05 §4.2, 08 §2.4 item 6).
 *
 * @property articleNumber the size's order number: the sitemap's (`TG0557-250`), the page's own
 *   for its size, else `<base>-<grams>`.
 * @property priceCents the page's price for its own size; an estimate for a larger size; null for
 *   a smaller one (a sample with a surcharge: „Preis beim Hinzufügen") or without a page price.
 * @property priceIsEstimate [priceCents] is estimated from the page's price and the size's savings.
 * @property savingsPercent the savings the configurator notes for the size („3% gespart").
 * @property variantUrl the size's own shop page (the exact price fetch, CAT-12; *Im Shop öffnen*), if known.
 * @property available the stock of the page's own size; null for the other sizes (never fetched, D-076).
 */
data class ShopPackage(
    val articleNumber: String,
    val grams: Double,
    val priceCents: Long?,
    val priceIsEstimate: Boolean,
    val savingsPercent: Int?,
    val variantUrl: String?,
    val available: Boolean?,
)

/**
 * A green tea's package sizes (08 §2.4 item 6) from its page and its sitemap URLs, without any
 * further request (D-076): the sizes are the configurator's options and the variant URLs' gram
 * suffixes; the page's own size has the page's price; a larger size is estimated as
 * `page price ÷ page grams × size × (1 − savings %)`, rounded to the cent; a smaller size gets
 * no price.
 */
object Packages {
    private const val PERCENT = 100

    /** The packages of [page] (fetched for [group]), smallest first. */
    fun of(page: ProductPage, group: ProductGroup): List<ShopPackage> {
        val ownGrams = page.packageGrams ?: page.sizeOptions.firstOrNull { it.selected }?.grams
        val base = page.articleNumber?.let { ArticleNumber.parse(it)?.base } ?: group.base
        val sitemapSizes = group.variantUrls.mapNotNull { (number, url) ->
            ArticleNumber.parse(number)?.grams?.let { it.toDouble() to (number to url) }
        }.toMap()
        val savings = page.sizeOptions.associate { it.grams to it.savingsPercent }
        val sizes = (listOfNotNull(ownGrams) + page.sizeOptions.map { it.grams } + sitemapSizes.keys).distinct()
        return sizes.sorted().map { grams ->
            val sitemap = sitemapSizes[grams]
            if (grams == ownGrams) {
                val number = page.articleNumber ?: sitemap?.first ?: base
                ShopPackage(
                    articleNumber = number,
                    grams = grams,
                    priceCents = page.priceCents,
                    priceIsEstimate = false,
                    savingsPercent = savings[grams],
                    variantUrl = group.variantUrls[number] ?: page.productUrl,
                    available = page.available,
                )
            } else {
                val estimate = estimate(page.priceCents, ownGrams, grams, savings[grams])
                ShopPackage(
                    articleNumber = sitemap?.first ?: "$base-${gramsText(grams)}",
                    grams = grams,
                    priceCents = estimate,
                    priceIsEstimate = estimate != null,
                    savingsPercent = savings[grams],
                    variantUrl = sitemap?.second,
                    available = null,
                )
            }
        }
    }

    /** 08 §2.4 item 6: only for a size larger than the page's own, rounded half up to the cent. */
    private fun estimate(priceCents: Long?, ownGrams: Double?, grams: Double, savingsPercent: Int?): Long? {
        val reference = ownGrams?.takeIf { it > 0.0 && grams > it }
        if (priceCents == null || reference == null) return null
        return BigDecimal(priceCents)
            .multiply(BigDecimal.valueOf(grams))
            .multiply(BigDecimal(PERCENT - (savingsPercent ?: 0)))
            .divide(BigDecimal.valueOf(reference).multiply(BigDecimal(PERCENT)), 0, RoundingMode.HALF_UP)
            .toLong()
    }

    private fun gramsText(grams: Double): String = BigDecimal.valueOf(grams).stripTrailingZeros().toPlainString()
}
