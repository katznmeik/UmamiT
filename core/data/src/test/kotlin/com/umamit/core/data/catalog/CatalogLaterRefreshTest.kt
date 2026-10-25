package com.umamit.core.data.catalog

import android.content.Context
import android.database.sqlite.SQLiteFullException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.catalog.parser.SanityCheck
import com.umamit.core.catalog.parser.SyntheticShop
import com.umamit.core.catalog.parser.SyntheticShop.Size
import com.umamit.core.data.decodedSize
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.data.syntheticJpeg
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.network.ShopClient
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
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
 * Later refreshes against a simulated shop (#70; spec #62 seam 2; CAT-07 AC3/AC4, CAT-08, 08 §2.4,
 * 05 §4.5): MockWebServer serving a synthetic robots.txt, sitemap index, gzip url set, product
 * pages and JPEGs (D-016), an in-memory `catalog.db`, the photo folder in a temporary directory,
 * a fake clock and the collection's catalog keys. Each test runs two refreshes; every request
 * costs half a second (the real rate limit), so the shop stays small.
 *
 * The shop of the first refresh: a Sencha, a Gyokuro, a Matcha-free Sencha sold under one number
 * per size (merged, 08 §2.4 item 7), a Kabusecha the PO owns, a Bancha nobody owns, and a black
 * tea (out of scope, onto the skip list). Each green tea has its own photo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class CatalogLaterRefreshTest {
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

    /** The catalog keys the collection's teas link to (`tea.catalog_key` in `umamit.db`). */
    private val owned = MutableStateFlow(setOf(KABUSE_KEY))

    /** How often the refresh linked the seed teas (CAT-15). */
    private var seedLinks = 0

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
        product(SENCHA, "Morgennebel Sencha – 100g", photo = "sencha")
        product(GYOKURO, "Tautropfen Gyokuro – 100g", photo = "gyokuro", price = "18,00 €")
        product(SPLIT_30, "Abendrot Sencha 30g", photo = "abendrot", price = "9,90 €", grams = 30)
        product(SPLIT_100, "Abendrot Sencha – 100g", photo = "abendrot100", price = "29,00 €")
        product(KABUSE, "Schattenblatt Kabusecha – 100g", photo = "kabuse")
        product(BANCHA, "Herbstlaub Bancha – 100g", photo = "bancha")
        product(BLACK, "Abendglut Assam – 100g", photo = "assam", breadcrumb = SyntheticShop.BLACK_TEA_PATH)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    /** Lists the product page [path] in the sitemap and serves it with the photo [photo]. */
    @Suppress("LongParameterList")
    private fun product(
        path: String,
        title: String,
        photo: String,
        price: String = "12,40 €",
        grams: Int = 100,
        breadcrumb: List<Pair<String, String>> = SyntheticShop.SENCHA_PATH,
        description: String = "<p>Ein erfundener Tee.</p>",
        photoPath: String = SyntheticShop.photoPath(photo),
    ) {
        if (path !in listed) listed += path
        val sku = path.substringAfterLast('/')
        routes[path] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    title = title,
                    sku = sku,
                    canonicalPath = path,
                    breadcrumb = breadcrumb,
                    price = price,
                    unitContent = "${grams / 1000.0} kg",
                    unitReference = null,
                    photoPath = photoPath,
                    description = description,
                    brewRows = listOf("japan" to listOf("~4g/100ml", "~60°C", "~90 Sek", "~20 Sek", "-")),
                    sizes = listOf(Size("${grams}g", checked = true)),
                ),
            )
        }
        routes[photoPath.substringBefore('?')] = { jpeg(syntheticJpeg(400, 300)) }
    }

    private fun refresher() = CatalogRefresher(
        client = ShopClient.forTesting(baseUrl = server.url("/")),
        dao = dao,
        clock = clock,
        diagnostics = diagnostics,
        settings = FakeSettingsRepository(),
        photos = photos,
        sanity = SanityCheck(firstImportMinimum = 1),
        owned = OwnedCatalogKeys { owned },
        seedTeas = CatalogSeedLinker {
            seedLinks++
            emptyList()
        },
    )

    private fun paths() = requests.map { it.url.encodedPath }

    /** The first refresh, a day before the later one. */
    private suspend fun TestScope.firstRefresh() {
        val first = refresher().refresh()
        assertThat(first).isEqualTo(CatalogRefreshOutcome.Success(teas = 5, newTeas = 5))
        requests.clear()
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
    }

    @Test
    fun aLaterRefreshSendsTheRequestsOf08Section24AndReportsWhatChanged() = runTest {
        firstRefresh()
        // In the shop since: the Gyokuro's price and photo changed, the Kabusecha (owned) and the
        // Bancha left the range, a Shincha is new and its prose contradicts its grid („Rezept prüfen").
        product(
            GYOKURO,
            "Tautropfen Gyokuro – 100g",
            photo = "gyokuro",
            price = "19,50 €",
            photoPath = NEW_GYOKURO_PHOTO,
        )
        listed -= listOf(KABUSE, BANCHA)
        product(
            SHINCHA,
            "Frühlingshauch Shincha – 100g",
            photo = "shincha",
            description = "<p>Zubereitung: 4 g auf 100 ml bei 80 °C.</p>",
        )

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(
            CatalogRefreshOutcome.Success(
                teas = 4,
                newTeas = 1,
                changedTeas = 1,
                discontinuedTeas = 2,
                recipesToCheck = 1,
            ),
        )
        // 08 §2.4: robots.txt, the sitemap and its url set; the known green parents (the split
        // tea's two numbers included) and the new base number; no page on the skip list; only the
        // new and the changed photo.
        val sitemap = listOf(ROBOTS, SITEMAP, SITEMAP_GZ)
        val knownGreenParents = listOf(SENCHA, GYOKURO, SPLIT_30, SPLIT_100)
        val newBaseNumbers = listOf(SHINCHA)
        val changedPhotos = listOf(NEW_GYOKURO_PHOTO.substringBefore('?'), photoRoute("shincha"))
        assertThat(paths()).containsExactlyElementsIn(sitemap + knownGreenParents + newBaseNumbers + changedPhotos)
        assertThat(paths()).hasSize(10)
        assertThat(
            diagnostics.entries().map { it.message }.filter {
                "5 to fetch (4 known green, 1 new, 0 to check again)" in
                    it
            },
        )
            .hasSize(1)
    }

    @Test
    fun anUnchangedPhotoIsNeverFetchedAgainAndAChangedOneReplacesTheOld() = runTest {
        firstRefresh()
        val senchaPhoto = photos.photo(SENCHA_KEY).readBytes()
        product(GYOKURO, "Tautropfen Gyokuro – 100g", photo = "gyokuro", photoPath = NEW_GYOKURO_PHOTO)
        routes[NEW_GYOKURO_PHOTO.substringBefore('?')] = { jpeg(syntheticJpeg(300, 400)) }

        refresher().refresh()

        assertThat(paths().filter { it.startsWith("/media/") }).containsExactly(NEW_GYOKURO_PHOTO.substringBefore('?'))
        assertThat(photos.photo(SENCHA_KEY).readBytes()).isEqualTo(senchaPhoto)
        assertThat(decodedSize(photos.photo(GYOKURO_KEY).readBytes())).isEqualTo(300 to 400)
        val gyokuro = requireNotNull(dao.tea(GYOKURO_KEY))
        assertThat(gyokuro.photoUrl).isEqualTo("$base$NEW_GYOKURO_PHOTO")
        assertThat(gyokuro.photoFile).isEqualTo("$GYOKURO_KEY.webp")
        assertThat(dao.tea(SENCHA_KEY)?.photoFile).isEqualTo("$SENCHA_KEY.webp")
        assertThat(dao.refreshLog().last().photosCount).isEqualTo(1)
    }

    @Test
    fun aChangedPhotoThatFailsKeepsTheOldOneAndIsTriedAgainNextTime() = runTest {
        firstRefresh()
        val gyokuroPhoto = photos.photo(GYOKURO_KEY).readBytes()
        product(GYOKURO, "Tautropfen Gyokuro – 100g", photo = "gyokuro", photoPath = NEW_GYOKURO_PHOTO)
        routes.remove(NEW_GYOKURO_PHOTO.substringBefore('?'))

        refresher().refresh()

        // The old photo stays, still marked as the old URL's, so the next refresh asks again.
        assertThat(photos.photo(GYOKURO_KEY).readBytes()).isEqualTo(gyokuroPhoto)
        assertThat(dao.tea(GYOKURO_KEY)?.photoUrl).isEqualTo("$base${SyntheticShop.photoPath("gyokuro")}")
        requests.clear()
        routes[NEW_GYOKURO_PHOTO.substringBefore('?')] = { jpeg(syntheticJpeg(300, 400)) }

        refresher().refresh()

        assertThat(paths().filter { it.startsWith("/media/") }).containsExactly(NEW_GYOKURO_PHOTO.substringBefore('?'))
        assertThat(dao.tea(GYOKURO_KEY)?.photoUrl).isEqualTo("$base$NEW_GYOKURO_PHOTO")
    }

    @Test
    fun aDiscontinuedTeaIsHiddenAndKeptOnlyWhileTheCollectionLinksToIt() = runTest {
        firstRefresh()
        listed -= listOf(KABUSE, BANCHA)

        refresher().refresh()

        val repository = RoomCatalogRepository(dao, photos)
        assertThat(repository.observeCatalog().first().teas.map { it.key })
            .containsNoneOf(KABUSE_KEY, BANCHA_KEY)
        // The owned Kabusecha stays as „Nicht mehr im Sortiment", with its photo (CAT-07 AC4) …
        val kabuse = requireNotNull(repository.observeTea(KABUSE_KEY).first())
        assertThat(kabuse.discontinued).isTrue()
        assertThat(kabuse.photoPath).isEqualTo(photos.photo(KABUSE_KEY).path)
        assertThat(photos.exists(KABUSE_KEY)).isTrue()
        assertThat(kabuse.packages).isNotEmpty()
        // … the Bancha nobody owns is gone with its photos.
        assertThat(dao.tea(BANCHA_KEY)).isNull()
        assertThat(folder.root.resolve("catalog").list()?.filter { BANCHA_KEY in it }).isEmpty()
        assertThat(dao.refreshLog().last().discontinuedCount).isEqualTo(2)

        // Once the collection no longer links to it, the next refresh deletes it; it is counted once only.
        owned.value = emptySet()
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
        val outcome = refresher().refresh()

        assertThat(dao.tea(KABUSE_KEY)).isNull()
        assertThat(photos.exists(KABUSE_KEY)).isFalse()
        assertThat((outcome as CatalogRefreshOutcome.Success).discontinuedTeas).isEqualTo(0)
    }

    @Test
    fun aDiscontinuedTeaBackInTheShopIsInTheRangeAgainAndKeepsItsFirstSighting() = runTest {
        val firstSeen = clock.wall
        firstRefresh()
        listed -= KABUSE
        refresher().refresh()
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
        listed += KABUSE

        val outcome = refresher().refresh() as CatalogRefreshOutcome.Success

        val kabuse = requireNotNull(dao.tea(KABUSE_KEY))
        assertThat(kabuse.discontinued).isFalse()
        assertThat(kabuse.firstSeenAt).isEqualTo(firstSeen)
        assertThat(outcome.newTeas).isEqualTo(0)
        assertThat(outcome.changedTeas).isEqualTo(1)
    }

    @Test
    fun aKnownTeaWhosePageFailsButStaysInTheSitemapIsKeptAsItWas() = runTest {
        firstRefresh()
        val before = requireNotNull(dao.tea(BANCHA_KEY))
        routes[BANCHA] = { MockResponse.Builder().code(404).build() }

        val outcome = refresher().refresh() as CatalogRefreshOutcome.Success

        // 08 §2.4 item 5: only a tea missing from the sitemap is discontinued.
        assertThat(dao.tea(BANCHA_KEY)).isEqualTo(before)
        assertThat(dao.observePackages(BANCHA_KEY).first()).isNotEmpty()
        assertThat(photos.exists(BANCHA_KEY)).isTrue()
        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 5, newTeas = 0))
    }

    @Test
    fun onlyAShopChangeCountsAsChanged() = runTest {
        firstRefresh()
        // Only the stock of the Sencha changed: a change the PO sees in the catalog.
        routes[SENCHA] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    title = "Morgennebel Sencha – 100g",
                    sku = SENCHA_KEY,
                    canonicalPath = SENCHA,
                    price = "12,40 €",
                    unitContent = "0.1 kg",
                    unitReference = null,
                    photoPath = SyntheticShop.photoPath("sencha"),
                    description = "<p>Ein erfundener Tee.</p>",
                    stock = SyntheticShop.Stock.OUT_OF_STOCK,
                    brewRows = listOf("japan" to listOf("~4g/100ml", "~60°C", "~90 Sek", "~20 Sek", "-")),
                    sizes = listOf(Size("100g", checked = true)),
                ),
            )
        }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 5, newTeas = 0, changedTeas = 1))
        assertThat(dao.refreshLog().last().changedCount).isEqualTo(1)
    }

    @Test
    fun aTeaFlaggedBeforeIsNoNewRecipeToCheck() = runTest {
        product(SENCHA, "Morgennebel Sencha – 100g", photo = "sencha", description = PROSE_80)
        val first = refresher().refresh() as CatalogRefreshOutcome.Success
        assertThat(first.recipesToCheck).isEqualTo(1)
        assertThat(dao.tea(SENCHA_KEY)?.recipeCheckReason).isEqualTo(RecipeCheckReason.TABLE_TEXT_CONFLICT)
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
        // Now the Gyokuro gets a contradicting prose too.
        product(GYOKURO, "Tautropfen Gyokuro – 100g", photo = "gyokuro", price = "18,00 €", description = PROSE_80)

        val outcome = refresher().refresh() as CatalogRefreshOutcome.Success

        assertThat(outcome.recipesToCheck).isEqualTo(1)
        assertThat(dao.refreshLog().last().flaggedCount).isEqualTo(1)
    }

    @Test
    fun theSeedTeasAreLinkedAfterEachSuccessfulSwapOnly() = runTest {
        firstRefresh()
        assertThat(seedLinks).isEqualTo(1)
        routes[GYOKURO] = { MockResponse.Builder().code(429).build() }

        val outcome = refresher().refresh()

        assertThat(outcome).isInstanceOf(CatalogRefreshOutcome.Blocked::class.java)
        assertThat(seedLinks).isEqualTo(1)
        assertThat(dao.refreshLog().last().status).isEqualTo(CatalogRefreshStatus.FAILED)
    }

    @Test
    fun aFailingSeedLinkNeverFailsTheRefresh() = runTest {
        val refresher = CatalogRefresher(
            client = ShopClient.forTesting(baseUrl = server.url("/")),
            dao = dao,
            clock = clock,
            diagnostics = diagnostics,
            settings = FakeSettingsRepository(),
            photos = photos,
            sanity = SanityCheck(firstImportMinimum = 1),
            seedTeas = CatalogSeedLinker { throw SQLiteFullException("full") },
        )

        val outcome = refresher.refresh()

        assertThat(outcome).isInstanceOf(CatalogRefreshOutcome.Success::class.java)
        assertThat(dao.refreshLog().last().status).isEqualTo(CatalogRefreshStatus.SUCCESS)
    }

    private fun photoRoute(name: String) = SyntheticShop.photoPath(name).substringBefore('?')

    private fun text(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()

    private fun jpeg(bytes: ByteArray) =
        MockResponse.Builder().addHeader("Content-Type", "image/jpeg").body(Buffer().write(bytes)).build()

    private companion object {
        const val ROBOTS = "/robots.txt"
        const val SITEMAP = "/sitemap.xml"
        const val SITEMAP_GZ = "/sitemap/synthetic-1.xml.gz"
        const val SENCHA_KEY = "XY0100"
        const val GYOKURO_KEY = "XY0200"
        const val KABUSE_KEY = "XY0300"
        const val BANCHA_KEY = "XY0400"
        const val SENCHA = "/morgennebel-sencha/$SENCHA_KEY"
        const val GYOKURO = "/tautropfen-gyokuro/$GYOKURO_KEY"
        const val KABUSE = "/schattenblatt-kabusecha/$KABUSE_KEY"
        const val BANCHA = "/herbstlaub-bancha/$BANCHA_KEY"
        const val BLACK = "/abendglut-assam/XY0500"
        const val SHINCHA = "/fruehlingshauch-shincha/XY0600"
        const val SPLIT_30 = "/abendrot-sencha-30g/XY0868"
        const val SPLIT_100 = "/abendrot-sencha-100g/XY0869"
        const val NEW_GYOKURO_PHOTO = "/media/aa/bb/cc/1800000000/gyokuro.jpg?ts=1800000000"
        const val PROSE_80 = "<p>Zubereitung: 4 g auf 100 ml bei 80 °C.</p>"
    }
}
