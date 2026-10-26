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
import com.umamit.core.data.syntheticJpeg
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.network.ShopClient
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * The refresh's wall time against its request gaps (D-091, NFR-PERF-11, CLAUDE.md rule 7).
 *
 * Full live run 1 sent 1,747 requests in 1,207 s, ≈ 330 s more than their 500 ms gaps, because
 * each page was parsed (each photo re-encoded) before the next one was asked for. Here the shop
 * answers after [LATENCY_MS], processing a page or photo takes [PROCESSING_MS] and the client
 * keeps [GAP_MS] between requests: one after the other, each page or photo would cost latency
 * plus processing ([SERIAL_MS]); overlapped, only the gap. The synthetic shop is twelve green teas
 * with a photo each (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class CatalogRefreshTimingTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val db = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val routes = ConcurrentHashMap<String, () -> MockResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val sent = CopyOnWriteArrayList<Long>()
    private val base get() = server.url("/").toString().removeSuffix("/")
    private val pages = (1..TEAS).map { "/erfundener-sencha-$it/XY${1000 + it}" }

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
        routes[SITEMAP] = { text(SyntheticShop.urlSet(pages.map { "$base$it" })) }
        pages.forEachIndexed { index, path ->
            val photo = SyntheticShop.photoPath("tee-$index")
            routes[path] = {
                text(
                    SyntheticShop.productPage(
                        baseUrl = base,
                        title = "Erfundener Sencha $index – 100g",
                        sku = path.substringAfterLast('/'),
                        canonicalPath = path,
                        photoPath = photo,
                    ),
                ).slow()
            }
            val jpeg = syntheticJpeg(PHOTO_PX, PHOTO_PX)
            routes[photo.substringBefore('?')] = {
                MockResponse.Builder().addHeader("Content-Type", "image/jpeg").body(Buffer().write(jpeg)).build().slow()
            }
        }
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    @Test
    fun processingOverlapsTheRequestGapsInsteadOfAddingUp() = runTest {
        val refresher = CatalogRefresher(
            client = ShopClient.forTesting(
                baseUrl = server.url("/"),
                socketFactory = SendTimes(sent),
                requestIntervalMillis = GAP_MS,
            ),
            dao = db.catalogDao(),
            clock = FakeClock(),
            diagnostics = DiagnosticsLog(folder.newFolder(), maxBytes = 100_000, dispatcher = Dispatchers.IO),
            settings = FakeSettingsRepository(),
            photos = CatalogPhotoFiles(folder.newFolder("catalog")),
            sanity = SanityCheck(firstImportMinimum = 1),
            parseDispatcher = SlowWork(PROCESSING_MS),
        )

        val start = System.nanoTime()
        val outcome = refresher.refresh()
        val wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = TEAS, newTeas = TEAS))
        assertThat(sent).hasSize(requests.size)
        val paths = requests.map { it.url.encodedPath }
        val pageSends = sent.filterIndexed { index, _ -> paths[index] in pages }
        val photoSends = sent.filterIndexed { index, _ -> paths[index].startsWith("/media/") }
        assertThat(pageSends).hasSize(TEAS)
        assertThat(photoSends).hasSize(TEAS)
        val gapsMs = sent.zipWithNext { a, b -> TimeUnit.NANOSECONDS.toMillis(b - a) }
        val report = "wall $wallMs ms for ${sent.size} requests, budget ${(sent.size - 1) * GAP_MS} ms; gaps $gapsMs"

        // Still one request at a time on one connection, never faster than the gap (rule 7) …
        assertThat(requests.map { it.connectionIndex }.distinct()).containsExactly(0)
        gapsMs.forEach { assertWithMessage(report).that(it).isAtLeast(GAP_MS - GAP_TOLERANCE_MS) }
        // … and each page or photo costs the gap, not latency plus processing one after the other.
        assertWithMessage(report).that(meanGapMs(pageSends)).isLessThan(THRESHOLD_MS)
        assertWithMessage(report).that(meanGapMs(photoSends)).isLessThan(THRESHOLD_MS)
    }

    private fun meanGapMs(sends: List<Long>): Long =
        TimeUnit.NANOSECONDS.toMillis(sends.last() - sends.first()) / (sends.size - 1)

    private fun MockResponse.slow() = newBuilder().headersDelay(LATENCY_MS, TimeUnit.MILLISECONDS).build()

    private fun text(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()

    /** A page's or photo's processing: [millis] on a worker thread before the real work, one at a time. */
    private class SlowWork(private val millis: Long) : CoroutineDispatcher() {
        private val worker = Executors.newSingleThreadExecutor { Thread(it, "slow-work").apply { isDaemon = true } }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            worker.execute {
                Thread.sleep(millis)
                block.run()
            }
        }
    }

    private companion object {
        const val ROBOTS = "/robots.txt"
        const val SITEMAP = "/sitemap.xml"
        const val TEAS = 12
        const val PHOTO_PX = 400
        const val GAP_MS = 200L
        const val LATENCY_MS = 150L
        const val PROCESSING_MS = 150L

        /** Latency plus processing: what each page or photo cost when processed before the next request. */
        const val SERIAL_MS = LATENCY_MS + PROCESSING_MS

        /** Halfway between the gap and [SERIAL_MS]. */
        const val THRESHOLD_MS = (GAP_MS + SERIAL_MS) / 2

        /** The few ms between the limiter's clock read and the socket write. */
        const val GAP_TOLERANCE_MS = 20L
    }
}
