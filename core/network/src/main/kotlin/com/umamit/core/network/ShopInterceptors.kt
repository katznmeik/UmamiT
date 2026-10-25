package com.umamit.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * The response header that carries the shop's `Retry-After` past OkHttp. OkHttp itself repeats
 * a 503 with `Retry-After: 0` (RetryAndFollowUpInterceptor), which would bypass the stop rule
 * and the rate limit (rule 7); [retryAfterShield] renames the header so OkHttp never sees it.
 */
internal const val STOP_RETRY_AFTER_HEADER = "X-UmamiT-Retry-After"

/** Rejects every request whose URL is not on the allow-list ([HostNotAllowedException]). */
internal fun hostAllowList(isAllowed: (HttpUrl) -> Boolean) = Interceptor { chain ->
    val url = chain.request().url
    if (!isAllowed(url)) throw HostNotAllowedException(url)
    chain.proceed(chain.request())
}

/** Counts the network attempts of one call; tagged on by [tagAttempts]. */
internal class AttemptCounter {
    val attempts = AtomicInteger()
}

/** Application interceptor: gives each call its own [AttemptCounter]. */
internal fun tagAttempts() = Interceptor { chain ->
    chain.proceed(chain.request().newBuilder().tag(AttemptCounter::class.java, AttemptCounter()).build())
}

/**
 * Network interceptor: one call is exactly one request. A second network attempt of the same
 * call (any OkHttp follow-up: a retry, an auth or a 421 repeat) fails before it is written.
 */
internal fun singleAttempt() = Interceptor { chain ->
    val counter = chain.request().tag(AttemptCounter::class.java)
    if (counter != null && counter.attempts.incrementAndGet() > 1) {
        throw SecondAttemptRefusedException()
    }
    chain.proceed(chain.request())
}

/** A second network attempt of one call, refused before it was written; never worth a retry. */
internal class SecondAttemptRefusedException : IOException("second network attempt refused: one call is one request")

/** Network interceptor: hides `Retry-After` from OkHttp's follow-up logic (see [STOP_RETRY_AFTER_HEADER]). */
internal fun retryAfterShield() = Interceptor { chain ->
    val response = chain.proceed(chain.request())
    val retryAfter = response.header("Retry-After")
    if (retryAfter == null) {
        response
    } else {
        response.newBuilder().removeHeader("Retry-After").header(STOP_RETRY_AFTER_HEADER, retryAfter).build()
    }
}

/**
 * Network interceptor: the rate limit, stamped when the request goes out on the (already
 * connected) connection, so connection setup never shortens the gap on the wire. A call
 * cancelled while it waits is not sent.
 */
internal fun rateLimit(limiter: RequestRateLimiter) = Interceptor { chain ->
    limiter.acquire()
    if (chain.call().isCanceled()) throw IOException("Canceled")
    chain.proceed(chain.request())
}
