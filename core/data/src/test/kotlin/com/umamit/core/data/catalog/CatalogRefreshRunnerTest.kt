package com.umamit.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.catalog.parser.SanityCheck
import com.umamit.core.catalog.parser.SyntheticShop
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.network.ShopClient
import com.umamit.core.testing.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The app-scoped refresh job (#64): one refresh at a time, its progress and result for the sheet,
 * *Abbrechen*, and the list filled from the catalog database afterwards (SCR-19, SCR-22).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogRefreshRunnerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val db = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    /** Holds the tea page's answer until released, so a test can act while the refresh runs. */
    private val pageGate = CountDownLatch(1)
    private val pageRequested = CountDownLatch(1)

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val base = server.url("/").toString().removeSuffix("/")
                return when (request.url.encodedPath) {
                    "/robots.txt" -> ok(SyntheticShop.robotsTxt())

                    "/sitemap.xml" -> ok(SyntheticShop.urlSet(listOf("$base$TEA")))

                    TEA -> {
                        pageRequested.countDown()
                        pageGate.await(GATE_SECONDS, TimeUnit.SECONDS)
                        ok(SyntheticShop.productPage(baseUrl = base, canonicalPath = TEA))
                    }

                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        pageGate.countDown()
        server.close()
        db.close()
    }

    private fun TestScope.runner(): AppCatalogRefreshRunner {
        val refresher = CatalogRefresher(
            client = ShopClient.forTesting(baseUrl = server.url("/")),
            dao = db.catalogDao(),
            clock = FakeClock(),
            diagnostics = DiagnosticsLog(folder.newFolder(), maxBytes = 100_000, dispatcher = Dispatchers.IO),
            sanity = SanityCheck(firstImportMinimum = 1),
        )
        return AppCatalogRefreshRunner(refresher, TestScope(StandardTestDispatcher(testScheduler)))
    }

    /** Waits (in real time: the refresh does real I/O) for the result. */
    private suspend fun AppCatalogRefreshRunner.awaitFinished(): CatalogRefreshState.Finished =
        state.first { it is CatalogRefreshState.Finished } as CatalogRefreshState.Finished

    @Test
    fun aRefreshReportsProgressThenItsResultAndFillsTheList() = runTest {
        val runner = runner()
        val repository = RoomCatalogRepository(db.catalogDao())
        assertThat(repository.observeCatalog().first().isEmpty).isTrue()

        runner.start()
        assertThat(runner.state.value).isEqualTo(CatalogRefreshState.Running(CatalogRefreshProgress.STARTING))
        pageGate.countDown()
        val finished = runner.awaitFinished()

        assertThat(finished.outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 1, newTeas = 1))
        val catalog = repository.observeCatalog().first()
        assertThat(catalog.teas.map { it.name }).containsExactly("Morgennebel Sencha")
        assertThat(catalog.lastRefreshAtMillis).isEqualTo(FakeClock.DEFAULT_WALL_MS)
        runner.acknowledge()
        assertThat(runner.state.value).isEqualTo(CatalogRefreshState.Idle)
    }

    @Test
    fun aSecondStartWhileRunningStartsNothing() = runTest {
        val runner = runner()

        runner.start()
        testScheduler.runCurrent()
        runner.start()
        pageGate.countDown()
        runner.awaitFinished()

        // One robots.txt, one sitemap, one page: a single refresh.
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun cancelEndsTheRunAsCancelledWithTheListUnchanged() = runTest {
        val runner = runner()

        runner.start()
        testScheduler.runCurrent()
        pageRequested.await(GATE_SECONDS, TimeUnit.SECONDS)
        runner.cancel()
        val finished = runner.awaitFinished()

        assertThat(finished.outcome).isEqualTo(CatalogRefreshOutcome.Cancelled)
        assertThat(RoomCatalogRepository(db.catalogDao()).observeCatalog().first().isEmpty).isTrue()
    }

    @Test
    fun cancelBeforeTheRunStartedStillEndsIt() = runTest {
        val runner = runner()

        runner.start()
        runner.cancel()
        testScheduler.runCurrent()

        assertThat(runner.state.value).isEqualTo(CatalogRefreshState.Finished(CatalogRefreshOutcome.Cancelled))
        assertThat(server.requestCount).isEqualTo(0)
    }

    private fun ok(body: String) = MockResponse.Builder().body(Buffer().writeUtf8(body)).build()

    private companion object {
        const val TEA = "/morgennebel-sencha/XY0100"
        const val GATE_SECONDS = 10L
    }
}
