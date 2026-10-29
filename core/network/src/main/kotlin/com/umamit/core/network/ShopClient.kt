package com.umamit.core.network

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The app's only HTTP client (GEN-01, D-036, D-050, CLAUDE.md rule 7, 06 §4, 08 §2.8). Every
 * request of the four network uses goes through one instance:
 *
 * - **Host allow-list:** only `https://www.tee-kontor-kiel.de` (scheme, host and port must
 *   match [baseUrl]). Any other URL fails with [HostNotAllowedException] before a connection
 *   is opened, and every request is checked again on the network. Redirects are not followed.
 *   The connection is direct (no system proxy), so the shop is the real destination.
 * - **One connection, one request per call:** calls run on a single dispatcher thread with
 *   `maxRequests = maxRequestsPerHost = 1`, a pool of one connection and no fast fallback (no
 *   parallel connects). OkHttp never repeats a request: connection retries are off, the shop's
 *   `Retry-After` is hidden from OkHttp, and a second network attempt of a call is refused.
 * - **At most 2 requests per second:** requests go out at least 500 ms apart, measured when
 *   each is written to the connection.
 * - **Honest User-Agent** „UmamiT/<version> (private app)"; **no cookies**, no cache.
 * - **Stop signals** are classified, never retried or bypassed ([classify]): a Cloudflare
 *   challenge or block page, HTTP 429 or any `Retry-After` is a [ShopResponse.Blocked].
 *
 * The base URL (and with it the allowed host) is fixed in production ([create]); only
 * [forTesting] can point the client at a MockWebServer. A whole job (the seed photos, a catalog
 * refresh, an exact price fetch) uses it through a [ShopSession], which adds robots.txt and
 * the stop of the whole job (D-074).
 */
class ShopClient private constructor(
    /** The shop's root; [get] accepts only URLs with its scheme, host and port. */
    val baseUrl: HttpUrl,
    appVersion: String,
    rateLimiter: RequestRateLimiter,
    socketFactory: SocketFactory,
) {
    /** The User-Agent sent with every request (08 §2.8). */
    val userAgent: String = "UmamiT/$appVersion (private app)"

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        // Checked before anything else, so a foreign host costs no connection and no wait.
        .addInterceptor(hostAllowList(::isAllowed))
        .addInterceptor(tagAttempts())
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        // On the connection, in this order: host again, one attempt, rate limit, then the
        // answer's Retry-After hidden from OkHttp's follow-up logic.
        .addNetworkInterceptor(hostAllowList(::isAllowed))
        .addNetworkInterceptor(singleAttempt())
        .addNetworkInterceptor(rateLimit(rateLimiter))
        .addNetworkInterceptor(retryAfterShield())
        .dispatcher(
            // A daemon thread: an idle client never keeps a JVM (a test run) alive.
            Dispatcher(
                Executors.newSingleThreadExecutor {
                    Thread(it, "UmamiT-shop").apply { isDaemon = true }
                },
            ).apply {
                maxRequests = 1
                maxRequestsPerHost = 1
            },
        )
        // A short keep-alive: connection retries are off, so a connection the shop (or a NAT) closed
        // while idle must not be reused for the next job's first request (D-090).
        .connectionPool(ConnectionPool(1, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS))
        .fastFallback(false)
        .proxy(Proxy.NO_PROXY)
        .socketFactory(socketFactory)
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Whether [url] is on the allow-list: same scheme, host and port as [baseUrl]. */
    fun isAllowed(url: HttpUrl): Boolean =
        url.scheme == baseUrl.scheme && url.host == baseUrl.host && url.port == baseUrl.port

    /** [path] (an absolute path such as `/robots.txt`, never a URL) resolved against [baseUrl]. */
    fun resolve(path: String): HttpUrl {
        require(path.startsWith("/") && !path.startsWith("//")) { "not an absolute path: $path" }
        return requireNotNull(baseUrl.resolve(path)) { "not a URL path: $path" }
    }

    /**
     * Fetches [url] with exactly one GET request and classifies the answer, reading a body of at
     * most [limit] (D-091). Suspends until the single connection is free and the rate limit
     * allows the request. Cancelling the caller cancels the call; a call cancelled before it went
     * out is never sent.
     *
     * @throws HostNotAllowedException when [url] is not on the allow-list (a programming error,
     *   so it is not folded into [ShopResponse.NetworkError]).
     */
    suspend fun get(url: HttpUrl, limit: BodyLimit = BodyLimit.PAGE): ShopResponse {
        if (!isAllowed(url)) throw HostNotAllowedException(url)
        val call = okHttp.newCall(Request.Builder().url(url).get().build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (e is HostNotAllowedException) {
                            continuation.resumeWithException(e)
                        } else {
                            continuation.resume(ShopResponse.NetworkError(e))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        // Read on the dispatcher thread: the next call waits until the body is in.
                        val result = try {
                            response.use { classify(it, limit) }
                        } catch (e: IOException) {
                            ShopResponse.NetworkError(e)
                        }
                        continuation.resume(result)
                    }
                },
            )
        }
    }

    companion object {
        /** The only host the app talks to (GEN-01 AC1); images are served from it too (08 §2.2). */
        const val SHOP_HOST = "www.tee-kontor-kiel.de"

        private val SHOP_BASE_URL = "https://$SHOP_HOST/".toHttpUrl()
        private const val KEEP_ALIVE_SECONDS = 30L
        private const val TIMEOUT_SECONDS = 20L

        /** The production client for `https://www.tee-kontor-kiel.de/`. */
        fun create(appVersion: String): ShopClient =
            ShopClient(SHOP_BASE_URL, appVersion, RequestRateLimiter(), SocketFactory.getDefault())

        /**
         * A client whose allow-list is [baseUrl]'s scheme, host and port, e.g. a MockWebServer.
         * [socketFactory] lets a test add connection latency. [requestIntervalMillis] is the gap
         * between requests: the real 500 ms by default; a test that doesn't measure the rate may
         * shorten it, so it never waits on the wall clock (D-090).
         */
        @VisibleForTesting
        fun forTesting(
            baseUrl: HttpUrl,
            appVersion: String = "test",
            socketFactory: SocketFactory = SocketFactory.getDefault(),
            requestIntervalMillis: Long? = null,
        ): ShopClient = ShopClient(
            baseUrl,
            appVersion,
            requestIntervalMillis?.let {
                RequestRateLimiter(TimeUnit.MILLISECONDS.toNanos(it))
            } ?: RequestRateLimiter(),
            socketFactory,
        )
    }
}

/** A request to a URL outside the allow-list (GEN-01 AC1); no connection was opened for it. */
class HostNotAllowedException(url: HttpUrl) : IOException("host not allowed: ${url.scheme}://${url.host}:${url.port}")
