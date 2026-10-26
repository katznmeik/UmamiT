package com.umamit.core.catalog.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Safelist

/**
 * The detail fields of a product page (08 §2.3; #66): photo, properties table, badges, taste
 * scales, description, ingredients, flavoured flag, region and the brewing grid and prose.
 *
 * Every field is read from the product's own sections: a copy of the page without the other
 * products it embeds (cross-selling boxes and sliders, D-076). A field the page doesn't show
 * stays null (or false, or empty).
 */
internal class ProductDetails(document: Document, private val title: String?, private val breadcrumb: List<Crumb>) {
    private val own: Document = document.clone().also { it.select(OTHER_PRODUCTS).remove() }

    private val properties: List<Pair<String, String>> by lazy { readProperties() }
    private val badges: List<Element> by lazy { own.select(".st-usps .filter-active") }
    private val description: Element? by lazy { own.selectFirst(".product-detail-description-text")?.let(::sanitised) }
    private val paragraphs: List<String> by lazy { description?.let(::blocks).orEmpty() }

    fun applyTo(page: ProductPage): ProductPage {
        val aroma = property("aroma")
        val ingredients = ingredients()
        val descriptionText = paragraphs.joinToString("\n\n").ifEmpty { null }
        return page.copy(
            photoUrl = photoUrl(),
            originCountry = property("anbaugebiet"),
            originRegion = Regions.find(title) ?: Regions.find(descriptionText),
            cultivar = property("kultivar"),
            harvest = factBadge("ernte"),
            shading = factBadge("beschattung"),
            steaming = factBadge("dämpfung"),
            isOrganic = plainBadges().any {
                it.equals(BIO, ignoreCase = true) ||
                    it.startsWith("$BIO ", ignoreCase = true)
            },
            isFlavored = Flavour.isFlavored(aroma, ingredients, breadcrumb),
            coldBrewSuitable = plainBadges().any { COLD_BREW.containsMatchIn(it) },
            aroma = aroma,
            tasteNotes = property("geschmack"),
            manufacturer = property("hersteller"),
            season = property("saison"),
            matchaGrade = property("matcha qualit"),
            tasteScales = scales(),
            ingredients = ingredients,
            descriptionHtml = description?.html()?.trim()?.ifEmpty { null },
            descriptionText = descriptionText,
            brewTable = brewGrid(),
            brewText = paragraphs.filter { BrewProse.isInstruction(it) && !it.isIngredients() }
                .joinToString("\n\n")
                .ifEmpty { null },
        )
    }

    /** The gallery's first full-size image, else the product microdata's image; only http(s) URLs. */
    private fun photoUrl(): String? {
        val gallery: Element? = own.selectFirst(".product-detail-media img.gallery-slider-image[data-full-image]")
            ?: own.selectFirst("img.gallery-slider-image[data-full-image]")
        val microdata: Element? = own.selectFirst("[itemscope][itemtype~=(?i)schema\\.org/Product$] [itemprop=image]")
        return listOfNotNull(
            gallery?.absUrl("data-full-image"),
            microdata?.let { it.absUrl("content").ifEmpty { it.absUrl("href") }.ifEmpty { it.absUrl("src") } },
        ).firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
    }

    /** `th.properties-label` („Anbaugebiete:") to its values (`td > span`, joined), without the mobile label. */
    private fun readProperties(): List<Pair<String, String>> =
        own.select("table.product-detail-properties-table tr.properties-row").mapNotNull { row ->
            val label = row.selectFirst("th.properties-label")?.cleanText()?.removeSuffix(":")?.trim()
            val cell = row.selectFirst("td.properties-value")
            if (label.isNullOrEmpty() || cell == null) return@mapNotNull null
            // Each value after the first starts with its own „, " on the shop (live sample 1).
            val spans = cell.children().filter { it.tagName() == "span" && !it.hasClass(PROPERTY_LABEL) }
                .map { it.cleanText().trim(',', ';', ' ') }
                .filter { it.isNotEmpty() }
            val value = spans.joinToString(", ")
                .ifEmpty { cell.clone().also { it.select(".$PROPERTY_LABEL").remove() }.cleanText() }
            value.takeIf { it.isNotEmpty() }?.let { label to it }
        }

    /** The first property whose label starts with [prefix] (lower case). */
    private fun property(prefix: String): String? = properties.firstOrNull { (label, _) ->
        label.lowercase().startsWith(prefix)
    }?.second

    private fun plainBadges(): List<String> = badges.filterNot { it.hasClass(INVERTED) }.map { it.cleanText() }

