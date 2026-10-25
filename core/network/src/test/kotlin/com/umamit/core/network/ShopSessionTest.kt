package com.umamit.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The shared polite shop session (#63; CLAUDE.md rule 7, 08 §2.8, RFC 9309, D-050, D-074,
 * D-076) against a synthetic shop on MockWebServer: robots.txt once, its status rules, the stop
 * causes, a disallowed URL never fetched and the refresh's retries of connection errors. No
 * shop content is used (D-016).
 */
class ShopSessionTest {
    private val server = MockWebServer()

    /** What the synthetic shop answers per path; unknown paths are 404. */
    private val routes = mutableMapOf<String, () -> MockResponse>()

    /** Every path the shop saw, in order. */
    private val requested = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requested += request.url.encodedPath
                return routes[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        routes[ROBOTS] = { ok("User-agent: *\nAllow: /\nDisallow: */?\n") }
        routes[PAGE] = { ok("page") }
    }

    @After
    fun tearDown() = server.close()

    private fun client() = ShopClient.forTesting(baseUrl = server.url("/"))

    private suspend fun openSession() = ShopSession.open(client())

    @Test
    fun readsRobotsTxtOnceThenFetchesAllowedPages() = runTest {
        routes[OTHER] = { ok("other") }
        val session = openSession()

        val first = session.fetch(session.resolve(PAGE))
        val second = session.fetch(session.resolve(OTHER))

        assertThat((first as ShopFetch.Fetched).body.decodeToString()).isEqualTo("page")
        assertThat((second as ShopFetch.Fetched).body.decodeToString()).isEqualTo("other")
        assertThat(session.stopCause).isNull()
        assertThat(requested).containsExactly(ROBOTS, PAGE, OTHER).inOrder()
    }

    @Test
    fun aDisallowedUrlIsNeverFetched() = runTest {
        routes[ROBOTS] = { ok("User-agent: *\nDisallow: /media/\nDisallow: */?\n") }
        routes[IMAGE] = { ok("image") }
        val session = openSession()

        assertThat(session.isAllowed(session.resolve(IMAGE))).isFalse()
        assertThat(session.fetch(session.resolve(IMAGE))).isEqualTo(ShopFetch.Disallowed)
        assertThat(session.fetch(server.url("/tee/gruener-tee/?p=2"))).isEqualTo(ShopFetch.Disallowed)
        assertThat(session.isAllowed(session.resolve(PAGE))).isTrue()
        assertThat(session.fetch(session.resolve(PAGE))).isInstanceOf(ShopFetch.Fetched::class.java)

        assertThat(session.stopCause).isNull()
        assertThat(requested).containsExactly(ROBOTS, PAGE).inOrder()
    }

    @Test
    fun theOwnUserAgentGroupWinsOverTheStarGroup() = runTest {
        routes[ROBOTS] = { ok("User-agent: *\nDisallow: /\n\nUser-agent: UmamiT\nAllow: /\n") }
        val session = openSession()

        assertThat(session.fetch(session.resolve(PAGE))).isInstanceOf(ShopFetch.Fetched::class.java)
    }

    @Test
    fun aMissingRobotsTxtAllowsEverything() = runTest {
        listOf(404, 410).forEach { code ->
            requested.clear()
            routes[ROBOTS] = { MockResponse.Builder().code(code).build() }
            val session = openSession()

            assertThat(session.fetch(session.resolve(PAGE))).isInstanceOf(ShopFetch.Fetched::class.java)
            assertThat(requested).containsExactly(ROBOTS, PAGE).inOrder()
        }
    }

    @Test
    fun anUnusableRobotsTxtDisallowsEverythingWithoutStopping() = runTest {
        listOf(301, 400, 418).forEach { code ->
            requested.clear()
            routes[ROBOTS] = { MockResponse.Builder().code(code).addHeader("Location", "/elsewhere").build() }
            val session = openSession()

            assertThat(session.stopCause).isNull()
            assertThat(session.isAllowed(session.resolve(PAGE))).isFalse()
            assertThat(session.fetch(session.resolve(PAGE))).isEqualTo(ShopFetch.Disallowed)
            assertThat(requested).containsExactly(ROBOTS)
        }
    }

    @Test
    fun aStopAnswerToRobotsTxtStopsTheSessionBeforeAnyPage() = runTest {
        STOPS.forEach { (response, cause) ->
            requested.clear()
            routes[ROBOTS] = response
            val session = openSession()

            assertThat(session.stopCause).isEqualTo(cause)
            assertThat(session.fetch(session.resolve(PAGE))).isEqualTo(ShopFetch.Stopped(cause))
            assertThat(requested).containsExactly(ROBOTS)
        }
    }

