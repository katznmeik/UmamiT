package com.umamit.core.catalog.parser

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Synthetic shop documents for tests (D-016): the structure of the shop's product pages, sitemaps
 * and robots.txt (08 §2.2–§2.4), with invented names, numbers and texts. Nothing here is taken
 * from the shop; each parameter switches one part of a page on, off or to another shape.
 */
object SyntheticShop {
    /** The `Tee › Grüner Tee › Japan › Sencha` path of a green tea. */
    val SENCHA_PATH = listOf(
        "Tee" to "/tee/",
        "Grüner Tee" to "/tee/gruener-tee/",
        "Japan" to "/tee/gruener-tee/japan/",
        "Sencha" to "/tee/gruener-tee/japan/sencha/",
    )

    /** A black tea's path: out of scope. */
    val BLACK_TEA_PATH = listOf(
        "Tee" to "/tee/",
        "Schwarzer Tee" to "/tee/schwarzer-tee/",
        "Indien" to "/tee/schwarzer-tee/indien/",
    )

    /** Teaware: out of scope. */
    val TEAWARE_PATH = listOf(
        "Zubehör" to "/zubehoer/",
        "Kannen" to "/zubehoer/kannen/",
    )

    /** How the buy box shows the stock (08 §2.3). */
    enum class Stock { IN_STOCK, OUT_OF_STOCK, UNKNOWN }

    /** The brewing grid's header, as the shop labels its columns (08 §2.3). */
    val BREW_HEADER = listOf("Menge", "Temperatur", "1. Aufguss", "2. Aufguss", "3. Aufguss")

    /** The path of a gallery photo on the shop (`/media/…`, with the shop's `?ts=` query). */
    fun photoPath(name: String = "synthetic") = "/media/aa/bb/cc/1700000000/$name.jpg?ts=1700000000"

