package com.umamit.core.data.catalog

import com.umamit.core.catalog.parser.ProductParse
import com.umamit.core.catalog.parser.TeeKontorParser
import com.umamit.core.common.WallClock
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.network.HostNotAllowedException
import com.umamit.core.network.NetworkRetry
import com.umamit.core.network.ShopClient
import com.umamit.core.network.ShopFetch
import com.umamit.core.network.ShopSession
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import kotlin.math.abs

/**
 * The exact price of a package size, fetched from the size's own shop page when the user adds
 * it (CAT-12, network use (c) of GEN-01, 08 §2.4 item 6). One [session] per add sheet.
 */
fun interface ExactPrices {
    /** A new job against the shop: it reads robots.txt with its first price, once. */
    fun session(): ExactPriceSession
}

/** One add sheet's price fetches (D-074: one [ShopSession], never retried, a stop ends it). */
fun interface ExactPriceSession {
    /**
     * The price in cents of the [grams] size on its page [variantUrl], or null: offline, on any
     * stop (challenge, block, 429, `Retry-After`, 401/403/5xx), for a page robots.txt disallows,
     * that is gone or shows another size, and for a URL off the shop. The sheet then keeps the
     * estimate or an empty price, editable either way, and saving never waits for it.
     */
    suspend fun price(variantUrl: String, grams: Double): Long?
}

/**
 * [ExactPrices] through the allow-listed [ShopClient] (≤ 2 requests/s, one connection, D-050).
 *
 * Like the refresh (D-077, D-079), nothing a live page does reaches the process the timer runs
 * in (rule 8, D-090): any error but a cancellation, the parser's included, gives no price, so the
 * sheet keeps the estimate, and one line goes to the [DiagnosticsLog].
 */
class ShopExactPrices internal constructor(
    private val client: ShopClient,
    private val diagnostics: DiagnosticsLog,
    private val clock: WallClock,
    private val parse: (ByteArray, String) -> ProductParse,
) : ExactPrices {
    @Inject
    constructor(client: ShopClient, diagnostics: DiagnosticsLog, clock: WallClock) :
        this(client, diagnostics, clock, TeeKontorParser::parseProduct)

    override fun session(): ExactPriceSession = Session()

    private fun log(message: String) = diagnostics.append(clock.nowMs(), clock.zone(), message)

    private inner class Session : ExactPriceSession {
        private val opening = Mutex()
        private var shop: ShopSession? = null

        override suspend fun price(variantUrl: String, grams: Double): Long? = try {
            exactPrice(variantUrl, grams)
        } catch (e: CancellationException) {
            throw e
        } catch (
            // Rule 8: a stack overflow in a page, a parser bug, … ends this price fetch, never the process.
            @Suppress("TooGenericExceptionCaught") e: Throwable,
        ) {
            log("exact price: unexpected ${e.javaClass.simpleName}, the estimate stays")
            null
        }

        private suspend fun exactPrice(variantUrl: String, grams: Double): Long? {
            val url = variantUrl.toHttpUrlOrNull()?.takeIf(client::isAllowed) ?: return null
            val answer = fetch(url)
            val page = (answer as? ShopFetch.Fetched)?.let { fetched ->
                withContext(Dispatchers.Default) { (parse(fetched.body, url.toString()) as? ProductParse.Parsed)?.page }
            }
            // Only a page that shows this size: never another size's price.
            val shown = page?.packageGrams
            val cents = page?.priceCents?.takeIf { shown != null && abs(shown - grams) < SAME_GRAMS }
            if (cents == null) {
                val why = if (answer is ShopFetch.Fetched) {
                    "shows no price of this size"
                } else {
                    "not read (${answer ?: "off the host"})"
                }
                log("exact price: ${url.encodedPath} $why, the estimate stays")
            }
            return cents
        }

        /** [url] through the sheet's session, opened (robots.txt read) with the first fetch; null off the host. */
        private suspend fun fetch(url: HttpUrl): ShopFetch? {
            val session = opening.withLock { shop ?: ShopSession.open(client, NetworkRetry.NONE).also { shop = it } }
            return try {
                session.fetch(url)
            } catch (_: HostNotAllowedException) {
                null
            }
        }
    }

    private companion object {
        const val SAME_GRAMS = 0.01
    }
}

/** Binds [ExactPrices] (CAT-12). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExactPricesModule {
    @Binds
    abstract fun exactPrices(impl: ShopExactPrices): ExactPrices
}
