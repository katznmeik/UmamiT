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
     * At most this many guides per refresh (D-087): 08 §2.4's G ≈ 10–40, so the guides never
     * stretch the request budget the PO accepted (D-076, NFR-PERF-11).
     */
    const val MAX_GUIDES = 40

    private const val SECTION = "tee-info"
    private val SLUG = Regex("[A-Za-z0-9-]+")

    /**
     * The selected guides among [urls], each once, by slug; at most [max]. With more candidates
     * the most relevant are kept (D-087): those whose slug names the most keywords, then the
     * shorter slug (a general guide before a narrow recipe), then the slug.
     */
    fun select(urls: List<String>, max: Int = MAX_GUIDES): List<GuideCandidate> =
        urls.mapNotNull { url -> slugOf(url)?.let { GuideCandidate(it, url) } }
            .distinctBy { it.slug }
            .map { it to keywordsIn(it.slug) }
            .filter { (_, keywords) -> keywords > 0 }
            .sortedWith(
                compareByDescending<Pair<GuideCandidate, Int>> { it.second }
                    .thenBy { it.first.slug.length }
                    .thenBy { it.first.slug },
            )
            .take(max)
            .map { it.first }
            .sortedBy { it.slug }

    private fun keywordsIn(slug: String): Int = KEYWORDS.count { slug.contains(it, ignoreCase = true) }

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
