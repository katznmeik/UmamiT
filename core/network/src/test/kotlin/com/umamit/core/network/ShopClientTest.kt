package com.umamit.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory

/** The shop client's rule-7 guarantees (GEN-01 AC1, D-036, CLAUDE.md rule 7), against MockWebServer. */
class ShopClientTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private suspend fun assertRejected(request: suspend () -> ShopResponse) {
        val error = runCatching { request() }.exceptionOrNull()
        assertThat(error).isInstanceOf(HostNotAllowedException::class.java)
    }

    private fun testClient() = ShopClient.forTesting(baseUrl = server.url("/"), appVersion = "1.2.3")

    @Test
    fun productionClientRejectsEveryHostButTheShopWithoutContactingIt() = runTest {
        val client = ShopClient.create(appVersion = "1.2.3")

        assertRejected { client.get(server.url("/robots.txt")) }
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun productionClientAllowsOnlyHttpsOnTheShopHost() {
        val client = ShopClient.create(appVersion = "1.2.3")

        assertThat(client.isAllowed("https://www.tee-kontor-kiel.de/media/a.jpg".toHttpUrl())).isTrue()
        assertThat(client.isAllowed("http://www.tee-kontor-kiel.de/media/a.jpg".toHttpUrl())).isFalse()
        assertThat(client.isAllowed("https://tee-kontor-kiel.de/".toHttpUrl())).isFalse()
        assertThat(client.isAllowed("https://www.tee-kontor-kiel.de.example.com/".toHttpUrl())).isFalse()
        assertThat(client.isAllowed("https://www.tee-kontor-kiel.de:8443/".toHttpUrl())).isFalse()
        assertThat(client.isAllowed("https://cdn.example.com/a.jpg".toHttpUrl())).isFalse()
    }

    @Test
    fun allowListResistsHostTricks() {
        val client = ShopClient.create(appVersion = "1.2.3")

        // Same host in upper case: allowed (hosts are case-insensitive and normalised).
        assertThat(client.isAllowed("https://WWW.TEE-KONTOR-KIEL.DE/".toHttpUrl())).isTrue()
        assertThat(client.isAllowed("https://www.tee-kontor-kiel.de./".toHttpUrl())).isFalse()
        assertThat(client.isAllowed("https://www.tee-kontor-kiel.de@evil.example/".toHttpUrl())).isFalse()
        assertThat(client.isAllowed("https://93.184.216.34/".toHttpUrl())).isFalse()
        // Cyrillic „і" in place of the Latin „i" (an IDN homograph).
        assertThat(client.isAllowed("https://www.tee-kontor-k${0x0456.toChar()}el.de/".toHttpUrl())).isFalse()
    }

    @Test
    fun resolveAcceptsOnlyAbsolutePaths() {
        val client = ShopClient.create(appVersion = "1.2.3")

        assertThat(client.resolve("/robots.txt").toString()).isEqualTo("https://www.tee-kontor-kiel.de/robots.txt")
        listOf("//evil.example/x", "https://evil.example/x", "relative/x").forEach { path ->
            assertThat(runCatching { client.resolve(path) }.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun allowListInterceptorsRejectAForeignHostWithoutSendingIt() {
        server.enqueue(MockResponse.Builder().build())
        server.enqueue(MockResponse.Builder().build())
        val applicationLevel = OkHttpClient.Builder().addInterceptor(hostAllowList { false }).build()
        val networkLevel = OkHttpClient.Builder().addNetworkInterceptor(hostAllowList { false }).build()

        listOf(applicationLevel, networkLevel).forEach { client ->
            val error = runCatching { client.newCall(Request.Builder().url(server.url("/a")).build()).execute() }
            assertThat(error.exceptionOrNull()).isInstanceOf(HostNotAllowedException::class.java)
        }
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun aSecondNetworkAttemptOfACallIsRefusedBeforeItIsSent() {
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().body("second").build())
        // An authenticator makes OkHttp repeat the call, like any OkHttp follow-up would.
        val client = OkHttpClient.Builder()
            .addInterceptor(tagAttempts())
            .addNetworkInterceptor(singleAttempt())
            .authenticator { _, response -> response.request }
            .build()

        val result = runCatching { client.newCall(Request.Builder().url(server.url("/a")).build()).execute() }

        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun retryAfterZeroOn503IsAStopSignalAndNeverRepeatedByOkHttp() = runTest {
        server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "0").build())
        server.enqueue(MockResponse.Builder().body("second").build())

        val response = testClient().get(server.url("/robots.txt"))

        assertThat(response).isEqualTo(ShopResponse.Blocked(BlockReason.RETRY_AFTER))
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun rateLimitHoldsOnTheWireDespiteConnectionSetupTime() = runTest {
        val arrivals = CopyOnWriteArrayList<Long>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                arrivals += System.nanoTime()
                return MockResponse.Builder().body("ok").build()
            }
        }
        // A slow first connect (DNS + TCP + TLS on the phone); the second request reuses it.
        val client = ShopClient.forTesting(server.url("/"), socketFactory = SlowConnectSocketFactory(CONNECT_DELAY_MS))

        withContext(Dispatchers.Default) {
            client.get(server.url("/robots.txt"))
            client.get(server.url("/media/a.jpg"))
        }

        assertThat(arrivals).hasSize(2)
        assertThat(TimeUnit.NANOSECONDS.toMillis(arrivals[1] - arrivals[0])).isAtLeast(MIN_GAP_MS)
    }

    @Test
    fun aCallCancelledWhileQueuedIsNeverSent() = runTest {
        val firstArrived = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                firstArrived.countDown()
                Thread.sleep(SLOW_RESPONSE_MS)
                return MockResponse.Builder().body("ok").build()
            }
        }
        val client = testClient()

        withContext(Dispatchers.Default) {
            val first = async { client.get(server.url("/first")) }
            val queued = async { client.get(server.url("/queued")) }
            runInterruptible { firstArrived.await(5, TimeUnit.SECONDS) }
            queued.cancel()
            assertThat(first.await()).isInstanceOf(ShopResponse.Success::class.java)
            // Long enough for the queued call to have gone out after the rate-limit gap.
            Thread.sleep(SLOW_RESPONSE_MS + 2 * MIN_GAP_MS)
        }

        assertThat(server.requestCount).isEqualTo(1)
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/first")
    }

    @Test
    fun bodiesOverTheLimitAreNetworkErrors() = runTest {
        val tooBig = Buffer().write(ByteArray(MAX_BODY_BYTES.toInt() + 1))
        server.enqueue(MockResponse.Builder().body(tooBig.copy()).build())
        server.enqueue(MockResponse.Builder().chunkedBody(tooBig, CHUNK_BYTES).build())
        val client = testClient()

        assertThat(client.get(server.url("/declared"))).isInstanceOf(ShopResponse.NetworkError::class.java)
        assertThat(client.get(server.url("/chunked"))).isInstanceOf(ShopResponse.NetworkError::class.java)
    }

    @Test
    fun cloudflareBlockPagesAreStopSignals() = runTest {
        server.enqueue(
            MockResponse.Builder().code(403).addHeader("Server", "cloudflare").addHeader("CF-RAY", "abc")
                .body("<title>Attention Required! | Cloudflare</title> Sorry, you have been blocked").build(),
        )
        server.enqueue(MockResponse.Builder().code(503).addHeader("CF-RAY", "abc").build())
        server.enqueue(MockResponse.Builder().code(401).body("<div class=\"cf-turnstile\"></div>").build())
        val client = testClient()

        assertThat(client.get(server.url("/a"))).isEqualTo(ShopResponse.Blocked(BlockReason.BLOCKED))
        assertThat(client.get(server.url("/b"))).isEqualTo(ShopResponse.Blocked(BlockReason.BLOCKED))
        assertThat(client.get(server.url("/c"))).isEqualTo(ShopResponse.Blocked(BlockReason.CHALLENGE))
    }

    @Test
    fun testClientRejectsAnotherHostOrPort() = runTest {
        val other = MockWebServer().apply { start() }
        try {
            val client = testClient()

            assertRejected { client.get(other.url("/")) }
            assertRejected { client.get("https://example.com/".toHttpUrl()) }
            assertThat(other.requestCount).isEqualTo(0)
            assertThat(server.requestCount).isEqualTo(0)
        } finally {
            other.close()
        }
    }

    @Test
    fun allowListedRequestReturnsTheBody() = runTest {
        server.enqueue(MockResponse.Builder().body("User-agent: *\nAllow: /\n").build())

        val response = testClient().get(server.url("/robots.txt"))

        assertThat(response).isInstanceOf(ShopResponse.Success::class.java)
        assertThat((response as ShopResponse.Success).body.decodeToString()).isEqualTo("User-agent: *\nAllow: /\n")
    }

    @Test
    fun sendsTheHonestUserAgentAndNeverACookie() = runTest {
        server.enqueue(MockResponse.Builder().addHeader("Set-Cookie", "session=abc; Path=/").build())
        server.enqueue(MockResponse.Builder().build())
        val client = testClient()

        client.get(server.url("/a"))
        client.get(server.url("/b"))

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertThat(first.headers["User-Agent"]).isEqualTo("UmamiT/1.2.3 (private app)")
        assertThat(second.headers["User-Agent"]).isEqualTo("UmamiT/1.2.3 (private app)")
        assertThat(second.headers["Cookie"]).isNull()
    }

    @Test
    fun concurrentRequestsShareOneConnectionOneAtATime() = runTest {
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                Thread.sleep(SERVER_WORK_MS)
                inFlight.decrementAndGet()
                return MockResponse.Builder().body("ok").build()
            }
        }
        val client = testClient()

        val responses = withContext(Dispatchers.Default) {
            (1..3).map { async { client.get(server.url("/page-$it")) } }.awaitAll()
        }

        assertThat(responses.all { it is ShopResponse.Success }).isTrue()
        assertThat(maxInFlight.get()).isEqualTo(1)
        assertThat((1..3).map { server.takeRequest().connectionIndex }).containsExactly(0, 0, 0)
    }

    @Test
    fun requestsStartAtMostTwicePerSecond() = runTest {
        val arrivals = CopyOnWriteArrayList<Long>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                arrivals += System.nanoTime()
                return MockResponse.Builder().body("ok").build()
            }
        }
        val client = testClient()

        withContext(Dispatchers.Default) {
            (1..4).map { async { client.get(server.url("/page-$it")) } }.awaitAll()
        }

        val gapsMs = arrivals.zipWithNext { a, b -> (b - a) / 1_000_000 }
        assertThat(gapsMs).hasSize(3)
        // 500 ms between starts; a little slack for the server thread's own scheduling.
        gapsMs.forEach { assertThat(it).isAtLeast(MIN_GAP_MS) }
    }

    @Test
    fun http429IsAStopSignal() = runTest {
        server.enqueue(MockResponse.Builder().code(429).build())

        assertThat(testClient().get(server.url("/a"))).isEqualTo(ShopResponse.Blocked(BlockReason.TOO_MANY_REQUESTS))
    }

    @Test
    fun anyRetryAfterIsAStopSignal() = runTest {
        server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "120").build())
        server.enqueue(MockResponse.Builder().addHeader("Retry-After", "5").body("ok").build())
        val client = testClient()

        assertThat(client.get(server.url("/a"))).isEqualTo(ShopResponse.Blocked(BlockReason.RETRY_AFTER))
        assertThat(client.get(server.url("/b"))).isEqualTo(ShopResponse.Blocked(BlockReason.RETRY_AFTER))
    }

    @Test
    fun cloudflareChallengeIsAStopSignal() = runTest {
        server.enqueue(MockResponse.Builder().code(403).addHeader("cf-mitigated", "challenge").build())
        server.enqueue(
            MockResponse.Builder().code(503).body("<html><title>Just a moment...</title>_cf_chl_opt</html>").build(),
        )
        server.enqueue(MockResponse.Builder().code(403).body("<script src=\"/cdn-cgi/challenge-platform/x\">").build())
        val client = testClient()

        repeat(3) {
            assertThat(client.get(server.url("/a"))).isEqualTo(ShopResponse.Blocked(BlockReason.CHALLENGE))
        }
    }

    @Test
    fun plainErrorsAreNotStopSignals() = runTest {
        server.enqueue(MockResponse.Builder().code(403).body("Forbidden").build())
        server.enqueue(MockResponse.Builder().code(404).build())
        val client = testClient()

        assertThat(client.get(server.url("/a"))).isEqualTo(ShopResponse.HttpError(403))
        assertThat(client.get(server.url("/b"))).isEqualTo(ShopResponse.HttpError(404))
    }

    @Test
    fun redirectsAreNeverFollowed() = runTest {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/b").toString()).build())
        server.enqueue(MockResponse.Builder().code(301).addHeader("Location", "http://example.com/a.jpg").build())
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "https://example.com/a.jpg").build())
        val client = testClient()

        assertThat(client.get(server.url("/a"))).isEqualTo(ShopResponse.HttpError(302))
        assertThat(client.get(server.url("/c"))).isEqualTo(ShopResponse.HttpError(301))
        assertThat(client.get(server.url("/d"))).isEqualTo(ShopResponse.HttpError(302))
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun unreachableServerIsANetworkError() = runTest {
        val url = server.url("/a")
        server.close()

        assertThat(testClient().get(url)).isInstanceOf(ShopResponse.NetworkError::class.java)
    }

    private companion object {
        const val SERVER_WORK_MS = 50L
        const val MIN_GAP_MS = 480L
        const val CONNECT_DELAY_MS = 400L
        const val SLOW_RESPONSE_MS = 300L
        const val CHUNK_BYTES = 64 * 1024
    }
}

/** Plain sockets whose connect takes [delayMs] longer, like connection setup on a phone. */
private class SlowConnectSocketFactory(private val delayMs: Long) : SocketFactory() {
    override fun createSocket(): Socket = object : Socket() {
        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            Thread.sleep(delayMs)
            super.connect(endpoint, timeout)
        }
    }

    override fun createSocket(host: String?, port: Int): Socket = unsupported()

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = unsupported()

    override fun createSocket(host: InetAddress?, port: Int): Socket = unsupported()

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("OkHttp uses createSocket()")
}
