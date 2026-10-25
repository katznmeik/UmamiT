package com.umamit.core.catalog.parser

import com.umamit.core.model.GuideBlock
import com.umamit.core.model.GuideDocument
import com.umamit.core.model.GuideInline
import com.umamit.core.model.GuideLink
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URISyntaxException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * A converted *Tee-Info* article (CAT-09, 08 §2.7) as read from the shop: its images and links
 * still the shop's absolute URLs until the refresh [resolve]s them.
 *
 * @property url the article's page (canonical), for *Im Browser öffnen*.
 * @property publishedAtMillis JSON-LD `datePublished`, if any.
 * @property heroUrl JSON-LD `image`, if any; the body doesn't repeat it.
 */
data class GuideArticle(
    val slug: String,
    val url: String,
    val title: String,
    val publishedAtMillis: Long?,
    val heroUrl: String?,
    val body: GuideDocument,
) {
    /** Every image the article shows, the hero first, each once. */
    val imageUrls: List<String>
        get() = (listOfNotNull(heroUrl) + body.blocks.filterIsInstance<GuideBlock.Image>().map { it.source }).distinct()

    /**
     * The article for storing (D-087): each image becomes its local file from [imageFiles] (an
     * image without one is left out), each link what [links] makes of it.
     */
    fun resolve(imageFiles: Map<String, String>, links: GuideLinkResolver): ResolvedGuide {
        val linked = mutableListOf<String>()
        fun span(inline: GuideInline): GuideInline {
            val link = (inline as? GuideInline.Span)?.link ?: return inline
            val target = links.resolve(link)
            (GuideLink.parse(target) as? GuideLink.Tea)?.let { linked += it.key }
            return inline.copy(link = target)
        }
        val blocks = body.blocks.mapNotNull { block ->
            when (block) {
                is GuideBlock.Image -> imageFiles[block.source]?.let { block.copy(source = it) }
                is GuideBlock.Paragraph -> block.copy(inlines = block.inlines.map(::span))
                is GuideBlock.ListBlock -> block.copy(items = block.items.map { item -> item.map(::span) })
                is GuideBlock.Heading -> block
            }
        }
        return ResolvedGuide(GuideDocument(blocks), heroUrl?.let { imageFiles[it] }, linked.distinct())
    }
}

/** An article ready for `catalog_guide` (05 §4.3): local images, in-app links and the teas it links to. */
data class ResolvedGuide(val body: GuideDocument, val heroFile: String?, val linkedCatalogKeys: List<String>)

/**
 * Where a guide's links lead (CAT-09): a product page of the shop whose tea is in the catalog
 * opens that tea, a stored guide opens that guide (both inside the app); every other link stays
 * the web address the browser opens.
 *
 * @param shopHost the shop's host (with or without `www.`); only its pages can be catalog teas or guides.
 * @param catalogKeys each base article number of the catalog (merged split numbers included,
 *   D-081) to its tea's catalog key.
 * @param guideSlugs the guides the catalog holds.
 */
class GuideLinkResolver(
    private val shopHost: String,
    private val catalogKeys: Map<String, String>,
    private val guideSlugs: Set<String>,
) {
    fun resolve(url: String): String {
        val host = try {
            URI(url).host
        } catch (_: URISyntaxException) {
            null
        }
        val onShop = host != null && host.removePrefix(WWW).equals(shopHost.removePrefix(WWW), ignoreCase = true)
        val guide = GuideUrls.slugOf(url)?.takeIf { onShop && it in guideSlugs }
        val tea = ProductUrls.articleNumberOf(url)?.let { catalogKeys[it.base] }?.takeIf { onShop }
        return when {
            guide != null -> GuideLink.guide(guide)
            tea != null -> GuideLink.tea(tea)
            else -> url
        }
    }

    private companion object {
        const val WWW = "www."
    }
}

/** How reading a guide page ended. */
sealed interface GuideParse {
    data class Parsed(val article: GuideArticle) : GuideParse

    /** The page has no article block (08 §2.7): the blog's layout changed, or it is no article. */
    data object Failed : GuideParse
}

/**
 * Reads the shop's *Tee-Info* articles (08 §2.7) and converts their body to the app's guide text
 * (08 §2.8, D-087). Never throws.
 */
