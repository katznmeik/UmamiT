package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.network.ShopClient
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
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
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The first start in the background (SCR-01, ONB-06, D-054): the seeder, then the seed photo
 * download, on the real database and against MockWebServer. The photos only arrive because the
 * teas exist first: the downloader sends nothing before them. Synthetic images only (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FirstStartTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val settings = FlakySeedMarkerSettings()
    private val data = TestDatabase(settings = settings)
    private val requests = CopyOnWriteArrayList<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        val jpeg = syntheticJpeg(width = 320, height = 240)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                requests += path
                return when (path) {
                    "/robots.txt" -> MockResponse.Builder().body("User-agent: *\nAllow: /\n").build()

                    SeedData.TANABATA_PHOTO_PATH, SeedData.HISUI_PHOTO_PATH ->
                        MockResponse.Builder().addHeader(
                            "Content-Type",
                            "image/jpeg",
                        ).body(Buffer().write(jpeg)).build()

                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        // The first start's work ends before the database closes (#50).
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        server.close()
        data.close()
    }

    private fun firstStart(): FirstStart {
        val seeder = SeedDataSeeder(data.db, data.teas, data.recipes, data.vessels, data.stock, settings, data.time)
        val photos = SeedPhotoDownloader(
            client = ShopClient.forTesting(baseUrl = server.url("/")),
            db = data.db,
            teas = data.teas,
            settings = settings,
            photoFiles = PhotoFiles(File(folder.root, "photos")),
        )
        return FirstStart(seeder, { photos }, settings, scope)
    }

    private suspend fun teaCount() = data.teas.observeTeas().first().size

    private suspend fun cover(teaId: String) = data.teas.observeTea(teaId).first()?.coverPhoto

    @Test
    fun `the first start seeds the teas, then downloads their photos`() = runBlocking {
        val firstStart = firstStart()

        firstStart.start().join()

        assertThat(settings.seedDataCreated.first()).isTrue()
        assertThat(cover(SeedData.TANABATA_TEA_ID)).isNotNull()
        assertThat(cover(SeedData.HISUI_TEA_ID)).isNotNull()
        assertThat(firstStart.photoProgress.value).isEqualTo(SeedPhotoProgress.Done(stored = 2))
        assertThat(requests.first()).isEqualTo("/robots.txt")
    }

    @Test
    fun `it runs once per process, and a later start sends nothing`() = runBlocking {
        val firstStart = firstStart()
        val job = firstStart.start()

        assertThat(firstStart.start()).isSameInstanceAs(job)
        job.join()
        val sent = requests.size
        firstStart().start().join()

        assertThat(requests).hasSize(sent)
    }

    @Test
    fun `Ueberspringen before the download leaves the placeholders and sends no photo request`() = runBlocking {
        val firstStart = firstStart()

        firstStart.skipPhotos()
        firstStart.start().join()

        assertThat(settings.seedDataCreated.first()).isTrue()
        assertThat(cover(SeedData.TANABATA_TEA_ID)).isNull()
        assertThat(requests.filter { it != "/robots.txt" }).isEmpty()
    }

    @Test
    fun `a failed first seed is created at the next app start, without the photo download`() = runBlocking {
        settings.failSeedMarkerReads = true
        firstStart().start().join()
        assertThat(teaCount()).isEqualTo(0)
        settings.update { it.copy(onboardingCompleted = true) } // *Weiter*
        settings.failSeedMarkerReads = false

        firstStart().retrySeedAtAppStart().join() // the next process start

        assertThat(settings.seedDataCreated.first()).isTrue()
        assertThat(teaCount()).isEqualTo(SeedData.teas.size)
        assertThat(cover(SeedData.TANABATA_TEA_ID)).isNull()
        assertThat(requests).isEmpty()
    }

    @Test
    fun `the retry seeds only once, also over several app starts`() = runBlocking {
        settings.update { it.copy(onboardingCompleted = true) }

        val first = firstStart()
        val job = first.retrySeedAtAppStart()
        assertThat(first.retrySeedAtAppStart()).isSameInstanceAs(job)
        job.join()
        firstStart().retrySeedAtAppStart().join()

        assertThat(teaCount()).isEqualTo(SeedData.teas.size)
        assertThat(requests).isEmpty()
    }

    @Test
    fun `the retry leaves the seed to the setup page while the setup is not done`() = runBlocking {
        firstStart().retrySeedAtAppStart().join()

        assertThat(settings.seedDataCreated.first()).isFalse()
        assertThat(teaCount()).isEqualTo(0)
    }

    @Test
    fun `a seed that fails again at app start stays due and throws nothing`() = runBlocking {
        settings.update { it.copy(onboardingCompleted = true) }
        settings.failSeedMarkerReads = true

        val job = firstStart().retrySeedAtAppStart()
        job.join()

        assertThat(job.isCancelled).isFalse()
        settings.failSeedMarkerReads = false
        assertThat(settings.seedDataCreated.first()).isFalse()
        assertThat(teaCount()).isEqualTo(0)
    }
}

/** [FakeSettingsRepository] whose seed marker read can fail like an unreadable settings file. */
private class FlakySeedMarkerSettings(private val fake: FakeSettingsRepository = FakeSettingsRepository()) :
    SettingsRepository by fake {
    @Volatile
    var failSeedMarkerReads = false

    override val seedDataCreated: Flow<Boolean> = flow {
        if (failSeedMarkerReads) throw IOException("synthetic read failure")
        emit(fake.seedDataCreated.first())
    }
}
