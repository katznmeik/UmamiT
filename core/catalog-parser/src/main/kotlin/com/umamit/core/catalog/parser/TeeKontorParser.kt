package com.umamit.core.catalog.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URISyntaxException

/**
 * Reads the shop's product pages (08 §2.3), with versioned selectors (NFR-MAINT-04). A product
 * page embeds ~24 other products (cross-selling) with their own names, prices, order numbers and
 * buy buttons, so every buy-box field is read from the product's own buy box only (D-076).
 *
 * Each buy-box field has its own reader below, the detail fields theirs in [ProductDetails]; a
 * field the page doesn't show stays null, and a page without any product part is a
 * [ProductParse.Failed]. Never throws.
 */
object TeeKontorParser {
    /**
     * The selector version (06 §5.3), stored with each refresh (05 §4.4). Raise it whenever the
     * selectors or the fields read change.
     */
    const val PARSER_VERSION = 5

    /** Parses [body] (the HTML of [pageUrl], charset from the page, else UTF-8). */
    fun parseProduct(body: ByteArray, pageUrl: String): ProductParse {
        val document = Jsoup.parse(ByteArrayInputStream(body), null, pageUrl)
        val page = ProductDocument(document, pageUrl).read()
        val anyProductPart = page.name != null || page.articleNumber != null || page.breadcrumb.isNotEmpty() ||
            page.priceCents != null
        return if (anyProductPart) ProductParse.Parsed(page) else ProductParse.Failed(ParseFailure.NOT_A_PRODUCT_PAGE)
    }
}

/** One parsed page and the readers of its fields. */
@Suppress("TooManyFunctions") // one reader per field of 08 §2.3; later fields add theirs here
private class ProductDocument(private val document: Document, private val pageUrl: String) {
    /** The product's own buy box; without it, the page without the other products' boxes. */
    private val buyBox: Element = document.selectFirst(".product-detail-buy")
        ?: document.clone().also { it.select(OTHER_PRODUCTS).remove() }

    fun read(): ProductPage {
        val articleNumber = articleNumber()
        val title = title()
        val breadcrumb = breadcrumb()
        val price = price()
        val grams = packageGrams()
        val page = ProductPage(
            articleNumber = articleNumber,
            key = (articleNumber ?: ProductUrls.articleNumberOf(pageUrl)?.text)?.let { ArticleNumber.parse(it)?.base },
            productUrl = canonicalUrl() ?: pageUrl,
            name = title?.let(Titles::name),
            secondaryName = title?.let(Titles::secondaryName),
            breadcrumb = breadcrumb,
            scope = GreenTeaScope.scope(breadcrumb),
            teaType = GreenTeaScope.teaType(breadcrumb),
            priceCents = price,
            packageGrams = grams,
            basePriceCentsPer100g = basePricePer100g() ?: Prices.per100g(price, grams),
            available = available(),
            sizeOptions = SizeOptions.read(buyBox),
        )
        val detailed = ProductDetails(document, title, breadcrumb).applyTo(page)
        // A Japan tea without a type level needs its name and badges (D-089).
        val greenBadge = detailed.steaming != null || detailed.shading != null
        return detailed.copy(
            scope = GreenTeaScope.scope(breadcrumb, detailed.name, greenBadge),
            teaType = GreenTeaScope.teaType(breadcrumb, detailed.name),
        )
    }

    private fun title(): String? = document.selectFirst("h1.product-detail-name")?.cleanText()
        ?: productMicrodata("name")

    private fun articleNumber(): String? =
        buyBox.selectFirst(".product-detail-ordernumber[itemprop=sku], .product-detail-ordernumber")?.cleanText()

    private fun canonicalUrl(): String? = document.selectFirst("link[rel=canonical]")?.absUrl("href")?.ifBlank { null }

    private fun price(): Long? = buyBox.selectFirst(".product-detail-price")?.cleanText()?.let(Prices::euroCents)
        ?: buyBox.selectFirst("meta[itemprop=price]")?.attr("content")?.let(Prices::decimalCents)

    private fun unitText(): String? = buyBox.selectFirst(".product-detail-price-unit")?.cleanText()

    private fun packageGrams(): Double? {
        val content = buyBox.selectFirst(".price-unit-content")?.cleanText() ?: unitText()?.substringBefore('(')
        return content?.let(Prices::grams)
    }

    private fun basePricePer100g(): Long? = unitText()?.let(Prices::referencePer100g)

    private fun available(): Boolean? = when {
        buyBox.selectFirst(".acris-stock-notification") != null -> false
        buyBox.selectFirst(".delivery-information") != null && buyBox.selectFirst(".btn-buy") != null -> true
        else -> null
    }