object GuideParser {
    private const val ARTICLE = ".pixel-blog-detail .cms-element-text"
    private val json = Json { ignoreUnknownKeys = true }

    /** Parses [body] (the HTML of [pageUrl], charset from the page, else UTF-8). */
    fun parse(body: ByteArray, pageUrl: String): GuideParse {
        val document = Jsoup.parse(ByteArrayInputStream(body), null, pageUrl)
        val canonical = document.selectFirst("link[rel=canonical]")?.absUrl("href")
            ?.takeIf { GuideUrls.slugOf(it) != null }
        val url = canonical ?: pageUrl
        val slug = GuideUrls.slugOf(url)
        val root = document.selectFirst(ARTICLE)
        return if (root == null ||
            slug == null
        ) {
            GuideParse.Failed
        } else {
            GuideParse.Parsed(article(document, root, url, slug))
        }
    }

    /** The article [slug] at [url] (its canonical page) from its [document] and its article block [root]. */
    private fun article(document: Document, root: Element, url: String, slug: String): GuideArticle {
        val posting = blogPosting(document)
        val heading = root.selectFirst("h1")?.cleanText()?.ifEmpty { null }
        val title = posting?.string("headline") ?: posting?.string("name") ?: heading ?: slug
        val hero = posting?.get("image")?.let { imageUrl(it, url) }
        val content = root.clone().also { it.select(".title, h1").remove() }
        return GuideArticle(
            slug = slug,
            url = url,
            title = normalisedSpace(title),
            publishedAtMillis = posting?.string("datePublished")?.let(::epochMillis),
            heroUrl = hero,
            body = GuideConverter(hero).convert(content),
        )
    }

