package com.umamit.core.data.catalog

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.catalog.parser.ProductParse
import com.umamit.core.catalog.parser.SyntheticShop
import com.umamit.core.catalog.parser.TeeKontorParser
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.network.ShopClient
import com.umamit.core.testing.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The exact price fetch when a package is added (CAT-12, network use (c) of GEN-01, D-074,
 * D-076) against a simulated shop with synthetic pages (D-016): one request for robots.txt and
 * one for the size's page, never a retry; offline or on any stop no price, so the sheet keeps
 * the estimate; a page the parser can't handle never takes the process down (rule 8, D-090).
 *
 * Under Robolectric like the other shop tests (D-090): the client and the server run warm in
 * the shared sandbox, the client's request gap is shortened (the rate limit has its own tests),
 * and each price assertion names the session's diagnostics, so a failure says why.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExactPricesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, () -> MockResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val base get() = server.url("/").toString().removeSuffix("/")
    private lateinit var diagnostics: DiagnosticsLog

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return routes[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        diagnostics = DiagnosticsLog(folder.newFolder(), maxBytes = 100_000, dispatcher = Dispatchers.IO)
        routes["/robots.txt"] = { text(SyntheticShop.robotsTxt("Disallow: /verboten/")) }
        routes[VARIANT] = { text(variantPage()) }
    }

    @After
    fun tearDown() = server.close()

    private fun prices(parse: (ByteArray, String) -> ProductParse = TeeKontorParser::parseProduct) = ShopExactPrices(
        client = ShopClient.forTesting(baseUrl = server.url("/"), requestIntervalMillis = 0),
        diagnostics = diagnostics,
        clock = FakeClock(),
        parse = parse,
    )

    /** What the price session logged, for the assertion messages. */
    private suspend fun logged() = diagnostics.entries().map { it.message }

    private fun paths() = requests.map { it.url.encodedPath }

    private fun variantPage(unitContent: String = "0.25 kg") = SyntheticShop.productPage(
        baseUrl = base,
        title = "Morgennebel Sencha – 250g",
        sku = "XY0100-250",
        canonicalPath = VARIANT,
        price = "30,10 €",
        metaPrice = "30.1",
        unitContent = unitContent,
        unitReference = "(120,40 € / 1 kg)",
        sizes = listOf(SyntheticShop.Size("100g"), SyntheticShop.Size("250g", "3% gespart¹", checked = true)),
    )

    @Test
    fun onlineTheSizesPageGivesTheExactPrice() = runTest {
        val price = prices().session().price("$base$VARIANT", grams = 250.0)

        assertWithMessage("diagnostics: ${logged()}").that(price).isEqualTo(3010)
        assertThat(paths()).containsExactly("/robots.txt", VARIANT).inOrder()
    }

    @Test
    fun aPageThatBreaksTheParserGivesNoPriceAndNeverCrashes() = runTest {
        // Rule 8: the timer runs in this process; a live page the parser chokes on keeps the estimate (D-090).
        val prices = prices(parse = { _, _ -> throw StackOverflowError() })

        val price = prices.session().price("$base$VARIANT", grams = 250.0)

        assertThat(price).isNull()
        assertThat(logged()).contains("exact price: unexpected StackOverflowError, the estimate stays")
    }

    @Test
    fun oneSessionReadsRobotsTxtOnceForSeveralSizes() = runTest {
        val session = prices().session()
        session.price("$base$VARIANT", grams = 250.0)
        session.price("$base/morgennebel-sencha/XY0100-10", grams = 10.0)

        assertThat(paths()).containsExactly("/robots.txt", VARIANT, "/morgennebel-sencha/XY0100-10").inOrder()
    }

    @Test
    fun aPageOfAnotherSizeGivesNoPrice() = runTest {
        routes[VARIANT] = { text(variantPage(unitContent = "0.1 kg")) }

        assertWithMessage("diagnostics: ${logged()}").that(prices().session().price("$base$VARIANT", grams = 250.0))
            .isNull()
    }

    @Test
    fun offlineThereIsNoPrice() = runTest {
        val prices = prices()
        server.close()

        assertThat(prices.session().price("$base$VARIANT", grams = 250.0)).isNull()
    }

    @Test
    fun aStopEndsTheSessionWithoutRetryOrFurtherRequest() = runTest {
        routes[VARIANT] = { MockResponse.Builder().code(429).build() }
        val session = prices().session()

        assertThat(session.price("$base$VARIANT", grams = 250.0)).isNull()
        assertThat(session.price("$base/morgennebel-sencha/XY0100-10", grams = 10.0)).isNull()

        assertThat(paths()).containsExactly("/robots.txt", VARIANT).inOrder()
    }

    @Test
    fun aDisallowedOrForeignUrlIsNeverFetched() = runTest {
        val session = prices().session()

        assertThat(session.price("$base/verboten/XY0100-250", grams = 250.0)).isNull()
        assertThat(session.price("https://elsewhere.example/a/XY0100-250", grams = 250.0)).isNull()
        assertThat(session.price("kein Link", grams = 250.0)).isNull()

        assertThat(paths()).containsExactly("/robots.txt")
    }

    private fun text(body: String) =
        MockResponse.Builder().code(200).setHeader("Content-Type", "text/html; charset=utf-8").body(body).build()

    private companion object {
        const val VARIANT = "/morgennebel-sencha/XY0100-250"
    }
}
