package com.umamit.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.catalog.parser.SanityCheck
import com.umamit.core.catalog.parser.SyntheticShop
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.data.syntheticJpeg
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.database.entity.CatalogSkipEntity
import com.umamit.core.database.entity.CatalogSkipReason
import com.umamit.core.network.ShopClient
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
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
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The shop's Kräutertees in the refresh (v0.2.1, #89; CAT-01, 08 §2.4 item 3, D-093) against a
 * simulated shop (spec #81 seam 4): synthetic robots.txt, sitemap and product pages (D-016), an
 * in-memory `catalog.db`. The shop: a Sencha, a Kräutertee, a fruit tea and a black tea (both out
 * of scope) and a set of Kräutertees (a set).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class CatalogHerbalRefreshTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val db = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.catalogDao()
    private val clock = FakeClock()
    private lateinit var photos: CatalogPhotoFiles
    private lateinit var diagnostics: DiagnosticsLog
    private val routes = mutableMapOf<String, () -> MockResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val listed = mutableListOf<String>()
    private val base get() = server.url("/").toString().removeSuffix("/")

    @Before
    fun setUp() {
        photos = CatalogPhotoFiles(folder.newFolder("catalog"))
        diagnostics = DiagnosticsLog(folder.newFolder(), maxBytes = 100_000, dispatcher = Dispatchers.IO)
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
        product(
            HERBAL,
            "Wiesenzauber – 100g",
            SyntheticShop.HERBAL_PATH,
            brewRows = listOf("westlich" to listOf("~12g/L", "100°C", "~8 Min")),
        )
        product(FRUIT, "Beerenwolke – 100g", SyntheticShop.FRUIT_TEA_PATH)
        product(BLACK, "Abendglut Assam – 100g", SyntheticShop.BLACK_TEA_PATH)
        product(HERBAL_SET, "Kräuter Probierset", SyntheticShop.HERBAL_PATH)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun product(
        path: String,
        title: String,
        breadcrumb: List<Pair<String, String>>,
        brewRows: List<Pair<String, List<String>>> = SyntheticShop.BREW_ROWS,
    ) {
        if (path !in listed) listed += path
        val sku = path.substringAfterLast('/')
        val photo = SyntheticShop.photoPath(sku.lowercase())
        routes[path] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    title = title,
                    sku = sku,
                    canonicalPath = path,
                    breadcrumb = breadcrumb,
                    photoPath = photo,
                    brewRows = brewRows,
                ),
            )
        }
        routes[photo.substringBefore('?')] = {
            MockResponse.Builder().addHeader("Content-Type", "image/jpeg")
                .body(Buffer().write(syntheticJpeg(400, 300))).build()
        }
    }

    private fun refresher() = CatalogRefresher(
        client = ShopClient.forTesting(baseUrl = server.url("/"), requestIntervalMillis = 0),
        dao = dao,
        clock = clock,
        diagnostics = diagnostics,
        settings = FakeSettingsRepository(),
        photos = photos,
        sanity = SanityCheck(firstImportMinimum = 1),
    )

    private fun paths() = requests.map { it.url.encodedPath }

    @Test
    fun theRefreshStoresTheKraeuterteesAsHerbalNextToTheGreenTeas() = runTest {
        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 2))
        val herbal = dao.tea(HERBAL_KEY)!!
        assertThat(herbal.styleKey).isEqualTo("herbal")
        assertThat(herbal.teaType).isEqualTo("Wiesenmischungen")
        assertThat(herbal.recipeCheckReason).isNull()
        assertThat(dao.tea(SENCHA_KEY)!!.styleKey).isEqualTo("sencha")
        assertThat(photos.exists(HERBAL_KEY)).isTrue()
        assertThat(dao.skipList().associate { it.baseKey to it.reason }).containsExactly(
            FRUIT_KEY,
            CatalogSkipReason.NOT_GREEN,
            BLACK_KEY,
            CatalogSkipReason.NOT_GREEN,
            HERBAL_SET_KEY,
            CatalogSkipReason.SET,
        )
    }

    /**
     * What v0.2.0 left behind (parser v6, before Kräutertees): its last successful refresh put the
     * Kräutertee, the fruit tea and the black tea on the skip list as not green and the set as a
     * set, a day ago (05 §9, D-093 Q12).
     */
    private suspend fun skipListOfV020() {
        val now = clock.nowMs()
        dao.swap(
            skips = listOf(
                CatalogSkipEntity(HERBAL_KEY, CatalogSkipReason.NOT_GREEN, now),
                CatalogSkipEntity(FRUIT_KEY, CatalogSkipReason.NOT_GREEN, now),
                CatalogSkipEntity(BLACK_KEY, CatalogSkipReason.NOT_GREEN, now),
                CatalogSkipEntity(HERBAL_SET_KEY, CatalogSkipReason.SET, now),
            ),
            entry = CatalogRefreshLogEntity(
                startedAt = now,
                finishedAt = now,
                status = CatalogRefreshStatus.SUCCESS,
                newCount = 0,
                changedCount = 0,
                discontinuedCount = 0,
                flaggedCount = 0,
                photosCount = 0,
                parserVersion = PARSER_BEFORE_HERBAL,
                errorSummary = null,
            ),
        )
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
    }

    @Test
    fun theFirstRefreshAfterTheUpdateClassifiesTheNotGreenSkipEntriesOnceAgainAndOnlyOnce() = runTest {
        skipListOfV020()

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 2))
        // The not-green entries are fetched again; the set stays skipped.
        assertThat(paths()).containsAtLeast(HERBAL, FRUIT, BLACK)
        assertThat(paths()).doesNotContain(HERBAL_SET)
        assertThat(dao.tea(HERBAL_KEY)!!.styleKey).isEqualTo("herbal")
        assertThat(dao.skipList().associate { it.baseKey to it.reason }).containsExactly(
            FRUIT_KEY,
            CatalogSkipReason.NOT_GREEN,
            BLACK_KEY,
            CatalogSkipReason.NOT_GREEN,
            HERBAL_SET_KEY,
            CatalogSkipReason.SET,
        )

        requests.clear()
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
        val later = refresher().refresh()

        assertThat(later).isInstanceOf(CatalogRefreshOutcome.Success::class.java)
        assertThat(paths()).containsAtLeast(SENCHA, HERBAL)
        assertThat(paths()).containsNoneOf(FRUIT, BLACK, HERBAL_SET)
    }

    @Test
    fun aRefreshThatStopsKeepsTheOldSkipListAndTheNextOneClassifiesAgain() = runTest {
        skipListOfV020()
        routes[BLACK] = { MockResponse.Builder().code(429).build() }

        val stopped = refresher().refresh()

        assertThat(stopped).isNotInstanceOf(CatalogRefreshOutcome.Success::class.java)
        assertThat(dao.skipList().map { it.baseKey }).contains(HERBAL_KEY)
        assertThat(dao.tea(HERBAL_KEY)).isNull()

        product(BLACK, "Abendglut Assam – 100g", SyntheticShop.BLACK_TEA_PATH)
        requests.clear()
        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 2))
        assertThat(paths()).containsAtLeast(HERBAL, FRUIT, BLACK)
        assertThat(dao.skipList().map { it.baseKey }).doesNotContain(HERBAL_KEY)
    }

    private companion object {
        /** v0.2.0's parser (D-091), the last without Kräutertees. */
        const val PARSER_BEFORE_HERBAL = 6

        const val ROBOTS = "/robots.txt"
        const val SITEMAP = "/sitemap.xml"
        const val SITEMAP_GZ = "/sitemap/synthetic-1.xml.gz"
        const val SENCHA_KEY = "XY0100"
        const val HERBAL_KEY = "XK0200"
        const val FRUIT_KEY = "XF0300"
        const val BLACK_KEY = "XB0400"
        const val HERBAL_SET_KEY = "XK0500"
        const val SENCHA = "/morgennebel-sencha/$SENCHA_KEY"
        const val HERBAL = "/wiesenzauber/$HERBAL_KEY"
        const val FRUIT = "/beerenwolke/$FRUIT_KEY"
        const val BLACK = "/abendglut-assam/$BLACK_KEY"
        const val HERBAL_SET = "/kraeuter-probierset/$HERBAL_SET_KEY"

        fun text(body: String) =
            MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()
    }
}
