package com.umamit.core.network

import okhttp3.Interceptor
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * At most 2 requests per second (CLAUDE.md rule 7, 08 §2.4): each request starts at least
 * [minIntervalNanos] after the previous one started. All requests of the app share one
 * limiter, because they share one [ShopClient].
 *
 * Runs inside an OkHttp interceptor on the client's single dispatcher thread, so blocking
 * there delays only the shop's queue.
 */
internal class RequestRateLimiter(
    private val minIntervalNanos: Long = TimeUnit.MILLISECONDS.toNanos(MIN_INTERVAL_MS),
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleepNanos: (Long) -> Unit = { TimeUnit.NANOSECONDS.sleep(it) },
) {
    private var lastStart: Long? = null

    /** Blocks until the next request may start and records its start. */
    @Synchronized
    fun acquire() {
        lastStart?.let { last ->
            val wait = last + minIntervalNanos - nanoTime()
            if (wait > 0) sleepNanos(wait)
        }
        lastStart = nanoTime()
    }

    /** [acquire] for [chain]'s request; a call cancelled while waiting is not sent. */
    fun acquire(chain: Interceptor.Chain): Request {
        acquire()
        if (chain.call().isCanceled()) throw IOException("Canceled")
        return chain.request()
    }

    private companion object {
        /** 1000 ms / 2 requests. */
        const val MIN_INTERVAL_MS = 500L
    }
}
