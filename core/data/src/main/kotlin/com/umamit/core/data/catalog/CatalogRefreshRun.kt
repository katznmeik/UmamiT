package com.umamit.core.data.catalog

import com.umamit.core.catalog.parser.CatalogEntry
import com.umamit.core.catalog.parser.TeeKontorParser
import com.umamit.core.common.WallClock
import com.umamit.core.database.dao.CatalogTeaState
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.database.entity.CatalogSkipEntity
import com.umamit.core.network.StopCause
import java.time.Instant

/** One refresh's counts, for the progress, the details and the log; it also names the stops. */
internal class CatalogRefreshRun(
    val startedAt: Long,
    private val onProgress: (CatalogRefreshProgress) -> Unit,
    private val clock: WallClock,
    private val userAgent: String,
) {
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
     * The last successful refresh ran a parser before the current scope (Kräutertees v7, black
     * teas v8): this one classifies the skip list's not-green entries again and, on success,
     * replaces them (05 §9, D-093 Q12, D-108).
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
        appendLine("$DETAILS_PREFIX$reason")
        appendLine("client: $userAgent")
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

    private companion object {
        /** Defective article numbers listed in the details. */
        const val MAX_LISTED = 10
    }
}

/** A staged tea's shop content hash and whether its recipe is flagged, for the summary (#70). */
internal class StagedTea(val hash: String?, val flagged: Boolean)

/** The counts of a refresh's summary (CAT-07 AC3), as logged (05 §4.4). */
internal data class Summary(
    val newTeas: Int,
    val changedTeas: Int,
    val discontinuedTeas: Int,
    val recipesToCheck: Int,
) {
    companion object {
        val NONE = Summary(0, 0, 0, 0)
    }
}

/** Ends a refresh early with [outcome] (a stop or a structural failure); caught in [CatalogRefresher.refresh]. */
internal class RunStopped(val outcome: CatalogRefreshOutcome.Failure) : Exception(outcome.details)
