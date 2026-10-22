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
    /** A Cloudflare challenge page (`cf-mitigated: challenge`, or a 403/503 with challenge markers). */
    CHALLENGE,

    /** HTTP 429. */
    TOO_MANY_REQUESTS,

    /** A `Retry-After` header on any other response. */
    RETRY_AFTER,
}

private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_UNAVAILABLE = 503

/** Bodies above this size are refused; the largest expected answer is a product photo. */
private const val MAX_BODY_BYTES = 10L * 1024 * 1024

/** Markers of a Cloudflare challenge page in a 403/503 body (08 §2.8). */
private val CHALLENGE_MARKERS =
    listOf("cdn-cgi/challenge-platform", "_cf_chl_opt", "Just a moment...", "Just a moment…")

/** Classifies [response] per 08 §2.8: challenge, then 429, then `Retry-After`, then success. */
internal fun classify(response: Response): ShopResponse {
    val code = response.code
    val body = readLimited(response)
    return when {
        response.header("cf-mitigated").equals("challenge", ignoreCase = true) ->
            ShopResponse.Blocked(BlockReason.CHALLENGE)

        (code == HTTP_FORBIDDEN || code == HTTP_UNAVAILABLE) && body.hasChallengeMarker() ->
            ShopResponse.Blocked(BlockReason.CHALLENGE)

        code == HTTP_TOO_MANY_REQUESTS -> ShopResponse.Blocked(BlockReason.TOO_MANY_REQUESTS)

        response.header("Retry-After") != null -> ShopResponse.Blocked(BlockReason.RETRY_AFTER)

        response.isSuccessful -> ShopResponse.Success(code, response.header("Content-Type"), body)

        else -> ShopResponse.HttpError(code)
    }
}

private fun readLimited(response: Response): ByteArray {
    val source = response.body.source()
    if (source.request(MAX_BODY_BYTES + 1)) throw IOException("response body larger than $MAX_BODY_BYTES bytes")
    return source.buffer.readByteArray()
}

private fun ByteArray.hasChallengeMarker(): Boolean {
    val text = decodeToString()
    return CHALLENGE_MARKERS.any { text.contains(it) }
}