    /** The value of an inverted badge „[label]: value" (Dämpfung, Ernte, Beschattung). */
    private fun factBadge(label: String): String? = badges.filter { it.hasClass(INVERTED) }
        .map { it.cleanText() }
        .firstOrNull { it.substringBefore(':').trim().lowercase() == label && ':' in it }
        ?.substringAfter(':')
        ?.trim()
        ?.ifEmpty { null }

    /** Each scale's label and its filled leaves (`scale-full-leaf.svg`) of 5. */
    private fun scales(): List<ShopScale> = own.select(".st-scales-usp .st-scale-label").mapNotNull { label ->
        // The shop's labels end with a colon („Umami:"); the label is the name alone.
        val text = label.cleanText().removeSuffix(":").trim().ifEmpty { return@mapNotNull null }
        val row = label.closest(".container") ?: label.parent() ?: return@mapNotNull null
        ShopScale(text, row.select(".st-scale-value img[src*=scale-full-leaf]").size.coerceAtMost(SCALE_LEAVES))
    }

    private fun ingredients(): String? = paragraphs.firstOrNull { it.isIngredients() }
        ?.substringAfter(':')
        ?.let { normalisedSpace(it) }
        ?.ifEmpty { null }

    private fun String.isIngredients() = startsWith(INGREDIENTS, ignoreCase = true)

    /**
     * The brewing grid (`div.st-zubereitung`): the outermost of the nested matches only, so each
     * row is read once; each cell without the mobile label, labelled by its header column; the
     * method icon cell is left out.
     */
    private fun brewGrid(): List<BrewGridRow> {
        val grid = own.selectFirst("div.st-zubereitung") ?: return emptyList()
        val header = grid.select(".st-zubereitung-header-col").map { it.cleanText() }
        return grid.select(".st-zubereitung-value").mapNotNull { row ->
            val kind = row.classNames().firstOrNull { it.startsWith(GRID_PREFIX) && it !in GRID_CLASSES }
                ?.removePrefix(GRID_PREFIX)
                ?: return@mapNotNull null
            val cells = row.select(".st-zubereitung-value-col")
                .filterNot { cell -> cell.selectFirst("img") != null && cell.cleanText().isEmpty() }
                .mapIndexed { index, cell ->
                    val mobileLabel = cell.selectFirst(".st-zubereitung-mobilelabel")?.cleanText()
                    val text = cell.clone().also { it.select(".st-zubereitung-mobilelabel").remove() }.cleanText()
                    BrewGridCell(label = header.getOrNull(index) ?: mobileLabel?.ifEmpty { null }, text = text)
                }
            BrewGridRow(kind, cells).takeIf { cells.isNotEmpty() }
        }
    }

    private companion object {
        const val BIO = "Bio"
        val COLD_BREW = Regex("""cold\s*-?\s*brew""", RegexOption.IGNORE_CASE)
        const val INVERTED = "st-invers"
        const val PROPERTY_LABEL = "st-property-label"
        const val INGREDIENTS = "Zutaten:"
        const val SCALE_LEAVES = 5
        const val GRID_PREFIX = "st-zubereitung-"
        val GRID_CLASSES = setOf("st-zubereitung-value", "st-zubereitung-header")
    }
}

/** The other products' boxes and sliders on a product page (cross-selling, D-076). */
internal const val OTHER_PRODUCTS = ".product-box, .cms-element-product-slider, .product-slider"

internal fun Element.cleanText(): String = normalisedSpace(text())

/** [text] on one line: control characters (U+0007 occurs on the shop) and runs of white space become one space. */
internal fun normalisedSpace(text: String): String =
    text.replace(CONTROL, " ").replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()

/** C0 and C1 control characters but tab, line feed and carriage return (08 §2.8). */
private val CONTROL = Regex("""[\p{Cc}&&[^\t\n\r]]""")

/**
 * The description sanitised (08 §2.8): staff tips (personal data, D-076), scripts, styles,
 * forms, images and tracking pixels removed; embedded players become a plain link; headings
 * become bold paragraphs; only basic formatting and absolute http(s) links remain, without
 * attributes but `href`; empty paragraphs and links without a target are dropped.
 */
