package com.umamit.core.data.catalog

import android.database.SQLException
import com.umamit.core.catalog.parser.ArticleNumber
import com.umamit.core.catalog.parser.CatalogEntry
import com.umamit.core.catalog.parser.CatalogScope
import com.umamit.core.catalog.parser.ProductGroup
import com.umamit.core.catalog.parser.ProductPage
import com.umamit.core.catalog.parser.ProductParse
import com.umamit.core.catalog.parser.TeaLink
import com.umamit.core.catalog.parser.TeaLinkResult
import com.umamit.core.catalog.parser.TeaLinks
import com.umamit.core.catalog.parser.TeeKontorParser
import com.umamit.core.common.WallClock
import com.umamit.core.data.CatalogPhoto
import com.umamit.core.data.PhotoTranscoder
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.database.entity.CatalogImageFailureEntity
import com.umamit.core.database.entity.CatalogImageFailureReason
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.network.BodyLimit
import com.umamit.core.network.HostNotAllowedException
import com.umamit.core.network.NetworkRetry
import com.umamit.core.network.ShopClient
import com.umamit.core.network.ShopFetch
import com.umamit.core.network.ShopSession
import com.umamit.core.network.StopCause
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import javax.inject.Inject

/** What a shared or pasted tea link led to (CAT-17). Only [Opened] may have changed the catalog. */
sealed interface TeaLinkOutcome {
    /** The tea [key] is in the catalog (stored or refreshed from its page, or found there): its detail opens. */
    data class Opened(val key: String) : TeaLinkOutcome

    /** Not a tea link of the shop (CAT-17 AC2): no request. */
    data object NotATeaLink : TeaLinkOutcome

    /** Not a green or herbal tea (AC5), or a set or sale duplicate: nothing stored. */
    data object NotInCatalog : TeaLinkOutcome

    /** A catalog refresh runs (AC7): no request. */
    data object RefreshRunning : TeaLinkOutcome

    /**
     * No tea at that link: 404/410, a redirect (never followed), another 4xx, or a page that
     * shows no product or another one. Nothing stored.
     */
    data object NoTeaPage : TeaLinkOutcome

    /**
     * The page can't be read: robots.txt disallows it, it is over the body limit, it has no
     * breadcrumb, or it broke the parser. Nothing stored.
     */
    data object Unreadable : TeaLinkOutcome

    /** No connection to the shop (offline, a timeout): nothing stored. */
    data object Offline : TeaLinkOutcome

    /** The shop asked the app to stop: a Cloudflare challenge or block page, 429 or `Retry-After` (08 §2.8). */
    data object Blocked : TeaLinkOutcome

    /** The shop refused or is down (HTTP 401/403/5xx). */
    data object ShopUnavailable : TeaLinkOutcome

    /** The catalog database or the photo couldn't be written (e.g. the storage is full). */
    data object StorageFailed : TeaLinkOutcome
}

/**
 * A tea from a shop link the user shares to the app or pastes (*Link einfügen*; CAT-17, 08 §2.4
 * item 9, D-094): network use (d) of GEN-01, only ever on the user's action.
 */
interface TeaLinkLoader {
    /** The tea link in [text] as the app reads it (`https://www.tee-kontor-kiel.de/…`), or null; never a request. */
    fun linkIn(text: String): String?

    /** Fetches the one tea page [text] links to and stores a green or herbal tea in the catalog. */
    suspend fun load(text: String): TeaLinkOutcome

    companion object {
        /** The longest text read; a share carries at most one character more, so a longer one is refused. */
        const val MAX_TEXT_LENGTH = TeaLinks.MAX_TEXT_LENGTH

        /** No shop: every text is refused. For previews and tests of screens that never load a link. */
        val NONE: TeaLinkLoader = object : TeaLinkLoader {
            override fun linkIn(text: String): String? = null

            override suspend fun load(text: String): TeaLinkOutcome = TeaLinkOutcome.NotATeaLink
        }
    }
}