    /** A sitemap index pointing at [children] (absolute URLs). */
    fun sitemapIndex(vararg children: String): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""")
        children.forEach {
            append("<sitemap><loc>").append(it.xmlEscaped())
            append("</loc><lastmod>2026-01-01T00:00:00+00:00</lastmod></sitemap>")
        }
        append("</sitemapindex>")
    }

    /** A sitemap url set listing [urls] (absolute URLs), on one line as the shop serves it. */
    fun urlSet(urls: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""")
        urls.forEach {
            append("<url><loc>").append(it.xmlEscaped())
            append("</loc><lastmod>2026-01-01</lastmod><changefreq>daily</changefreq><priority>1.0</priority></url>")
        }
        append("</urlset>")
    }

    /** [text] gzip-compressed, as the shop serves its `.xml.gz` (without `Content-Encoding`). */
    fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(text.toByteArray()) }
    }.toByteArray()

    /** The shop's robots.txt rules in shape (08 §2.2), with [extra] lines added to the star group. */
    fun robotsTxt(vararg extra: String): String = buildString {
        appendLine("User-agent: *")
        appendLine("Allow: /")
        appendLine("Disallow: */?")
        appendLine("Disallow: */account/")
        appendLine("Disallow: */bundles/")
        extra.forEach { appendLine(it) }
    }

    /**
     * A product page in the shop's structure (08 §2.3): breadcrumb, gallery, buy box (name,
     * price, unit price, stock, configurator, order number), description and cross-selling
     * boxes of other products with their own prices and buy buttons, which the parser must
     * ignore. A null parameter leaves its part out.
     */
    @Suppress("LongParameterList")
    fun productPage(
        baseUrl: String = "https://shop.example",
        title: String? = "Morgennebel Sencha – 100g",
        sku: String? = "XY0100",
        canonicalPath: String? = "/morgennebel-sencha/XY0100",
        breadcrumb: List<Pair<String, String>>? = SENCHA_PATH,
        price: String? = "12,40 €",
        metaPrice: String? = "12.4",
        unitContent: String? = "0.1 kg",
        unitReference: String? = "(124,00 € / 1 kg)",
        stock: Stock = Stock.IN_STOCK,
        crossSellingBoxes: Int = 2,
        microdataName: String? = null,
        photoPath: String? = photoPath(),
        description: String? = "<p>Ein erfundener Beschreibungstext für den Test.</p>",
        teamTip: Boolean = false,
        properties: List<Pair<String, List<String>>> = emptyList(),
        badges: List<String> = emptyList(),
        invertedBadges: List<String> = emptyList(),
        scales: List<Pair<String, Int>> = emptyList(),
        brewRows: List<Pair<String, List<String>>> = emptyList(),
    ): String = buildString {
        append("<!DOCTYPE html><html lang=\"de-DE\"><head><meta charset=\"utf-8\">")
        append("<title>Synthetic shop</title>")
        if (canonicalPath != null) append("<link rel=\"canonical\" href=\"$baseUrl$canonicalPath\">")
        append("<meta property=\"og:type\" content=\"product\">")
        append("<script src=\"/cdn-cgi/challenge-platform/scripts/beacon.js\"></script>")
        append("</head><body><main class=\"content-main\">")
        if (breadcrumb != null) append(breadcrumbHtml(baseUrl, breadcrumb))
        append("<div class=\"product-detail\" itemscope itemtype=\"https://schema.org/Product\">")
        if (microdataName != null) append("<meta itemprop=\"name\" content=\"${microdataName.htmlEscaped()}\">")
        append("<div class=\"row product-detail-main\">")
        append("<div class=\"col-lg-7 col-xl-8 product-detail-media\"><div class=\"cms-element-image-gallery\">")
        append("<img class=\"gallery-slider-image img-fluid\" itemprop=\"image\" ")
        if (photoPath != null) append("data-full-image=\"$baseUrl$photoPath\" ")
        append("data-srcset=\"$baseUrl/thumbnail/aa/bb/cc/synthetic_400x400.jpg 400w\" src=\"data:,\">")
        // The zoom modal repeats the image without data-full-image.
        append("<img class=\"gallery-slider-image js-load-img\" src=\"data:,\">")
        append("</div></div>")
        append("<div class=\"col-lg-5 col-xl-4 product-detail-buy\">")
        if (title != null) {
            append("<div class=\"cms-element-product-name\"><h1 class=\"product-detail-name\" itemprop=\"name\">")
            append(title.htmlEscaped()).append("</h1></div>")
        }
        append("<div class=\"cms-element-buy-box\"><div class=\"product-detail-buy-1\">")
        append("<div class=\"product-detail-price-container\">")
        if (metaPrice != null) append("<meta itemprop=\"price\" content=\"$metaPrice\">")
        append("<span class=\"visually-hidden product-detail-price-label\">Preis</span>")
        if (price != null) append("<p class=\"product-detail-price\">$price*</p>")
        append("</div>")
        if (unitContent != null || unitReference != null) {
            append("<div class=\"product-detail-price-unit\"><span class=\"price-unit-label\">Inhalt:</span>")
            if (unitContent != null) append("<span class=\"price-unit-content\"> $unitContent </span>")
            if (unitReference != null) append("<span class=\"price-unit-reference-content\">$unitReference</span>")
            append("</div>")
        }
        append(stockHtml(stock))
        append(configuratorHtml())
        if (sku != null) {
            append("<div class=\"product-detail-ordernumber-container\">")
            append("<span class=\"product-detail-ordernumber-label\">Produktnummer:</span>")
            append("<meta itemprop=\"productID\" content=\"0123abcd\">")
            append("<span class=\"product-detail-ordernumber\" itemprop=\"sku\">$sku</span></div>")
        }
        append("</div></div></div></div>")
        append("<div class=\"product-detail-tabs-content card-body\"><div class=\"tab-content\">")
        append("<div class=\"tab-pane\" id=\"description-tab-pane\">")
        append("<div class=\"product-detail-description-text\" itemprop=\"description\">")
        if (description != null) append(description)
        if (teamTip) append(TEAM_TIP)
        append("</div></div>")
        append(propertiesHtml(properties, badges, invertedBadges, scales, brewRows))
        append("<div class=\"tab-pane\" id=\"cross-selling-tab-pane\">")
        repeat(crossSellingBoxes) { append(crossSellingBox(baseUrl, it)) }
        append("</div></div></div>")
        append("</div></main></body></html>")
    }

    /** A staff tip block in the description: a person's photo, quote, name and role (stripped, D-076). */
    private const val TEAM_TIP =
        "<div class=\"st-teamtipp mt-5\"><div class=\"st-teamtipp-title\"><h4>Unser Tipp</h4></div>" +
            "<img src=\"/media/ff/ff/ff/1700000000/person.jpg\" alt=\"Erika\">" +
            "<p class=\"fst-italic\">Ein erfundenes Zitat aus dem Team.</p><h4>Erika Beispiel</h4>" +
            "<h6>Teeberaterin</h6></div>"

    private const val SCALE_LEAVES = 5

    /** The properties tab: taste scales with the badges nested inside, the properties table, the brewing grid. */
    private fun propertiesHtml(
        properties: List<Pair<String, List<String>>>,
        badges: List<String>,
        invertedBadges: List<String>,
        scales: List<Pair<String, Int>>,
        brewRows: List<Pair<String, List<String>>>,
    ): String = buildString {
        append("<div class=\"tab-pane\" id=\"properties-tab-pane\"><div class=\"offcanvas-body\">")
        append("<div class=\"product-detail-properties tab-pane-container\">")
        append("<div class=\"st-scales-usp mb-3\"><h2>Geschmacksprofil</h2>")
        scales.forEach { (label, full) ->
            append("<div class=\"container mb-1\"><div class=\"row\">")
            // As on the shop, the label ends with a colon („Umami:").
            append("<div class=\"col-auto st-scale-label\">${label.htmlEscaped()}:</div>")
            repeat(SCALE_LEAVES) { index ->
                val leaf = if (index < full) "scale-full-leaf.svg" else "scale-empty-leaf.svg"
                append("<div class=\"col-auto st-scale-value\"><img src=\"/bundles/teekontorkiel/$leaf\"></div>")
            }
            append("</div></div>")
        }
        append("<div class=\"st-usps mt-5\"><div class=\"st-usp-title\"><h4>Besonderheiten</h4></div>")
        badges.forEach { append("<div class=\"filter-active\">${it.htmlEscaped()}</div>") }
        invertedBadges.forEach { append("<div class=\"filter-active st-invers\">${it.htmlEscaped()}</div>") }
        append("</div></div>")
        if (properties.isNotEmpty()) {
            append("<table class=\"table table-striped product-detail-properties-table\"><tbody>")
            properties.forEach { (label, values) ->
                append("<tr class=\"properties-row\"><th class=\"properties-label\">${label.htmlEscaped()}:</th>")
                append("<td class=\"properties-value\"><span class=\"d-inline-block d-sm-none st-property-label\">")
                append("<strong>${label.htmlEscaped()}:</strong></span>")
                // As on the shop, every value after the first starts with its own „, ".
                values.forEachIndexed { index, value ->
                    append("<span>").append(if (index > 0) ", " else "").append(value.htmlEscaped()).append("</span>")
                }
                append("</td></tr>")
            }
            append("</tbody></table>")
        }
        if (brewRows.isNotEmpty()) append(brewGridHtml(brewRows))
        append("</div></div></div>")
    }

    /** The brewing grid, nested as on the shop: two elements match `div.st-zubereitung`. */
    private fun brewGridHtml(rows: List<Pair<String, List<String>>>): String = buildString {
        append("<div class=\"st-zubereitung mt-5 mb-5\"><h2>Zubereitung</h2>")
        append("<p class=\"p-0\">Unsere Angaben sind Richtwerte.</p><div class=\"container st-zubereitung\">")
        append("<div class=\"row st-zubereitung-header d-none d-sm-flex\"><div class=\"col-auto\"></div>")
        BREW_HEADER.forEach { append("<div class=\"col-2 st-zubereitung-header-col\"><strong>$it</strong></div>") }
        append("</div>")
        rows.forEach { (kind, cells) ->
            val icon = if (kind == "japan") "kyusu" else kind
            append("<div class=\"row st-zubereitung-$kind st-zubereitung-value\">")
            append("<div class=\"col-auto st-zubereitung-value-col\">")
            append("<img src=\"/bundles/teekontorkiel/$icon.svg\"></div>")
            cells.forEachIndexed { index, cell ->
                append("<div class=\"col-2 st-zubereitung-value-col\">")
                append(
                    "<div class=\"st-zubereitung-mobilelabel d-sm-none\">${BREW_HEADER.getOrElse(index) {
                        ""
                    }}</div>",
                )
                append(cell.htmlEscaped()).append("</div>")
            }
            append("</div>")
        }
        append("</div></div>")
    }

    private fun breadcrumbHtml(baseUrl: String, crumbs: List<Pair<String, String>>): String = buildString {
        append("<nav aria-label=\"breadcrumb\">")
        append("<ol class=\"breadcrumb\" itemscope itemtype=\"https://schema.org/BreadcrumbList\">")
        append("<li class=\"breadcrumb-pretext\"><span class=\"breadcrumb-title\">Sie sind hier:</span></li>")
        append("<li class=\"breadcrumb-item home-link\" itemprop=\"itemListElement\">")
        append("<a href=\"/\" class=\"breadcrumb-link\" itemprop=\"item\"><link itemprop=\"url\" href=\"/\">")
        append("<span class=\"breadcrumb-title\" itemprop=\"name\">Home</span></a></li>")
        crumbs.forEachIndexed { index, (title, path) ->
            append("<li class=\"breadcrumb-placeholder\"><span class=\"icon\"><svg></svg></span></li>")
            val current = if (index == crumbs.lastIndex) " aria-current=\"page\"" else ""
            val active = if (index == crumbs.lastIndex) " is-active" else ""
            append("<li class=\"breadcrumb-item\"$current itemprop=\"itemListElement\">")
            append("<a href=\"$baseUrl$path\" class=\"breadcrumb-link$active\" itemprop=\"item\">")
            append("<link itemprop=\"url\" href=\"$baseUrl$path\"><span class=\"breadcrumb-title\" itemprop=\"name\">")
            append(title.htmlEscaped()).append("</span></a><meta itemprop=\"position\" content=\"${index + 2}\"></li>")
        }
        append("</ol></nav>")
    }

    private fun stockHtml(stock: Stock): String = when (stock) {
        Stock.IN_STOCK ->
            "<div class=\"product-detail-delivery-information\"><div class=\"product-delivery-information\">" +
                "<p class=\"delivery-information delivery-available\">" +
                "<span class=\"delivery-status-indicator bg-success\"></span>Sofort verfügbar</p></div></div>" +
                "<form class=\"buy-widget\"><button class=\"btn btn-buy acris-quantity-btn\">+</button>" +
                "<button class=\"btn btn-primary btn-block btn-buy\">In den Warenkorb</button></form>"

        Stock.OUT_OF_STOCK ->
            "<div class=\"acris-stock-notification\"><p>Benachrichtige mich</p></div>"

        Stock.UNKNOWN -> ""
    }

    private fun configuratorHtml(): String =
        "<div class=\"product-detail-configurator-container\"><div class=\"product-detail-configurator\">" +
            "<form data-variant-switch=\"true\"><fieldset class=\"product-detail-configurator-group\">" +
            "<legend class=\"product-detail-configurator-group-title\">Größe</legend>" +
            "<div class=\"product-detail-configurator-options\">" +
            "<div class=\"product-detail-configurator-option\"><input type=\"radio\" checked id=\"o1\">" +
            "<label class=\"product-detail-configurator-option-label\" for=\"o1\">100g</label></div>" +
            "</div></fieldset></form></div></div>"

    private fun crossSellingBox(baseUrl: String, index: Int): String =
        "<div class=\"card product-box box-standard\" itemscope itemtype=\"https://schema.org/Product\">" +
            "<a class=\"product-name\" href=\"$baseUrl/anderer-tee-$index/ZZ09${index}0\" itemprop=\"name\">" +
            "Anderer Tee $index</a>" +
            "<div class=\"product-price-info\"><p class=\"product-price\">99,9$index €</p>" +
            "<p class=\"product-detail-price\">88,8$index €</p></div>" +
            "<span class=\"product-detail-ordernumber\" itemprop=\"sku\">ZZ09${index}0</span>" +
            "<img class=\"product-image gallery-slider-image\" " +
            "data-full-image=\"$baseUrl/media/zz/other-$index.jpg\">" +
            "<div class=\"filter-active\">Bio</div>" +
            "<div class=\"filter-active st-invers\">Ernte: Herbst</div>" +
            "<div class=\"product-detail-description-text\">" +
            "<p>Zutaten: Fremder Tee, Fremdes Aroma</p></div>" +
            "<div class=\"acris-stock-notification\"></div>" +
            "<button class=\"btn btn-buy\">In den Warenkorb</button></div>"

    private fun String.xmlEscaped() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun String.htmlEscaped() = xmlEscaped().replace("\"", "&quot;")
}
