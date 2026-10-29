package com.umamit.core.data.catalog

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The jobs that write `catalog.db` take turns: the catalog refresh (CAT-07) and a tea from a
 * shared or pasted link (CAT-17). A refresh swaps the whole catalog in at its end (05 §4.5), so
 * a tea a link stored meanwhile would be lost with it; and a link's fetch never runs while a
 * refresh does (CAT-17 AC7, „Gerade läuft ein Abruf").
 *
 * - A [refresh] waits for a link job in progress (a few seconds at most) and then holds the turn
 *   until it ends, cleanup included.
 * - A [link] job never waits: while another job holds the turn, it does nothing and sends no
 *   request.
 */
@Singleton
class CatalogJobs @Inject constructor() {
    private val turn = Mutex()

    /** Runs a refresh's [block] in its turn, after a link job in progress. */
    suspend fun <T> refresh(block: suspend () -> T): T = turn.withLock { block() }

    /** Runs a link job's [block] now, or returns null without running it while another job has the turn. */
    suspend fun <T> link(block: suspend () -> T): T? {
        if (!turn.tryLock()) return null
        return try {
            block()
        } finally {
            turn.unlock()
        }
    }
}