private fun sanitised(description: Element): Element {
    val copy = description.clone()
    copy.select(".st-teamtipp, script, style, noscript, form, object, embed, video, audio, svg, img, picture").remove()
    copy.select("iframe").forEach { frame ->
        val src = frame.absUrl("src")
        if (src.startsWith("https://") || src.startsWith("http://")) {
            frame.replaceWith(Element("p").appendChild(Element("a").attr("href", src).text(src)))
        } else {
            frame.remove()
        }
    }
    copy.select("h1, h2, h3, h4, h5, h6").forEach { heading ->
        heading.replaceWith(Element("p").appendChild(Element("b").text(heading.text())))
    }
    val safelist = Safelist()
        .addTags("p", "br", "b", "strong", "i", "em", "u", "ul", "ol", "li", "a")
        .addAttributes("a", "href")
        .addProtocols("a", "href", "http", "https")
    val output = Document.OutputSettings().prettyPrint(false)
    // In a shell of its own, so the HTML handed to the cleaner is not pretty-printed.
    val shell = Document.createShell(description.baseUri()).also { it.outputSettings(output) }
    shell.body().appendChild(copy)
    // Control characters are no markup: replacing them in the cleaned HTML only touches text.
    val clean = Jsoup.clean(copy.html(), description.baseUri(), safelist, output).replace(CONTROL, " ")
    val body = Jsoup.parseBodyFragment(clean, description.baseUri()).also { it.outputSettings(output) }.body()
    body.select("a:not([href])").forEach { it.unwrap() }
    body.select("p, li").filter { it.text().isBlank() && it.selectFirst("br") == null }.forEach { it.remove() }
    return body
}

/** The plain text of each paragraph or list item of [body], a line break for each `<br>`. */
private fun blocks(body: Element): List<String> {
    val elements = body.select("p, li").ifEmpty { listOf(body) }
    return elements.map { block ->
        val copy = block.clone()
        copy.select("br").forEach { it.replaceWith(TextNode("\n")) }
        copy.wholeText().lines().map(::normalisedSpace).filter { it.isNotEmpty() }.joinToString("\n")
    }.filter { it.isNotEmpty() }
}

/** Whether a tea is flavoured (08 §2.3, D-078). */
private object Flavour {
    /** Words that make an ingredient tea. */
    private val TEA = listOf(
        "tee", "tea", "matcha", "sencha", "gyokuro", "bancha", "kukicha", "karigane", "hojicha", "houjicha",
        "genmaicha", "tencha", "kabusecha", "shincha", "tamaryokucha", "kamairicha",
    )

    /** Genmaicha's roasted rice is a classic blend, not a flavouring. */
    private val RICE = listOf("reis", "genmai")

    /** Commas and semicolons outside parentheses. */
    private val SEPARATOR = Regex("""[,;](?![^(]*\))""")
    private val PARENTHESES = Regex("""\([^)]*\)""")

    fun isFlavored(aroma: String?, ingredients: String?, breadcrumb: List<Crumb>): Boolean =
        (aroma != null && !aroma.contains("naturbelassen", ignoreCase = true)) ||
            (ingredients != null && beyondTea(ingredients)) ||
            breadcrumb.any { "aromatisierter-gruentee" in it.path }

    private fun beyondTea(ingredients: String): Boolean = ingredients.split(SEPARATOR)
        .map { it.replace(PARENTHESES, " ").trim().trimEnd('.', '*').trim().lowercase() }
        .filter { it.isNotEmpty() && !it.startsWith("*") }
        .any { item -> TEA.none { it in item } && RICE.none { it in item } }
}

/** Growing regions recognised in titles and prose (08 §2.3): Japan, China, Taiwan, Korea. */
private object Regions {
    private val NAMES = listOf(
        // Japan
        "Uji", "Kyoto", "Wazuka", "Shizuoka", "Kakegawa", "Makinohara", "Kawane", "Honyama", "Tenryu",
        "Kagoshima", "Kirishima", "Chiran", "Satsuma", "Yame", "Hoshino", "Fukuoka", "Miyazaki", "Takachiho",
        "Gokase", "Kumamoto", "Ureshino", "Nara", "Tsukigase", "Mie", "Ise", "Nishio", "Aichi",
        "Sayama", "Saitama", "Shirakawa", "Gifu", "Tanegashima", "Yakushima", "Okinawa",
        // China
        "Zhejiang", "Hangzhou", "Anhui", "Huangshan", "Fujian", "Yunnan", "Sichuan", "Jiangsu", "Guangxi",
        "Hunan", "Hubei", "Guizhou", "Jiangxi", "Henan", "Xinyang", "Anji",
        // Taiwan
        "Nantou", "Pinglin", "Sanxia", "Alishan",
        // Korea
        "Jeju", "Hadong", "Boseong",
    )
    private val PATTERN = Regex("""(?<![\p{L}\p{N}])(${NAMES.joinToString("|")})(?![\p{L}\p{N}])""")

    /** The first region named in [text], or null. */
    fun find(text: String?): String? = text?.let { PATTERN.find(it)?.groupValues?.get(1) }
}