    private fun breadcrumb(): List<Crumb> {
        val list = document.selectFirst("ol.breadcrumb") ?: return emptyList()
        return list.select("li.breadcrumb-item").filterNot { it.hasClass("home-link") }.mapNotNull { item ->
            val link = item.selectFirst("a[href]") ?: return@mapNotNull null
            val path = pathOf(link.absUrl("href").ifBlank { link.attr("href") }) ?: return@mapNotNull null
            val title = (item.selectFirst(".breadcrumb-title") ?: link).cleanText()
            Crumb(title = title, path = path).takeIf { title.isNotEmpty() && path != "/" }
        }
    }

    /** A property of the page's own schema.org Product (not of the products nested in it). */
    private fun productMicrodata(property: String): String? {
        val product = document.selectFirst("[itemscope][itemtype~=(?i)schema\\.org/Product$]") ?: return null
        return product.select("[itemprop=$property]")
            .firstOrNull { element -> element.parents().firstOrNull { it.hasAttr("itemscope") } == product }
            ?.let { if (it.tagName() == "meta") it.attr("content") else it.cleanText() }
            ?.let(::normalisedSpace)
            ?.ifBlank { null }
    }

    private fun pathOf(url: String): String? = try {
        URI(url).path?.ifBlank { null }
    } catch (_: URISyntaxException) {
        null
    }
}

/** The name and secondary name in a product title (08 §2.3). */
private object Titles {
    /** „– 100g", „- 30 g", „— 0,5 kg" at the end. */
    private val SIZE_SUFFIX = Regex("""\s+[–—-]\s*\d+(?:[.,]\d+)?\s*k?g\s*$""", RegexOption.IGNORE_CASE)

    /** CJK punctuation, kana, ideographs: a run of them, spaces inside allowed. */
    private const val CJK = "\\u3000-\\u303F\\u3040-\\u309F\\u30A0-\\u30FF\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF"
    private val CJK_RUN = Regex("[$CJK]+(?:\\s+[$CJK]+)*")
    private const val SEPARATORS = " -–—|/·,"

    private fun withoutSize(title: String) = normalisedSpace(title).replace(SIZE_SUFFIX, "")

    fun name(title: String): String? {
        val bare = withoutSize(title)
        val latin = normalisedSpace(bare.replace(CJK_RUN, " ")).trim { it in SEPARATORS }
        return latin.ifEmpty { bare }.ifEmpty { null }
    }

    fun secondaryName(title: String): String? {
        val bare = withoutSize(title)
        val latin = normalisedSpace(bare.replace(CJK_RUN, " ")).trim { it in SEPARATORS }
        if (latin.isEmpty()) return null
        return CJK_RUN.findAll(bare).joinToString(" ") { it.value.trim() }.ifEmpty { null }
    }
}

/** German price and quantity texts (08 §2.3): „1.234,50 €", „0.1 kg", „(179,00 € / 1 kg)". */
private object Prices {
    private const val EURO = """(\d{1,3}(?:\.\d{3})+|\d+),(\d{2})"""
    private val EURO_AMOUNT = Regex(EURO)
    private val QUANTITY = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|g)\b""", RegexOption.IGNORE_CASE)
    private val REFERENCE = Regex("""$EURO\s*€\s*/\s*(\d+(?:[.,]\d+)?)?\s*(kg|g)\b""", RegexOption.IGNORE_CASE)
    private const val CENTS_PER_EURO = 100L
    private const val GRAMS_PER_KG = 1000
    private const val PER_100_G = 100
    private const val QUANTITY_GROUP = 3
    private const val UNIT_GROUP = 4

    fun euroCents(text: String): Long? = EURO_AMOUNT.find(text)?.let { cents(it.groupValues[1], it.groupValues[2]) }

    fun decimalCents(text: String): Long? = text.trim().toBigDecimalOrNull()
        ?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()

    fun grams(text: String): Double? = QUANTITY.find(text)?.let { quantityGrams(it.groupValues[1], it.groupValues[2]) }

    fun referencePer100g(text: String): Long? = REFERENCE.find(text)?.groupValues?.let { groups ->
        val grams = quantityGrams(groups[QUANTITY_GROUP].ifEmpty { "1" }, groups[UNIT_GROUP])
        grams?.let { per100g(cents(groups[1], groups[2]), it) }
    }

    fun per100g(cents: Long?, grams: Double?): Long? {
        if (cents == null || grams == null || grams <= 0.0) return null
        return BigDecimal(cents).multiply(BigDecimal(PER_100_G))
            .divide(BigDecimal.valueOf(grams), 0, RoundingMode.HALF_UP)
            .toLong()
    }

    private fun cents(euros: String, cents: String) = euros.replace(".", "").toLong() * CENTS_PER_EURO + cents.toLong()

    private fun quantityGrams(number: String, unit: String): Double? {
        val value = number.replace(',', '.').toBigDecimalOrNull() ?: return null
        val grams = if (unit.equals("kg", ignoreCase = true)) value.multiply(BigDecimal(GRAMS_PER_KG)) else value
        return grams.toDouble()
    }
}