/**
 * [TeaLinkLoader] on the shop (CLAUDE.md rule 7, 08 §2.8). The text is untrusted: [TeaLinks]
 * accepts only `https://www.tee-kontor-kiel.de/<slug>/<number>` (optional `-<grams>`), and
 * anything else ends here without a request. Then one [ShopSession] (robots.txt read first, the
 * allow-listed [ShopClient] with its one connection and ≤ 2 requests/s, no retry, a stop ends it)
 * fetches only:
 * - that one page, at the link's path resolved against the shop's own base URL; a redirect is
 *   never followed;
 * - and its photo, per SET-22 and only when the catalog has no photo from that URL (CAT-08).
 *
 * The page must show that product (its canonical and own number, [ShopProduct], as the re-check
 * of CAT-16 judges it) and be green or herbal (08 §2.4 item 3). It is then stored as a regular
 * entry ([CatalogDao.putLinkedTea]): the page's own size and the configurator's sizes, the page's
 * stock („nicht vorrätig" when sold out), as #97 keeps a tea missing from the sitemap. A tea out
 * of scope is neither stored nor put onto the skip list. A tea already in the catalog is
 * refreshed from its page; a number merged into another tea (D-081) opens that tea without a
 * request. Any stop stores nothing. It never runs while a refresh does ([CatalogJobs]).
 *
 * Like the refresh (rule 8, D-090), nothing a page does reaches the process the timer runs in:
 * an unexpected error ends as [TeaLinkOutcome.Unreadable]. The [DiagnosticsLog] gets one line per
 * link, with the article number only, never the shared text.
 */
