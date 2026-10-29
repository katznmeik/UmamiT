package com.umamit.core.data.catalog

import android.database.SQLException
import com.umamit.core.catalog.parser.ArticleNumber
import com.umamit.core.catalog.parser.CatalogEntries
import com.umamit.core.catalog.parser.CatalogEntry
import com.umamit.core.catalog.parser.CatalogRecipes
import com.umamit.core.catalog.parser.CatalogScope
import com.umamit.core.catalog.parser.ProductGroup
import com.umamit.core.catalog.parser.ProductPage
import com.umamit.core.catalog.parser.ProductParse
import com.umamit.core.catalog.parser.ProductUrls
import com.umamit.core.catalog.parser.SanityCheck
import com.umamit.core.catalog.parser.ShopPackage
import com.umamit.core.catalog.parser.Sitemap
import com.umamit.core.catalog.parser.SitemapReader
import com.umamit.core.catalog.parser.TeeKontorParser
import com.umamit.core.common.WallClock
import com.umamit.core.data.PhotoTranscoder
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.database.dao.CatalogTeaState
import com.umamit.core.database.entity.CatalogImageFailureReason
import com.umamit.core.database.entity.CatalogPackageEntity
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.database.entity.CatalogSkipEntity
import com.umamit.core.database.entity.CatalogSkipReason
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.database.entity.StagedCatalogPackageEntity
import com.umamit.core.database.entity.StagedCatalogTeaEntity
import com.umamit.core.network.BodyLimit
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
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
 * 2. Candidates: one page per base number, sets and sale duplicates left out (D-091), base
 *    numbers on the skip list for less than 90 days left out; so a first refresh classifies every
 *    parent (D-076), and a later one fetches the known green teas (with the numbers merged into
 *    them, D-081), the base numbers new in the sitemap and the skip entries due again (08 §2.4
 *    item 5).
 * 3. Each candidate's page is parsed ([TeeKontorParser]); green teas are kept, other products
 *    and sets without a set number go onto the new skip entries. Then each known tea none of
 *    whose numbers the sitemap lists (the shop leaves sold-out products out) gets its own product
 *    page fetched once (CAT-16, D-094): still that product's page in scope → kept with the page's
 *    data and stock; 404/410, a redirect away (judged by its `Location`, never followed) or out
 *    of scope → it left the range; not readable this once → kept as it was.
 * 4. The green teas go into the staging tables, one entry per tea with its package sizes
 *    ([CatalogEntries]: split numbers merged, sizes and prices from the page and the sitemap,
 *    no size page fetched; 08 §2.4 items 6–7, D-081), its style and derived recipes
 *    ([CatalogRecipes], CAT-04 – CAT-06) and its [CatalogContentHash]; then the sanity check
 *    (08 §2.9).
 * 5. The green teas' new and changed photos (CAT-08), unless SET-22 *Katalog: Fotos laden* is
 *    off: a photo whose URL is the one the stored photo came from is never fetched again (#70);
 *    each other one through the same session (robots.txt, stops; up to the image body limit,
 *    D-091), re-encoded to `<key>.webp` and `<key>_thumb.webp`
 *    ([PhotoTranscoder.transcodeForCatalog], D-074). A photo that is missing or doesn't decode
 *    leaves only that tea without one (or with the photo it had); one that failed for good is not
 *    asked for again while its URL is the same ([ImageFailures], D-091); a stop ends the run.
 * 6. The *Tee-Info* guides (CAT-09, D-087) through the same session, with their images unless
 *    SET-22 is off, converted and staged ([CatalogGuides]).
 * 7. One transaction swaps the staged catalog and guides in, adds the skip entries and logs the
 *    run with its summary (05 §4.5, CAT-07 AC3). A known tea still in the sitemap whose page couldn't be
 *    read stays as it was, and so does one missing from the sitemap whose page couldn't be read
 *    this once; a tea that left the range stays hidden as „Nicht mehr im Sortiment"
 *    while a collection tea links to it ([OwnedCatalogKeys], read before the swap: the two
 *    databases share no transaction, D-076) and is deleted otherwise (CAT-07 AC4). Then the
 *    photos of teas and the images of guides no longer in the catalog are deleted and the
 *    seed teas are linked ([CatalogSeedLinker], CAT-15; each part once, D-090).
 *
 * Pages, photos and guides are fetched one after another while the previous answer is processed
 * ([fetchOverlapped], D-091): the wire sees the same requests at the same rate, and the
 * processing no longer adds to the request gaps.
 *
 * Any stop (challenge, block, 429, `Retry-After`, a refusal, offline), a structural failure, a
 * storage error or a cancellation leaves the catalog, its guides **and the skip list** as they were (photos
 * of teas it doesn't hold are deleted again) and empties the staging tables; a failed run's
 * classifications are not kept, so a broken parser can never hide teas (D-077). Each run is
 * logged in `catalog_refresh_log` and its steps in the [DiagnosticsLog] (06 §5.6).
 *
 * Cancelling the calling coroutine cancels the refresh: the request in flight (it may be the
 * next page's, fetched while this one is processed) is cancelled, no further request is sent,
 * the run is logged as cancelled and the [CancellationException] is rethrown. Once the swap
 * begins, a cancel changes nothing: the run returns its success (D-090).
 */
class CatalogRefresher(
    private val client: ShopClient,
    private val dao: CatalogDao,
    private val clock: WallClock,
    private val diagnostics: DiagnosticsLog,
    private val settings: SettingsRepository,
    private val photos: CatalogPhotoFiles,
    private val sanity: SanityCheck = SanityCheck(),
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val fileDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val owned: OwnedCatalogKeys = OwnedCatalogKeys { flowOf(emptySet()) },
    private val seedTeas: CatalogSeedLinker = CatalogSeedLinker { emptyList() },
    guideLimits: GuideLimits = GuideLimits.DEFAULT,
) {
    @Inject
    constructor(
        client: ShopClient,
        dao: CatalogDao,
        clock: WallClock,
        diagnostics: DiagnosticsLog,
        settings: SettingsRepository,
        photos: CatalogPhotoFiles,
        owned: OwnedCatalogKeys,
        seedTeas: CatalogSeedLinker,
    ) : this(
        client,
        dao,
        clock,
        diagnostics,
        settings,
        photos,
        SanityCheck(),
        Dispatchers.Default,
        Dispatchers.IO,
        owned,
        seedTeas,
    )

    private val guides =
        CatalogGuides(guideLimits, client, dao, photos.guideImages, clock, parseDispatcher, fileDispatcher, ::log)

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
            // Cancelled once the swap was in: the run is the success it is (D-086, D-090).
            run.success?.let { return it }
            withContext(NonCancellable) {
                end(run, CatalogRefreshStatus.CANCELLED, "cancelled after ${run.pagesLine()}")
            }
            throw e
        } catch (e: SQLException) {
            failed(run, CatalogRefreshOutcome.StorageFailed(run.details("storage error: ${e.javaClass.simpleName}")))
        } catch (e: IOException) {
            // Only the photo files are written here; the shop's answers come as ShopFetch, never thrown.
            failed(
                run,
                CatalogRefreshOutcome.StorageFailed(run.details("photo storage error: ${e.javaClass.simpleName}")),
            )
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Throwable,
        ) {
            // A bug (a stack overflow in a page, …) must never take down the process the timer runs in
            // (rule 8, CAT-07 AC8), nor skip the cleanup and the log (D-090).
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
            dao.log(run.logEntry(status, Summary.NONE, errorSummary = summary))
            keepPhotosOfCatalog()
        } catch (e: SQLException) {
            log("catalog refresh: log not written (${e.javaClass.simpleName})")
        }
    }

    private fun log(message: String) = diagnostics.append(clock.nowMs(), clock.zone(), message)

    /**
     * Deletes the photo files of every tea the catalog doesn't hold (CAT-07 AC4, CAT-08). Never
     * fails the run: unreadable keys leave the files for the next refresh.
     */
    private suspend fun keepPhotosOfCatalog() {
        val keys = try {
            dao.keys().toSet()
        } catch (e: SQLException) {
            log("catalog refresh: photos not tidied (${e.javaClass.simpleName})")
            return
        }
        withContext(fileDispatcher) { photos.retainOnly(keys) }
        try {
            guides.keepImagesOfGuides()
        } catch (e: SQLException) {
            log("catalog refresh: guide images not tidied (${e.javaClass.simpleName})")
        }
    }

    /** One refresh's counts, for the progress, the details and the log; it also names the stops. */
    private inner class Run(val startedAt: Long, private val onProgress: (CatalogRefreshProgress) -> Unit) {
        var pagesTotal = 0
        var pagesDone = 0
        var green = 0
        var lackingRequired = 0
        var lackingBrewing = 0
        var unavailable = 0
        var sitemapUrls = 0
        var sets = 0
        var sales = 0
        var skippedKnown = 0

        /**
         * The last successful refresh ran a parser before Kräutertees: this one classifies the skip
         * list's not-green entries again and, on success, replaces them (05 §9, D-093 Q12).
         */
        var reclassifyNotGreen = false
        var photosStored = 0
        var photosMissing = 0

        /** Photos without one because they failed for good before (D-091): not asked for again. */
        var photosFailedBefore = 0

        /** The images that failed for good, read with the previous catalog (D-091). */
        var imageFailures = ImageFailures(emptyList(), startedAt)

        /** SET-22 *Katalog: Fotos laden* as this run read it. */
        var downloadPhotos = true

        /** The outcome once the swap and the steps after it are done; a cancel then changes nothing. */
        var success: CatalogRefreshOutcome.Success? = null
        val guides = GuideCounts()

        /** Each base number of the staged catalog (merged split numbers too) to its tea's key (guide links). */
        val catalogKeys = mutableMapOf<String, String>()
        var photosKept = 0
        var known = 0
        var new = 0
        var recheck = 0

        /**
         * Known teas none of whose numbers the sitemap lists, each checked once (CAT-16, D-094),
         * those „Nicht mehr im Sortiment" included.
         */
        var unlisted = 0

        /** Of them: kept in the range with their page, and gone (404/410, a redirect away, out of scope). */
        var unlistedKept = 0
        var unlistedGone = 0

        /** Of them, the teas whose page couldn't be read this once: kept as they were (CAT-16 AC3). */
        val unlistedUnread = mutableSetOf<String>()

        /** The catalog before this refresh, by key (#70). */
        var previous: Map<String, CatalogTeaState> = emptyMap()

        /** Each tea's base numbers in the catalog before this refresh: its key and those merged into it (D-081). */
        var previousBases: Map<String, Set<String>> = emptyMap()

        /** The base numbers the sitemap lists, and those on the skip list or onto it in this run. */
        var listedBases: Set<String> = emptySet()
        val skippedBases = mutableSetOf<String>()

        /** The staged teas' keys and the numbers merged into them. */
        val stagedBases = mutableSetOf<String>()

        /** Each staged tea's hash and flag, for the summary. */
        val staged = mutableMapOf<String, StagedTea>()

        /** The teas whose photo this run downloaded. */
        val photosWritten = mutableSetOf<String>()

        /** Each green tea's key and photo URL, in page order. */
        val photoUrls = mutableListOf<Pair<String, String>>()

        /** Each green tea's page and packages, in page order, until split numbers are merged and staged. */
        val entries = mutableListOf<CatalogEntry>()
        val skips = mutableListOf<CatalogSkipEntity>()
        val defective = mutableListOf<String>()

        fun progress() = onProgress(
            CatalogRefreshProgress(
                pagesDone = pagesDone,
                pagesTotal = pagesTotal,
                products = green,
                photos = photosStored,
                guides = guides.read,
            ),
        )

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
            appendLine("sitemap: $sitemapUrls URLs, $sets sets, $sales sale duplicates, $skippedKnown on the skip list")
            appendLine("candidates: $known known green, $new new, $recheck to check again")
            appendLine(
                "missing from the sitemap: $unlisted checked once, $unlistedKept kept, $unlistedGone gone, " +
                    "${unlistedUnread.size} unread",
            )
            appendLine("pages: ${pagesLine()}, green $green, not green ${skips.size}, unavailable $unavailable")
            appendLine(
                "photos: $photosStored stored, $photosKept unchanged, $photosMissing without " +
                    "($photosFailedBefore failed before, not asked again)",
            )
            appendLine(guides.line())
            append("defective: ${defective.size}")
            if (defective.isNotEmpty()) {
                val more = if (defective.size > MAX_LISTED) ", …" else ""
                append(" (${defective.take(MAX_LISTED).joinToString()}$more)")
            }
        }

        fun logEntry(status: CatalogRefreshStatus, summary: Summary, errorSummary: String?) = CatalogRefreshLogEntity(
            startedAt = startedAt,
            finishedAt = clock.nowMs(),
            status = status,
            newCount = summary.newTeas,
            changedCount = summary.changedTeas,
            discontinuedCount = summary.discontinuedTeas,
            // Teas to check since this refresh (CAT-07 AC3, D-086).
            flaggedCount = summary.recipesToCheck,
            photosCount = photosStored,
            parserVersion = TeeKontorParser.PARSER_VERSION,
            errorSummary = errorSummary,
        )
    }

    /** The steps of one refresh; a stop ends it by [Run.stop] or [Run.shopChanged]. */
    @Suppress("TooManyFunctions") // one function per step of 06 §5.3 / 08 §2.4
    private inner class Pipeline(private val run: Run) {
        suspend fun execute(): CatalogRefreshOutcome.Success {
            val session = ShopSession.open(client, NetworkRetry.REFRESH)
            session.stopCause?.let { run.stop(it, "robots.txt") }
            val urls = sitemapUrls(session)
            run.sitemapUrls = urls.size
            val products = ProductUrls.group(urls)
            run.sets = products.sets
            run.sales = products.sales
            if (products.groups.isEmpty()) run.shopChanged("no product URLs in the sitemap (${urls.size} URLs)")
            readPrevious(products.groups)
            val candidates = candidates(session, products.groups)
            val unlisted = unlistedTeas(session)
            log(
                "catalog refresh: sitemap ${urls.size} URLs, ${products.groups.size} products, " +
                    "${products.sets} sets, ${products.sales} sale duplicates, ${run.skippedKnown} on the skip list, " +
                    "${candidates.size} to fetch " +
                    "(${run.known} known green, ${run.new} new, ${run.recheck} to check again), " +
                    "${run.unlisted} known missing from the sitemap (${unlisted.size} to fetch once)",
            )
            run.pagesTotal = candidates.size + unlisted.size
            run.progress()
            fetchProducts(session, candidates)
            recheckUnlisted(session, unlisted)
            stageEntries()
            checkSanity()
            storePhotos(session)
            storeGuides(session, urls)
            return swap()
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
                is ShopFetch.TooLarge -> run.shopChanged(answer.line(url))
            }

        /**
         * The catalog before this refresh (#70): each tea's state and base numbers, the numbers
         * merged into it read from its packages (each page's own size keeps its number, D-081).
         */
        private suspend fun readPrevious(groups: List<ProductGroup>) {
            run.previous = dao.teaStates().associateBy { it.key }
            val merged = dao.packageNumbers()
                .groupBy({ it.catalogKey }) { ArticleNumber.parse(it.articleNumber)?.base }
            run.previousBases = run.previous.keys.associateWith { key ->
                setOf(key) + merged[key].orEmpty().filterNotNull()
            }
            run.listedBases = groups.map { it.base }.toSet()
            run.imageFailures = ImageFailures(dao.imageFailures(), run.startedAt)
        }

        /**
         * Every product's page but those on the fresh skip list and those robots.txt disallows
         * (08 §2.4): on a later refresh the known green teas, the new base numbers and the skip
         * entries due again.
         */
        private suspend fun candidates(session: ShopSession, groups: List<ProductGroup>): List<ProductGroup> {
            val skipList = dao.skipList()
            run.reclassifyNotGreen =
                (dao.lastSuccessParserVersion() ?: TeeKontorParser.PARSER_VERSION) <
                TeeKontorParser.HERBAL_SINCE_VERSION
            val notGreen = skipList.filter { it.reason == CatalogSkipReason.NOT_GREEN }.map { it.baseKey }.toSet()
            val fresh = dao.skippedSince(run.startedAt - SKIP_RECHECK_MS).toSet() -
                (if (run.reclassifyNotGreen) notGreen else emptySet())
            val due = skipList.map { it.baseKey }.toSet() - fresh
            if (run.reclassifyNotGreen) {
                log(
                    "catalog refresh: ${notGreen.size} not-green products classified again once",
                )
            }
            run.skippedBases += fresh
            run.skippedKnown = groups.count { it.base in fresh }
            val knownGreen = run.previousBases.values.flatten().toSet()
            return groups.filter { it.base !in fresh }.filter { group ->
                val url = group.pageUrl.toHttpUrlOrNull()
                url != null && session.isAllowed(url)
            }.onEach { group ->
                when (group.base) {
                    in knownGreen -> run.known++
                    in due -> run.recheck++
                    else -> run.new++
                }
            }
        }

        /** Each candidate's page, the next one fetched while this one is parsed and classified (D-091). */
        private suspend fun fetchProducts(session: ShopSession, candidates: List<ProductGroup>) = fetchOverlapped(
            items = candidates,
            fetch = { group -> session.fetch(group.url()) },
            isLast = { it is ShopFetch.Stopped },
        ) { group, answer ->
            productAnswer(group, answer)
            run.pagesDone++
            run.progress()
        }

        private fun ProductGroup.url() = requireNotNull(pageUrl.toHttpUrlOrNull())

        private suspend fun productAnswer(group: ProductGroup, answer: ShopFetch) {
            val url = group.url()
            when (answer) {
                is ShopFetch.Fetched -> when (val parsed = parse(answer.body, group.pageUrl)) {
                    is ProductParse.Parsed -> classify(group, parsed.page)
                    is ProductParse.Failed -> run.defective += group.base
                }

                is ShopFetch.Stopped -> run.stop(answer.cause, url.encodedPath)

                // Never sent (robots.txt) or gone (404, 3xx): only this product is left out.
                ShopFetch.Disallowed, is ShopFetch.Unavailable -> run.unavailable++

                // Over the size limit: only this product is left out, no stop (D-090).
                is ShopFetch.TooLarge -> {
                    log("catalog refresh: ${answer.line(url)}")
                    run.unavailable++
                }
            }
        }

        private suspend fun parse(body: ByteArray, url: String): ProductParse =
            withContext(parseDispatcher) { TeeKontorParser.parseProduct(body, url) }

        private suspend fun classify(group: ProductGroup, page: ProductPage) {
            when (page.scope) {
                // Kräutertees are kept next to the green teas, told apart by their style (D-093).
                CatalogScope.GREEN, CatalogScope.HERBAL -> {
                    run.entries += CatalogEntry.of(group, page)
                    run.green++
                    if (page.lacksRequiredField) run.lackingRequired++
                    if (page.brewTable.isEmpty() && page.brewText == null) run.lackingBrewing++
                }

                CatalogScope.NOT_GREEN -> skip(group, CatalogSkipReason.NOT_GREEN)

                // A set without a set number, by its name or breadcrumb (D-091).
                CatalogScope.SET -> skip(group, CatalogSkipReason.SET)

                // No breadcrumb: neither kept nor skipped; the sanity check counts it.
                CatalogScope.UNKNOWN -> run.defective += group.base
            }
        }

        private fun skip(group: ProductGroup, reason: CatalogSkipReason) {
            run.skips += CatalogSkipEntity(group.base, reason, run.startedAt)
            run.skippedBases += group.base
        }

        /**
         * The known teas none of whose numbers the sitemap lists (CAT-16, 08 §2.4 item 5, D-094):
         * the shop leaves sold-out products out of it. Those „Nicht mehr im Sortiment" too (kept
         * for a collection tea): v0.2.0 counted a tea that had only sold out as discontinued, so a
         * page still in scope brings it back into the range; any other answer leaves it as it was.
         * Each one's product page is fetched once, after the sitemap's pages; one that robots.txt
         * disallows or that is off the shop's host is kept as it was without a request (CAT-16 AC3).
         */
        private suspend fun unlistedTeas(session: ShopSession): List<Unlisted> {
            val unlisted = run.previous.values.filter { before ->
                run.previousBases[before.key].orEmpty().none { it in run.listedBases }
            }.sortedBy { it.key }
            run.unlisted = unlisted.size
            return unlisted.mapNotNull { before ->
                val url = before.productUrl.toHttpUrlOrNull()?.takeIf { session.isAllowed(it) }
                if (url == null) {
                    unread(before.key, "not fetched (robots.txt or off the shop)")
                    null
                } else {
                    Unlisted(before.key, run.previousBases[before.key].orEmpty(), url)
                }
            }
        }

        /** Each unlisted tea's page once, the next one fetched while this one is parsed (D-091). */
        private suspend fun recheckUnlisted(session: ShopSession, unlisted: List<Unlisted>) = fetchOverlapped(
            items = unlisted,
            fetch = { tea -> session.fetch(tea.url) },
            isLast = { it is ShopFetch.Stopped },
        ) { tea, answer ->
            unlistedAnswer(tea, answer)
            run.pagesDone++
            run.progress()
        }

        /**
         * CAT-16: a page of that product still in scope keeps the tea with the page's data and
         * stock; 404/410 or a page out of scope leaves it gone (CAT-07 AC4); a page that can't be
         * read this once without a stop keeps it as it was; a stop ends the run (08 §2.8).
         */
        private suspend fun unlistedAnswer(tea: Unlisted, answer: ShopFetch) {
            when (answer) {
                is ShopFetch.Fetched -> when (val parsed = parse(answer.body, tea.url.toString())) {
                    is ProductParse.Parsed -> unlistedPage(tea, parsed.page)

                    is ProductParse.Failed -> {
                        run.defective += tea.key
                        unread(tea.key, "${tea.url.encodedPath} shows no product")
                    }
                }

                is ShopFetch.Stopped -> run.stop(answer.cause, tea.url.encodedPath)

                ShopFetch.Disallowed -> unread(tea.key, "${tea.url.encodedPath} disallowed by robots.txt")

                is ShopFetch.TooLarge -> unread(tea.key, answer.line(tea.url))

                is ShopFetch.Unavailable -> unlistedUnavailable(tea, answer)
            }
        }

        /**
         * 404/410, or a redirect whose `Location` leads away from the product (never followed):
         * gone (CAT-16 AC2). A redirect to one of the product's own pages (a new slug, a size) or
         * without a `Location`, and any other 4xx: not read this once, kept as it was (AC3).
         */
        private fun unlistedUnavailable(tea: Unlisted, answer: ShopFetch.Unavailable) {
            val status = "${tea.url.encodedPath} answered HTTP ${answer.code}"
            val location = answer.location
            when {
                answer.code in GONE -> gone(tea.key, status)

                location != null && !location.namesProductOf(tea) -> gone(
                    tea.key,
                    "$status to " + if (client.isAllowed(location)) location.encodedPath else "another host",
                )

                location != null -> unread(tea.key, "$status to its own ${location.encodedPath}, not followed")

                else -> unread(tea.key, status)
            }
        }

        /** A fetched page of an unlisted tea: classified as any page while it still shows that product. */
        private suspend fun unlistedPage(tea: Unlisted, page: ProductPage) {
            if (!page.showsProductOf(tea)) {
                unread(tea.key, "${tea.url.encodedPath} shows another product (${page.key})")
                return
            }
            classify(tea.group, page)
            when (page.scope) {
                CatalogScope.GREEN, CatalogScope.HERBAL -> run.unlistedKept++
                CatalogScope.NOT_GREEN, CatalogScope.SET -> gone(tea.key, "${tea.url.encodedPath} out of scope")
                CatalogScope.UNKNOWN -> unread(tea.key, "${tea.url.encodedPath} has no breadcrumb")
            }
        }

        /** [this] is a product page of [tea] on the shop: `/<slug>/<number>` with one of its numbers. */
        private fun HttpUrl.namesProductOf(tea: Unlisted): Boolean =
            client.isAllowed(this) && ProductUrls.articleNumberOf(toString())?.base in tea.bases

        /** The page is [tea]'s: its canonical (else the fetched URL) names it, and so does its own number if read. */
        private fun ProductPage.showsProductOf(tea: Unlisted): Boolean {
            val canonical = productUrl.toHttpUrlOrNull() ?: return false
            return canonical.namesProductOf(tea) && (key ?: tea.key) in tea.bases
        }

        private fun unread(key: String, why: String) {
            run.unlistedUnread += key
            log("catalog refresh: $key missing from the sitemap, kept as it was: $why")
        }

        private fun gone(key: String, why: String) {
            run.unlistedGone++
            log("catalog refresh: $key missing from the sitemap, gone: $why")
        }

        /**
         * Stages one entry per tea with its packages (05 §4.2, 08 §2.4 items 6–7): split numbers
         * merged under the lowest one, each size with its own page (D-081).
         */
        private suspend fun stageEntries() {
            val entries = CatalogEntries.merge(run.entries)
            run.entries.clear()
            for (entry in entries) {
                val packages = entry.packages.map { it.toEntity(entry.key) }
                val page = entry.page.toEntity(entry.key, run.startedAt)
                val tea = page.copy(contentHash = CatalogContentHash.of(page, packages))
                dao.stage(StagedCatalogTeaEntity(tea))
                dao.stagePackages(packages.map { StagedCatalogPackageEntity(it) })
                tea.photoUrl?.let { run.photoUrls += tea.key to it }
                (entry.mergedKeys + entry.key + tea.key).forEach { run.catalogKeys[it] = tea.key }
                run.staged[tea.key] = StagedTea(tea.contentHash, flagged = tea.recipeCheckReason != null)
                run.stagedBases += tea.key
                run.stagedBases += entry.mergedKeys
                if (entry.mergedKeys.isNotEmpty()) {
                    log("catalog refresh: ${entry.mergedKeys.joinToString()} merged into ${entry.key}")
                }
            }
        }

        /** The sanity check (08 §2.9): before the photos, so a broken parser costs no photo request. */
        private suspend fun checkSanity() {
            val staged = dao.countStaged()
            val counts = SanityCheck.Counts(
                previousGreen = dao.countCurrent(),
                green = staged,
                defective = run.defective.size,
                lackingRequired = run.lackingRequired,
                lackingBrewing = run.lackingBrewing,
            )
            val problems = sanity.problems(counts)
            if (problems.isNotEmpty()) {
                run.shopChanged(
                    "sanity check failed: ${problems.joinToString()} (green $staged, " +
                        "previous ${counts.previousGreen}, defective ${run.defective.size}, " +
                        "lacking name/price/number ${run.lackingRequired}, lacking brewing ${run.lackingBrewing})",
                )
            }
        }

        /**
         * The green teas' new and changed photos (CAT-08) unless SET-22 is off; then every staged
         * tea whose photo is on disk gets its file: downloaded now, or kept from an earlier
         * refresh. A photo is unchanged when the stored one came from the page's photo URL (the
         * shop's media URL changes with the image, 05 §4.1); it is never fetched again (#70). A
         * kept photo stays marked with the URL it came from, so a changed photo that couldn't be
         * fetched this time is asked for again next time.
         */
        private suspend fun storePhotos(session: ShopSession) {
            val download = settings.settings.first().catalogDownloadPhotos
            run.downloadPhotos = download
            val toFetch = mutableListOf<Pair<String, String>>()
            for ((key, photoUrl) in run.photoUrls) {
                when {
                    unchangedPhoto(key, photoUrl) -> run.photosKept++

                    // No request either way; a photo that failed for good stays remembered (D-091).
                    !download -> run.imageFailures.remembers(photoUrl)

                    run.imageFailures.remembers(photoUrl) -> {
                        run.photosFailedBefore++
                        run.photosMissing++
                    }

                    else -> toFetch += key to photoUrl
                }
            }
            // The next photo is fetched while this one is re-encoded and written (D-091).
            fetchOverlapped(
                items = toFetch,
                fetch = { (_, photoUrl) -> photoUrl.shopUrl()?.let { session.fetch(it, BodyLimit.IMAGE) } },
                isLast = { it is ShopFetch.Stopped },
            ) { (key, photoUrl), answer ->
                if (storePhoto(key, photoUrl, answer)) {
                    run.photosWritten += key
                    run.photosStored++
                    run.progress()
                } else {
                    run.photosMissing++
                }
            }
            val pageUrls = run.photoUrls.toMap()
            for (key in dao.stagedKeys()) {
                if (withContext(fileDispatcher) { photos.exists(key) }) {
                    val source = if (key in run.photosWritten) pageUrls[key] else run.previous[key]?.photoUrl
                    dao.setStagedPhoto(key, photos.photoName(key), source)
                }
            }
        }

        private suspend fun unchangedPhoto(key: String, photoUrl: String): Boolean {
            val before = run.previous[key] ?: return false
            return before.photoUrl == photoUrl &&
                before.photoFile != null &&
                withContext(fileDispatcher) { photos.exists(key) }
        }

        /** Only the shop's own host: a photo elsewhere is never fetched (rule 7). */
        private fun String.shopUrl() = toHttpUrlOrNull()?.takeIf { client.isAllowed(it) }

        /**
         * Re-encodes and writes one tea's photo from its [answer] (null: not on the shop's host,
         * never fetched); false when there is none to store. A photo too large or unreadable
         * failed for good (D-091).
         */
        private suspend fun storePhoto(key: String, photoUrl: String, answer: ShopFetch?): Boolean = when (answer) {
            null -> false

            is ShopFetch.Fetched -> {
                val photo = withContext(parseDispatcher) { PhotoTranscoder.transcodeForCatalog(answer.body) }
                if (photo != null) {
                    withContext(fileDispatcher) { photos.write(key, photo.full.bytes, photo.thumbnail.bytes) }
                } else {
                    run.imageFailures.failed(photoUrl, CatalogImageFailureReason.UNREADABLE)
                }
                photo != null
            }

            is ShopFetch.Stopped -> run.stop(answer.cause, requireNotNull(photoUrl.shopUrl()).encodedPath)

            // Never sent (robots.txt) or gone: only this tea is left without a new photo.
            ShopFetch.Disallowed, is ShopFetch.Unavailable -> false

            // Over the image limit: only this tea is left without a new photo, no stop (D-090, D-091).
            is ShopFetch.TooLarge -> {
                log("catalog refresh: ${answer.line(requireNotNull(photoUrl.shopUrl()))}")
                run.imageFailures.failed(photoUrl, CatalogImageFailureReason.TOO_LARGE)
                false
            }
        }

        /** The guides (CAT-09, D-087): staged for the swap; read after the photos, each counted in the progress. */
        private suspend fun storeGuides(session: ShopSession, urls: List<String>) {
            guides.stage(
                session = session,
                sitemapUrls = urls,
                catalogKeys = guideCatalogKeys(),
                downloadImages = run.downloadPhotos,
                failures = run.imageFailures,
                counts = run.guides,
                onGuide = run::progress,
                stop = run::stop,
            )
            log("catalog refresh: ${run.guides.line()}")
        }

        /**
         * The teas a guide can link to in the new catalog: each staged tea's numbers, and those of
         * a known tea the swap keeps unread (#70), each to its key.
         */
        private fun guideCatalogKeys(): Map<String, String> {
            val keys = run.catalogKeys.toMutableMap()
            for ((key, before) in run.previous) {
                if (key !in run.staged && stillInRange(key, before)) {
                    (run.previousBases[key].orEmpty() + key).forEach { keys.putIfAbsent(it, key) }
                }
            }
            return keys
        }

        /**
         * The swap (05 §4.5) with the old catalog's teas the refresh didn't stage (#70), then the
         * photos of teas no longer in the catalog are deleted and the seed teas linked (CAT-15).
         * Once the swap is in, the run is a success: nothing after it cancels or fails it.
         */
        private suspend fun swap(): CatalogRefreshOutcome.Success {
            // Read before the swap: umamit.db and catalog.db share no transaction (D-076).
            val ownedKeys = owned.observe().first()
            val unchanged = mutableListOf<String>()
            val discontinued = mutableListOf<String>()
            var gone = 0
            for ((key, before) in run.previous) {
                when {
                    key in run.staged -> Unit

                    stillInRange(key, before) -> unchanged += key

                    else -> {
                        if (!before.discontinued) gone++
                        if (key in ownedKeys) discontinued += key
                    }
                }
            }
            // A tea kept unread fetched no photo: what failed of it stays remembered (D-091).
            unchanged.forEach { key -> run.previous[key]?.photoUrl?.let(run.imageFailures::remembers) }
            val summary = summary(gone)
            val entry = run.logEntry(CatalogRefreshStatus.SUCCESS, summary, errorSummary = null)
            // The teas in the range: the staged ones and the unread ones kept (none of them discontinued).
            val teas = run.staged.size + unchanged.size
            // The last moment a cancel ends the run: the swap and everything after it run to their end (D-090).
            currentCoroutineContext().ensureActive()
            return withContext(NonCancellable) {
                dao.swap(
                    run.skips,
                    entry,
                    unchanged,
                    discontinued,
                    run.imageFailures.stored(),
                    clearNotGreen = run.reclassifyNotGreen,
                )
                keepPhotosOfCatalog()
                log(
                    "catalog refresh: success, $teas teas (${summary.newTeas} new, ${summary.changedTeas} changed, " +
                        "$gone gone, ${discontinued.size} kept as discontinued, ${unchanged.size} kept unread, " +
                        "${summary.recipesToCheck} to check), ${run.skips.size} not green, " +
                        "${run.defective.size} defective, ${run.unavailable} unavailable, " +
                        "${run.photosStored} photos stored, ${run.photosKept} unchanged, " +
                        "${run.photosMissing} without (${run.photosFailedBefore} failed before), " +
                        "${TimeUnit.MILLISECONDS.toSeconds(clock.nowMs() - run.startedAt)} s",
                )
                linkSeedTeas()
                CatalogRefreshOutcome.Success(
                    teas = teas,
                    newTeas = summary.newTeas,
                    changedTeas = summary.changedTeas,
                    discontinuedTeas = summary.discontinuedTeas,
                    recipesToCheck = summary.recipesToCheck,
                ).also { run.success = it }
            }
        }

        /**
         * The summary (CAT-07 AC3, D-086): staged teas not in the old catalog are new; known ones
         * whose shop content changed, or that are back in the range, changed; flagged ones that
         * weren't flagged before (new ones included) are recipes to check; [gone] left the range.
         */
        private fun summary(gone: Int) = Summary(
            newTeas = run.staged.keys.count { it !in run.previous },
            changedTeas = run.staged.count { (key, tea) ->
                val before = run.previous[key]
                before != null && (before.discontinued || CatalogContentHash.changed(before.contentHash, tea.hash))
            },
            discontinuedTeas = gone,
            recipesToCheck = run.staged.count { (key, tea) ->
                tea.flagged && run.previous[key]?.recipeCheckReason == null
            },
        )

        /**
         * A known tea that is still in the shop's range although this refresh staged none of it:
         * its page couldn't be read this time (robots.txt, a 404 while listed, a defective page)
         * while its base numbers are in the sitemap, or, missing from the sitemap, while its own
         * page couldn't be read this once without a stop (CAT-16 AC3); none of its numbers is
         * skipped or staged under another key, and it hadn't left the range. A tea missing from
         * the sitemap whose page is gone or out of scope is discontinued (08 §2.4 item 5).
         */
        private fun stillInRange(key: String, before: CatalogTeaState): Boolean {
            val bases = run.previousBases[key].orEmpty()
            return !before.discontinued &&
                (bases.any { it in run.listedBases } || key in run.unlistedUnread) &&
                bases.none { it in run.skippedBases || it in run.stagedBases }
        }

        /** CAT-15 after the swap; what failed or is still to do is tried again after the next successful refresh. */
        private suspend fun linkSeedTeas() {
            try {
                val linked = seedTeas.linkSeedTeas(withPhotos = run.downloadPhotos)
                if (linked.isNotEmpty()) log("catalog refresh: seed teas linked (${linked.joinToString()})")
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                // The catalog is in; the collection's database or files failing must not undo that.
                log("catalog refresh: seed teas not linked (${e.javaClass.simpleName})")
            }
        }
    }

    private companion object {
        const val SITEMAP_PATH = "/sitemap.xml"

        /** A product page that is gone: a tea missing from the sitemap left the range (CAT-16 AC2). */
        val GONE = setOf(404, 410)

        /** 05 §4.2a: a base number on the skip list is classified again after 90 days. */
        val SKIP_RECHECK_MS = TimeUnit.DAYS.toMillis(90)

        /** Defective article numbers listed in the details. */
        const val MAX_LISTED = 10
    }
}

