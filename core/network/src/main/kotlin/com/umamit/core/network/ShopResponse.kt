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

    /** No usable answer: offline, DNS failure, timeout, a dropped connection or an oversized body. */
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

/** Bodies above this size are refused; the largest expected answer is a product photo. */
internal const val MAX_BODY_BYTES = 5L * 1024 * 1024

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
 * Cloudflare, then success. A body over [MAX_BODY_BYTES] fails as an [IOException].
 */
internal fun classify(response: Response): ShopResponse {
    val code = response.code
    val stop = when {
        response.header("cf-mitigated").equals("challenge", ignoreCase = true) -> BlockReason.CHALLENGE

        code == HTTP_TOO_MANY_REQUESTS -> BlockReason.TOO_MANY_REQUESTS

        response.header(STOP_RETRY_AFTER_HEADER) != null || response.header("Retry-After") != null ->
            BlockReason.RETRY_AFTER

        else -> null
    }
    if (stop != null) return ShopResponse.Blocked(stop)
    val body = readLimited(response)
    return when {
        code in REFUSALS && body.hasChallengeMarker() -> ShopResponse.Blocked(BlockReason.CHALLENGE)
        code in REFUSALS && response.isFromCloudflare() -> ShopResponse.Blocked(BlockReason.BLOCKED)
        response.isSuccessful -> ShopResponse.Success(code, response.header("Content-Type"), body)
        else -> ShopResponse.HttpError(code)
    }
}

private fun Response.isFromCloudflare() =
    header("CF-RAY") != null || header("Server").equals("cloudflare", ignoreCase = true)

private fun readLimited(response: Response): ByteArray {
    val declared = response.body.contentLength()
    if (declared > MAX_BODY_BYTES) throw IOException("response body of $declared bytes is over the limit")
    val source = response.body.source()
    if (source.request(MAX_BODY_BYTES + 1)) throw IOException("response body larger than $MAX_BODY_BYTES bytes")
    return source.buffer.readByteArray()
}

private fun ByteArray.hasChallengeMarker(): Boolean {
    val text = decodeToString()
    return CHALLENGE_MARKERS.any { text.contains(it) }
}
