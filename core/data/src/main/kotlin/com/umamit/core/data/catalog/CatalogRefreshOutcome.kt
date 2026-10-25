package com.umamit.core.data.catalog

/**
 * How far a catalog refresh is (SCR-22, CAT-07 AC1).
 *
 * @property pagesDone product pages fetched for classification and parsing so far.
 * @property pagesTotal the product pages this refresh fetches; 0 while robots.txt and the sitemap
 *   are read (then the total is not known yet).
 * @property products green teas found so far.
 * @property photos photos downloaded so far (from #66 on).
 * @property guides *Tee-Info* guides read so far (CAT-09, #76); they come after the photos.
 */
data class CatalogRefreshProgress(
    val pagesDone: Int,
    val pagesTotal: Int,
    val products: Int,
    val photos: Int = 0,
    val guides: Int = 0,
) {
    companion object {
        /** Reading robots.txt and the sitemap. */
        val STARTING = CatalogRefreshProgress(pagesDone = 0, pagesTotal = 0, products = 0)
    }
}

/**
 * How a catalog refresh ended (CAT-07). Only [Success] changed the catalog; every other outcome
 * left it exactly as it was (05 §4.5, CAT-07 AC2/AC5).
 */
sealed interface CatalogRefreshOutcome {
    /** The new catalog is in: [teas] green teas, [newTeas] of them not in the previous catalog. */
    data class Success(val teas: Int, val newTeas: Int) : CatalogRefreshOutcome

    /** The user cancelled (*Abbrechen*). */
    data object Cancelled : CatalogRefreshOutcome

    /** A stop or failure; [details] is technical English for *Details kopieren* (no shop content). */
    sealed interface Failure : CatalogRefreshOutcome {
        val details: String
    }

    /** The shop asked the app to stop: a Cloudflare challenge or block page, HTTP 429 or `Retry-After` (08 §2.8). */
    data class Blocked(val cause: BlockCause, override val details: String) : Failure

    /** No connection to the shop (offline, timeouts after the retries, D-076). */
    data class Offline(override val details: String) : Failure

    /** The shop refused or failed (HTTP 401/403/5xx without Cloudflare's block page). */
    data class ShopUnavailable(override val details: String) : Failure

    /** The shop's pages or sitemap changed under the parser (CAT-07 AC5, 08 §2.9): an app update is needed. */
    data class ShopChanged(override val details: String) : Failure

    /** The catalog database could not be written (e.g. the storage is full). */
    data class StorageFailed(override val details: String) : Failure
}

/** Why the shop stopped the refresh (08 §2.8, CLAUDE.md rule 7). */
enum class BlockCause { CHALLENGE, BLOCKED, TOO_MANY_REQUESTS, RETRY_AFTER }
