package com.umamit.core.network

import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * At most 2 requests per second (CLAUDE.md rule 7, 08 §2.4): each request goes out at least
 * [minIntervalNanos] after the previous one went out. All requests of the app share one
 * limiter, because they share one [ShopClient].
 *
 * Runs in a network interceptor ([rateLimit]) on the client's single dispatcher thread, so
 * blocking there delays only the shop's queue.
 */
internal class RequestRateLimiter(
    private val minIntervalNanos: Long = TimeUnit.MILLISECONDS.toNanos(MIN_INTERVAL_MS),
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleepNanos: (Long) -> Unit = ::sleepInterruptibly,
) {
    private var lastStart: Long? = null

    /** Blocks until the next request may go out and records it. */
    @Synchronized
    fun acquire() {
        lastStart?.let { last ->
            val wait = last + minIntervalNanos - nanoTime()
            if (wait > 0) sleepNanos(wait)
        }
        lastStart = nanoTime()
    }

    private companion object {
        /** 1000 ms / 2 requests. */
        const val MIN_INTERVAL_MS = 500L
    }
}

/** An interrupt becomes an [InterruptedIOException], which OkHttp reports as a failed call. */
private fun sleepInterruptibly(nanos: Long) {
    try {
        TimeUnit.NANOSECONDS.sleep(nanos)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw InterruptedIOException("interrupted while rate limited").apply { initCause(e) }
    }
}
