package com.umamit.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

/**
 * One polite job against the shop (CLAUDE.md rule 7, 08 §2.8, D-050, D-074): the seed photo
 * download (ONB-06), one catalog refresh (CAT-07) or one exact price fetch (CAT-12). Every
 * request goes through the allow-listed [ShopClient] (one connection, ≤ 2 requests/s, honest
 * User-Agent); the session adds what a whole job must honour:
 *
 * - **robots.txt once** ([open]), with RFC 9309's status rules (§2.3.1): a 2xx is parsed; a
 *   404/410 means there is none, so everything may be fetched; a stop answer (below) stops the
 *   session before anything else is sent; any other answer (3xx, other 4xx) disallows everything.
 * - **A disallowed URL is never fetched** ([ShopFetch.Disallowed]).
 * - **Stops** on a Cloudflare challenge or block page, HTTP 429, any `Retry-After`, a 401/403/5xx
 *   or a network error (offline): the answer becomes [ShopFetch.Stopped] with its [StopCause],
 *   and from then on every [fetch] returns that stop **without a request** — no waiting, no
 *   retry, never an attempt to bypass a challenge.
 * - **Retries** ([NetworkRetry], D-076): only a catalog refresh retries a timeout or a
 *   connection error, after 2, 4 and 8 s, each retry a new call through the client (rate
 *   limited, allow-listed). Stop signals and HTTP errors are never retried; the seed photos and
 *   the exact price fetch never retry.
 * - **One fetch at a time:** fetches of one session are serialised, so a fetch queued behind
 *   the one that stops is never sent.
 *
 * Cancelling the caller cancels the fetch in flight (a call cancelled before it went out is
 * never sent); a cancellation is no stop, the job itself ends.
 */
class ShopSession private constructor(
    private val client: ShopClient,
    private val retry: NetworkRetry,
    private val robots: RobotsTxt,
    initialStop: StopCause?,
) {
    private val oneAtATime = Mutex()

    /** Why the session stopped, or null while it may still send requests. */
    @Volatile
    var stopCause: StopCause? = initialStop
        private set

    /** [path] (an absolute path such as `/media/a.jpg`) on the shop; see [ShopClient.resolve]. */
    fun resolve(path: String): HttpUrl = client.resolve(path)

    /** Whether [url] is on the host allow-list and robots.txt lets the app fetch it. */
    suspend fun isAllowed(url: HttpUrl): Boolean =
        client.isAllowed(url) && withContext(Dispatchers.Default) { robots.isAllowed(url) }

    /**
     * Fetches [url] with at most one GET request and returns what the job does with it.
     *
     * @throws HostNotAllowedException when [url] is not on the allow-list (a programming
     *   error); nothing is sent.
     */
    suspend fun fetch(url: HttpUrl): ShopFetch = oneAtATime.withLock {
        stopCause?.let { return@withLock ShopFetch.Stopped(it) }
        if (!client.isAllowed(url)) throw HostNotAllowedException(url)
        if (!withContext(Dispatchers.Default) { robots.isAllowed(url) }) return@withLock ShopFetch.Disallowed
        val answer = client.getRetrying(url, retry)
        val stop = answer.stopCause()
        if (stop != null) {
            stopCause = stop
            return@withLock ShopFetch.Stopped(stop)
        }
        when (answer) {
            is ShopResponse.Success -> ShopFetch.Fetched(answer.contentType, answer.body)

            is ShopResponse.HttpError -> ShopFetch.Unavailable(answer.code)

            // Both are stops, handled above.
            is ShopResponse.Blocked, is ShopResponse.NetworkError -> error("unreachable: $answer")
        }
    }

    companion object {
        private val MISSING = setOf(404, 410)
        private val REFUSED = setOf(401, 403)
        private val SERVER_ERRORS = 500..599

        /**
         * Opens a session: fetches robots.txt (one request) and applies RFC 9309's status
         * rules. A stop answer gives a session that is already stopped ([stopCause] set), so
         * its first [fetch] sends nothing. [retry] applies to robots.txt and every [fetch].
         */
        suspend fun open(client: ShopClient, retry: NetworkRetry = NetworkRetry.NONE): ShopSession {
            val answer = client.getRetrying(client.resolve(RobotsTxt.ROBOTS_PATH), retry)
            val stop = answer.stopCause()
            val robots = withContext(Dispatchers.Default) {
                when {
                    answer is ShopResponse.Success -> RobotsTxt.parse(answer.body)
                    answer is ShopResponse.HttpError && answer.code in MISSING -> RobotsTxt.ALLOW_ALL
                    else -> RobotsTxt.DISALLOW_ALL
                }
            }
            return ShopSession(client, retry, robots, stop)
        }

        /** [url] fetched, and again after each of [retry]'s pauses while the answer is a transient network error. */
        private suspend fun ShopClient.getRetrying(url: HttpUrl, retry: NetworkRetry): ShopResponse {
            var answer = get(url)
            for (pause in retry.pausesMillis) {
                if (answer !is ShopResponse.NetworkError || !answer.isTransient) break
                delay(pause)
                answer = get(url)
            }
            return answer
        }

        /** The cause that ends the whole job after [this] answer, or null to go on (08 §2.8, D-050). */
        private fun ShopResponse.stopCause(): StopCause? = when (this) {
            is ShopResponse.Blocked -> when (reason) {
                BlockReason.CHALLENGE -> StopCause.CHALLENGE
                BlockReason.BLOCKED -> StopCause.BLOCKED
                BlockReason.TOO_MANY_REQUESTS -> StopCause.TOO_MANY_REQUESTS
                BlockReason.RETRY_AFTER -> StopCause.RETRY_AFTER
            }

            is ShopResponse.NetworkError -> StopCause.NETWORK

            is ShopResponse.HttpError -> StopCause.HTTP_ERROR.takeIf { code in REFUSED || code in SERVER_ERRORS }

            is ShopResponse.Success -> null
        }
    }
}

