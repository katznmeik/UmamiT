package com.umamit.core.data.catalog

import com.umamit.core.catalog.parser.SanityCheck
import com.umamit.core.catalog.parser.SyntheticShop
import com.umamit.core.common.WallClock
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.network.ShopClient
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okio.Buffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.SocketFactory

/**
 * The simulated shop of the catalog refresh tests (D-122, CAT-A-07): a [MockWebServer] that records
 * every request in [requests] (in the order they reached it, 06 §5.3) and answers each path from
 * [routes], unknown paths with 404. [start] serves the synthetic robots.txt, the sitemap index and
 * the gzipped url set of the [listed] product paths (D-016); each test adds its product pages.
 * Started in a test's `@Before`, closed in its `@After`.
 */
class FakeShop {
    val server = MockWebServer()

    /** What the shop answers per path (read on the server's thread). */
    val routes = ConcurrentHashMap<String, () -> MockResponse>()
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    /** The product paths in the sitemap's url set, in order. */
    val listed = CopyOnWriteArrayList<String>()

    val url: HttpUrl get() = server.url("/")
    val base: String get() = url.toString().removeSuffix("/")

    fun paths() = requests.map { it.url.encodedPath }

    fun start() {
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

    fun close() = server.close()

    companion object {
        const val ROBOTS = "/robots.txt"
        const val SITEMAP = "/sitemap.xml"
        const val SITEMAP_GZ = "/sitemap/synthetic-1.xml.gz"

        fun text(body: String) =
            MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()
    }
}

/**
 * A [CatalogRefresher] against [shop] with the refresh tests' defaults: no request gap (the rate
 * limit has its own tests; [requestIntervalMillis] `null` is the real one), the first import's
 * minimum of one tea ([sanity]), and the main constructor's defaults for what a class does not vary.
 */
@Suppress("LongParameterList")
fun catalogRefresher(
    shop: FakeShop,
    dao: CatalogDao,
    clock: WallClock,
    diagnostics: DiagnosticsLog,
    photos: CatalogPhotoFiles,
    settings: SettingsRepository = FakeSettingsRepository(),
    owned: OwnedCatalogKeys = OwnedCatalogKeys { flowOf(emptySet()) },
    seedTeas: CatalogSeedLinker = CatalogSeedLinker { emptyList() },
    bookmarks: CatalogBookmarkRepository = CatalogBookmarkRepository.NONE,
    socketFactory: SocketFactory = SocketFactory.getDefault(),
    requestIntervalMillis: Long? = 0,
    sanity: SanityCheck = SanityCheck(firstImportMinimum = 1),
    parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
    guideLimits: GuideLimits = GuideLimits.DEFAULT,
    jobs: CatalogJobs = CatalogJobs(),
) = CatalogRefresher(
    client = ShopClient.forTesting(
        baseUrl = shop.url,
        socketFactory = socketFactory,
        requestIntervalMillis = requestIntervalMillis,
    ),
    dao = dao,
    clock = clock,
    diagnostics = diagnostics,
    settings = settings,
    photos = photos,
    sanity = sanity,
    parseDispatcher = parseDispatcher,
    owned = owned,
    seedTeas = seedTeas,
    guideLimits = guideLimits,
    jobs = jobs,
    bookmarks = bookmarks,
)
