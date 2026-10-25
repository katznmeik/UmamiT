package com.umamit.core.catalog.parser

import java.net.URI
import java.net.URISyntaxException

/** A *Tee-Info* article the refresh fetches (CAT-09): its [slug] and its page's [url]. */
data class GuideCandidate(val slug: String, val url: String)

/**
 * Finds the green-tea guides among a sitemap's URLs (08 §2.7): the articles `/tee-info/<slug>`
 * whose slug names one of the keywords. The blog's index shows only category tiles, so the
 * sitemap is the only way to them.
 */
object GuideUrls {
    /** 08 §2.7's slug keywords. */
    val KEYWORDS = listOf(
        "japantee", "gruentee", "matcha", "sencha", "gyokuro", "kabuse", "shincha", "houjicha", "genmaicha",
        "kaltaufguss", "kyusu", "umami",
    )

    /**
     * At most this many guides per refresh (D-087): 08 §2.4 expects 10–40; the cap keeps a shop
     * that suddenly names hundreds of articles from stretching the refresh.
     */
    const val MAX_GUIDES = 60

    private const val SECTION = "tee-info"
    private val SLUG = Regex("[A-Za-z0-9-]+")

    /** The selected guides among [urls], each once, by slug; at most [MAX_GUIDES]. */
    fun select(urls: List<String>): List<GuideCandidate> =
        urls.mapNotNull { url -> slugOf(url)?.let { GuideCandidate(it, url) } }
            .filter { candidate -> KEYWORDS.any { candidate.slug.contains(it, ignoreCase = true) } }
            .distinctBy { it.slug }
            .sortedBy { it.slug }
            .take(MAX_GUIDES)

    /** The slug of an article URL `/tee-info/<slug>`, or null for any other URL. */
    fun slugOf(url: String): String? {
        val path = try {
            URI(url).path
        } catch (_: URISyntaxException) {
            null
        } ?: return null
        val segments = path.split('/').filter { it.isNotEmpty() }
        return segments.takeIf { !path.endsWith("/") && it.size == 2 && it[0] == SECTION && SLUG.matches(it[1]) }
            ?.get(1)
    }
}