/**
 * How a [ShopSession] answers a timeout or a connection error (D-076): retried after each of
 * [pausesMillis], each retry a new call; when the last one fails too, the session stops with
 * [StopCause.NETWORK].
 */
class NetworkRetry private constructor(val pausesMillis: List<Long>) {
    companion object {
        /** No retry: the seed photo download (runs once) and the exact price fetch (falls back to the estimate). */
        val NONE = NetworkRetry(emptyList())

        /** The catalog refresh (CAT-07 AC2): ≤ 3 retries after 2, 4 and 8 s. */
        val REFRESH = NetworkRetry(listOf(2_000L, 4_000L, 8_000L))
    }
}

/** What one [ShopSession.fetch] gave the job. */
sealed interface ShopFetch {
    /** A 2xx answer that is no stop signal, with its whole body. */
    class Fetched(val contentType: String?, val body: ByteArray) : ShopFetch

    /** robots.txt disallows the URL; nothing was sent. */
    data object Disallowed : ShopFetch

    /** A status that leaves only this URL out and the job goes on: a 3xx (never followed), a 404 or another 4xx. */
    data class Unavailable(val code: Int) : ShopFetch

    /** The session stopped on [cause]: on this answer, or on an earlier one (then nothing was sent). */
    data class Stopped(val cause: StopCause) : ShopFetch
}

/** Why a [ShopSession] stopped (CLAUDE.md rule 7, 08 §2.8, D-050). */
enum class StopCause {
    /** Offline, DNS, timeout, a dropped connection (after the [NetworkRetry]) or an oversized answer. */
    NETWORK,

    /** A Cloudflare challenge page; never bypassed. */
    CHALLENGE,

    /** A Cloudflare block page. */
    BLOCKED,

    /** HTTP 429. */
    TOO_MANY_REQUESTS,

    /** A `Retry-After` header on any answer. */
    RETRY_AFTER,

    /** HTTP 401, 403 or 5xx: the shop refuses or is down. */
    HTTP_ERROR,
}
