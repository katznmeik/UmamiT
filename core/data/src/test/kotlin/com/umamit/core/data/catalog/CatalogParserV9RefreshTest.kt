package com.umamit.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.catalog.parser.SanityCheck
import com.umamit.core.catalog.parser.SyntheticShop
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
 * The white teas, oolongs and Pu-Erhs are parser v9's (#205; CAT-01, 08 §2.4 item 3, 05 §9, D-108):
 * a catalog an rc refreshed with parser v8 (the black teas) re-reads its not-green skip entries once
 * more. Simulated shop: synthetic robots.txt, sitemap and product pages (D-016), in-memory `catalog.db`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogParserV9RefreshTest {
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
        product(SENCHA, "Morgennebel Sencha – 100g", SyntheticShop.SENCHA_PATH, SyntheticShop.BREW_ROWS)
        product(WHITE, "Nebelgarten – 50g", SyntheticShop.WHITE_CLASSIC_PATH, SyntheticShop.WHITE_BREW_ROWS)
        product(OOLONG, "Bergnebel Oolong – 50g", SyntheticShop.OOLONG_TAIWAN_PATH, SyntheticShop.GONGFU_ROWS)
        product(SHENG, "2019 Nebelberg Bing Cha – 357g", SyntheticShop.SHENG_PATH, SyntheticShop.GONGFU_ROWS)
        product(FRUIT, "Beerenwolke – 100g", SyntheticShop.SWEETS_PATH, SyntheticShop.BLACK_BREW_ROWS)
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
        brewRows: List<Pair<String, List<String>>>,
    ) {
        if (path !in listed) listed += path
        routes[path] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    title = title,
                    sku = path.substringAfterLast('/'),
                    canonicalPath = path,
                    breadcrumb = breadcrumb,
                    brewRows = brewRows,
                ),
            )
        }
    }

    private fun refresher() = CatalogRefresher(
        client = ShopClient.forTesting(baseUrl = server.url("/"), requestIntervalMillis = 0),
        dao = dao,
        clock = clock,
        diagnostics = DiagnosticsLog(folder.newFolder(), maxBytes = 100_000, dispatcher = Dispatchers.IO),
        settings = FakeSettingsRepository(UserSettings(catalogDownloadPhotos = false)),
        photos = CatalogPhotoFiles(folder.newFolder()),
        sanity = SanityCheck(firstImportMinimum = 1),
    )

    private fun paths() = requests.map { it.url.encodedPath }

    /** An rc's last successful refresh ran parser v8: the three new kinds' teas and a fruit tea are skipped. */
    private suspend fun skipListOfParserV8() {
        val now = clock.nowMs()
        dao.swap(
            skips = listOf(WHITE_KEY, OOLONG_KEY, SHENG_KEY, FRUIT_KEY)
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
                parserVersion = PARSER_V8,
                errorSummary = null,
            ),
        )
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
    }

    @Test
    fun aCatalogOfParserV8ReReadsItsNotGreenEntriesOnceAndKeepsTheNewKinds() = runTest {
        skipListOfParserV8()
        assertThat(repository.observePendingKinds().first()).containsAtLeast(
            CatalogKind.WHITE,
            CatalogKind.OOLONG,
            CatalogKind.DARK,
        )

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 4, newTeas = 4))
        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SITEMAP_GZ, SENCHA, WHITE, OOLONG, SHENG, FRUIT)
        assertThat(dao.tea(WHITE_KEY)!!.styleKey).isEqualTo("white_generic")
        assertThat(dao.tea(OOLONG_KEY)!!.teaType).isEqualTo("Taiwan")
        val sheng = dao.tea(SHENG_KEY)!!
        assertThat(sheng.styleKey).isEqualTo("sheng")
        assertThat(sheng.teaType).isEqualTo("Sheng")
        assertThat(sheng.recipeCheckReason).isNull()
        assertThat(dao.skipList().associate { it.baseKey to it.reason })
            .containsExactly(FRUIT_KEY, CatalogSkipReason.NOT_GREEN)
        assertThat(repository.observePendingKinds().first()).isEmpty()

        // Only once: the next refresh fetches the known teas, not the fresh skip entry.
        requests.clear()
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
        assertThat(refresher().refresh()).isInstanceOf(CatalogRefreshOutcome.Success::class.java)
        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SITEMAP_GZ, SENCHA, WHITE, OOLONG, SHENG)
    }

    private companion object {
        /** The black teas' parser (#203), the last without the white teas, oolongs and Pu-Erhs. */
        const val PARSER_V8 = 8

        const val ROBOTS = "/robots.txt"
        const val SITEMAP = "/sitemap.xml"
        const val SITEMAP_GZ = "/sitemap/synthetic-1.xml.gz"
        const val WHITE_KEY = "XW0200"
        const val OOLONG_KEY = "XO0300"
        const val SHENG_KEY = "XP0400"
        const val FRUIT_KEY = "XF0500"
        const val SENCHA = "/morgennebel-sencha/XY0100"
        const val WHITE = "/nebelgarten/$WHITE_KEY"
        const val OOLONG = "/bergnebel-oolong/$OOLONG_KEY"
        const val SHENG = "/nebelberg-bing-cha/$SHENG_KEY"
        const val FRUIT = "/beerenwolke/$FRUIT_KEY"

        fun text(body: String) =
            MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()
    }
}