class ShopTeaLinkLoader internal constructor(
    private val client: ShopClient,
    private val dao: CatalogDao,
    private val clock: WallClock,
    private val diagnostics: DiagnosticsLog,
    private val settings: SettingsRepository,
    private val photos: CatalogPhotoFiles,
    private val jobs: CatalogJobs,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val fileDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TeaLinkLoader {
    @Inject
    constructor(
        client: ShopClient,
        dao: CatalogDao,
        clock: WallClock,
        diagnostics: DiagnosticsLog,
        settings: SettingsRepository,
        photos: CatalogPhotoFiles,
        jobs: CatalogJobs,
    ) : this(client, dao, clock, diagnostics, settings, photos, jobs, Dispatchers.Default, Dispatchers.IO)

    override fun linkIn(text: String): String? = (TeaLinks.find(text) as? TeaLinkResult.Accepted)?.link?.url

    override suspend fun load(text: String): TeaLinkOutcome = when (val result = TeaLinks.find(text)) {
        is TeaLinkResult.Accepted -> jobs.link { guarded(result.link) } ?: TeaLinkOutcome.RefreshRunning.also {
            log("tea link: ${result.link.base} not fetched, the catalog is being refreshed")
        }

        TeaLinkResult.OutOfScope -> TeaLinkOutcome.NotInCatalog.also {
            log("tea link: a set or sale duplicate, not fetched")
        }

        TeaLinkResult.Refused -> TeaLinkOutcome.NotATeaLink.also {
            log("tea link: not a tea link of the shop, nothing fetched")
        }
    }

    private suspend fun guarded(link: TeaLink): TeaLinkOutcome = try {
        LinkJob(link).run()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SQLException) {
        log("tea link: ${link.base} not stored (${e.javaClass.simpleName})")
        TeaLinkOutcome.StorageFailed
    } catch (e: HostNotAllowedException) {
        // Never: every URL is checked before it is fetched. Still no storage error.
        log("tea link: ${link.base} not read (${e.javaClass.simpleName})")
        TeaLinkOutcome.Unreadable
    } catch (e: IOException) {
        // Only the photo files are written here; the shop's answers come as ShopFetch, never thrown.
        log("tea link: ${link.base} photo not stored (${e.javaClass.simpleName})")
        TeaLinkOutcome.StorageFailed
    } catch (
        // Rule 8: a stack overflow in a page, a parser bug, … ends this link, never the process.
        @Suppress("TooGenericExceptionCaught") e: Throwable,
    ) {
        log("tea link: ${link.base} unexpected ${e.javaClass.simpleName}, nothing stored")
        TeaLinkOutcome.Unreadable
    }

    private fun log(message: String) = diagnostics.append(clock.nowMs(), clock.zone(), message)

    /**
     * One link's job: at most robots.txt, the page and its photo, then the catalog. A link that
     * leads to no tea ends it early ([LinkEnded]) with its outcome; nothing is stored then.
     */
    @Suppress("TooManyFunctions") // one function per step: the page, its photo, the row and each way to end
    private inner class LinkJob(private val link: TeaLink) {
        private val url: HttpUrl = client.resolve(link.path)
        private val where = "tea link: ${link.base}"

        suspend fun run(): TeaLinkOutcome = try {
            val merged = mergedInto()
            if (merged != null) {
                log("$where is merged into $merged, opened without a request")
                TeaLinkOutcome.Opened(merged)
            } else {
                fetchAndStore()
            }
        } catch (e: LinkEnded) {
            e.outcome
        }

        private suspend fun fetchAndStore(): TeaLinkOutcome {
            val session = ShopSession.open(client, NetworkRetry.NONE)
            session.stopCause?.let { stopped(it, "robots.txt") }
            val page = page(session.fetch(url))
            if (!ShopProduct(client, link.base, setOf(link.base)).isShownBy(page)) {
                noTeaPage("shows another product (${page.key})")
            }
            return when (page.scope) {
                CatalogScope.GREEN, CatalogScope.HERBAL -> store(session, page)

                CatalogScope.NOT_GREEN, CatalogScope.SET -> TeaLinkOutcome.NotInCatalog.also {
                    log("$where out of scope, not stored")
                }

                CatalogScope.UNKNOWN -> unreadable("has no breadcrumb")
            }
        }

        /** The key of the tea the link's number was merged into (split sizes, D-081), when that is another tea. */
        private suspend fun mergedInto(): String? = dao.packageNumbers()
            .firstOrNull { ArticleNumber.parse(it.articleNumber)?.base == link.base }
            ?.catalogKey
            ?.takeIf { it != link.base }

        /** The product page in [answer]; any other answer ends the job. */
        private suspend fun page(answer: ShopFetch): ProductPage = when (answer) {
            is ShopFetch.Fetched -> when (val parsed = parse(answer.body)) {
                is ProductParse.Parsed -> parsed.page
                is ProductParse.Failed -> noTeaPage("shows no product")
            }

            is ShopFetch.Stopped -> stopped(answer.cause, "its page")

            ShopFetch.Disallowed -> unreadable("disallowed by robots.txt, not fetched")

            // A 3xx (never followed), 404/410 or another 4xx.
            is ShopFetch.Unavailable -> noTeaPage("answered HTTP ${answer.code}")

            is ShopFetch.TooLarge -> unreadable("over the size limit")
        }

        private suspend fun parse(body: ByteArray): ProductParse =
            withContext(parseDispatcher) { TeeKontorParser.parseProduct(body, url.toString()) }

        /**
         * Stores the tea as a regular entry with its packages (05 §4.1–4.2) and its photo; a stop
         * on the photo stores nothing. Kept from a tea already in the catalog: its first sighting
         * and, when not downloaded again, its photo.
         */
        private suspend fun store(session: ShopSession, page: ProductPage): TeaLinkOutcome {
            val group = ProductGroup(base = link.base, pageUrl = url.toString(), variantUrls = emptyMap())
            val entry = CatalogEntry.of(group, page)
            val packages = entry.packages.map { it.toEntity(entry.key) }
            val fresh = entry.page.toEntity(entry.key, clock.nowMs()).let {
                it.copy(contentHash = CatalogContentHash.of(it, packages))
            }
            val before = dao.tea(entry.key)
            val photo = photo(session, fresh, before)
            return withContext(NonCancellable) {
                photo.encoded?.let { encoded ->
                    withContext(fileDispatcher) { photos.write(entry.key, encoded.full.bytes, encoded.thumbnail.bytes) }
                }
                val tea = withPhoto(fresh, before, downloaded = photo.encoded != null)
                try {
                    dao.putLinkedTea(tea, packages, photo.failure)
                } catch (e: SQLException) {
                    // A new tea's photo goes with it; a known one keeps its row, and the next refresh fixes the file.
                    if (before == null) withContext(fileDispatcher) { photos.delete(entry.key) }
                    throw e
                }
                val photoState = when {
                    photo.encoded != null -> "stored"
                    tea.photoFile != null -> "kept"
                    else -> "none"
                }
                log(
                    "$where stored (${if (before == null) "new" else "refreshed"}, ${tea.styleKey}, " +
                        "available ${tea.available}, ${packages.size} sizes, photo $photoState)",
                )
                TeaLinkOutcome.Opened(entry.key)
            }
        }

        /**
         * The page's photo (CAT-08): fetched with SET-22 on, when it is on the shop's host, the
         * catalog has none from that URL and it didn't fail for good before (D-091). A stop on it
         * ends the job.
         */
        private suspend fun photo(session: ShopSession, fresh: CatalogTeaEntity, before: CatalogTeaEntity?): Photo {
            val photoUrl = fresh.photoUrl
            val shopUrl = photoUrl?.toHttpUrlOrNull()?.takeIf(client::isAllowed)
            return if (photoUrl != null && shopUrl != null && wanted(photoUrl, fresh.key, before)) {
                photo(photoUrl, session.fetch(shopUrl, BodyLimit.IMAGE))
            } else {
                Photo()
            }
        }

        /** SET-22 is on, the catalog has no photo from [photoUrl], and it didn't fail for good before. */
        private suspend fun wanted(photoUrl: String, key: String, before: CatalogTeaEntity?): Boolean {
            val unchanged = before?.photoUrl == photoUrl && before.photoFile != null &&
                withContext(fileDispatcher) { photos.exists(key) }
            val failedBefore = ImageFailures(dao.imageFailures(), clock.nowMs()).remembers(photoUrl)
            return !unchanged && !failedBefore && settings.settings.first().catalogDownloadPhotos
        }

        private suspend fun photo(photoUrl: String, answer: ShopFetch): Photo = when (answer) {
            is ShopFetch.Fetched -> {
                val encoded = withContext(parseDispatcher) { PhotoTranscoder.transcodeForCatalog(answer.body) }
                if (encoded != null) {
                    Photo(encoded = encoded)
                } else {
                    Photo(failure = failure(photoUrl, CatalogImageFailureReason.UNREADABLE))
                }
            }

            is ShopFetch.Stopped -> stopped(answer.cause, "its photo")

            // Never sent (robots.txt) or gone: the tea is stored without a new photo.
            ShopFetch.Disallowed, is ShopFetch.Unavailable -> Photo()

            is ShopFetch.TooLarge -> Photo(failure = failure(photoUrl, CatalogImageFailureReason.TOO_LARGE))
        }

        private fun failure(photoUrl: String, reason: CatalogImageFailureReason) =
            CatalogImageFailureEntity(photoUrl, reason, ImageFailures.RULES_VERSION, clock.nowMs())

        /** The row with its photo file: the one just downloaded, else the one kept on disk with its source URL. */
        private suspend fun withPhoto(
            fresh: CatalogTeaEntity,
            before: CatalogTeaEntity?,
            downloaded: Boolean,
        ): CatalogTeaEntity {
            val onDisk = withContext(fileDispatcher) { photos.exists(fresh.key) }
            return when {
                downloaded -> fresh.copy(photoFile = photos.photoName(fresh.key))

                onDisk && before?.photoFile != null -> fresh.copy(
                    photoFile = before.photoFile,
                    photoUrl = before.photoUrl,
                )

                else -> fresh.copy(photoFile = null)
            }
        }

        /** Ends the job: the shop said stop at [what] (08 §2.8); nothing is stored. */
        private fun stopped(cause: StopCause, what: String): Nothing {
            log("$where stopped: $cause at $what, nothing stored")
            throw LinkEnded(
                when (cause) {
                    StopCause.NETWORK -> TeaLinkOutcome.Offline

                    StopCause.HTTP_ERROR -> TeaLinkOutcome.ShopUnavailable

                    StopCause.CHALLENGE, StopCause.BLOCKED, StopCause.TOO_MANY_REQUESTS, StopCause.RETRY_AFTER ->
                        TeaLinkOutcome.Blocked
                },
            )
        }

        private fun noTeaPage(why: String): Nothing {
            log("$where ${url.encodedPath} $why, nothing stored")
            throw LinkEnded(TeaLinkOutcome.NoTeaPage)
        }

        private fun unreadable(why: String): Nothing {
            log("$where ${url.encodedPath} $why, nothing stored")
            throw LinkEnded(TeaLinkOutcome.Unreadable)
        }
    }
}

/** A link's photo: the [encoded] photo to store, or the [failure] to remember, or neither (not asked for, gone). */
private class Photo(val encoded: CatalogPhoto? = null, val failure: CatalogImageFailureEntity? = null)

/** Ends a link's job early with [outcome]; caught in its `run`. */
private class LinkEnded(val outcome: TeaLinkOutcome) : Exception(outcome.toString())

/** Binds [TeaLinkLoader] (CAT-17). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TeaLinkLoaderModule {
    @Binds
    abstract fun teaLinkLoader(impl: ShopTeaLinkLoader): TeaLinkLoader
}
