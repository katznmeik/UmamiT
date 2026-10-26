package com.umamit.core.network

import okhttp3.Response
import java.io.IOException

/** The classified answer to one [ShopClient.get]. */
sealed interface ShopResponse {
    /** A 2xx answer that is not a stop signal, with its whole body. */
    class Success(val code: Int, val contentType: String?, val body: ByteArray) : ShopResponse

    /**
     * The shop asked the app to stop (08 §2.8, CLAUDE.md rule 7). The caller ends its whole job
     * at once: no further request, no waiting, no retry, never an attempt to bypass a challenge.
     */
    data class Blocked(val reason: BlockReason) : ShopResponse

    /** Any other status, including a 3xx (redirects are never followed). */
    data class HttpError(val code: Int) : ShopResponse

    /**
     * A 2xx answer whose body is over the request's [BodyLimit], left unread (D-090): [declaredBytes]
     * is its `Content-Length`, or null when the shop didn't declare one. The same request would
     * get it again, so it is never retried; it leaves only this URL out, it is no stop.
     */
    data class TooLarge(val declaredBytes: Long?) : ShopResponse

    /** No usable answer: offline, DNS failure, timeout or a dropped connection. */
    data class NetworkError(val cause: IOException) : ShopResponse
}

/** Why the shop stopped the app (08 §2.8). */
enum class BlockReason {
    /** A Cloudflare challenge page (`cf-mitigated: challenge`, or a 401/403/503 with challenge markers). */
    CHALLENGE,

    /** A Cloudflare block page: a 401/403/503 served by Cloudflare (`CF-RAY` or `Server: cloudflare`). */
    BLOCKED,

    /** HTTP 429. */
    TOO_MANY_REQUESTS,

    /** A `Retry-After` header on any other response. */
    RETRY_AFTER,
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_UNAVAILABLE = 503
private val REFUSALS = setOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_UNAVAILABLE)
private const val PAGE_BYTES = 5L * 1024 * 1024
private const val IMAGE_BYTES = 16L * 1024 * 1024

/**
 * The largest answer a request reads (D-090, D-091); a larger 2xx answer is a
 * [ShopResponse.TooLarge]. Images may be larger than pages: the shop serves product photos and
 * guide images as PNGs of up to ≈ 12 MB (full live run 1).
 */
enum class BodyLimit(val maxBytes: Long) {
    /** Pages, robots.txt and sitemaps: 5 MiB (the largest page seen is ≈ 1.3 MB). */
    PAGE(PAGE_BYTES),

    /** Product photos and guide images: 16 MiB, decoded with sampling (D-091). */
    IMAGE(IMAGE_BYTES),
}

/** A page's limit, the default of every request. */
internal val MAX_BODY_BYTES = BodyLimit.PAGE.maxBytes

/** Markers of a Cloudflare challenge page in a refusal's body (08 §2.8). */
private val CHALLENGE_MARKERS = listOf(
    "cdn-cgi/challenge-platform",
    "_cf_chl_opt",
    "challenges.cloudflare.com",
    "cf-turnstile",
    "Just a moment...",
    "Just a moment…",
)

/**
 * Classifies [response] per 08 §2.8 and D-050: a challenge header, 429 or `Retry-After` first
 * (without reading the body), then a refusal (401/403/503) with challenge markers or served by
 * Cloudflare, then success. A body over [limit] is not read (D-090): a 2xx becomes
 * [ShopResponse.TooLarge], any other status counts by its code alone (a refusal served by
 * Cloudflare is still a block).
 */
internal fun classify(response: Response, limit: BodyLimit = BodyLimit.PAGE): ShopResponse =
    stopSignal(response)?.let { ShopResponse.Blocked(it) } ?: classifyRead(response, limit.maxBytes)

/** A challenge header, 429 or `Retry-After`: a stop before the body is read. */
private fun stopSignal(response: Response): BlockReason? = when {
    response.header("cf-mitigated").equals("challenge", ignoreCase = true) -> BlockReason.CHALLENGE

    response.code == HTTP_TOO_MANY_REQUESTS -> BlockReason.TOO_MANY_REQUESTS

    response.header(STOP_RETRY_AFTER_HEADER) != null || response.header("Retry-After") != null ->
        BlockReason.RETRY_AFTER

    else -> null
}

/** An answer without a stop signal, by its status and its body (at most [maxBytes], read here). */
private fun classifyRead(response: Response, maxBytes: Long): ShopResponse {
    val code = response.code
    val body = try {
        readLimited(response, maxBytes)
    } catch (e: ResponseTooLargeException) {
        return tooLarge(response, e.declaredBytes)
    }
    return when {
        code in REFUSALS && body.hasChallengeMarker() -> ShopResponse.Blocked(BlockReason.CHALLENGE)
        code in REFUSALS && response.isFromCloudflare() -> ShopResponse.Blocked(BlockReason.BLOCKED)
        response.isSuccessful -> ShopResponse.Success(code, response.header("Content-Type"), body)
        else -> ShopResponse.HttpError(code)
    }
}

/** An answer whose body is over its limit, unread: by its status alone (D-090). */
private fun tooLarge(response: Response, declaredBytes: Long?): ShopResponse = when {
    response.code in REFUSALS && response.isFromCloudflare() -> ShopResponse.Blocked(BlockReason.BLOCKED)
    response.isSuccessful -> ShopResponse.TooLarge(declaredBytes)
    else -> ShopResponse.HttpError(response.code)
}

private fun Response.isFromCloudflare() =
    header("CF-RAY") != null || header("Server").equals("cloudflare", ignoreCase = true)

private fun readLimited(response: Response, maxBytes: Long): ByteArray {
    val declared = response.body.contentLength()
    if (declared > maxBytes) throw ResponseTooLargeException(maxBytes, declared)
    val source = response.body.source()
    if (source.request(maxBytes + 1)) throw ResponseTooLargeException(maxBytes, declaredBytes = null)
    return source.buffer.readByteArray()
}

/** An answer over [maxBytes], with its declared `Content-Length` if any; never read on. */
private class ResponseTooLargeException(maxBytes: Long, val declaredBytes: Long?) :
    IOException("response body over $maxBytes bytes (declared: ${declaredBytes ?: "none"})")

/**
 * Whether this network error is a timeout or a connection error that a new request may get past
 * (D-076): not a refused second attempt.
 */
internal val ShopResponse.NetworkError.isTransient: Boolean
    get() = cause !is SecondAttemptRefusedException

private fun ByteArray.hasChallengeMarker(): Boolean {
    val text = decodeToString()
    return CHALLENGE_MARKERS.any { text.contains(it) }
}
