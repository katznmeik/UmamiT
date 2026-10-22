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
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The app's only HTTP client (GEN-01, D-036, CLAUDE.md rule 7, 06 §4, 08 §2.8). Every request
 * of the three network uses goes through one instance:
 *
 * - **Host allow-list:** only `https://www.tee-kontor-kiel.de` (scheme, host and port must
 *   match [baseUrl]). Any other URL fails with [HostNotAllowedException] before a connection
 *   is opened. Redirects are not followed, so a 3xx can never lead to another host.
 * - **One connection, one at a time:** calls run on a single dispatcher thread with
 *   `maxRequests = maxRequestsPerHost = 1` and a pool of one connection; OkHttp's silent
 *   retry on connection failure is off, so one call is exactly one request.
 * - **At most 2 requests per second:** request starts are at least 500 ms apart.
 * - **Honest User-Agent** „UmamiT/<version> (private app)"; **no cookies**, no cache.
 * - **Stop signals** are classified, never retried or bypassed: a Cloudflare challenge,
 *   HTTP 429 or any `Retry-After` is returned as [ShopResponse.Blocked], and the caller stops.
 *
 * The base URL (and with it the allowed host) is fixed in production ([create]); only
 * [forTesting] can point the client at a MockWebServer. M2 (catalog refresh, CAT-07; exact
 * prices, CAT-12) extends this client with the sitemap and its own retry policy.
 */
class ShopClient private constructor(
    /** The shop's root; [get] accepts only URLs with its scheme, host and port. */
    val baseUrl: HttpUrl,
    appVersion: String,
    rateLimiter: RequestRateLimiter,
) {
    /** The User-Agent sent with every request (08 §2.8). */
    val userAgent: String = "UmamiT/$appVersion (private app)"

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        // Checked before anything else, so a foreign host costs no request and no wait.
        .addInterceptor(Interceptor { chain -> chain.proceed(checkAllowed(chain.request())) })
        .addInterceptor(Interceptor { chain -> chain.proceed(rateLimiter.acquire(chain)) })
        .addInterceptor(
            Interceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            },
        )
        // Defence in depth: also checks every request that reaches the network.
        .addNetworkInterceptor(Interceptor { chain -> chain.proceed(checkAllowed(chain.request())) })
        .dispatcher(
            Dispatcher(Executors.newSingleThreadExecutor { Thread(it, "UmamiT-shop") }).apply {
                maxRequests = 1
                maxRequestsPerHost = 1
            },
        )
        .connectionPool(ConnectionPool(1, KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Whether [url] is on the allow-list: same scheme, host and port as [baseUrl]. */
    fun isAllowed(url: HttpUrl): Boolean =
        url.scheme == baseUrl.scheme && url.host == baseUrl.host && url.port == baseUrl.port

    /** [path] (e.g. `/robots.txt`) resolved against [baseUrl]. */
    fun resolve(path: String): HttpUrl = requireNotNull(baseUrl.resolve(path)) { "not a URL path: $path" }

    /**
     * Fetches [url] with one GET request and classifies the answer. Suspends until the single
     * connection is free and the rate limit allows the request. Cancelling the caller cancels
     * the call.
     *
     * @throws HostNotAllowedException when [url] is not on the allow-list (a programming error,
     *   so it is not folded into [ShopResponse.NetworkError]).
     */
    suspend fun get(url: HttpUrl): ShopResponse {
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
                            response.use(::classify)
                        } catch (e: IOException) {
                            ShopResponse.NetworkError(e)
                        }
                        continuation.resume(result)
                    }
                },
            )
        }
    }

    private fun checkAllowed(request: Request): Request {
        if (!isAllowed(request.url)) throw HostNotAllowedException(request.url)
        return request
    }

    companion object {
        /** The only host the app talks to (GEN-01 AC1); images are served from it too (08 §2.2). */
        const val SHOP_HOST = "www.tee-kontor-kiel.de"

        private val SHOP_BASE_URL = "https://$SHOP_HOST/".toHttpUrl()
        private const val KEEP_ALIVE_MINUTES = 5L
        private const val TIMEOUT_SECONDS = 20L

        /** The production client for `https://www.tee-kontor-kiel.de/`. */
        fun create(appVersion: String): ShopClient = ShopClient(SHOP_BASE_URL, appVersion, RequestRateLimiter())

        /** A client whose allow-list is [baseUrl]'s scheme, host and port, e.g. a MockWebServer. */
        @VisibleForTesting
        fun forTesting(baseUrl: HttpUrl, appVersion: String = "test"): ShopClient =
            ShopClient(baseUrl, appVersion, RequestRateLimiter())
    }
}

/** A request to a URL outside the allow-list (GEN-01 AC1); no connection was opened for it. */
class HostNotAllowedException(url: HttpUrl) : IOException("host not allowed: ${url.scheme}://${url.host}:${url.port}")
