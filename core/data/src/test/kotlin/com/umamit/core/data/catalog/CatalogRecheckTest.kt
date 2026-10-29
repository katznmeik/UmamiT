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
import com.umamit.core.data.syntheticJpeg
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.database.entity.CatalogSkipReason
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
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * The re-check of known teas missing from the sitemap (v0.2.1, #97; CAT-16, CAT-07 AC4, 08 §2.4
 * item 5, §2.8, D-094) against a simulated shop (spec #81 seam 4): MockWebServer serving a
 * synthetic robots.txt, sitemap and product pages (D-016), an in-memory `catalog.db`, a fake
 * clock and the collection's catalog keys.
 *
 * The shop of the first refresh: a Sencha, a Gyokuro and a Sencha that sells out before the
 * second refresh („Nebelhang Sencha"); the shop then leaves it out of its sitemap, as it does
 * with sold-out products (08 §2.2). Each test answers its page in one way and runs the second
 * refresh. The client's request gap is shortened (the rate has its own test), so no test waits
 * on the wall clock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class CatalogRecheckTest {
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
    private val owned = MutableStateFlow(emptySet<String>())

    /** What the synthetic shop answers per path; unknown paths are 404 (read on the server's thread). */
    private val routes = ConcurrentHashMap<String, () -> MockResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val listed = CopyOnWriteArrayList<String>()
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
        product(SENCHA, "Morgennebel Sencha – 100g")
        product(GYOKURO, "Tautropfen Gyokuro – 100g", breadcrumb = GYOKURO_PATH)
        product(SOLD_OUT, "Nebelhang Sencha – 100g")
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    /** Lists the product page [path] in the sitemap and serves it, with its photo. */
    @Suppress("LongParameterList")
    private fun product(
        path: String,
        title: String,
        breadcrumb: List<Pair<String, String>> = SyntheticShop.SENCHA_PATH,
        stock: Stock = Stock.IN_STOCK,
        canonicalPath: String = path,
        list: Boolean = true,
    ) {
        if (list && path !in listed) listed += path
        val sku = path.substringAfterLast('/')
        val photo = SyntheticShop.photoPath(sku.lowercase())
        routes[path] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    title = title,
                    sku = sku,
                    canonicalPath = canonicalPath,
                    breadcrumb = breadcrumb,
                    stock = stock,
                    photoPath = photo,
                    sizes = listOf(
                        SyntheticShop.Size("100g", checked = true),
                        SyntheticShop.Size("250g", savings = "3% gespart¹"),
                    ),
                ),
            )
        }
        routes[photo.substringBefore('?')] = { jpeg() }
    }

    private fun refresher() = CatalogRefresher(
        client = ShopClient.forTesting(baseUrl = server.url("/"), requestIntervalMillis = 0),
        dao = dao,
        clock = clock,
        diagnostics = diagnostics,
        settings = FakeSettingsRepository(),
        photos = photos,
        sanity = SanityCheck(firstImportMinimum = 1),
        owned = OwnedCatalogKeys { owned },
    )

    private fun paths() = requests.map { it.url.encodedPath }

    /** The first refresh with all [teas] in the sitemap, then the shop leaves the sold-out one out a day later. */
    private suspend fun TestScope.soldOutSinceTheFirstRefresh(teas: Int = 3) {
        assertThat(refresher().refresh()).isEqualTo(CatalogRefreshOutcome.Success(teas = teas, newTeas = teas))
        requests.clear()
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
        listed -= SOLD_OUT
    }

    @Test
    fun aSoldOutPageKeepsTheTeaAsNotAvailable() = runTest {
        soldOutSinceTheFirstRefresh()
        product(SOLD_OUT, "Nebelhang Sencha – 100g", stock = Stock.OUT_OF_STOCK, list = false)

        val outcome = refresher().refresh()

        // Still in the range, never „nicht mehr im Sortiment"; its stock changed, so it is „geändert".
        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 3, newTeas = 0, changedTeas = 1))
        val tea = requireNotNull(dao.tea(SOLD_OUT_KEY))
        assertThat(tea.discontinued).isFalse()
        assertThat(tea.available).isFalse()
        val listing = RoomCatalogRepository(dao, photos).observeCatalog().first()
        assertThat(listing.teas.map { it.key }).contains(SOLD_OUT_KEY)
        // Its own page once, after the pages of the sitemap; its unchanged photo is not fetched again.
        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SITEMAP_GZ, SENCHA, GYOKURO, SOLD_OUT).inOrder()
    }

    @Test
    fun anInStockPageKeepsTheTeaAsAvailable() = runTest {
        soldOutSinceTheFirstRefresh()

        val outcome = refresher().refresh()

        // The page shows what it showed before: nothing to report.
        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 3, newTeas = 0))
        val tea = requireNotNull(dao.tea(SOLD_OUT_KEY))
        assertThat(tea.discontinued).isFalse()
        assertThat(tea.available).isTrue()
        assertThat(paths().count { it == SOLD_OUT }).isEqualTo(1)
    }

    @Test
    fun http404LeavesTheRange() = runTest {
        soldOutSinceTheFirstRefresh()
        routes[SOLD_OUT] = { MockResponse.Builder().code(404).build() }

        val outcome = refresher().refresh()

        // Nobody owns it: deleted with its photos (CAT-07 AC4).
        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 0, discontinuedTeas = 1))
        assertThat(dao.tea(SOLD_OUT_KEY)).isNull()
        assertThat(photos.exists(SOLD_OUT_KEY)).isFalse()
        assertThat(paths().count { it == SOLD_OUT }).isEqualTo(1)
    }

    @Test
    fun http410LeavesTheRangeAndAnOwnedTeaStaysAsNichtMehrImSortiment() = runTest {
        owned.value = setOf(SOLD_OUT_KEY)
        soldOutSinceTheFirstRefresh()
        routes[SOLD_OUT] = { MockResponse.Builder().code(410).build() }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 0, discontinuedTeas = 1))
        val tea = requireNotNull(dao.tea(SOLD_OUT_KEY))
        assertThat(tea.discontinued).isTrue()
        assertThat(photos.exists(SOLD_OUT_KEY)).isTrue()
        assertThat(RoomCatalogRepository(dao, photos).observeCatalog().first().teas.map { it.key })
            .doesNotContain(SOLD_OUT_KEY)
    }

    @Test
    fun aRedirectAwayFromTheProductLeavesTheRangeWithoutBeingFollowed() = runTest {
        soldOutSinceTheFirstRefresh()
        routes[SOLD_OUT] = {
            MockResponse.Builder().code(301).addHeader("Location", "$base$SENCHA_CATEGORY").build()
        }
        routes[SENCHA_CATEGORY] = { text("<html><body>Kategorie</body></html>") }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 0, discontinuedTeas = 1))
        assertThat(dao.tea(SOLD_OUT_KEY)).isNull()
        assertThat(paths()).doesNotContain(SENCHA_CATEGORY)
    }

    @Test
    fun aRedirectToAnotherHostLeavesTheRangeWithoutBeingFollowed() = runTest {
        soldOutSinceTheFirstRefresh()
        routes[SOLD_OUT] = {
            MockResponse.Builder().code(302).addHeader("Location", "https://elsewhere.example$SOLD_OUT").build()
        }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 0, discontinuedTeas = 1))
        assertThat(paths().count { it == SOLD_OUT }).isEqualTo(1)
    }

    @Test
    fun aRedirectToThisProductsOtherPageKeepsTheTeaAsItWasWithoutBeingFollowed() = runTest {
        // The same article number under a new slug, or one of its sizes: still this product, but
        // the redirect is never followed, so its page isn't read this once (CAT-16 AC3).
        soldOutSinceTheFirstRefresh()
        val before = requireNotNull(dao.tea(SOLD_OUT_KEY))
        for (location in listOf("/nebelhang-sencha-neu/$SOLD_OUT_KEY", "/nebelhang-sencha/$SOLD_OUT_KEY-250")) {
            routes[SOLD_OUT] = { MockResponse.Builder().code(308).addHeader("Location", location).build() }
            requests.clear()

            val outcome = refresher().refresh()

            assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 3, newTeas = 0))
            assertThat(dao.tea(SOLD_OUT_KEY)).isEqualTo(before)
            assertThat(paths()).doesNotContain(location)
        }
    }

    @Test
    fun aRedirectWithoutLocationKeepsTheTeaAsItWas() = runTest {
        soldOutSinceTheFirstRefresh()
        val before = requireNotNull(dao.tea(SOLD_OUT_KEY))
        routes[SOLD_OUT] = { MockResponse.Builder().code(302).build() }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 3, newTeas = 0))
        assertThat(dao.tea(SOLD_OUT_KEY)).isEqualTo(before)
    }

    @Test
    fun aBreadcrumbOutOfScopeLeavesTheRange() = runTest {
        soldOutSinceTheFirstRefresh()
        product(SOLD_OUT, "Nebelhang Assam – 100g", breadcrumb = SyntheticShop.BLACK_TEA_PATH, list = false)

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 0, discontinuedTeas = 1))
        assertThat(dao.tea(SOLD_OUT_KEY)).isNull()
        // Like any page out of scope, it goes onto the skip list (05 §4.2a).
        assertThat(dao.skipList().map { it.baseKey to it.reason })
            .containsExactly(SOLD_OUT_KEY to CatalogSkipReason.NOT_GREEN)
    }

    @Test
    fun aKraeuterteeMissingFromTheSitemapStaysToo() = runTest {
        product(HERBAL, "Wiesenzauber – 100g", breadcrumb = SyntheticShop.HERBAL_PATH)
        assertThat(refresher().refresh()).isEqualTo(CatalogRefreshOutcome.Success(teas = 4, newTeas = 4))
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
        listed -= HERBAL
        product(
            HERBAL,
            "Wiesenzauber – 100g",
            breadcrumb = SyntheticShop.HERBAL_PATH,
            stock = Stock.OUT_OF_STOCK,
            list = false,
        )

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 4, newTeas = 0, changedTeas = 1))
        val herbal = requireNotNull(dao.tea(HERBAL_KEY))
        assertThat(herbal.available).isFalse()
        assertThat(herbal.styleKey).isEqualTo("herbal")
    }

    @Test
    fun aPageRobotsTxtDisallowsKeepsTheTeaAsItWasAndIsNeverFetched() = runTest {
        soldOutSinceTheFirstRefresh()
        val before = requireNotNull(dao.tea(SOLD_OUT_KEY))
        routes[ROBOTS] = { text(SyntheticShop.robotsTxt("Disallow: /nebelhang-sencha/")) }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 3, newTeas = 0))
        assertThat(paths()).doesNotContain(SOLD_OUT)
        assertThat(dao.tea(SOLD_OUT_KEY)).isEqualTo(before)
        assertThat(photos.exists(SOLD_OUT_KEY)).isTrue()
    }

    @Test
    fun aPageOverTheBodyLimitKeepsTheTeaAsItWas() = runTest {
        soldOutSinceTheFirstRefresh()
        val before = requireNotNull(dao.tea(SOLD_OUT_KEY))
        routes[SOLD_OUT] = { text("<html>" + "x".repeat(OVER_PAGE_LIMIT) + "</html>") }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 3, newTeas = 0))
        assertThat(dao.tea(SOLD_OUT_KEY)).isEqualTo(before)
    }

    @Test
    fun anotherHttp4xxKeepsTheTeaAsItWas() = runTest {
        soldOutSinceTheFirstRefresh()
        val before = requireNotNull(dao.tea(SOLD_OUT_KEY))
        routes[SOLD_OUT] = { MockResponse.Builder().code(400).build() }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 3, newTeas = 0))
        assertThat(dao.tea(SOLD_OUT_KEY)).isEqualTo(before)
    }

    @Test
    fun aPageShowingAnotherProductKeepsTheTeaAsItWas() = runTest {
        // The canonical names another article: not this product's page, so not read this once.
        soldOutSinceTheFirstRefresh()
        val before = requireNotNull(dao.tea(SOLD_OUT_KEY))
        product(SOLD_OUT, "Morgennebel Sencha – 100g", canonicalPath = SENCHA, list = false)

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 3, newTeas = 0))
        assertThat(dao.tea(SOLD_OUT_KEY)).isEqualTo(before)
    }

    @Test
    fun aPageWithoutProductKeepsTheTeaAsItWasAndCountsInTheSanityCheck() = runTest {
        // A fourth tea: one page without product of four stays under the 30 % threshold (08 §2.9).
        product(KABUSE, "Schattenblatt Kabusecha – 100g")
        soldOutSinceTheFirstRefresh(teas = 4)
        val before = requireNotNull(dao.tea(SOLD_OUT_KEY))
        routes[SOLD_OUT] = { text("<html><body><p>Wartung</p></body></html>") }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 4, newTeas = 0))
        assertThat(dao.tea(SOLD_OUT_KEY)).isEqualTo(before)

        // Like any page the parser can't read, it counts as defective: next to two teas, one is too
        // many, and the old catalog stays.
        listed -= KABUSE
        routes[KABUSE] = { MockResponse.Builder().code(404).build() }

        val broken = refresher().refresh()

        assertThat(broken).isInstanceOf(CatalogRefreshOutcome.ShopChanged::class.java)
        assertThat((broken as CatalogRefreshOutcome.ShopChanged).details).contains("REQUIRED_FIELDS_MISSING")
        assertThat(dao.tea(SOLD_OUT_KEY)).isEqualTo(before)
        assertThat(dao.tea(KABUSE_KEY)).isNotNull()
    }

    // One test per stop (D-090): each keeps the catalog of the first refresh.
    @Test
    fun aServerErrorStopsTheRefreshAndKeepsTheOldCatalog() = runTest {
        stopOnTheUnlistedPage(CatalogRefreshOutcome.ShopUnavailable::class.java) {
            MockResponse.Builder().code(503).build()
        }
    }

    @Test
    fun http429StopsTheRefreshAndKeepsTheOldCatalog() = runTest {
        stopOnTheUnlistedPage(CatalogRefreshOutcome.Blocked::class.java) {
            MockResponse.Builder().code(429).build()
        }
    }

    @Test
    fun aChallengeStopsTheRefreshAndKeepsTheOldCatalog() = runTest {
        stopOnTheUnlistedPage(CatalogRefreshOutcome.Blocked::class.java) {
            MockResponse.Builder().code(403).addHeader("cf-mitigated", "challenge").build()
        }
    }

    @Test
    fun aRetryAfterStopsTheRefreshAndKeepsTheOldCatalog() = runTest {
        stopOnTheUnlistedPage(CatalogRefreshOutcome.Blocked::class.java) {
            MockResponse.Builder().code(404).addHeader("Retry-After", "120").build()
        }
    }

    @Test
    fun aNetworkErrorAfterItsRetriesStopsTheRefreshAndKeepsTheOldCatalog() = runTest {
        // Every attempt (the first and the refresh's three retries, D-076) loses its connection.
        stopOnTheUnlistedPage(CatalogRefreshOutcome.Offline::class.java) {
            MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()
        }
    }

    /**
     * The shop answers [answer] on the unlisted tea's page: the run ends as [expected] with the
     * old catalog and skip list, nothing staged, and nothing asked for after the stop.
     */
    private suspend fun TestScope.stopOnTheUnlistedPage(
        expected: Class<out CatalogRefreshOutcome>,
        answer: () -> MockResponse,
    ) {
        soldOutSinceTheFirstRefresh()
        val before = dao.observeList().first()
        val skipsBefore = dao.skipList()
        // The Gyokuro's photo changed: it would be fetched after the pages.
        routes[GYOKURO] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    title = "Tautropfen Gyokuro – 100g",
                    sku = GYOKURO_KEY,
                    canonicalPath = GYOKURO,
                    breadcrumb = GYOKURO_PATH,
                    photoPath = NEW_PHOTO,
                ),
            )
        }
        routes[NEW_PHOTO.substringBefore('?')] = { jpeg() }
        routes[SOLD_OUT] = answer

        val outcome = refresher().refresh()

        assertThat(outcome).isInstanceOf(expected)
        assertThat(dao.observeList().first()).isEqualTo(before)
        assertThat(dao.skipList()).isEqualTo(skipsBefore)
        assertThat(dao.countStaged()).isEqualTo(0)
        assertThat(dao.refreshLog().last().status).isEqualTo(CatalogRefreshStatus.FAILED)
        assertThat(paths().last()).isEqualTo(SOLD_OUT)
        assertThat(paths()).doesNotContain(NEW_PHOTO.substringBefore('?'))
    }

    /**
     * The owned tea is „Nicht mehr im Sortiment", as v0.2.0 left a tea that had only sold out
     * (CAT-07 AC4): out of the sitemap, kept for the collection. Here a refresh whose page answer
     * was 404 got it there; the next one runs a day later.
     */
    private suspend fun TestScope.ownedTeaNichtMehrImSortiment() {
        owned.value = setOf(SOLD_OUT_KEY)
        soldOutSinceTheFirstRefresh()
        routes[SOLD_OUT] = { MockResponse.Builder().code(404).build() }
        assertThat(refresher().refresh())
            .isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 0, discontinuedTeas = 1))
        assertThat(requireNotNull(dao.tea(SOLD_OUT_KEY)).discontinued).isTrue()
        requests.clear()
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
    }

    @Test
    fun anOwnedTeaNichtMehrImSortimentWhosePageIsSoldOutIsBackInTheRange() = runTest {
        ownedTeaNichtMehrImSortiment()
        product(SOLD_OUT, "Nebelhang Sencha – 100g", stock = Stock.OUT_OF_STOCK, list = false)

        val outcome = refresher().refresh()

        // Back in the range counts as „geändert" (CAT-07 AC3), not as new.
        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 3, newTeas = 0, changedTeas = 1))
        assertThat(paths().count { it == SOLD_OUT }).isEqualTo(1)
        val tea = requireNotNull(dao.tea(SOLD_OUT_KEY))
        assertThat(tea.discontinued).isFalse()
        assertThat(tea.available).isFalse()
        assertThat(RoomCatalogRepository(dao, photos).observeCatalog().first().teas.map { it.key })
            .contains(SOLD_OUT_KEY)
    }

    @Test
    fun anOwnedTeaNichtMehrImSortimentWhosePageIsGoneStaysSoAndIsNotCountedAgain() = runTest {
        ownedTeaNichtMehrImSortiment()
        val before = requireNotNull(dao.tea(SOLD_OUT_KEY))

        val outcome = refresher().refresh()

        // Checked once more; still 404: no change, and no second „nicht mehr im Sortiment".
        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 0))
        assertThat(paths().count { it == SOLD_OUT }).isEqualTo(1)
        assertThat(dao.tea(SOLD_OUT_KEY)).isEqualTo(before)
        assertThat(photos.exists(SOLD_OUT_KEY)).isTrue()
    }

    @Test
    fun anOwnedTeaNichtMehrImSortimentWhosePageCantBeReadStaysAsItWas() = runTest {
        ownedTeaNichtMehrImSortiment()
        val before = requireNotNull(dao.tea(SOLD_OUT_KEY))
        routes[SOLD_OUT] = { MockResponse.Builder().code(302).build() }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 0))
        assertThat(dao.tea(SOLD_OUT_KEY)).isEqualTo(before)
    }

    @Test
    fun aTeaKeptUnreadIsCheckedAgainOnTheNextRefresh() = runTest {
        soldOutSinceTheFirstRefresh()
        routes[SOLD_OUT] = { MockResponse.Builder().code(302).build() }
        refresher().refresh()
        requests.clear()
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
        product(SOLD_OUT, "Nebelhang Sencha – 100g", stock = Stock.OUT_OF_STOCK, list = false)

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 3, newTeas = 0, changedTeas = 1))
        assertThat(paths()).contains(SOLD_OUT)
        assertThat(requireNotNull(dao.tea(SOLD_OUT_KEY)).available).isFalse()
    }

    @Test
    fun theProgressCountsTheCheckedPages() = runTest {
        soldOutSinceTheFirstRefresh()
        val seen = mutableListOf<CatalogRefreshProgress>()

        refresher().refresh { seen += it }

        val pages = seen.filter { it.pagesTotal > 0 }
        assertThat(pages.map { it.pagesTotal }.distinct()).containsExactly(3)
        assertThat(pages.last().pagesDone).isEqualTo(3)
        assertThat(pages.last().products).isEqualTo(3)
        assertThat(pages.map { it.pagesDone }).isInOrder()
    }

    @Test
    fun theDiagnosticsNameTheCheckedTeasByNumber() = runTest {
        soldOutSinceTheFirstRefresh()
        routes[SOLD_OUT] = { MockResponse.Builder().code(404).build() }

        refresher().refresh()

        val messages = diagnostics.entries().map { it.message }
        assertThat(messages.filter { "1 known missing from the sitemap (1 to fetch once)" in it }).hasSize(1)
        assertThat(messages.filter { "$SOLD_OUT_KEY missing from the sitemap, gone" in it }).hasSize(1)
    }

    // The only test on the real rate limiter: its requests and the photos' decoding run on the wall
    // clock, so on a loaded machine it may need more than runTest's default minute (seen once).
    @Test
    fun requestsStayAtMostTwicePerSecondOnOneConnection() = runTest(timeout = 3.minutes) {
        soldOutSinceTheFirstRefresh()
        product(SOLD_OUT, "Nebelhang Sencha – 100g", stock = Stock.OUT_OF_STOCK, list = false)
        val sent = CopyOnWriteArrayList<Long>()
        val refresher = CatalogRefresher(
            client = ShopClient.forTesting(baseUrl = server.url("/"), socketFactory = SendTimes(sent)),
            dao = dao,
            clock = clock,
            diagnostics = diagnostics,
            settings = FakeSettingsRepository(),
            photos = photos,
            sanity = SanityCheck(firstImportMinimum = 1),
            owned = OwnedCatalogKeys { owned },
        )

        refresher.refresh()

        assertThat(paths()).contains(SOLD_OUT)
        assertThat(requests.map { it.connectionIndex }.distinct()).hasSize(1)
        assertThat(sent).hasSize(requests.size)
        val gapsMs = sent.zipWithNext { a, b -> TimeUnit.NANOSECONDS.toMillis(b - a) }
        gapsMs.forEach { assertWithMessage("send gaps $gapsMs ms").that(it).isAtLeast(MIN_GAP_MS) }
    }

    private fun text(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()

    private fun jpeg() = MockResponse.Builder().addHeader("Content-Type", "image/jpeg")
        .body(Buffer().write(syntheticJpeg(400, 300))).build()

    private companion object {
        const val ROBOTS = "/robots.txt"
        const val SITEMAP = "/sitemap.xml"
        const val SITEMAP_GZ = "/sitemap/synthetic-1.xml.gz"
        const val SENCHA_KEY = "XY0100"
        const val GYOKURO_KEY = "XY0200"
        const val SOLD_OUT_KEY = "XY0170"
        const val SENCHA = "/morgennebel-sencha/$SENCHA_KEY"
        const val GYOKURO = "/tautropfen-gyokuro/$GYOKURO_KEY"
        const val SOLD_OUT = "/nebelhang-sencha/$SOLD_OUT_KEY"
        const val KABUSE_KEY = "XY0300"
        const val KABUSE = "/schattenblatt-kabusecha/$KABUSE_KEY"
        const val HERBAL_KEY = "XK0300"
        const val HERBAL = "/wiesenzauber/$HERBAL_KEY"
        const val SENCHA_CATEGORY = "/tee/gruener-tee/japan/sencha/"
        const val NEW_PHOTO = "/media/aa/bb/cc/1800000000/gyokuro-neu.jpg?ts=1800000000"

        /** Just over a page's body limit (5 MiB, D-090). */
        const val OVER_PAGE_LIMIT = 5 * 1024 * 1024 + 1

        /** The limiter's interval (500 ms) less the few ms between its clock read and the socket write. */
        const val MIN_GAP_MS = 480L

        val GYOKURO_PATH = listOf(
            "Tee" to "/tee/",
            "Japantee" to "/tee/japantee/",
            "Gyokuro" to "/tee/japantee/gyokuro/",
        )
    }
}
