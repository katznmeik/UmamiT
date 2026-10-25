package com.umamit.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.catalog.parser.SanityCheck
import com.umamit.core.catalog.parser.SyntheticShop
import com.umamit.core.catalog.parser.SyntheticShop.Stock
import com.umamit.core.data.SendTimes
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.database.entity.CatalogSkipReason
import com.umamit.core.model.UserSettings
import com.umamit.core.network.ShopClient
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The whole catalog refresh against a simulated shop (spec #62 seam 2, #64; CLAUDE.md rule 7,
 * 08 §2.4, §2.8, §2.9, 05 §4.5): MockWebServer serving a synthetic robots.txt, sitemap index,
 * gzip url set and product pages (D-016), an in-memory `catalog.db` and a fake clock.
 *
 * The shop: two green teas (a Sencha with size variants, a Gyokuro in the Japantee tree), a
 * black tea and a teapot (out of scope), a set (never fetched), a guide and category pages.
 * Every request costs half a second (the real rate limit), so the shops stay small.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogRefresherTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val db = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.catalogDao()
    private val clock = FakeClock()

    /** What the synthetic shop answers per path; unknown paths are 404. */
    private val routes = mutableMapOf<String, () -> MockResponse>()

    /** Every request the shop saw, in order. */
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    /** When the client wrote each request onto its socket (System.nanoTime), in order. */
    private val sent = CopyOnWriteArrayList<Long>()

    /** The product URLs (paths) the sitemap lists. */
    private val listed = mutableListOf<String>()

    private val base get() = server.url("/").toString().removeSuffix("/")

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return routes[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        routes[ROBOTS] = { text(SyntheticShop.robotsTxt()) }
        routes[SITEMAP] = { text(SyntheticShop.sitemapIndex("$base$SITEMAP_GZ")) }
        routes[SITEMAP_GZ] = {
            MockResponse.Builder().addHeader("Content-Type", "application/x-gzip")
                .body(Buffer().write(SyntheticShop.gzip(SyntheticShop.urlSet(listed.map { "$base$it" })))).build()
        }
        product(
            SENCHA,
            "Morgennebel Sencha – 100g",
            "XY0100",
            SyntheticShop.SENCHA_PATH,
            variants = listOf("-10", "-250", "M"),
        )
        product(
            GYOKURO,
            "Tautropfen Gyokuro – 50g",
            "XY0200",
            GYOKURO_PATH,
            price = "18,00 €",
            content = "50 g",
            reference = "(36,00 € / 100 g)",
        )
        product(BLACK, "Abendglut Assam – 100g", "XY0300", SyntheticShop.BLACK_TEA_PATH)
        product(TEAPOT, "Kanne aus Ton", "XY0500", SyntheticShop.TEAWARE_PATH)
        listed += listOf(
            "/geschenk-set/XY0400SET",
            "/tee-info/ein-erfundener-ratgeber",
            "/tee/gruener-tee/",
            "/tee/gruener-tee/japan/sencha/",
        )
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    /** Lists a product page at [path] (its variants too) and serves it. */
    @Suppress("LongParameterList")
    private fun product(
        path: String,
        title: String,
        sku: String,
        breadcrumb: List<Pair<String, String>>?,
        price: String = "12,40 €",
        content: String = "0.1 kg",
        reference: String = "(124,00 € / 1 kg)",
        stock: Stock = Stock.IN_STOCK,
        variants: List<String> = emptyList(),
        list: Boolean = true,
    ) {
        if (list) {
            listed += path
            variants.forEach { listed += path + it }
        }
        routes[path] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    title = title,
                    sku = sku,
                    canonicalPath = path,
                    breadcrumb = breadcrumb,
                    price = price,
                    metaPrice = null,
                    unitContent = content,
                    unitReference = reference,
                    stock = stock,
                ),
            )
        }
    }

    private fun refresher(sanity: SanityCheck = SanityCheck(firstImportMinimum = 1)) = CatalogRefresher(
        client = ShopClient.forTesting(baseUrl = server.url("/"), socketFactory = SendTimes(sent)),
        dao = dao,
        clock = clock,
        diagnostics = DiagnosticsLog(folder.newFolder(), maxBytes = 100_000, dispatcher = Dispatchers.IO),
        // The photos have their own tests (CatalogRefresherDetailTest); here they would only add requests.
        settings = FakeSettingsRepository(UserSettings(catalogDownloadPhotos = false)),
        photos = CatalogPhotoFiles(folder.newFolder()),
        sanity = sanity,
    )

    private fun paths() = requests.map { it.url.encodedPath }

    private suspend fun names() = dao.observeList().first().map { it.name }

    /** A catalog from an earlier refresh: the two green teas, swapped in by a first run. */
    private suspend fun TestScope.previousCatalog() {
        assertThat(refresher().refresh()).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 2))
        requests.clear()
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
    }

    @Test
    fun aFirstRefreshStoresTheGreenTeasAndSkipsTheOthers() = runTest {
        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 2))
        val rows = dao.observeList().first()
        assertThat(rows.map { Triple(it.key, it.name, it.basePriceCentsPer100g) }).containsExactly(
            Triple("XY0100", "Morgennebel Sencha", 1240L),
            Triple("XY0200", "Tautropfen Gyokuro", 3600L),
        ).inOrder()
        assertThat(rows.map { it.teaType }).containsExactly("Sencha", "Gyokuro").inOrder()
        assertThat(dao.skipList().map { it.baseKey to it.reason })
            .containsExactly("XY0300" to CatalogSkipReason.NOT_GREEN, "XY0500" to CatalogSkipReason.NOT_GREEN)
        // robots.txt, the sitemap and each parent page once: no variant, set, guide or category page.
        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SITEMAP_GZ, SENCHA, GYOKURO, BLACK, TEAPOT).inOrder()
        assertThat(dao.refreshLog().single().status).isEqualTo(CatalogRefreshStatus.SUCCESS)
        assertThat(dao.observeLastSuccess().first()).isEqualTo(clock.wall)
        assertThat(dao.countStaged()).isEqualTo(0)
    }

    @Test
    fun aLaterRefreshFetchesKnownGreensAndNewProductsButNotTheSkipList() = runTest {
        previousCatalog()
        product(NEW_TEA, "Frühlingshauch Shincha – 100g", "XY0600", SHINCHA_PATH)

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 3, newTeas = 1))
        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SITEMAP_GZ, SENCHA, GYOKURO, NEW_TEA).inOrder()
        assertThat(names())
            .containsExactly("Frühlingshauch Shincha", "Morgennebel Sencha", "Tautropfen Gyokuro").inOrder()
    }

    @Test
    fun theSkipListIsCheckedAgainAfter90Days() = runTest {
        previousCatalog()
        clock.advanceBy(TimeUnit.DAYS.toMillis(SKIP_RECHECK_DAYS))

        refresher().refresh()

        assertThat(paths()).containsAtLeast(BLACK, TEAPOT)
        assertThat(dao.skipList().map { it.checkedAt }.distinct()).containsExactly(clock.wall)
    }

    @Test
    fun requestsGoOutAtMostTwicePerSecondOnOneConnection() = runTest {
        val start = System.nanoTime()
        refresher().refresh()
        val end = System.nanoTime()

        assertThat(requests.map { it.connectionIndex }.distinct()).containsExactly(0)
        assertThat(sent).hasSize(requests.size)
        val gapsMs = sent.zipWithNext { a, b -> TimeUnit.NANOSECONDS.toMillis(b - a) }
        gapsMs.forEach { assertWithMessage("send gaps $gapsMs ms").that(it).isAtLeast(MIN_GAP_MS) }
        assertThat(TimeUnit.NANOSECONDS.toMillis(end - start)).isAtLeast(LIMITER_INTERVAL_MS * (sent.size - 1))
        requests.forEach { assertThat(it.headers["User-Agent"]).isEqualTo("UmamiT/test (private app)") }
    }

    @Test
    fun aPageRobotsTxtDisallowsIsNeverFetched() = runTest {
        routes[ROBOTS] = { text(SyntheticShop.robotsTxt("Disallow: /tautropfen-gyokuro/")) }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 1, newTeas = 1))
        assertThat(paths()).doesNotContain(GYOKURO)
        assertThat(names()).containsExactly("Morgennebel Sencha")
    }

    @Test
    fun aSitemapRobotsTxtDisallowsMeansTheShopChanged() = runTest {
        routes[ROBOTS] = { text(SyntheticShop.robotsTxt("Disallow: /sitemap")) }

        val outcome = refresher().refresh()

        assertThat(outcome).isInstanceOf(CatalogRefreshOutcome.ShopChanged::class.java)
        assertThat(paths()).containsExactly(ROBOTS)
    }

    @Test
    fun eachStopKeepsTheOldCatalog() = runTest {
        previousCatalog()
        val skipsBefore = dao.skipList()
        val stops = listOf(
            stopOnPage({ MockResponse.Builder().code(403).addHeader("cf-mitigated", "challenge").build() }) to
                CatalogRefreshOutcome.Blocked::class.java,
            stopOnPage({ MockResponse.Builder().code(429).build() }) to CatalogRefreshOutcome.Blocked::class.java,
            stopOnPage({ MockResponse.Builder().addHeader("Retry-After", "120").body("x").build() }) to
                CatalogRefreshOutcome.Blocked::class.java,
            stopOnPage({ MockResponse.Builder().code(503).build() }) to
                CatalogRefreshOutcome.ShopUnavailable::class.java,
            stopOnPage({ MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build() }) to
                CatalogRefreshOutcome.Offline::class.java,
        )
        for ((stop, expected) in stops) {
            requests.clear()
            stop()

            val outcome = refresher().refresh()

            assertThat(outcome).isInstanceOf(expected)
            assertThat(names()).containsExactly("Morgennebel Sencha", "Tautropfen Gyokuro").inOrder()
            assertThat(dao.skipList()).isEqualTo(skipsBefore)
            assertThat(dao.countStaged()).isEqualTo(0)
            assertThat(dao.refreshLog().last().status).isEqualTo(CatalogRefreshStatus.FAILED)
            // Nothing after the stop: the Gyokuro page stopped the run, the new page was never asked for.
            assertThat(paths()).doesNotContain(NEW_TEA)
        }
    }

    /** The shop answers [answer] on the Gyokuro page; a new tea listed after it must not be fetched. */
    private fun stopOnPage(answer: () -> MockResponse): () -> Unit = {
        routes[GYOKURO] = answer
        if (NEW_TEA !in listed) product(NEW_TEA, "Frühlingshauch Shincha – 100g", "XY0600", SHINCHA_PATH)
    }

    @Test
    fun theBlockCauseIsTyped() = runTest {
        val causes = mapOf(
            { MockResponse.Builder().code(403).addHeader("cf-mitigated", "challenge").build() } to BlockCause.CHALLENGE,
            { MockResponse.Builder().code(429).build() } to BlockCause.TOO_MANY_REQUESTS,
            { MockResponse.Builder().addHeader("Retry-After", "60").body(SyntheticShop.robotsTxt()).build() } to
                BlockCause.RETRY_AFTER,
            {
                MockResponse.Builder().code(403).addHeader("Server", "cloudflare")
                    .body("Sorry, you have been blocked").build()
            } to BlockCause.BLOCKED,
        )
        for ((answer, cause) in causes) {
            requests.clear()
            routes[ROBOTS] = answer

            val outcome = refresher().refresh()

            assertThat((outcome as CatalogRefreshOutcome.Blocked).cause).isEqualTo(cause)
            assertThat(outcome.details).contains(cause.name)
            assertThat(paths()).containsExactly(ROBOTS)
        }
    }

    @Test
    fun offlineFromTheStartKeepsTheOldCatalog() = runTest {
        previousCatalog()
        server.close()

        val outcome = refresher().refresh()

        assertThat(outcome).isInstanceOf(CatalogRefreshOutcome.Offline::class.java)
        assertThat(names()).containsExactly("Morgennebel Sencha", "Tautropfen Gyokuro").inOrder()
    }

    @Test
    fun cancellingKeepsTheOldCatalogAndSendsNothingMore() = runTest {
        previousCatalog()
        val refresher = refresher()

        val run = async {
            refresher.refresh { progress -> if (progress.pagesDone == 1) this@async.cancel() }
        }

        assertThat(runCatching { run.await() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(names()).containsExactly("Morgennebel Sencha", "Tautropfen Gyokuro").inOrder()
        assertThat(dao.countStaged()).isEqualTo(0)
        assertThat(dao.refreshLog().last().status).isEqualTo(CatalogRefreshStatus.CANCELLED)
        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SITEMAP_GZ, SENCHA).inOrder()
    }

    @Test
    fun aStructuralFailureKeepsTheOldCatalogAndTheSkipList() = runTest {
        previousCatalog()
        val skipsBefore = dao.skipList()
        // The breadcrumb selector broke: no page can be classified.
        product(SENCHA, "Morgennebel Sencha – 100g", "XY0100", breadcrumb = null, list = false)
        product(GYOKURO, "Tautropfen Gyokuro – 50g", "XY0200", breadcrumb = null, list = false)

        val outcome = refresher().refresh()

        assertThat(outcome).isInstanceOf(CatalogRefreshOutcome.ShopChanged::class.java)
        assertThat((outcome as CatalogRefreshOutcome.ShopChanged).details).contains("TOO_FEW_PRODUCTS")
        assertThat(names()).containsExactly("Morgennebel Sencha", "Tautropfen Gyokuro").inOrder()
        assertThat(dao.skipList()).isEqualTo(skipsBefore)
        assertThat(dao.countStaged()).isEqualTo(0)
    }

    @Test
    fun aFirstImportBelow150GreenTeasIsAStructuralFailure() = runTest {
        val outcome = refresher(sanity = SanityCheck()).refresh()

        assertThat(outcome).isInstanceOf(CatalogRefreshOutcome.ShopChanged::class.java)
        assertThat((outcome as CatalogRefreshOutcome.ShopChanged).details).contains("TOO_FEW_PRODUCTS")
        assertThat(names()).isEmpty()
        // A failed run writes no skip entry, so a broken parser can't hide teas for 90 days.
        assertThat(dao.skipList()).isEmpty()
    }

    @Test
    fun aMissingOrBrokenSitemapMeansTheShopChanged() = runTest {
        val broken = listOf(
            { routes.remove(SITEMAP) },
            { routes[SITEMAP] = { text("<html><body>Wartung</body></html>") } },
            {
                val truncatedGzip = byteArrayOf(0x1f, 0x8b.toByte(), 1, 2)
                routes[SITEMAP_GZ] = { MockResponse.Builder().body(Buffer().write(truncatedGzip)).build() }
            },
            { routes[SITEMAP_GZ] = { text(SyntheticShop.urlSet(emptyList())) } },
        )
        val shop = routes.toMap()
        for (breakIt in broken) {
            routes.clear()
            routes.putAll(shop)
            requests.clear()
            breakIt()

            val outcome = refresher().refresh()

            assertThat(outcome).isInstanceOf(CatalogRefreshOutcome.ShopChanged::class.java)
            assertThat(paths()).containsNoneOf(SENCHA, GYOKURO)
        }
    }

    @Test
    fun progressCountsPagesAndProducts() = runTest {
        val seen = mutableListOf<CatalogRefreshProgress>()

        refresher().refresh { seen += it }

        assertThat(seen.first()).isEqualTo(CatalogRefreshProgress.STARTING)
        assertThat(seen.last()).isEqualTo(CatalogRefreshProgress(pagesDone = 4, pagesTotal = 4, products = 2))
        assertThat(seen.map { it.pagesDone }).isInOrder()
    }

    private fun text(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()

    private companion object {
        const val ROBOTS = "/robots.txt"
        const val SITEMAP = "/sitemap.xml"
        const val SITEMAP_GZ = "/sitemap/synthetic-1.xml.gz"
        const val SENCHA = "/morgennebel-sencha/XY0100"
        const val GYOKURO = "/tautropfen-gyokuro/XY0200"
        const val BLACK = "/abendglut-assam/XY0300"
        const val TEAPOT = "/kanne-aus-ton/XY0500"
        const val NEW_TEA = "/fruehlingshauch-shincha/XY0600"
        const val SKIP_RECHECK_DAYS = 90L
        const val LIMITER_INTERVAL_MS = 500L

        /** The limiter's interval less the few ms between its clock read and the socket write. */
        const val MIN_GAP_MS = 480L

        val GYOKURO_PATH = listOf(
            "Tee" to "/tee/",
            "Japantee" to "/tee/japantee/",
            "Gyokuro" to "/tee/japantee/gyokuro/",
        )
        val SHINCHA_PATH = listOf(
            "Tee" to "/tee/",
            "Grüner Tee" to "/tee/gruener-tee/",
            "Shincha 2026" to "/tee/gruener-tee/shincha-2026/",
        )
    }
}
