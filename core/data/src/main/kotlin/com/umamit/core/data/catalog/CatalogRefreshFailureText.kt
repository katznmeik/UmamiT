package com.umamit.core.data.catalog

import com.umamit.core.network.StopCause

/** The first line of a failure's details: the log's error summary. */
internal fun CatalogRefreshOutcome.Failure.summary(): String =
    details.lineSequence().first().removePrefix(DETAILS_PREFIX)

internal const val DETAILS_PREFIX = "UmamiT catalog refresh: "

/**
 * The failure a logged error summary ([summary]) was written for: its kind from the line's own
 * start, as [CatalogRefresher] writes it („stopped: <cause> at …", „storage error: …", „photo
 * storage error: …"; anything else ended as shop changed), with the line as its details.
 */
internal fun failureOf(summary: String): CatalogRefreshOutcome.Failure {
    val stopped = summary.removePrefix("stopped: ").takeIf { it != summary }?.substringBefore(' ')
    val block = BlockCause.entries.firstOrNull { it.name == stopped }
    return when {
        stopped == StopCause.NETWORK.name -> CatalogRefreshOutcome.Offline(summary)

        stopped == StopCause.HTTP_ERROR.name -> CatalogRefreshOutcome.ShopUnavailable(summary)

        block != null -> CatalogRefreshOutcome.Blocked(block, summary)

        summary.startsWith("storage error") || summary.startsWith("photo storage error") ->
            CatalogRefreshOutcome.StorageFailed(summary)

        else -> CatalogRefreshOutcome.ShopChanged(summary)
    }
}
