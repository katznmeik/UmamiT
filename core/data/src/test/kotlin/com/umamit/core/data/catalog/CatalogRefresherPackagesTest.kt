package com.umamit.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.catalog.parser.SanityCheck
import com.umamit.core.catalog.parser.SyntheticShop
import com.umamit.core.catalog.parser.SyntheticShop.Size
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogPackageEntity
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Package sizes and prices against a simulated shop (#67; 08 §2.4 items 6–7, D-076, D-081):
 * MockWebServer serving synthetic pages and sitemaps (D-016), an in-memory `catalog.db`, photos off.
 *
 * The shop: a Sencha with a page per size in the sitemap (10, 50 and 250 g besides its own 100 g),
 * and a Matcha sold under one article number per size (30 g and 100 g). The client's request gap
 * is shortened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogRefresherPackagesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val db = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.catalogDao()
    private val settings = FakeSettingsRepository()

    private val routes = mutableMapOf<String, () -> MockResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
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
        routes[SITEMAP] = { text(SyntheticShop.urlSet(listed.map { "$base$it" })) }
        product(
            SENCHA,
            title = "Morgennebel Sencha – 100g",
            price = "17,90 €",
            content = "0.1 kg",
            sizes = listOf(Size("10g"), Size("50g"), Size("100g", checked = true), Size("250g", "3% gespart¹")),
        )
        // The size pages are in the sitemap; the refresh must never fetch them (D-076).
        listed += listOf("$SENCHA-10", "$SENCHA-50", "$SENCHA-250")
        product(MATCHA_100, title = "Abendrot Matcha – 100g", price = "29,00 €", content = "0.1 kg")
        product(MATCHA_30, title = "Abendrot Matcha 30g", price = "9,90 €", content = "0.03 kg")
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun product(path: String, title: String, price: String, content: String, sizes: List<Size>? = null) {
        listed += path
        val sku = path.substringAfterLast('/')
        val grams = (content.removeSuffix(" kg").toDouble() * 1000).toInt()
        routes[path] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    title = title,
                    sku = sku,
                    canonicalPath = path,
                    price = price,
                    unitContent = content,
                    unitReference = null,
                    sizes = sizes ?: listOf(Size("${grams}g", checked = true)),
                ),
            )
        }
    }

    private suspend fun refresh(): CatalogRefreshOutcome {
        settings.update { it.copy(catalogDownloadPhotos = false) }
        return CatalogRefresher(
            client = ShopClient.forTesting(baseUrl = server.url("/"), requestIntervalMillis = 0),
            dao = dao,
            clock = FakeClock(),
            diagnostics = DiagnosticsLog(folder.newFolder(), maxBytes = 100_000, dispatcher = Dispatchers.IO),
            settings = settings,
            photos = CatalogPhotoFiles(folder.newFolder("catalog")),
            sanity = SanityCheck(firstImportMinimum = 1),
        ).refresh()
    }

    @Test
    fun eachTeaIsOneCatalogEntryWithAllItsSizes() = runTest {
        val outcome = refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 2))
        assertThat(dao.keys()).containsExactly("XY0100", "XY0868")
        // Only the pages of the two base numbers: no size page (D-076).
        assertThat(requests.map { it.url.encodedPath }).containsExactly(ROBOTS, SITEMAP, SENCHA, MATCHA_100, MATCHA_30)
        assertThat(dao.observePackages("XY0100").first()).containsExactly(
            CatalogPackageEntity("XY0100", "XY0100-10", "10 g", 10.0, null, false, null, "$base$SENCHA-10", null),
            CatalogPackageEntity("XY0100", "XY0100-50", "50 g", 50.0, null, false, null, "$base$SENCHA-50", null),
            CatalogPackageEntity("XY0100", "XY0100", "100 g", 100.0, 1790, false, null, "$base$SENCHA", true),
            // SCR-23's example: 17,90 € × 2,5 × 0,97 ≈ 43,41 €.
            CatalogPackageEntity(
                "XY0100",
                "XY0100-250",
                "250 g · 3 % gespart",
                250.0,
                4341,
                true,
                3,
                "$base$SENCHA-250",
                null,
            ),
        ).inOrder()
    }

    @Test
    fun aTeaSplitIntoOneNumberPerSizeIsOneEntryUnderTheLowestNumber() = runTest {
        refresh()

        val matcha = requireNotNull(dao.tea("XY0868"))
        assertThat(matcha.name).isEqualTo("Abendrot Matcha 30g")
        assertThat(matcha.productUrl).isEqualTo("$base$MATCHA_30")
        assertThat(dao.tea("XY0869")).isNull()
        assertThat(dao.observePackages("XY0868").first()).containsExactly(
            CatalogPackageEntity("XY0868", "XY0868", "30 g", 30.0, 990, false, null, "$base$MATCHA_30", true),
            CatalogPackageEntity("XY0868", "XY0869", "100 g", 100.0, 2900, false, null, "$base$MATCHA_100", true),
        ).inOrder()
    }

    @Test
    fun theDetailShowsThePackagesAndTheListThePricePer100g() = runTest {
        refresh()
        val repository = RoomCatalogRepository(dao, CatalogPhotoFiles(folder.newFolder("photos")))

        val list = repository.observeCatalog().first().teas
        assertThat(list.map { it.key to it.basePriceCentsPer100g }).containsExactly(
            "XY0868" to 3300L, // 9,90 € for 30 g
            "XY0100" to 1790L,
        )
        val detail = requireNotNull(repository.observeTea("XY0100").first())
        assertThat(detail.packages.map { it.grams to it.priceCents }).containsExactly(
            10.0 to null,
            50.0 to null,
            100.0 to 1790L,
            250.0 to 4341L,
        ).inOrder()
    }

    private fun text(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()

    private companion object {
        const val ROBOTS = "/robots.txt"
        const val SITEMAP = "/sitemap.xml"
        const val SENCHA = "/morgennebel-sencha/XY0100"
        const val MATCHA_30 = "/abendrot-matcha-30g/XY0868"
        const val MATCHA_100 = "/abendrot-matcha-100g/XY0869"
    }
}
