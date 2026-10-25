package com.umamit.core.model

/**
 * A Tee-Kontor *Tee-Info* guide in the guides list (*Ratgeber*, SCR-20, CAT-09).
 *
 * @property slug the article's slug (`/tee-info/<slug>`), its key (05 §4.3).
 * @property publishedAtMillis the shop's publication date (JSON-LD `datePublished`), if it gave one.
 * @property heroPath the local hero image file, if the guide has one.
 */
data class GuideSummary(val slug: String, val title: String, val publishedAtMillis: Long?, val heroPath: String?)

/**
 * A guide as its article (SCR-20): read only from the local catalog, so it works offline.
 *
 * @property url the article on the shop, for *Im Browser öffnen*.
 * @property body the converted article (08 §2.7); its images are local file paths.
 */
data class GuideDetail(
    val slug: String,
    val title: String,
    val url: String,
    val publishedAtMillis: Long?,
    val heroPath: String?,
    val body: GuideDocument,
)

/** A guide's body (CAT-09, 08 §2.7): headings, paragraphs, lists and images; only basic formatting (08 §2.8). */
data class GuideDocument(val blocks: List<GuideBlock>)

/** One block of a guide's body. */
sealed interface GuideBlock {
    /** A heading; the shop's headings and bold lead lines of a paragraph become these. */
    data class Heading(val text: String) : GuideBlock

    data class Paragraph(val inlines: List<GuideInline>) : GuideBlock

    /** A bulleted or numbered list, one line per item. */
    data class ListBlock(val ordered: Boolean, val items: List<List<GuideInline>>) : GuideBlock

    /** An image: before the refresh downloads it its URL, afterwards its local file (D-087). */
    data class Image(val source: String, val alt: String = "") : GuideBlock
}

/** Text in a paragraph or list item. */
sealed interface GuideInline {
    /** A run of text in one style; [link] is its link's target ([GuideLink]), if it is a link. */
    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val link: String? = null,
    ) : GuideInline

    data object LineBreak : GuideInline
}

/**
 * Where a link in a guide leads (CAT-09): a catalog tea or another guide inside the app, anything
 * else in the browser. The refresh writes in-app targets as `umamit://catalog/<key>` and
 * `umamit://guide/<slug>`; these are no registered links, only markers in the stored text.
 */
sealed interface GuideLink {
    data class Tea(val key: String) : GuideLink

    data class Guide(val slug: String) : GuideLink

    /** A web page, opened in the browser (the user's own navigation; the app sends no request). */
    data class Web(val url: String) : GuideLink

    companion object {
        private const val TEA_PREFIX = "umamit://catalog/"
        private const val GUIDE_PREFIX = "umamit://guide/"

        fun tea(key: String): String = TEA_PREFIX + key

        fun guide(slug: String): String = GUIDE_PREFIX + slug

        /** The link of [target], or null for a target that is neither in-app nor http(s). */
        fun parse(target: String): GuideLink? = when {
            target.startsWith(TEA_PREFIX) -> target.removePrefix(TEA_PREFIX).takeIf { it.isNotEmpty() }?.let(::Tea)

            target.startsWith(GUIDE_PREFIX) -> target.removePrefix(GUIDE_PREFIX).takeIf {
                it.isNotEmpty()
            }?.let(::Guide)

            target.startsWith("https://") || target.startsWith("http://") -> Web(target)

            else -> null
        }
    }
}
