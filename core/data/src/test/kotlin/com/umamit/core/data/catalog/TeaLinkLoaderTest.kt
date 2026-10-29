package com.umamit.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.catalog.parser.SanityCheck
import com.umamit.core.catalog.parser.SyntheticShop
import com.umamit.core.catalog.parser.SyntheticShop.Stock
import com.umamit.core.catalog.parser.TeeKontorParser
import com.umamit.core.data.SendTimes
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.data.syntheticJpeg
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.UserSettings
import com.umamit.core.network.ShopClient
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * A tea from a shared or pasted shop link (v0.2.1, #98; CAT-17, GEN-01 (d), 08 §2.4 item 9,
 * D-094) against a simulated shop (spec #81 seam 4): MockWebServer serving a synthetic robots.txt
 * and product pages (D-016), an in-memory `catalog.db`, a fake clock. The links name the shop's
 * real host; the client's base URL points them at the server, as it points every request of the
 * app at the one allow-listed host. The request gap is shortened (the rate has its own test).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
@Suppress("LargeClass") // one test per answer of the shop (CAT-17 AC2–AC8)
class TeaLinkLoaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val db = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.catalogDao()
    private val clock = FakeClock()
    private val jobs = CatalogJobs()
    private lateinit var photos: CatalogPhotoFiles
    private lateinit var diagnostics: DiagnosticsLog

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
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    /** Serves the product page [path] with its photo; [list] also puts it into the sitemap (for a refresh). */
    @Suppress("LongParameterList")
    private fun product(
        path: String,
        title: String,
        breadcrumb: List<Pair<String, String>> = SyntheticShop.SENCHA_PATH,
        stock: Stock = Stock.IN_STOCK,
        canonicalPath: String = path,
        photo: String = SyntheticShop.photoPath(path.substringAfterLast('/').lowercase()),
        list: Boolean = false,
    ) {
        if (list && path !in listed) listed += path
        val sku = path.substringAfterLast('/')
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

    private fun client(requestIntervalMillis: Long? = 0) =
        ShopClient.forTesting(baseUrl = server.url("/"), requestIntervalMillis = requestIntervalMillis)

    private fun loader(settings: UserSettings = UserSettings(), client: ShopClient = client()) = ShopTeaLinkLoader(
        client = client,
        dao = dao,
        clock = clock,
        diagnostics = diagnostics,
        settings = FakeSettingsRepository(settings),
        photos = photos,
        jobs = jobs,
    )

    private fun refresher() = CatalogRefresher(
        client = client(),
        dao = dao,
        clock = clock,
        diagnostics = diagnostics,
        settings = FakeSettingsRepository(),
        photos = photos,
        sanity = SanityCheck(firstImportMinimum = 1),
        jobs = jobs,
    )

    private fun paths() = requests.map { it.url.encodedPath }

    private suspend fun catalogKeys() = RoomCatalogRepository(dao, photos).observeCatalog().first().teas.map { it.key }

    // --- AC2: refused links send nothing ---

    @Test
    fun aRefusedLinkSendsNoRequest() = runTest {
        val texts = listOf(
            "https://www.example.com$SOLD_OUT",
            "http://www.tee-kontor-kiel.de$SOLD_OUT",
            "https://user@www.tee-kontor-kiel.de$SOLD_OUT",
            "$SHOP$SOLD_OUT?p=2",
            "$SHOP/tee/gruener-tee/japan/sencha/",
            "$SHOP/tee-info/ein-guide-fuer-japantee",
        )
        texts.forEach { text ->
            assertWithMessage(text).that(loader().load(text)).isEqualTo(TeaLinkOutcome.NotATeaLink)
        }

        assertThat(requests).isEmpty()
        assertThat(dao.keys()).isEmpty()
    }

    @Test
    fun aSetOrSaleLinkIsNoCatalogTeaAndSendsNoRequest() = runTest {
        assertThat(loader().load("$SHOP/sencha-probierset/XY0170SET")).isEqualTo(TeaLinkOutcome.NotInCatalog)
        assertThat(loader().load("$SHOP/nebelhang-reduziert/XY0170SALE")).isEqualTo(TeaLinkOutcome.NotInCatalog)

        assertThat(requests).isEmpty()
    }

    @Test
    fun theDiagnosticsNeverHoldTheSharedText() = runTest {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")

        loader().load("Mein geheimer Notizzettel https://www.example.com/privat")
        loader().load("Nebelhang Sencha | Tee-Kontor Kiel $SHOP$SOLD_OUT")

        val messages = diagnostics.entries().joinToString("\n") { it.message }
        assertThat(messages).doesNotContain("geheimer")
        assertThat(messages).doesNotContain("example.com")
        assertThat(messages).doesNotContain("Tee-Kontor Kiel")
        assertThat(messages).contains("tea link: $SOLD_OUT_KEY stored")
    }

    // --- AC3, AC4: the one page, stored ---

    @Test
    fun aGreenTeasPageIsStoredAsACatalogEntry() = runTest {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")

        val outcome = loader().load("$SHOP$SOLD_OUT")

        assertThat(outcome).isEqualTo(TeaLinkOutcome.Opened(SOLD_OUT_KEY))
        val tea = requireNotNull(dao.tea(SOLD_OUT_KEY))
        assertThat(tea.name).isEqualTo("Nebelhang Sencha")
        assertThat(tea.productUrl).isEqualTo("$base$SOLD_OUT")
        assertThat(tea.styleKey).isEqualTo("sencha")
        assertThat(tea.derivedRecipesJson).isNotNull()
        assertThat(tea.contentHash).isNotNull()
        assertThat(tea.available).isTrue()
        assertThat(tea.discontinued).isFalse()
        // robots.txt first, then only that one page and its photo (SET-22 on by default).
        assertThat(paths()).containsExactly(ROBOTS, SOLD_OUT, PHOTO).inOrder()
        assertThat(photos.exists(SOLD_OUT_KEY)).isTrue()
        assertThat(tea.photoFile).isEqualTo(photos.photoName(SOLD_OUT_KEY))
        assertThat(catalogKeys()).containsExactly(SOLD_OUT_KEY)
    }

    @Test
    fun aSizeLinkFetchesThatPageAndStoresTheTea() = runTest {
        product("$SOLD_OUT-250", "Nebelhang Sencha – 250g", canonicalPath = SOLD_OUT)

        val outcome = loader().load("$SHOP$SOLD_OUT-250")

        assertThat(outcome).isEqualTo(TeaLinkOutcome.Opened(SOLD_OUT_KEY))
        assertThat(paths()).containsAtLeast(ROBOTS, "$SOLD_OUT-250").inOrder()
        assertThat(dao.tea(SOLD_OUT_KEY)).isNotNull()
    }

    @Test
    fun aSoldOutPageIsStoredAsNotAvailableAndNotNew() = runTest {
        // Two refreshes ran before: a tea first seen in the latest one would be „Neu" (D-083).
        dao.log(refreshLog(startedAt = clock.nowMs()))
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
        dao.log(refreshLog(startedAt = clock.nowMs()))
        clock.advanceBy(TimeUnit.MINUTES.toMillis(5))
        product(SOLD_OUT, "Nebelhang Sencha – 100g", stock = Stock.OUT_OF_STOCK)

        val outcome = loader().load("$SHOP$SOLD_OUT")

        assertThat(outcome).isEqualTo(TeaLinkOutcome.Opened(SOLD_OUT_KEY))
        val detail = requireNotNull(RoomCatalogRepository(dao, photos).observeTea(SOLD_OUT_KEY).first())
        assertThat(detail.available).isFalse()
        // As #97 keeps a sold-out tea: the page's own size is out of stock, the configurator's other
        // size has no stock known.
        assertThat(detail.packages.map { it.grams to it.available }).containsExactly(100.0 to false, 250.0 to null)
        val row = RoomCatalogRepository(dao, photos).observeCatalog().first().teas.single()
        assertThat(row.isNew).isFalse()
        assertThat(row.available).isFalse()
    }

    @Test
    fun aKraeuterteeIsStoredWithItsStyle() = runTest {
        product(HERBAL, "Wiesenzauber – 100g", breadcrumb = SyntheticShop.HERBAL_PATH)

        val outcome = loader().load("$SHOP$HERBAL")

        assertThat(outcome).isEqualTo(TeaLinkOutcome.Opened(HERBAL_KEY))
        assertThat(requireNotNull(dao.tea(HERBAL_KEY)).styleKey).isEqualTo("herbal")
    }

    @Test
    fun theStoredTeaIsFoundBySearch() = runTest {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")

        loader().load("$SHOP$SOLD_OUT")

        val listing = RoomCatalogRepository(dao, photos)
            .observeListing(CatalogFilter.DEFAULT.copy(search = "Nebelhang")).first()
        assertThat(listing.teas.map { it.key }).containsExactly(SOLD_OUT_KEY)
    }

    @Test
    fun withoutPhotosTheTeaIsStoredWithoutAPhotoRequest() = runTest {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")

        val outcome = loader(settings = UserSettings(catalogDownloadPhotos = false)).load("$SHOP$SOLD_OUT")

        assertThat(outcome).isEqualTo(TeaLinkOutcome.Opened(SOLD_OUT_KEY))
        assertThat(paths()).containsExactly(ROBOTS, SOLD_OUT).inOrder()
        assertThat(requireNotNull(dao.tea(SOLD_OUT_KEY)).photoFile).isNull()
    }

    @Test
    fun aPhotoThatDoesNotDecodeLeavesTheTeaWithoutOneAndIsNotAskedForAgain() = runTest {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")
        routes[PHOTO] = { MockResponse.Builder().addHeader("Content-Type", "image/jpeg").body("kein Bild").build() }

        assertThat(loader().load("$SHOP$SOLD_OUT")).isEqualTo(TeaLinkOutcome.Opened(SOLD_OUT_KEY))
        assertThat(requireNotNull(dao.tea(SOLD_OUT_KEY)).photoFile).isNull()
        requests.clear()

        assertThat(loader().load("$SHOP$SOLD_OUT")).isEqualTo(TeaLinkOutcome.Opened(SOLD_OUT_KEY))
        assertThat(paths()).containsExactly(ROBOTS, SOLD_OUT).inOrder()
    }

    // --- AC6: a tea already in the catalog ---

    @Test
    fun aTeaAlreadyInTheCatalogIsRefreshedFromItsPageAndKeepsItsFirstSighting() = runTest {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")
        loader().load("$SHOP$SOLD_OUT")
        val before = requireNotNull(dao.tea(SOLD_OUT_KEY))
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
        product(SOLD_OUT, "Nebelhang Sencha – 100g", stock = Stock.OUT_OF_STOCK)
        requests.clear()

        val outcome = loader().load("$SHOP$SOLD_OUT")

        assertThat(outcome).isEqualTo(TeaLinkOutcome.Opened(SOLD_OUT_KEY))
        val after = requireNotNull(dao.tea(SOLD_OUT_KEY))
        assertThat(after.available).isFalse()
        assertThat(after.firstSeenAt).isEqualTo(before.firstSeenAt)
        assertThat(after.lastSeenAt).isGreaterThan(before.lastSeenAt)
        assertThat(after.contentHash).isNotEqualTo(before.contentHash)
        // Its photo is unchanged: not fetched again, kept.
        assertThat(paths()).containsExactly(ROBOTS, SOLD_OUT).inOrder()
        assertThat(after.photoFile).isEqualTo(before.photoFile)
        assertThat(after.photoUrl).isEqualTo(before.photoUrl)
    }

    @Test
    fun aTeaKeptAsNichtMehrImSortimentIsBackInTheRange() = runTest {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")
        loader().load("$SHOP$SOLD_OUT")
        dao.swap(emptyList(), refreshLog(clock.nowMs()), discontinued = listOf(SOLD_OUT_KEY))
        assertThat(requireNotNull(dao.tea(SOLD_OUT_KEY)).discontinued).isTrue()

        val outcome = loader().load("$SHOP$SOLD_OUT")

        assertThat(outcome).isEqualTo(TeaLinkOutcome.Opened(SOLD_OUT_KEY))
        assertThat(requireNotNull(dao.tea(SOLD_OUT_KEY)).discontinued).isFalse()
        assertThat(catalogKeys()).containsExactly(SOLD_OUT_KEY)
    }

    @Test
    fun aNumberMergedIntoAnotherTeaOpensThatTeaWithoutARequest() = runTest {
        // Split sizes under their own numbers, merged by the refresh under the lowest (08 §2.4 item 7).
        product("/abendrot-matcha-30g/XY0868", "Abendrot Matcha – 30g", list = true)
        routes["/abendrot-matcha-30g/XY0868"] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    title = "Abendrot Matcha – 30g",
                    sku = "XY0868",
                    canonicalPath = "/abendrot-matcha-30g/XY0868",
                    sizes = listOf(SyntheticShop.Size("30g", checked = true)),
                    unitContent = "0.03 kg",
                ),
            )
        }
        routes["/abendrot-matcha-100g/XY0869"] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    title = "Abendrot Matcha – 100g",
                    sku = "XY0869",
                    canonicalPath = "/abendrot-matcha-100g/XY0869",
                    sizes = listOf(SyntheticShop.Size("100g", checked = true)),
                ),
            )
        }
        listed += "/abendrot-matcha-100g/XY0869"
        assertThat(refresher().refresh()).isInstanceOf(CatalogRefreshOutcome.Success::class.java)
        assertThat(dao.keys()).containsExactly("XY0868")
        requests.clear()

        val outcome = loader().load("$SHOP/abendrot-matcha-100g/XY0869")

        assertThat(outcome).isEqualTo(TeaLinkOutcome.Opened("XY0868"))
        assertThat(requests).isEmpty()
    }

    // --- AC5: out of scope ---

    @Test
    fun aPageOutOfScopeIsNotStoredAndNotSkipped() = runTest {
        product(BLACK, "Nebelberg Assam – 100g", breadcrumb = SyntheticShop.BLACK_TEA_PATH)

        val outcome = loader().load("$SHOP$BLACK")

        assertThat(outcome).isEqualTo(TeaLinkOutcome.NotInCatalog)
        assertThat(dao.keys()).isEmpty()
        assertThat(dao.skipList()).isEmpty()
        // Nothing to store: its photo isn't asked for.
        assertThat(paths()).containsExactly(ROBOTS, BLACK).inOrder()
        assertThat(photos.exists(BLACK_KEY)).isFalse()
    }

    @Test
    fun aFruitTeaIsNotStored() = runTest {
        product(BLACK, "Beerenwiese – 100g", breadcrumb = SyntheticShop.FRUIT_TEA_PATH)

        assertThat(loader().load("$SHOP$BLACK")).isEqualTo(TeaLinkOutcome.NotInCatalog)
        assertThat(dao.keys()).isEmpty()
    }

    // --- No tea at the link ---

    @Test
    fun http404And410StoreNothing() = runTest {
        for (code in listOf(404, 410)) {
            routes[SOLD_OUT] = { MockResponse.Builder().code(code).build() }

            assertWithMessage("HTTP $code").that(loader().load("$SHOP$SOLD_OUT")).isEqualTo(TeaLinkOutcome.NoTeaPage)
        }
        assertThat(dao.keys()).isEmpty()
    }

    @Test
    fun aRedirectIsNeverFollowed() = runTest {
        product("/nebelhang-sencha-neu/$SOLD_OUT_KEY", "Nebelhang Sencha – 100g")
        for (location in listOf("$base/nebelhang-sencha-neu/$SOLD_OUT_KEY", "https://elsewhere.example$SOLD_OUT")) {
            routes[SOLD_OUT] = { MockResponse.Builder().code(301).addHeader("Location", location).build() }

            assertWithMessage(location).that(loader().load("$SHOP$SOLD_OUT")).isEqualTo(TeaLinkOutcome.NoTeaPage)
        }

        assertThat(paths()).doesNotContain("/nebelhang-sencha-neu/$SOLD_OUT_KEY")
        assertThat(dao.keys()).isEmpty()
    }

    @Test
    fun aPageOfAnotherProductStoresNothing() = runTest {
        product(SOLD_OUT, "Morgennebel Sencha – 100g", canonicalPath = "/morgennebel-sencha/XY0100")

        assertThat(loader().load("$SHOP$SOLD_OUT")).isEqualTo(TeaLinkOutcome.NoTeaPage)
        assertThat(dao.keys()).isEmpty()
    }

    @Test
    fun aPageWithoutProductStoresNothing() = runTest {
        routes[SOLD_OUT] = { text("<html><body><p>Wartung</p></body></html>") }

        assertThat(loader().load("$SHOP$SOLD_OUT")).isEqualTo(TeaLinkOutcome.NoTeaPage)
        assertThat(dao.keys()).isEmpty()
    }

    @Test
    fun aPageWithoutBreadcrumbOrOverTheLimitCantBeRead() = runTest {
        routes[SOLD_OUT] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    sku = SOLD_OUT_KEY,
                    canonicalPath = SOLD_OUT,
                    breadcrumb = null,
                ),
            )
        }
        assertThat(loader().load("$SHOP$SOLD_OUT")).isEqualTo(TeaLinkOutcome.Unreadable)

        routes[SOLD_OUT] = { text("<html>" + "x".repeat(OVER_PAGE_LIMIT) + "</html>") }
        assertThat(loader().load("$SHOP$SOLD_OUT")).isEqualTo(TeaLinkOutcome.Unreadable)

        assertThat(dao.keys()).isEmpty()
    }

    // --- Rule 7: robots.txt and the stop signals ---

    @Test
    fun aPageRobotsTxtDisallowsIsNeverFetched() = runTest {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")
        routes[ROBOTS] = { text(SyntheticShop.robotsTxt("Disallow: /nebelhang-sencha/")) }

        val outcome = loader().load("$SHOP$SOLD_OUT")

        assertThat(outcome).isEqualTo(TeaLinkOutcome.Unreadable)
        assertThat(paths()).containsExactly(ROBOTS)
        assertThat(dao.keys()).isEmpty()
    }

    @Test
    fun aChallengeStopsAndStoresNothing() = runTest {
        stopsOnThePage(TeaLinkOutcome.Blocked) {
            MockResponse.Builder().code(403).addHeader("cf-mitigated", "challenge").build()
        }
    }

    @Test
    fun http429StopsAndStoresNothing() = runTest {
        stopsOnThePage(TeaLinkOutcome.Blocked) { MockResponse.Builder().code(429).build() }
    }

    @Test
    fun aRetryAfterStopsAndStoresNothing() = runTest {
        stopsOnThePage(TeaLinkOutcome.Blocked) {
            MockResponse.Builder().code(200).addHeader("Retry-After", "120").body("<html></html>").build()
        }
    }

    @Test
    fun aServerErrorStopsAndStoresNothing() = runTest {
        stopsOnThePage(TeaLinkOutcome.ShopUnavailable) { MockResponse.Builder().code(503).build() }
    }

    @Test
    fun aStopOnRobotsTxtSendsNothingElse() = runTest {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")
        routes[ROBOTS] = { MockResponse.Builder().code(429).build() }

        assertThat(loader().load("$SHOP$SOLD_OUT")).isEqualTo(TeaLinkOutcome.Blocked)
        assertThat(paths()).containsExactly(ROBOTS)
        assertThat(dao.keys()).isEmpty()
    }

    @Test
    fun aStopOnThePhotoStoresNothing() = runTest {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")
        routes[PHOTO] = { MockResponse.Builder().code(429).build() }

        assertThat(loader().load("$SHOP$SOLD_OUT")).isEqualTo(TeaLinkOutcome.Blocked)
        assertThat(dao.keys()).isEmpty()
        assertThat(photos.exists(SOLD_OUT_KEY)).isFalse()
    }

    @Test
    fun offlineGivesTheOfflineMessageAndStoresNothing() = runTest {
        server.close()

        val outcome = loader().load("$SHOP$SOLD_OUT")

        assertThat(outcome).isEqualTo(TeaLinkOutcome.Offline)
        assertThat(requests).isEmpty()
        assertThat(dao.keys()).isEmpty()
    }

    /** The shop answers [answer] on the page: [expected], nothing stored, nothing asked for after it. */
    private suspend fun stopsOnThePage(expected: TeaLinkOutcome, answer: () -> MockResponse) {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")
        routes[SOLD_OUT] = answer

        val outcome = loader().load("$SHOP$SOLD_OUT")

        assertThat(outcome).isEqualTo(expected)
        assertThat(paths()).containsExactly(ROBOTS, SOLD_OUT).inOrder()
        assertThat(dao.keys()).isEmpty()
        assertThat(photos.exists(SOLD_OUT_KEY)).isFalse()
    }

    // The only test on the real rate limiter: its requests and the photo's decoding run on the wall clock.
    @Test
    fun requestsStayAtMostTwicePerSecondOnOneConnection() = runTest(timeout = 3.minutes) {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")
        val sent = CopyOnWriteArrayList<Long>()
        val client = ShopClient.forTesting(baseUrl = server.url("/"), socketFactory = SendTimes(sent))

        withContext(Dispatchers.IO) { loader(client = client).load("$SHOP$SOLD_OUT") }

        assertThat(paths()).containsExactly(ROBOTS, SOLD_OUT, PHOTO).inOrder()
        assertThat(requests.map { it.connectionIndex }.distinct()).hasSize(1)
        val gapsMs = sent.zipWithNext { a, b -> TimeUnit.NANOSECONDS.toMillis(b - a) }
        gapsMs.forEach { assertWithMessage("send gaps $gapsMs ms").that(it).isAtLeast(MIN_GAP_MS) }
    }

    // --- AC7: never during a refresh ---

    @Test
    fun aRunningRefreshBlocksTheFetch() = runTest {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")

        val outcome = jobs.refresh { loader().load("$SHOP$SOLD_OUT") }

        assertThat(outcome).isEqualTo(TeaLinkOutcome.RefreshRunning)
        assertThat(requests).isEmpty()
        assertThat(dao.keys()).isEmpty()
    }

    @Test
    fun aRefreshStartedDuringALinkWaitsForIt() = runTest {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")
        listed += SENCHA
        product(SENCHA, "Morgennebel Sencha – 100g")
        val linkHolds = CompletableDeferred<Unit>()
        val linkMayEnd = CompletableDeferred<Unit>()

        val link = async {
            jobs.link {
                linkHolds.complete(Unit)
                linkMayEnd.await()
            }
        }
        linkHolds.await()
        val refresh = async(Dispatchers.IO) { refresher().refresh() }
        repeat(10) { yield() }
        withContext(Dispatchers.IO) { Thread.sleep(200) }

        // The refresh waits for its turn: not a single request yet.
        assertThat(requests).isEmpty()
        linkMayEnd.complete(Unit)
        link.await()
        assertThat(refresh.await()).isInstanceOf(CatalogRefreshOutcome.Success::class.java)
        assertThat(paths()).contains(SENCHA)
    }

    // --- AC8 and the refresh's guards ---

    @Test
    fun aLaterRefreshKeepsATeaFromALinkThatIsMissingFromTheSitemap() = runTest {
        listed += SENCHA
        product(SENCHA, "Morgennebel Sencha – 100g")
        assertThat(refresher().refresh()).isEqualTo(CatalogRefreshOutcome.Success(teas = 1, newTeas = 1))
        clock.advanceBy(TimeUnit.HOURS.toMillis(1))
        product(SOLD_OUT, "Nebelhang Sencha – 100g", stock = Stock.OUT_OF_STOCK)
        assertThat(loader().load("$SHOP$SOLD_OUT")).isEqualTo(TeaLinkOutcome.Opened(SOLD_OUT_KEY))
        clock.advanceBy(TimeUnit.DAYS.toMillis(1))
        requests.clear()

        val outcome = refresher().refresh()

        // Re-checked once (CAT-16): still a green page, so kept, „nicht vorrätig", never new or gone.
        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 0))
        assertThat(paths().count { it == SOLD_OUT }).isEqualTo(1)
        assertThat(requireNotNull(dao.tea(SOLD_OUT_KEY)).available).isFalse()
        assertThat(catalogKeys()).containsExactly(SENCHA_KEY, SOLD_OUT_KEY)
    }

    @Test
    fun aTeaFromALinkBeforeTheFirstRefreshDoesNotWeakenTheFirstImportsMinimum() = runTest {
        product(SOLD_OUT, "Nebelhang Sencha – 100g")
        loader().load("$SHOP$SOLD_OUT")
        listed += SENCHA
        product(SENCHA, "Morgennebel Sencha – 100g")
        val refresher = CatalogRefresher(
            client = client(),
            dao = dao,
            clock = clock,
            diagnostics = diagnostics,
            settings = FakeSettingsRepository(),
            photos = photos,
            sanity = SanityCheck(firstImportMinimum = 3),
            jobs = jobs,
        )

        val outcome = refresher.refresh()

        // One tea in the shop is still too few for a first import (08 §2.9), whatever a link stored.
        assertThat(outcome).isInstanceOf(CatalogRefreshOutcome.ShopChanged::class.java)
        assertThat((outcome as CatalogRefreshOutcome.ShopChanged).details).contains("TOO_FEW_PRODUCTS")
        assertThat(dao.keys()).containsExactly(SOLD_OUT_KEY)
    }

    private fun refreshLog(startedAt: Long) = CatalogRefreshLogEntity(
        startedAt = startedAt,
        finishedAt = startedAt + 1,
        status = CatalogRefreshStatus.SUCCESS,
        newCount = 0,
        changedCount = 0,
        discontinuedCount = 0,
        flaggedCount = 0,
        photosCount = 0,
        parserVersion = TeeKontorParser.PARSER_VERSION,
        errorSummary = null,
    )

    private fun text(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()

    private fun jpeg() = MockResponse.Builder().addHeader("Content-Type", "image/jpeg")
        .body(Buffer().write(syntheticJpeg(400, 300))).build()

    private companion object {
        const val SHOP = "https://www.tee-kontor-kiel.de"
        const val ROBOTS = "/robots.txt"
        const val SITEMAP = "/sitemap.xml"
        const val SITEMAP_GZ = "/sitemap/synthetic-1.xml.gz"
        const val SENCHA_KEY = "XY0100"
        const val SENCHA = "/morgennebel-sencha/$SENCHA_KEY"
        const val SOLD_OUT_KEY = "XY0170"
        const val SOLD_OUT = "/nebelhang-sencha/$SOLD_OUT_KEY"
        const val HERBAL_KEY = "XK0300"
        const val HERBAL = "/wiesenzauber/$HERBAL_KEY"
        const val BLACK_KEY = "XB0400"
        const val BLACK = "/nebelberg-assam/$BLACK_KEY"
        val PHOTO = SyntheticShop.photoPath(SOLD_OUT_KEY.lowercase()).substringBefore('?')

        /** Just over a page's body limit (5 MiB, D-090). */
        const val OVER_PAGE_LIMIT = 5 * 1024 * 1024 + 1

        /** The limiter's interval (500 ms) less the few ms between its clock read and the socket write. */
        const val MIN_GAP_MS = 480L
    }
}
