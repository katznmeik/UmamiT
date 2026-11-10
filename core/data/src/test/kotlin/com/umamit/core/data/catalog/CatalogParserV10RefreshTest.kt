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
 * The Rotbusch, Früchtetee and Chai are parser v10's (#206; CAT-01, 08 §2.4 item 3, 05 §9, D-108): a catalog
 * an rc refreshed with parser v9 (the white teas, oolongs and Pu-Erhs) re-reads its not-green skip entries once
 * more. Simulated shop: synthetic robots.txt, sitemap and product pages (D-016), in-memory `catalog.db`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogParserV10RefreshTest {
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
        product(ROOIBOS, "Sonnenfeld Rotbusch – 100g", SyntheticShop.ROOIBOS_CLASSIC_PATH, INFUSION)
        product(FRUIT, "Beerenwolke – 100g", SyntheticShop.FRUIT_CLASSIC_PATH, INFUSION)
        product(CHAI, "Rotbusch Chai Savanne – 100g", SyntheticShop.ROOIBOS_FLAVOURED_PATH, INFUSION)
        product(SWEETS, "Zimtstern – 100g", SyntheticShop.SWEETS_PATH, emptyList())
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

    /** An rc's last successful refresh ran parser v9: the three new kinds' teas and a sweet are skipped. */
    private suspend fun skipListOfParserV9() {
        val now = clock.nowMs()
        dao.swap(
            skips = listOf(ROOIBOS_KEY, FRUIT_KEY, CHAI_KEY, SWEETS_KEY)
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
                parserVersion = PARSER_V9,
                errorSummary = null,
            ),
        )
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
    }

    @Test
    fun aCatalogOfParserV9ReReadsItsNotGreenEntriesOnceAndKeepsTheNewKinds() = runTest {
        skipListOfParserV9()
        assertThat(repository.observePendingKinds().first())
            .containsExactly(CatalogKind.ROOIBOS, CatalogKind.FRUIT, CatalogKind.CHAI)

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 4, newTeas = 4))
        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SITEMAP_GZ, SENCHA, ROOIBOS, FRUIT, CHAI, SWEETS)
        assertThat(dao.tea(ROOIBOS_KEY)!!.styleKey).isEqualTo("rooibos")
        assertThat(dao.tea(FRUIT_KEY)!!.styleKey).isEqualTo("fruit")
        val chai = dao.tea(CHAI_KEY)!!
        assertThat(chai.styleKey).isEqualTo("chai")
        assertThat(chai.teaType).isEqualTo("Rotbusch")
        assertThat(chai.recipeCheckReason).isNull()
        assertThat(dao.skipList().associate { it.baseKey to it.reason })
            .containsExactly(SWEETS_KEY, CatalogSkipReason.NOT_GREEN)
        assertThat(repository.observePendingKinds().first()).isEmpty()

        // Only once: the next refresh fetches the known teas, not the fresh skip entry.
        requests.clear()
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
        assertThat(refresher().refresh()).isInstanceOf(CatalogRefreshOutcome.Success::class.java)
        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SITEMAP_GZ, SENCHA, ROOIBOS, FRUIT, CHAI)
    }

    private companion object {
        /** The white teas', oolongs' and Pu-Erhs' parser (#205), the last before the Rotbusch, Früchtetee, Chai. */
        const val PARSER_V9 = 9
        val INFUSION = SyntheticShop.INFUSION_BREW_ROWS

        const val ROBOTS = "/robots.txt"
        const val SITEMAP = "/sitemap.xml"
        const val SITEMAP_GZ = "/sitemap/synthetic-1.xml.gz"
        const val ROOIBOS_KEY = "XR0200"
        const val FRUIT_KEY = "XF0300"
        const val CHAI_KEY = "XR0400"
        const val SWEETS_KEY = "XZ0500"
        const val SENCHA = "/morgennebel-sencha/XY0100"
        const val ROOIBOS = "/sonnenfeld-rotbusch/$ROOIBOS_KEY"
        const val FRUIT = "/beerenwolke/$FRUIT_KEY"
        const val CHAI = "/rotbusch-chai-savanne/$CHAI_KEY"
        const val SWEETS = "/zimtstern/$SWEETS_KEY"

        fun text(body: String) =
            MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()
    }
}
