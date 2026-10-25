package com.umamit.core.data.catalog

import android.database.SQLException
import com.umamit.core.catalog.parser.CatalogScope
import com.umamit.core.catalog.parser.ProductGroup
import com.umamit.core.catalog.parser.ProductPage
import com.umamit.core.catalog.parser.ProductParse
import com.umamit.core.catalog.parser.ProductUrls
import com.umamit.core.catalog.parser.SanityCheck
import com.umamit.core.catalog.parser.Sitemap
import com.umamit.core.catalog.parser.SitemapReader
import com.umamit.core.catalog.parser.TeeKontorParser
import com.umamit.core.common.WallClock
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.database.entity.CatalogSkipEntity
import com.umamit.core.database.entity.CatalogSkipReason
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.database.entity.StagedCatalogTeaEntity
import com.umamit.core.network.NetworkRetry
import com.umamit.core.network.ShopClient
import com.umamit.core.network.ShopFetch
import com.umamit.core.network.ShopSession
import com.umamit.core.network.StopCause
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The catalog refresh pipeline (CAT-07, 06 §5.3, 08 §2.4), independent of how it is hosted: the
 * app runs it while open (#64), the `dataSync` foreground service from #65 on.
 *
 * 1. robots.txt and the sitemap (index → `.xml.gz` url sets) through one [ShopSession] with the
 *    refresh's retries ([NetworkRetry.REFRESH], D-076): every request on the allow-listed
 *    [ShopClient], one connection, ≤ 2 requests/s, robots.txt honoured (CLAUDE.md rule 7).
 * 2. Candidates: one page per base number, sets left out, base numbers on the skip list for
 *    less than 90 days left out; so a first refresh classifies every parent (D-076).
 * 3. Each candidate's page is parsed ([TeeKontorParser]); green teas go into the staging
 *    tables, other products onto the new skip entries.
 * 4. The sanity check (08 §2.9); then one transaction swaps the staged catalog in, adds the
 *    skip entries and logs the run (05 §4.5).
 *
 * Any stop (challenge, block, 429, `Retry-After`, a refusal, offline), a structural failure, a
 * storage error or a cancellation leaves the catalog **and the skip list** as they were and
 * empties the staging tables; a failed run's classifications are not kept, so a broken parser
 * can never hide teas (D-077). Each run is logged in `catalog_refresh_log` and its steps in the
 * [DiagnosticsLog] (06 §5.6).
 *
 * Cancelling the calling coroutine cancels the refresh: the request in flight is cancelled, no
 * further request is sent, the run is logged as cancelled and the [CancellationException] is
 * rethrown.
 */
class CatalogRefresher(
    private val client: ShopClient,
    private val dao: CatalogDao,
    private val clock: WallClock,
    private val diagnostics: DiagnosticsLog,
    private val sanity: SanityCheck = SanityCheck(),
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @Inject
    constructor(client: ShopClient, dao: CatalogDao, clock: WallClock, diagnostics: DiagnosticsLog) :
        this(client, dao, clock, diagnostics, SanityCheck(), Dispatchers.Default)

    /**
     * Runs one refresh, reporting each step to [onProgress] (on the caller's context), and
     * returns how it ended. Only a [CatalogRefreshOutcome.Success] changed the catalog.
     */
    suspend fun refresh(onProgress: (CatalogRefreshProgress) -> Unit = {}): CatalogRefreshOutcome {
        val run = Run(startedAt = clock.nowMs(), onProgress = onProgress)
        log("catalog refresh: started, parser v${TeeKontorParser.PARSER_VERSION}")
        onProgress(CatalogRefreshProgress.STARTING)
        return try {
            dao.clearStaging()
            Pipeline(run).execute()
        } catch (e: RunStopped) {
            failed(run, e.outcome)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                end(run, CatalogRefreshStatus.CANCELLED, "cancelled after ${run.pagesLine()}")
            }
            throw e
        } catch (e: SQLException) {
            failed(run, CatalogRefreshOutcome.StorageFailed(run.details("storage error: ${e.javaClass.simpleName}")))
        } catch (
            @Suppress("TooGenericExceptionCaught") e: RuntimeException,
        ) {
            // A bug here must never take down the process the timer runs in (rule 8, CAT-07 AC8).
            failed(run, CatalogRefreshOutcome.ShopChanged(run.details("unexpected error: ${e.javaClass.simpleName}")))
        }
    }

    private suspend fun failed(run: Run, outcome: CatalogRefreshOutcome.Failure): CatalogRefreshOutcome {
        withContext(NonCancellable) { end(run, CatalogRefreshStatus.FAILED, outcome.summary()) }
        return outcome
    }

    /** Ends a run without a swap: staging emptied, the run logged; a storage error here changes nothing more. */
    private suspend fun end(run: Run, status: CatalogRefreshStatus, summary: String) {
        log("catalog refresh: ${status.name.lowercase()}, $summary")
        try {
            dao.clearStaging()
            dao.log(run.logEntry(status, newCount = 0, discontinuedCount = 0, errorSummary = summary))
        } catch (e: SQLException) {
            log("catalog refresh: log not written (${e.javaClass.simpleName})")
        }
    }

    private fun log(message: String) = diagnostics.append(clock.nowMs(), clock.zone(), message)

    /** One refresh's counts, for the progress, the details and the log; it also names the stops. */
    private inner class Run(val startedAt: Long, private val onProgress: (CatalogRefreshProgress) -> Unit) {
        var pagesTotal = 0
        var pagesDone = 0
        var green = 0
        var lackingRequired = 0
        var unavailable = 0
        var sitemapUrls = 0
        var sets = 0
        var skippedKnown = 0
        val skips = mutableListOf<CatalogSkipEntity>()
        val defective = mutableListOf<String>()

        fun progress() =
            onProgress(CatalogRefreshProgress(pagesDone = pagesDone, pagesTotal = pagesTotal, products = green))

        fun pagesLine() = "$pagesDone of $pagesTotal pages"

        /** Ends the run: the shop said stop at [where] (08 §2.8). */
        fun stop(cause: StopCause, where: String): Nothing {
            val details = details("stopped: $cause at $where")
            throw RunStopped(
                when (cause) {
                    StopCause.CHALLENGE -> CatalogRefreshOutcome.Blocked(BlockCause.CHALLENGE, details)
                    StopCause.BLOCKED -> CatalogRefreshOutcome.Blocked(BlockCause.BLOCKED, details)
                    StopCause.TOO_MANY_REQUESTS -> CatalogRefreshOutcome.Blocked(BlockCause.TOO_MANY_REQUESTS, details)
                    StopCause.RETRY_AFTER -> CatalogRefreshOutcome.Blocked(BlockCause.RETRY_AFTER, details)
                    StopCause.NETWORK -> CatalogRefreshOutcome.Offline(details)
                    StopCause.HTTP_ERROR -> CatalogRefreshOutcome.ShopUnavailable(details)
                },
            )
        }

        /** Ends the run: the shop's pages or sitemap no longer fit the app (CAT-07 AC5). */
        fun shopChanged(reason: String): Nothing =
            throw RunStopped(CatalogRefreshOutcome.ShopChanged(details("shop changed: $reason")))

        /** Technical English for *Details kopieren* (CAT-07 AC5): counts and article numbers, no shop content. */
        fun details(reason: String): String = buildString {
            appendLine("UmamiT catalog refresh: $reason")
            appendLine("client: ${client.userAgent}")
            appendLine("parser: v${TeeKontorParser.PARSER_VERSION}")
            appendLine("started: ${Instant.ofEpochMilli(startedAt).atZone(clock.zone()).toOffsetDateTime()}")
            appendLine("sitemap: $sitemapUrls URLs, $sets sets, $skippedKnown on the skip list")
            appendLine("pages: ${pagesLine()}, green $green, not green ${skips.size}, unavailable $unavailable")
            append("defective: ${defective.size}")
            if (defective.isNotEmpty()) {
                val more = if (defective.size > MAX_LISTED) ", …" else ""
                append(" (${defective.take(MAX_LISTED).joinToString()}$more)")
            }
        }

        fun logEntry(status: CatalogRefreshStatus, newCount: Int, discontinuedCount: Int, errorSummary: String?) =
            CatalogRefreshLogEntity(
                startedAt = startedAt,
                finishedAt = clock.nowMs(),
                status = status,
                newCount = newCount,
                changedCount = 0,
                discontinuedCount = discontinuedCount,
                flaggedCount = 0,
                photosCount = 0,
                parserVersion = TeeKontorParser.PARSER_VERSION,
                errorSummary = errorSummary,
            )
    }

    /** The steps of one refresh; a stop ends it by [Run.stop] or [Run.shopChanged]. */
    private inner class Pipeline(private val run: Run) {
        suspend fun execute(): CatalogRefreshOutcome.Success {
            val session = ShopSession.open(client, NetworkRetry.REFRESH)
            session.stopCause?.let { run.stop(it, "robots.txt") }
            val urls = sitemapUrls(session)
            run.sitemapUrls = urls.size
            val products = ProductUrls.group(urls)
            run.sets = products.sets
            if (products.groups.isEmpty()) run.shopChanged("no product URLs in the sitemap (${urls.size} URLs)")
            val candidates = candidates(session, products.groups)
            log(
                "catalog refresh: sitemap ${urls.size} URLs, ${products.groups.size} products, " +
                    "${products.sets} sets, ${run.skippedKnown} on the skip list, ${candidates.size} to fetch",
            )
            run.pagesTotal = candidates.size
            run.progress()
            for (group in candidates) {
                currentCoroutineContext().ensureActive()
                fetchProduct(session, group)
                run.pagesDone++
                run.progress()
            }
            return finish()
        }

        /** The URLs of `/sitemap.xml`: a url set, or an index of url sets (`.xml.gz`). */
        private suspend fun sitemapUrls(session: ShopSession): List<String> =
            when (val root = readSitemap(session, session.resolve(SITEMAP_PATH))) {
                is Sitemap.UrlSet -> root.urls

                is Sitemap.Index -> root.sitemaps.flatMap { child ->
                    val url = child.toHttpUrlOrNull()?.takeIf { session.isAllowed(it) }
                        ?: run.shopChanged("the sitemap index lists a URL the app can't fetch")
                    when (val sitemap = readSitemap(session, url)) {
                        is Sitemap.UrlSet -> sitemap.urls
                        is Sitemap.Index -> run.shopChanged("${url.encodedPath} is another sitemap index")
                        is Sitemap.Invalid -> run.shopChanged("${url.encodedPath}: ${sitemap.reason}")
                    }
                }

                is Sitemap.Invalid -> run.shopChanged("$SITEMAP_PATH: ${root.reason}")
            }

        private suspend fun readSitemap(session: ShopSession, url: HttpUrl): Sitemap =
            when (val answer = session.fetch(url)) {
                is ShopFetch.Fetched -> withContext(parseDispatcher) { SitemapReader.read(answer.body) }
                is ShopFetch.Stopped -> run.stop(answer.cause, url.encodedPath)
                ShopFetch.Disallowed -> run.shopChanged("${url.encodedPath} disallowed by robots.txt")
                is ShopFetch.Unavailable -> run.shopChanged("${url.encodedPath} answered HTTP ${answer.code}")
            }

        /** Every product's page but those on the fresh skip list and those robots.txt disallows. */
        private suspend fun candidates(session: ShopSession, groups: List<ProductGroup>): List<ProductGroup> {
            val fresh = dao.skippedSince(run.startedAt - SKIP_RECHECK_MS).toSet()
            run.skippedKnown = groups.count { it.base in fresh }
            return groups.filter { it.base !in fresh }.filter { group ->
                val url = group.pageUrl.toHttpUrlOrNull()
                url != null && session.isAllowed(url)
            }
        }

        private suspend fun fetchProduct(session: ShopSession, group: ProductGroup) {
            val url = requireNotNull(group.pageUrl.toHttpUrlOrNull())
            when (val answer = session.fetch(url)) {
                is ShopFetch.Fetched -> when (val parsed = parse(answer.body, group.pageUrl)) {
                    is ProductParse.Parsed -> classify(group, parsed.page)
                    is ProductParse.Failed -> run.defective += group.base
                }

                is ShopFetch.Stopped -> run.stop(answer.cause, url.encodedPath)

                // Never sent (robots.txt) or gone (404, 3xx): only this product is left out.
                ShopFetch.Disallowed, is ShopFetch.Unavailable -> run.unavailable++
            }
        }

        private suspend fun parse(body: ByteArray, url: String): ProductParse =
            withContext(parseDispatcher) { TeeKontorParser.parseProduct(body, url) }

        private suspend fun classify(group: ProductGroup, page: ProductPage) {
            when (page.scope) {
                CatalogScope.GREEN -> {
                    dao.stage(StagedCatalogTeaEntity(page.toEntity(group.base, run.startedAt)))
                    run.green++
                    if (page.lacksRequiredField) run.lackingRequired++
                }

                CatalogScope.NOT_GREEN ->
                    run.skips += CatalogSkipEntity(group.base, CatalogSkipReason.NOT_GREEN, run.startedAt)

                // No breadcrumb: neither kept nor skipped; the sanity check counts it.
                CatalogScope.UNKNOWN -> run.defective += group.base
            }
        }

        /** The sanity check (08 §2.9), then the swap (05 §4.5). */
        private suspend fun finish(): CatalogRefreshOutcome.Success {
            val staged = dao.countStaged()
            val counts = SanityCheck.Counts(
                previousGreen = dao.countCurrent(),
                green = staged,
                defective = run.defective.size,
                lackingRequired = run.lackingRequired,
                lackingBrewing = null, // the brewing grid and prose are parsed from #68 on
            )
            val problems = sanity.problems(counts)
            if (problems.isNotEmpty()) {
                run.shopChanged(
                    "sanity check failed: ${problems.joinToString()} (green $staged, " +
                        "previous ${counts.previousGreen}, defective ${run.defective.size}, " +
                        "lacking name/price/number ${run.lackingRequired})",
                )
            }
            val previous = dao.keys().toSet()
            val stagedKeys = dao.stagedKeys().toSet()
            val newCount = stagedKeys.count { it !in previous }
            val gone = previous.count { it !in stagedKeys }
            dao.swap(run.skips, run.logEntry(CatalogRefreshStatus.SUCCESS, newCount, gone, errorSummary = null))
            log(
                "catalog refresh: success, $staged teas ($newCount new, $gone gone), ${run.skips.size} not green, " +
                    "${run.defective.size} defective, ${run.unavailable} unavailable, " +
                    "${TimeUnit.MILLISECONDS.toSeconds(clock.nowMs() - run.startedAt)} s",
            )
            return CatalogRefreshOutcome.Success(teas = staged, newTeas = newCount)
        }
    }

    private companion object {
        const val SITEMAP_PATH = "/sitemap.xml"

        /** 05 §4.2a: a base number on the skip list is classified again after 90 days. */
        val SKIP_RECHECK_MS = TimeUnit.DAYS.toMillis(90)

        /** Defective article numbers listed in the details. */
        const val MAX_LISTED = 10
    }
}

/** Ends a refresh early with [outcome] (a stop or a structural failure); caught in [CatalogRefresher.refresh]. */
private class RunStopped(val outcome: CatalogRefreshOutcome.Failure) : Exception(outcome.details)

/** A green tea's page as a catalog row first and last seen at [seenAt] (05 §4.1; this slice's basic fields). */
private fun ProductPage.toEntity(base: String, seenAt: Long) = CatalogTeaEntity(
    key = key ?: base,
    name = name,
    secondaryName = secondaryName,
    productUrl = productUrl,
    shopCategory = shopCategory,
    teaType = teaType,
    breadcrumb = categoryPath,
    basePriceCentsPer100g = basePriceCentsPer100g,
    available = available,
    firstSeenAt = seenAt,
    lastSeenAt = seenAt,
)

/** The first line of a failure's details: the log's error summary. */
private fun CatalogRefreshOutcome.Failure.summary(): String =
    details.lineSequence().first().removePrefix("UmamiT catalog refresh: ")
