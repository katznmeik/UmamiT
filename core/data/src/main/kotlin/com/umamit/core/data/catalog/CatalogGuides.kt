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
import com.umamit.core.database.entity.CatalogImageFailureReason
import com.umamit.core.database.entity.StagedCatalogGuideEntity
import com.umamit.core.domain.guide.GuideMarkdown
import com.umamit.core.model.GuideBlock
import com.umamit.core.network.BodyLimit
import com.umamit.core.network.ShopClient
import com.umamit.core.network.ShopFetch
import com.umamit.core.network.ShopSession
import com.umamit.core.network.StopCause
import kotlinx.coroutines.CoroutineDispatcher
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

    /** Images without a file because they failed for good before (D-091): not asked for again. */
    var imagesFailedBefore = 0

    fun line() = "guides: $selected selected, $stored stored, $kept kept from before (unreadable), " +
        "$unavailable unavailable, images $imagesFetched fetched, $imagesMissing without " +
        "($imagesFailedBefore failed before)"
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
 * - Images only while SET-22 *Katalog: Fotos laden* is on, at most [GuideLimits.maxImagesPerGuide]
 *   per article (the hero first), each up to the image body limit and re-encoded to ≤ 1200 px
 *   WebP ([PhotoTarget.CATALOG]) and named after its URL: one already on disk is not fetched
 *   again, nor one that failed for good ([ImageFailures], D-091). An image that is missing or
 *   doesn't decode is left out of the text.
 * - The next article or image is fetched while this one is converted or re-encoded
 *   ([fetchOverlapped], D-091).
 * - Links to a catalog tea or a stored guide become in-app links; others stay web links.
 * - Any stop ends the run like a stop on a product page: the old catalog and guides stay.
 */
