package com.umamit.core.data.catalog

import com.umamit.core.database.entity.CatalogImageFailureEntity
import com.umamit.core.database.entity.CatalogImageFailureReason

/**
 * The catalog images that failed for good (05 §4.6, D-091): a product photo or guide image over
 * the image body limit, or no decodable JPEG, PNG or WebP within the transcoder's limits, is not
 * asked for again while its URL stays the same (the shop's media URL carries the image's version).
 * A 404, a robots.txt rule or a network error is no failure for good: the next refresh asks again.
 *
 * Read with the previous catalog; what a run [remembers] or records goes to the swap, which
 * replaces the stored failures: a failed run remembers nothing new, and a URL no run meets again
 * is forgotten.
 */
internal class ImageFailures(stored: List<CatalogImageFailureEntity>, private val now: Long) {
    private val remembered = stored.filter { it.rulesVersion == RULES_VERSION }.associateBy { it.url }
    private val toStore = linkedMapOf<String, CatalogImageFailureEntity>()

    /** Whether [url] failed for good before; it is then remembered for the next refresh as well. */
    fun remembers(url: String): Boolean {
        val failure = remembered[url] ?: return false
        toStore[url] = failure
        return true
    }

    /** [url] failed for good in this run, for [reason]. */
    fun failed(url: String, reason: CatalogImageFailureReason) {
        toStore[url] = CatalogImageFailureEntity(url, reason, RULES_VERSION, now)
    }

    /** The failures the swap stores: those remembered and met again, and this run's. */
    fun stored(): List<CatalogImageFailureEntity> = toStore.values.toList()

    companion object {
        /**
         * Raised whenever what the app can read changes (the transcoder's formats or limits, the
         * image body limit), so every remembered failure is tried once more.
         */
        const val RULES_VERSION = 1
    }
}