    @Test
    fun eachStopCauseOnAPageEndsTheSessionWithNoFurtherRequest() = runTest {
        STOPS.forEach { (response, cause) ->
            requested.clear()
            routes[PAGE] = response
            routes[OTHER] = { ok("other") }
            val session = openSession()

            assertThat(session.fetch(session.resolve(PAGE))).isEqualTo(ShopFetch.Stopped(cause))
            assertThat(session.stopCause).isEqualTo(cause)
            assertThat(session.fetch(session.resolve(OTHER))).isEqualTo(ShopFetch.Stopped(cause))
            assertThat(requested).containsExactly(ROBOTS, PAGE).inOrder()
        }
    }

    @Test
    fun offlineStopsTheSession() = runTest {
        val session = openSession()
        server.close()

        assertThat(session.fetch(session.resolve(PAGE))).isEqualTo(ShopFetch.Stopped(StopCause.NETWORK))
        assertThat(session.stopCause).isEqualTo(StopCause.NETWORK)
    }

    @Test
    fun offlineAtTheStartGivesAStoppedSession() = runTest {
        val client = client()
        server.close()

        val session = ShopSession.open(client)

        assertThat(session.stopCause).isEqualTo(StopCause.NETWORK)
        assertThat(session.fetch(session.resolve(PAGE))).isEqualTo(ShopFetch.Stopped(StopCause.NETWORK))
    }

    @Test
    fun aMissingPageOrAnotherClientErrorLeavesOnlyThatPageOut() = runTest {
        routes[OTHER] = { MockResponse.Builder().code(400).build() }
        routes[MOVED] = { MockResponse.Builder().code(301).addHeader("Location", PAGE).build() }
        val session = openSession()

        assertThat(session.fetch(session.resolve("/gone"))).isEqualTo(ShopFetch.Unavailable(404))
        assertThat(session.fetch(session.resolve(OTHER))).isEqualTo(ShopFetch.Unavailable(400))
        assertThat(session.fetch(session.resolve(MOVED))).isEqualTo(ShopFetch.Unavailable(301))
        assertThat(session.fetch(session.resolve(PAGE))).isInstanceOf(ShopFetch.Fetched::class.java)

        assertThat(session.stopCause).isNull()
        // The redirect is not followed: PAGE is requested once, by the last fetch.
        assertThat(requested).containsExactly(ROBOTS, "/gone", OTHER, MOVED, PAGE).inOrder()
    }

    @Test
    fun aFetchQueuedBehindTheStoppingOneIsNeverSent() = runTest {
        val pageArrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        routes[PAGE] = {
            pageArrived.countDown()
            release.await(BLOCK_SECONDS, TimeUnit.SECONDS)
            MockResponse.Builder().code(429).build()
        }
        routes[OTHER] = { ok("other") }
        val session = openSession()

        val (stopping, queued) = withContext(Dispatchers.Default) {
            try {
                val stopping = async { session.fetch(session.resolve(PAGE)) }
                runInterruptible { pageArrived.await(BLOCK_SECONDS, TimeUnit.SECONDS) }
                // Started while the 429 is still on its way; the client alone would send it next.
                val queued = async { session.fetch(session.resolve(OTHER)) }
                delay(QUEUE_MS)
                release.countDown()
                stopping.await() to queued.await()
            } finally {
                release.countDown()
            }
        }

        assertThat(stopping).isEqualTo(ShopFetch.Stopped(StopCause.TOO_MANY_REQUESTS))
        assertThat(queued).isEqualTo(ShopFetch.Stopped(StopCause.TOO_MANY_REQUESTS))
        Thread.sleep(AFTER_STOP_MS)
        assertThat(requested).containsExactly(ROBOTS, PAGE).inOrder()
    }

    @Test
    fun aForeignHostIsRejectedWithoutARequest() = runTest {
        val session = openSession()

        val error = runCatching { session.fetch("https://cdn.example.com/a.jpg".toHttpUrl()) }.exceptionOrNull()

        assertThat(error).isInstanceOf(HostNotAllowedException::class.java)
        assertThat(session.isAllowed("https://cdn.example.com/a.jpg".toHttpUrl())).isFalse()
        assertThat(requested).containsExactly(ROBOTS)
    }

    @Test
    fun aRefreshSessionRetriesADroppedConnectionAfter2And4Seconds() = runTest {
        val drops = dropping(times = 2, then = { ok("page") })
        routes[PAGE] = drops
        val session = ShopSession.open(client(), NetworkRetry.REFRESH)
        val start = testScheduler.currentTime

        val answer = session.fetch(session.resolve(PAGE))

        assertThat((answer as ShopFetch.Fetched).body.decodeToString()).isEqualTo("page")
        assertThat(testScheduler.currentTime - start).isEqualTo(2_000L + 4_000L)
        assertThat(session.stopCause).isNull()
        assertThat(requested).containsExactly(ROBOTS, PAGE, PAGE, PAGE).inOrder()
    }