/** A staged tea's shop content hash and whether its recipe is flagged, for the summary (#70). */
private class StagedTea(val hash: String?, val flagged: Boolean)

/**
 * A known tea missing from the sitemap (CAT-16): its [key], its base numbers ([bases], merged
 * ones included, D-081) and its product page [url], fetched once. Its page is classified and
 * staged like a sitemap product of its key without size URLs, so its sizes are the page's
 * (08 §2.4 item 6).
 */
private class Unlisted(val key: String, val bases: Set<String>, val url: HttpUrl) {
    val group = ProductGroup(base = key, pageUrl = url.toString(), variantUrls = emptyMap())
}

/** The counts of a refresh's summary (CAT-07 AC3), as logged (05 §4.4). */
private data class Summary(val newTeas: Int, val changedTeas: Int, val discontinuedTeas: Int, val recipesToCheck: Int) {
    companion object {
        val NONE = Summary(0, 0, 0, 0)
    }
}

/** Ends a refresh early with [outcome] (a stop or a structural failure); caught in [CatalogRefresher.refresh]. */
private class RunStopped(val outcome: CatalogRefreshOutcome.Failure) : Exception(outcome.details)

/**
 * A green tea's page as a catalog row first and last seen at [seenAt] (05 §4.1), with its style
 * and derived recipes (CAT-04 – CAT-06), without its photo file yet.
 */
