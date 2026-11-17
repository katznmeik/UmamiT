package com.umamit.core.data.catalog

import com.umamit.core.database.entity.CatalogImageFailureReason
import com.umamit.core.network.BodyLimit
import com.umamit.core.network.ShopClient
import com.umamit.core.network.ShopFetch
import com.umamit.core.network.ShopSession
import com.umamit.core.network.StopCause
import okhttp3.HttpUrl

// The refresh's photos (CAT-08) and the guides' images (CAT-09) are fetched and stored the same way (D-091).

/**
 * Fetches the image of each of [items] at its [url] with [fetchOverlapped]: one request at a time through [session]
 * with [BodyLimit.IMAGE], nothing more after a stop. An image off the shop's host is never fetched: [process] gets
 * null for it (rule 7).
 */
internal suspend fun <T> fetchImagesOverlapped(
    session: ShopSession,
    client: ShopClient,
    items: List<T>,
    url: (T) -> String,
    process: suspend (T, ShopFetch?) -> Unit,
) = fetchOverlapped(
    items = items,
    fetch = { item -> client.allowedUrl(url(item))?.let { session.fetch(it, BodyLimit.IMAGE) } },
    isLast = { it is ShopFetch.Stopped },
    process = process,
)

/**
 * Stores one image from this answer (null: not on the shop's host, never fetched) at [url]; false when there is none
 * to store. [store] re-encodes and writes it, false when it can't be decoded. An image too large or unreadable
 * [failed] for good (D-091); a stop ends the run through [stop].
 */
internal suspend fun ShopFetch?.storeImageAnswer(
    url: () -> HttpUrl,
    store: suspend (ByteArray) -> Boolean,
    failed: (CatalogImageFailureReason) -> Unit,
    stop: (StopCause, String) -> Nothing,
    log: (String) -> Unit,
): Boolean = when (this) {
    null -> false

    is ShopFetch.Fetched -> store(body).also { stored ->
        if (!stored) failed(CatalogImageFailureReason.UNREADABLE)
    }

    is ShopFetch.Stopped -> stop(cause, url().encodedPath)

    // Never sent (robots.txt) or gone: only this image is left out.
    ShopFetch.Disallowed, is ShopFetch.Unavailable -> false

    // Over the image limit: only this image is left out, no stop (D-090, D-091).
    is ShopFetch.TooLarge -> {
        log("catalog refresh: ${line(url())}")
        failed(CatalogImageFailureReason.TOO_LARGE)
        false
    }
}