internal class CatalogGuides(
    private val limits: GuideLimits,
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
     * catalog to its tea's key. [failures] knows the images that failed for good. [onGuide]
     * follows each article read; [stop] ends the run.
     */
    @Suppress("LongParameterList") // the session, the sitemap, the catalog, the setting, the run's state and callbacks
    suspend fun stage(
        session: ShopSession,
        sitemapUrls: List<String>,
        catalogKeys: Map<String, String>,
        downloadImages: Boolean,
        failures: ImageFailures,
        counts: GuideCounts,
        onGuide: () -> Unit,
        stop: (StopCause, String) -> Nothing,
    ) {
        val candidates = GuideUrls.select(sitemapUrls, limits.maxGuides)
        counts.selected = candidates.size
        if (candidates.isEmpty()) {
            dao.stageCurrentGuides()
            counts.kept = dao.countStagedGuides()
            log("catalog refresh: no guide in the sitemap, ${counts.kept} kept")
            return
        }
        val articles = fetchArticles(session, candidates, counts, onGuide, stop)
        val files =
            imageFiles(session, articles.map { it.article }, ImageDownload(downloadImages, failures), counts, stop)
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
        // The next article is fetched while this one is converted (D-091).
        fetchOverlapped(
            items = candidates,
            fetch = { candidate -> candidate.allowedUrl(session)?.let { session.fetch(it) } },
            isLast = { it is ShopFetch.Stopped },
        ) { candidate, answer ->
            val url = candidate.url.toHttpUrlOrNull()
            when (answer) {
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

                // Over the size limit: only this guide is left out, no stop (D-090).
                is ShopFetch.TooLarge -> {
                    log("catalog refresh: ${answer.line(requireNotNull(url))}")
                    counts.unavailable++
                }
            }
            counts.read++
            onGuide()
        }
        return articles
    }

    /** [GuideCandidate.url] when robots.txt and the allow-list let the app fetch it, else null (never sent). */
    private suspend fun GuideCandidate.allowedUrl(session: ShopSession) =
        url.toHttpUrlOrNull()?.takeIf { session.isAllowed(it) }

    /** Whether this run fetches images (SET-22), and which failed for good before (D-091). */
    private class ImageDownload(val enabled: Boolean, val failures: ImageFailures)

    /** Each image of [articles] to its local file: on disk already, fetched now, or none. */
    private suspend fun imageFiles(
        session: ShopSession,
        articles: List<GuideArticle>,
        download: ImageDownload,
        counts: GuideCounts,
        stop: (StopCause, String) -> Nothing,
    ): Map<String, String> {
        val files = mutableMapOf<String, String>()
        val toFetch = mutableListOf<Pair<String, String>>()
        for (source in articles.flatMap { it.imageUrls.take(limits.maxImagesPerGuide) }.distinct()) {
            val name = images.name(source)
            when {
                withContext(fileDispatcher) { images.exists(name) } -> files[source] = name

                // No request either way; an image that failed for good stays remembered (D-091).
                !download.enabled -> {
                    download.failures.remembers(source)
                    counts.imagesMissing++
                }

                download.failures.remembers(source) -> {
                    counts.imagesFailedBefore++
                    counts.imagesMissing++
                }

                else -> toFetch += source to name
            }
        }
        // The next image is fetched while this one is re-encoded and written (D-091).
        fetchOverlapped(
            items = toFetch,
            fetch = { (source, _) -> source.shopUrl()?.let { session.fetch(it, BodyLimit.IMAGE) } },
            isLast = { it is ShopFetch.Stopped },
        ) { (source, name), answer ->
            if (storeImage(source, name, answer, download.failures, stop)) {
                files[source] = name
                counts.imagesFetched++
            } else {
                counts.imagesMissing++
            }
        }
        return files
    }

    /** Only the shop's own host: an image elsewhere is never fetched (rule 7). */
    private fun String.shopUrl() = toHttpUrlOrNull()?.takeIf { client.isAllowed(it) }

    /**
     * Re-encodes and writes one image from its [answer] (null: not on the shop's host, never
     * fetched); false when there is none to store. An image too large or unreadable failed for
     * good (D-091).
     */
    private suspend fun storeImage(
        source: String,
        name: String,
        answer: ShopFetch?,
        failures: ImageFailures,
        stop: (StopCause, String) -> Nothing,
    ): Boolean = when (answer) {
        null -> false

        is ShopFetch.Fetched -> {
            val photo = withContext(parseDispatcher) { PhotoTranscoder.transcode(answer.body, PhotoTarget.CATALOG) }
            if (photo != null) {
                withContext(fileDispatcher) { images.write(name, photo.bytes) }
            } else {
                failures.failed(source, CatalogImageFailureReason.UNREADABLE)
            }
            photo != null
        }

        is ShopFetch.Stopped -> stop(answer.cause, requireNotNull(source.shopUrl()).encodedPath)

        ShopFetch.Disallowed, is ShopFetch.Unavailable -> false

        // Over the image limit: only this image is left out, no stop (D-090, D-091).
        is ShopFetch.TooLarge -> {
            log("catalog refresh: ${answer.line(requireNotNull(source.shopUrl()))}")
            failures.failed(source, CatalogImageFailureReason.TOO_LARGE)
            false
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
}

/**
 * The guides' share of the request budget (D-087, 08 §2.4): at most [maxGuides] articles and
 * [maxImagesPerGuide] images each (the hero first), so a first refresh sends at most
 * [maxRequests] guide requests and a later one the articles plus their new images only.
 */
data class GuideLimits(val maxGuides: Int = GuideUrls.MAX_GUIDES, val maxImagesPerGuide: Int = MAX_IMAGES_PER_GUIDE) {
    val maxRequests: Int get() = maxGuides * (1 + maxImagesPerGuide)

    companion object {
        /** 08 §2.4: G ≤ 40 guides with ≤ 4 images each, ≤ 200 requests (≈ 100 s at 2 requests/s). */
        val DEFAULT = GuideLimits()

        const val MAX_IMAGES_PER_GUIDE = 4
    }
}