private fun ProductPage.toEntity(base: String, seenAt: Long) = toEntity(base, seenAt, CatalogRecipes.derive(this))

private fun ProductPage.toEntity(base: String, seenAt: Long, recipes: CatalogRecipes) = CatalogTeaEntity(
    key = key ?: base,
    name = name,
    secondaryName = secondaryName,
    productUrl = productUrl,
    shopCategory = shopCategory,
    teaType = teaType,
    styleKey = recipes.styleKey,
    derivedRecipesJson = DerivedRecipesColumn.json(recipes.recipes),
    recipeCheckReason = recipes.checkReason,
    originCountry = originCountry,
    originRegion = originRegion,
    cultivar = cultivar,
    harvest = harvest,
    shading = shading,
    steaming = steaming,
    isOrganic = isOrganic,
    isFlavored = isFlavored,
    coldBrewSuitable = coldBrewSuitable,
    aroma = aroma,
    tasteNotes = tasteNotes,
    manufacturer = manufacturer,
    season = season,
    matchaGrade = matchaGrade,
    tasteScalesJson = CatalogColumns.tasteScalesJson(tasteScales),
    breadcrumb = categoryPath,
    ingredients = ingredients,
    descriptionHtml = descriptionHtml,
    descriptionText = descriptionText,
    brewTableRaw = CatalogColumns.brewTableJson(brewTable),
    brewTextRaw = brewText,
    basePriceCentsPer100g = basePriceCentsPer100g,
    available = available,
    photoUrl = photoUrl,
    firstSeenAt = seenAt,
    lastSeenAt = seenAt,
)

/** A package size of the tea [key] as a catalog row (05 §4.2). */
private fun ShopPackage.toEntity(key: String) = CatalogPackageEntity(
    catalogKey = key,
    articleNumber = articleNumber,
    label = CatalogColumns.packageLabel(grams, savingsPercent),
    grams = grams,
    priceCents = priceCents,
    priceIsEstimate = priceIsEstimate,
    savingsPercent = savingsPercent,
    variantUrl = variantUrl,
    available = available,
)

/** The diagnostics line of an answer over the client's size limit at [url] (D-090). */
internal fun ShopFetch.TooLarge.line(url: HttpUrl): String =
    "${url.encodedPath} over the size limit (${declaredBytes?.let { "$it bytes" } ?: "size not declared"}), left out"

/** The first line of a failure's details: the log's error summary. */
private fun CatalogRefreshOutcome.Failure.summary(): String =
    details.lineSequence().first().removePrefix("UmamiT catalog refresh: ")