    /** The page's JSON-LD `BlogPosting` (or another article type), alone, in a list or in `@graph`. */
    private fun blogPosting(document: Document): JsonObject? = document.select("script[type=application/ld+json]")
        .asSequence()
        .mapNotNull { script ->
            try {
                json.parseToJsonElement(script.data())
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        .flatMap(::objects)
        .firstOrNull { it.string("@type") in ARTICLE_TYPES }

    private val ARTICLE_TYPES = setOf("BlogPosting", "Article", "NewsArticle")

    private fun objects(element: JsonElement): Sequence<JsonObject> = when (element) {
        is JsonObject -> sequenceOf(element) + (element["@graph"]?.let(::objects) ?: emptySequence())
        is JsonArray -> element.asSequence().flatMap(::objects)
        else -> emptySequence()
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.ifEmpty { null }

    /** A JSON-LD image: a URL, an `ImageObject` with `url`/`contentUrl`, or a list of these; only http(s). */
    private fun imageUrl(element: JsonElement, pageUrl: String): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull?.let { absolute(it, pageUrl) }
        is JsonObject -> (element.string("url") ?: element.string("contentUrl"))?.let { absolute(it, pageUrl) }
        is JsonArray -> element.firstNotNullOfOrNull { imageUrl(it, pageUrl) }
    }

    private fun absolute(url: String, base: String): String? = try {
        URI(base).resolve(url.trim()).toString().takeIf { it.startsWith("https://") || it.startsWith("http://") }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun epochMillis(date: String): Long? = try {
        OffsetDateTime.parse(date).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(date).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}

/**
 * Converts an article's body (08 §2.7, §2.8, D-087): headings, paragraphs with bold, italics,
 * line breaks and absolute http(s) links, lists, tables as lists of rows, and images (the
 * gallery's and the body's, each once, never the [hero] again). Scripts, styles, forms, staff tips,
 * tracking pixels and embeds are left out; an embedded player becomes a plain link.
 */
private class GuideConverter(hero: String?) {
    private val blocks = mutableListOf<GuideBlock>()
    private var paragraph = mutableListOf<GuideInline>()
    private val seenImages = mutableSetOf<String>().also { seen -> hero?.let { seen += imageKey(it) } }

    fun convert(root: Element): GuideDocument {
        children(root)
        endParagraph()
        return GuideDocument(blocks)
    }

    /** The block-level walk: containers are descended, blocks end the paragraph, the rest is text. */
    private fun children(element: Element) {
        element.childNodes().forEach { node ->
            if (node is Element) blockElement(node) else inline(node, Style.PLAIN)
        }
    }

    private fun blockElement(element: Element) {
        val tag = element.normalName()
        when {
            isSkipped(element) -> Unit

            element.hasClass(GALLERY) -> {
                endParagraph()
                element.select("img").forEach(::image)
            }

            tag == "iframe" -> embed(element)

            tag in HEADINGS -> {
                endParagraph()
                element.cleanText().ifEmpty { null }?.let { blocks += GuideBlock.Heading(it) }
            }

            tag == "p" -> {
                endParagraph()
                element.childNodes().forEach { inline(it, Style.PLAIN) }
                endParagraph()
            }

            tag == "ul" || tag == "ol" -> {
                endParagraph()
                list(element)
            }

            tag == "table" -> {
                endParagraph()
                table(element)
            }

            tag == "img" -> image(element)

            tag in CONTAINERS -> {
                endParagraph()
                children(element)
                endParagraph()
            }

            else -> inline(element, Style.PLAIN)
        }
    }

    private fun inline(node: Node, style: Style) {
        when (node) {
            is TextNode -> {
                val text = node.wholeText.replace(CONTROL_OR_SPACE, " ")
                if (text.isNotEmpty()) paragraph += GuideInline.Span(text, style.bold, style.italic, style.link)
            }

            is Element -> inlineElement(node, style)

            else -> Unit
        }
    }

    private fun inlineElement(element: Element, style: Style) {
        val tag = element.normalName()
        when {
            isSkipped(element) -> Unit

            tag == "br" -> paragraph += GuideInline.LineBreak

            tag == "img" -> image(element)

            tag == "iframe" -> embed(element)

            element.hasClass(GALLERY) -> element.select("img").forEach(::image)

            else -> {
                val inner = when (tag) {
                    "b", "strong" -> style.copy(bold = true)
                    "i", "em" -> style.copy(italic = true)
                    "a" -> style.copy(link = httpUrl(element.absUrl("href")) ?: style.link)
                    else -> style
                }
                element.childNodes().forEach { inline(it, inner) }
            }
        }
    }

    /** An image as a block of its own; a tracking pixel, a placeholder or an image seen before is left out. */
    private fun image(img: Element) {
        val url =
            listOf("data-full-image", "data-src", "src").firstNotNullOfOrNull { httpUrl(img.absUrl(it)) } ?: return
        val pixel = listOf("width", "height").any { (img.attr(it).toIntOrNull() ?: Int.MAX_VALUE) <= PIXEL_SIZE }
        if (pixel || !seenImages.add(imageKey(url))) return
        endParagraph()
        blocks += GuideBlock.Image(url, alt = normalisedSpace(img.attr("alt")))
    }

    /** An embedded player becomes a plain link to it (08 §2.8). */
    private fun embed(frame: Element) {
        val url = httpUrl(frame.absUrl("src")) ?: return
        endParagraph()
        blocks += GuideBlock.Paragraph(listOf(GuideInline.Span(url, link = url)))
    }

    private fun list(element: Element) {
        val items = mutableListOf<List<GuideInline>>()
        element.children().filter { it.normalName() == "li" }.forEach { item ->
            val saved = paragraph
            paragraph = mutableListOf()
            item.childNodes().filterNot { it is Element && it.normalName() in setOf("ul", "ol") }
                .forEach { inline(it, Style.PLAIN) }
            GuideInlines.tidy(paragraph).takeIf { it.isNotEmpty() }?.let { items += it }
            paragraph = saved
            item.children().filter { it.normalName() == "ul" || it.normalName() == "ol" }.forEach { nested ->
                nested.select("li").map {
                    GuideInlines.tidy(listOf(GuideInline.Span(it.ownText())))
                }.filter { it.isNotEmpty() }
                    .forEach { items += it }
            }
        }
        if (items.isNotEmpty()) blocks += GuideBlock.ListBlock(ordered = element.normalName() == "ol", items = items)
    }

    /** A table becomes a list of its rows, the cells joined by „ · ". */
    private fun table(element: Element) {
        val rows = element.select("tr").mapNotNull { row ->
            row.select("th, td").map { it.cleanText() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
        }
        if (rows.isNotEmpty()) {
            blocks += GuideBlock.ListBlock(ordered = false, items = rows.map { listOf(GuideInline.Span(it)) })
        }
    }

    private fun endParagraph() {
        val inlines = GuideInlines.tidy(paragraph)
        paragraph = mutableListOf()
        if (inlines.isEmpty()) return
        val firstBreak = inlines.indexOf(GuideInline.LineBreak).let { if (it < 0) inlines.size else it }
        val lead = inlines.subList(0, firstBreak)
        val leadText = lead.joinToString("") { (it as GuideInline.Span).text }.trim()
        val leadIsHeading = lead.all { it is GuideInline.Span && it.bold && it.link == null } &&
            leadText.length in 1..MAX_HEADING_LENGTH
        if (leadIsHeading) {
            blocks += GuideBlock.Heading(leadText)
            GuideInlines.tidy(inlines.drop(firstBreak)).takeIf { it.isNotEmpty() }?.let {
                blocks +=
                    GuideBlock.Paragraph(it)
            }
        } else {
            blocks += GuideBlock.Paragraph(inlines)
        }
    }

    private fun isSkipped(element: Element) = element.normalName() in SKIPPED || element.hasClass(STAFF_TIP)

    private data class Style(val bold: Boolean = false, val italic: Boolean = false, val link: String? = null) {
        companion object {
            val PLAIN = Style()
        }
    }

    private companion object {
        const val GALLERY = "st-blog-gallery"
        const val STAFF_TIP = "st-teamtipp"
        const val PIXEL_SIZE = 2

        /** A bold lead line longer than this is emphasis, not a heading. */
        const val MAX_HEADING_LENGTH = 120
        val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        val CONTAINERS = setOf(
            "div", "section", "article", "blockquote", "figure", "figcaption", "picture", "header", "footer",
            "main", "aside", "center", "dl", "dd", "dt",
        )
        val SKIPPED = setOf(
            "script", "style", "noscript", "form", "object", "embed", "video", "audio", "svg", "button", "input",
            "select", "textarea", "template", "nav", "link", "meta", "canvas", "map",
        )
        val CONTROL_OR_SPACE = Regex("""[\p{Cc} ]""")

        fun httpUrl(url: String): String? = url.takeIf { it.startsWith("https://") || it.startsWith("http://") }

        /** Images are told apart without their query (the shop's `?ts=`). */
        fun imageKey(url: String) = url.substringBefore('?')
    }
}

/** Tidying a paragraph's converted text (D-087). */
private object GuideInlines {
    /**
     * Runs of spaces collapsed across spans, spaces at the start, at the end and around line breaks
     * dropped, no line break first, last or twice, adjacent spans of one style merged.
     */
    fun tidy(inlines: List<GuideInline>): List<GuideInline> {
        val out = mutableListOf<GuideInline>()
        var atLineStart = true
        for (inline in inlines) {
            when (inline) {
                GuideInline.LineBreak -> {
                    trimEnd(out)
                    if (out.isNotEmpty() && out.last() != GuideInline.LineBreak) out += inline
                    atLineStart = true
                }

                is GuideInline.Span -> {
                    var text = inline.text.replace(SPACES, " ")
                    if (atLineStart || (out.lastOrNull() as? GuideInline.Span)?.text?.endsWith(' ') == true) {
                        text = text.trimStart()
                    }
                    if (text.isEmpty()) continue
                    val last = out.lastOrNull()
                    if (last is GuideInline.Span && last.copy(text = "") == inline.copy(text = "")) {
                        out[out.lastIndex] = last.copy(text = last.text + text)
                    } else {
                        out += inline.copy(text = text)
                    }
                    atLineStart = false
                }
            }
        }
        trimEnd(out)
        if (out.lastOrNull() == GuideInline.LineBreak) out.removeAt(out.lastIndex)
        return out
    }

    private fun trimEnd(out: MutableList<GuideInline>) {
        while (true) {
            val last = out.lastOrNull() as? GuideInline.Span ?: return
            val trimmed = last.text.trimEnd()
            if (trimmed == last.text) return
            if (trimmed.isEmpty()) out.removeAt(out.lastIndex) else out[out.lastIndex] = last.copy(text = trimmed)
        }
    }

    private val SPACES = Regex("""\s+""")
}
