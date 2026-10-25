package com.umamit.core.data.catalog

import com.umamit.core.catalog.parser.GuideArticle
import com.umamit.core.catalog.parser.GuideCandidate
import com.umamit.core.catalog.parser.GuideLinkResolver
import com.umamit.core.catalog.parser.GuideParse
import com.umamit.core.catalog.parser.GuideParser
import com.umamit.core.catalog.parser.GuideUrls
import com.umamit.core.common.WallClock
import com.umamit.core.data.PhotoTarget
import com.umamit.core.data.PhotoTranscoder
import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.database.entity.CatalogGuideEntity
import com.umamit.core.database.entity.StagedCatalogGuideEntity
import com.umamit.core.domain.guide.GuideMarkdown
import com.umamit.core.model.GuideBlock
import com.umamit.core.network.ShopClient
import com.umamit.core.network.ShopFetch
import com.umamit.core.network.ShopSession
import com.umamit.core.network.StopCause
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** How the guides of one refresh went, for the details and the log (CAT-09, D-087). */
internal class GuideCounts {
    var selected = 0

    /** Articles fetched so far, whatever came of them (the progress). */
    var read = 0
    var stored = 0
    var kept = 0
    var unavailable = 0
    var imagesFetched = 0
    var imagesMissing = 0

    fun line() = "guides: $selected selected, $stored stored, $kept kept from before (unreadable), " +
        "$unavailable unavailable, images $imagesFetched fetched, $imagesMissing without"
}

/**
 * The refresh's *Tee-Info* step (CAT-09, 06 §5.3 step 6, 08 §2.7, D-087), after the photos and
 * before the swap: the selected articles and their images through the refresh's [ShopSession]
 * (robots.txt, ≤ 2 requests/s, stops), converted and staged in `catalog_guide_staging`, so they
 * replace the guides with the teas in the swap's transaction, or not at all.
 *
 * - The articles are the sitemap's `/tee-info/<slug>` URLs whose slug names a keyword
 *   ([GuideUrls]). Without any in the sitemap the current guides are carried over, so a renamed
 *   blog never empties *Ratgeber*.
 * - An article that is gone (404, robots.txt) is left out; one that no longer reads (the blog's
 *   layout changed) keeps its current version, images included.
 * - Images only while SET-22 *Katalog: Fotos laden* is on, at most [MAX_IMAGES] per article, each
 *   re-encoded to ≤ 1200 px WebP ([PhotoTarget.CATALOG]) and named after its URL: one already on
 *   disk is not fetched again. An image that is missing or doesn't decode is left out of the text.
 * - Links to a catalog tea or a stored guide become in-app links; others stay web links.
 * - Any stop ends the run like a stop on a product page: the old catalog and guides stay.
 */