    @Test
    fun aRefreshSessionStopsAfterThreeRetriesWithNoFurtherRequest() = runTest {
        routes[PAGE] = dropping(times = Int.MAX_VALUE, then = { ok("never") })
        val session = ShopSession.open(client(), NetworkRetry.REFRESH)
        val start = testScheduler.currentTime

        val answer = session.fetch(session.resolve(PAGE))

        assertThat(answer).isEqualTo(ShopFetch.Stopped(StopCause.NETWORK))
        assertThat(testScheduler.currentTime - start).isEqualTo(2_000L + 4_000L + 8_000L)
        assertThat(session.fetch(session.resolve(OTHER))).isEqualTo(ShopFetch.Stopped(StopCause.NETWORK))
        assertThat(requested).containsExactly(ROBOTS, PAGE, PAGE, PAGE, PAGE).inOrder()
    }

    @Test
    fun aRefreshSessionRetriesRobotsTxtToo() = runTest {
        routes[ROBOTS] = dropping(times = 1, then = { ok("User-agent: *\nDisallow: /tee/\n") })

        val session = ShopSession.open(client(), NetworkRetry.REFRESH)

        assertThat(session.stopCause).isNull()
        assertThat(session.fetch(session.resolve(PAGE))).isEqualTo(ShopFetch.Disallowed)
        assertThat(requested).containsExactly(ROBOTS, ROBOTS)
    }

    @Test
    fun stopSignalsAndHttpErrorsAreNeverRetried() = runTest {
        val notRetried = STOPS.map { (response, cause) -> response to ShopFetch.Stopped(cause) } +
            listOf({ MockResponse.Builder().code(404).build() } to ShopFetch.Unavailable(404))
        notRetried.forEach { (response, expected) ->
            requested.clear()
            routes[PAGE] = response
            val session = ShopSession.open(client(), NetworkRetry.REFRESH)

            assertThat(session.fetch(session.resolve(PAGE))).isEqualTo(expected)
            assertThat(requested).containsExactly(ROBOTS, PAGE).inOrder()
        }
    }

    @Test
    fun anOversizedAnswerIsNeverRetried() = runTest {
        routes[PAGE] = { MockResponse.Builder().body(Buffer().write(ByteArray(MAX_BODY_BYTES.toInt() + 1))).build() }
        val session = ShopSession.open(client(), NetworkRetry.REFRESH)

        assertThat(session.fetch(session.resolve(PAGE))).isEqualTo(ShopFetch.Stopped(StopCause.NETWORK))
        assertThat(requested).containsExactly(ROBOTS, PAGE).inOrder()
    }

    @Test
    fun byDefaultASessionNeverRetries() = runTest {
        routes[PAGE] = dropping(times = 1, then = { ok("page") })
        val session = openSession()

        assertThat(session.fetch(session.resolve(PAGE))).isEqualTo(ShopFetch.Stopped(StopCause.NETWORK))
        assertThat(requested).containsExactly(ROBOTS, PAGE).inOrder()
    }

    /** Drops the connection before answering for the first [times] requests, then answers [then]. */
    private fun dropping(times: Int, then: () -> MockResponse): () -> MockResponse {
        var dropped = 0
        return {
            if (dropped < times) {
                dropped++
                MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()
            } else {
                then()
            }
        }
    }

    private fun ok(body: String) = MockResponse.Builder().body(body).build()

    private companion object {
        const val ROBOTS = "/robots.txt"
        const val PAGE = "/tee/gruener-tee/japan/sencha/a"
        const val OTHER = "/tee/gruener-tee/japan/gyokuro/b"
        const val MOVED = "/tee/moved"
        const val IMAGE = "/media/ab/c.jpg"
        const val BLOCK_SECONDS = 10L
        const val QUEUE_MS = 300L
        const val AFTER_STOP_MS = 700L

        /** Every stop answer of 08 §2.8 and D-050 with the cause it gives. */
        val STOPS: List<Pair<() -> MockResponse, StopCause>> = listOf(
            { MockResponse.Builder().code(403).addHeader("cf-mitigated", "challenge").build() } to
                StopCause.CHALLENGE,
            { MockResponse.Builder().code(503).body("<title>Just a moment...</title>").build() } to
                StopCause.CHALLENGE,
            {
                MockResponse.Builder().code(403).addHeader("CF-RAY", "x").addHeader("Server", "cloudflare")
                    .body("Sorry, you have been blocked").build()
            } to StopCause.BLOCKED,
            { MockResponse.Builder().code(429).build() } to StopCause.TOO_MANY_REQUESTS,
            { MockResponse.Builder().code(200).addHeader("Retry-After", "30").body("ok").build() } to
                StopCause.RETRY_AFTER,
            { MockResponse.Builder().code(503).addHeader("Retry-After", "0").build() } to StopCause.RETRY_AFTER,
            { MockResponse.Builder().code(401).build() } to StopCause.HTTP_ERROR,
            { MockResponse.Builder().code(403).build() } to StopCause.HTTP_ERROR,
            { MockResponse.Builder().code(500).build() } to StopCause.HTTP_ERROR,
            { MockResponse.Builder().code(503).build() } to StopCause.HTTP_ERROR,
        )
    }
}
