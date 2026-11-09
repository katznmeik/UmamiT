package com.umamit.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.catalog.parser.SanityCheck
import com.umamit.core.catalog.parser.SyntheticShop
import com.umamit.core.data.SendTimes
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.database.entity.CatalogSkipEntity
import com.umamit.core.database.entity.CatalogSkipReason
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.UserSettings
import com.umamit.core.network.ShopClient
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
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
import java.util.concurrent.TimeUnit

/**
 * The shop's black teas in the first refresh after the update to v0.5.0 (#203; CAT-01, 08 §2.4
 * item 3, 05 §9, D-108) against a simulated shop: synthetic robots.txt, sitemap and product pages
 * (D-016), an in-memory `catalog.db`. v0.4.1 (parser v7) left a Darjeeling, a black set, a tea-bag
 * page and a fruit tea on the skip list as not green; the Sencha is in the catalog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogBlackRefreshTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val db = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.catalogDao()
    private val clock = FakeClock()
    private val routes = ConcurrentHashMap<String, () -> MockResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val sent = CopyOnWriteArrayList<Long>()
    private val listed = CopyOnWriteArrayList<String>()
    private val base get() = server.url("/").toString().removeSuffix("/")
    private val repository by lazy { RoomCatalogRepository(dao, CatalogPhotoFiles(folder.newFolder())) }

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
        product(SENCHA, "Morgennebel Sencha – 100g", SyntheticShop.SENCHA_PATH)
        product(DARJEELING, "Nebelhang Darjeeling – 100g", SyntheticShop.DARJEELING_PATH)
        product(BLACK_SET, "Darjeeling Probierset 3x50g", SyntheticShop.DARJEELING_PATH)
        product(TEA_BAGS, "Kontor Morgentee 20 Aufgussbeutel", SyntheticShop.TEA_BAGS_PATH)
        product(FRUIT, "Beerenwolke – 100g", SyntheticShop.FRUIT_TEA_PATH)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun product(path: String, title: String, breadcrumb: List<Pair<String, String>>) {
        if (path !in listed) listed += path
        routes[path] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    title = title,
                    sku = path.substringAfterLast('/'),
                    canonicalPath = path,
                    breadcrumb = breadcrumb,
                    brewRows = if (breadcrumb == SyntheticShop.SENCHA_PATH) {
                        SyntheticShop.BREW_ROWS
                    } else {
                        SyntheticShop.BLACK_BREW_ROWS
                    },
                ),
            )
        }
    }

    private fun refresher(realRateLimit: Boolean = false) = CatalogRefresher(
        client = ShopClient.forTesting(
            baseUrl = server.url("/"),
            socketFactory = SendTimes(sent),
            requestIntervalMillis = if (realRateLimit) null else 0,
        ),
        dao = dao,
        clock = clock,
        diagnostics = DiagnosticsLog(folder.newFolder(), maxBytes = 100_000, dispatcher = Dispatchers.IO),
        // The photos have their own tests; here they would only add requests.
        settings = FakeSettingsRepository(UserSettings(catalogDownloadPhotos = false)),
        photos = CatalogPhotoFiles(folder.newFolder()),
        sanity = SanityCheck(firstImportMinimum = 1),
    )

    private fun paths() = requests.map { it.url.encodedPath }

    private suspend fun skips() = dao.skipList().associate { it.baseKey to it.reason }

    /**
     * What v0.4.1 left behind: its last successful refresh (parser v7, before the black teas) put
     * the Darjeeling, the black set, the tea-bag page and the fruit tea on the skip list as not
     * green, a day ago.
     */
    private suspend fun skipListOfV041() {
        val now = clock.nowMs()
        dao.swap(
            skips = listOf(DARJEELING_KEY, BLACK_SET_KEY, TEA_BAGS_KEY, FRUIT_KEY)
                .map { CatalogSkipEntity(it, CatalogSkipReason.NOT_GREEN, now) },
            entry = CatalogRefreshLogEntity(
                startedAt = now,
                finishedAt = now,
                status = CatalogRefreshStatus.SUCCESS,
                newCount = 0,
                changedCount = 0,
                discontinuedCount = 0,
                flaggedCount = 0,
                photosCount = 0,
                parserVersion = PARSER_V041,
                errorSummary = null,
            ),
        )
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
    }

    @Test
    fun theFirstRefreshAfterTheUpdateReadsTheNotGreenEntriesOnceAndKeepsTheBlackTea() = runTest {
        skipListOfV041()

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 2))
        // Each page once, as any refresh: robots.txt, the sitemap and every product's page.
        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SITEMAP_GZ, SENCHA, DARJEELING, BLACK_SET, TEA_BAGS, FRUIT)
        val darjeeling = dao.tea(DARJEELING_KEY)!!
        assertThat(darjeeling.styleKey).isEqualTo("darjeeling")
        assertThat(darjeeling.teaType).isEqualTo("Darjeeling")
        assertThat(darjeeling.recipeCheckReason).isNull()
        // The set is a set now; what is still no tea of a section stays not green.
        assertThat(skips()).containsExactly(
            BLACK_SET_KEY,
            CatalogSkipReason.SET,
            TEA_BAGS_KEY,
            CatalogSkipReason.NOT_GREEN,
            FRUIT_KEY,
            CatalogSkipReason.NOT_GREEN,
        )

        // Only once: the next refresh fetches the known teas, not the fresh skip entries.
        requests.clear()
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
        assertThat(refresher().refresh()).isInstanceOf(CatalogRefreshOutcome.Success::class.java)
        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SITEMAP_GZ, SENCHA, DARJEELING)
    }

    @Test
    fun theBlackTeasArePendingUntilTheFirstSuccessfulRefresh() = runTest {
        skipListOfV041()
        assertThat(repository.observePendingKinds().first()).containsExactly(CatalogKind.BLACK)

        refresher().refresh()

        assertThat(repository.observePendingKinds().first()).isEmpty()
    }

    @Test
    fun aFailedRefreshKeepsTheSkipListAndTheHintAndTheNextOneReadsThemAgain() = runTest {
        skipListOfV041()
        routes[FRUIT] = { MockResponse.Builder().code(429).build() }

        val stopped = refresher().refresh()

        assertThat(stopped).isNotInstanceOf(CatalogRefreshOutcome.Success::class.java)
        assertThat(skips().keys).containsExactly(DARJEELING_KEY, BLACK_SET_KEY, TEA_BAGS_KEY, FRUIT_KEY)
        assertThat(dao.tea(DARJEELING_KEY)).isNull()
        assertThat(repository.observePendingKinds().first()).containsExactly(CatalogKind.BLACK)

        product(FRUIT, "Beerenwolke – 100g", SyntheticShop.FRUIT_TEA_PATH)
        requests.clear()
        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 2))
        assertThat(paths()).containsAtLeast(DARJEELING, BLACK_SET, TEA_BAGS, FRUIT)
        assertThat(skips().keys).doesNotContain(DARJEELING_KEY)
        assertThat(repository.observePendingKinds().first()).isEmpty()
    }

    @Test
    fun theReReadKeepsTheRateLimitAndTheOneConnection() = runTest {
        skipListOfV041()

        refresher(realRateLimit = true).refresh()

        assertThat(requests.map { it.connectionIndex }.distinct()).containsExactly(0)
        assertThat(sent).hasSize(requests.size)
        val gapsMs = sent.zipWithNext { a, b -> TimeUnit.NANOSECONDS.toMillis(b - a) }
        gapsMs.forEach { assertWithMessage("send gaps $gapsMs ms").that(it).isAtLeast(MIN_GAP_MS) }
    }

    private companion object {
        /** v0.4.1's parser (D-093), the last without black teas. */
        const val PARSER_V041 = 7

        /** The limiter's 500 ms (≤ 2 requests/s, rule 7) less the few ms before the socket write. */
        const val MIN_GAP_MS = 480L

        const val ROBOTS = "/robots.txt"
        const val SITEMAP = "/sitemap.xml"
        const val SITEMAP_GZ = "/sitemap/synthetic-1.xml.gz"
        const val SENCHA_KEY = "XY0100"
        const val DARJEELING_KEY = "XB0200"
        const val BLACK_SET_KEY = "XB0300"
        const val TEA_BAGS_KEY = "XB0400"
        const val FRUIT_KEY = "XF0500"
        const val SENCHA = "/morgennebel-sencha/$SENCHA_KEY"
        const val DARJEELING = "/nebelhang-darjeeling/$DARJEELING_KEY"
        const val BLACK_SET = "/darjeeling-probierset/$BLACK_SET_KEY"
        const val TEA_BAGS = "/kontor-morgentee/$TEA_BAGS_KEY"
        const val FRUIT = "/beerenwolke/$FRUIT_KEY"

        fun text(body: String) =
            MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()
    }
}