internal class CatalogGuides(
    private val client: ShopClient,
    private val dao: CatalogDao,
    private val images: GuideImageFiles,
    private val clock: WallClock,
    private val parseDispatcher: CoroutineDispatcher,
    private val fileDispatcher: CoroutineDispatcher,
    private val log: (String) -> Unit,
) {
    /**
     * Stages the guides of [sitemapUrls]; [catalogKeys] maps each base number of the staged
     * catalog to its tea's key. [onGuide] follows each article read; [stop] ends the run.
     */
    @Suppress("LongParameterList") // the session, the sitemap, the catalog, the setting and the run's callbacks
    suspend fun stage(
        session: ShopSession,
        sitemapUrls: List<String>,
        catalogKeys: Map<String, String>,
        downloadImages: Boolean,
        counts: GuideCounts,
        onGuide: () -> Unit,
        stop: (StopCause, String) -> Nothing,
    ) {
        val candidates = GuideUrls.select(sitemapUrls)
        counts.selected = candidates.size
        if (candidates.isEmpty()) {
            dao.stageCurrentGuides()
            counts.kept = dao.countStagedGuides()
            log("catalog refresh: no guide in the sitemap, ${counts.kept} kept")
            return
        }
        val articles = fetchArticles(session, candidates, counts, onGuide, stop)
        val files = imageFiles(session, articles.map { it.article }, downloadImages, counts, stop)
        val slugs = articles.map { it.article.slug }.toSet() + dao.stagedGuideSlugs()
        val resolver = GuideLinkResolver(client.baseUrl.host, catalogKeys, slugs)
        for ((article, fetchedAt) in articles) {
            val resolved = article.resolve(files, resolver)
            val guide = CatalogGuideEntity(
                slug = article.slug,
                title = article.title,
                url = article.url,
                bodyMarkdown = GuideMarkdown.write(resolved.body),
                heroFile = resolved.heroFile,
                linkedCatalogKeys = Json.encodeToString(
                    ListSerializer(String.serializer()),
                    resolved.linkedCatalogKeys,
                ),
                fetchedAt = fetchedAt,
                publishedAt = article.publishedAtMillis,
            )
            dao.stageGuide(StagedCatalogGuideEntity(guide))
            counts.stored++
        }
    }

    /** An article read in this run and when. */
    private data class Fetched(val article: GuideArticle, val fetchedAt: Long)

    /**
     * Each candidate's page, in order: a converted article; one that no longer reads keeps its
     * current version in the staging; one that is gone is left out; a stop ends the run.
     */
    private suspend fun fetchArticles(
        session: ShopSession,
        candidates: List<GuideCandidate>,
        counts: GuideCounts,
        onGuide: () -> Unit,
        stop: (StopCause, String) -> Nothing,
    ): List<Fetched> {
        val articles = mutableListOf<Fetched>()
        for (candidate in candidates) {
            currentCoroutineContext().ensureActive()
            val url = candidate.url.toHttpUrlOrNull()?.takeIf { session.isAllowed(it) }
            when (val answer = url?.let { session.fetch(it) }) {
                is ShopFetch.Fetched -> {
                    val parsed = withContext(parseDispatcher) { GuideParser.parse(answer.body, candidate.url) }
                    if (parsed is GuideParse.Parsed) {
                        articles += Fetched(parsed.article.copy(slug = candidate.slug), clock.nowMs())
                    } else {
                        dao.stageCurrentGuide(candidate.slug)
                        counts.kept++
                    }
                }

                is ShopFetch.Stopped -> stop(answer.cause, requireNotNull(url).encodedPath)

                // Never sent (robots.txt) or gone: only this guide is left out.
                null, ShopFetch.Disallowed, is ShopFetch.Unavailable -> counts.unavailable++
            }
            counts.read++
            onGuide()
        }
        return articles
    }

    /** Each image of [articles] to its local file: on disk already, fetched now, or none. */
    private suspend fun imageFiles(
        session: ShopSession,
        articles: List<GuideArticle>,
        download: Boolean,
        counts: GuideCounts,
        stop: (StopCause, String) -> Nothing,
    ): Map<String, String> {
        val files = mutableMapOf<String, String>()
        for (source in articles.flatMap { it.imageUrls.take(MAX_IMAGES) }.distinct()) {
            currentCoroutineContext().ensureActive()
            val name = images.name(source)
            when {
                withContext(fileDispatcher) { images.exists(name) } -> files[source] = name

                download && storeImage(session, source, name, stop) -> {
                    files[source] = name
                    counts.imagesFetched++
                }

                else -> counts.imagesMissing++
            }
        }
        return files
    }

    /** Fetches, re-encodes and writes one image; false when there is none to store. */
    private suspend fun storeImage(
        session: ShopSession,
        source: String,
        name: String,
        stop: (StopCause, String) -> Nothing,
    ): Boolean {
        // Only the shop's own host: an image elsewhere is never fetched (rule 7).
        val url = source.toHttpUrlOrNull()?.takeIf { client.isAllowed(it) } ?: return false
        return when (val answer = session.fetch(url)) {
            is ShopFetch.Fetched -> {
                val photo = withContext(parseDispatcher) { PhotoTranscoder.transcode(answer.body, PhotoTarget.CATALOG) }
                if (photo != null) withContext(fileDispatcher) { images.write(name, photo.bytes) }
                photo != null
            }

            is ShopFetch.Stopped -> stop(answer.cause, url.encodedPath)

            ShopFetch.Disallowed, is ShopFetch.Unavailable -> false
        }
    }

    /** Deletes every guide image the catalog's guides don't show (after every run, like the photos). */
    suspend fun keepImagesOfGuides() {
        val names = dao.guides().flatMap { guide ->
            listOfNotNull(guide.heroFile) +
                GuideMarkdown.read(guide.bodyMarkdown).blocks.filterIsInstance<GuideBlock.Image>().map { it.source }
        }.toSet()
        withContext(fileDispatcher) { images.retainOnly(names) }
    }

    companion object {
        /** At most this many images per article, its hero included (D-087): bounds the request budget. */
        const val MAX_IMAGES = 12
    }
}
