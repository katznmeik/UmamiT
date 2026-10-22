package com.umamit.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SeedPhotoProgress.Failed
import com.umamit.core.data.SeedPhotoProgress.FailureCause
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.network.ShopClient
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The one-time seed photo download (ONB-06, GEN-01 (b), CLAUDE.md rule 7) against MockWebServer.
 * The images are synthetic JPEG bytes; no shop photo is committed (D-016, rule 3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeedPhotoDownloaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val data = TestDatabase()
    private val settings = FakeSettingsRepository()
    private val photosDir by lazy { File(folder.root, "photos") }

    /** What the synthetic shop answers per path; unknown paths are 404. */
    private val routes = mutableMapOf<String, () -> MockResponse>()

    /** Every request the shop saw, in order, with its arrival time. */
    private val requests = CopyOnWriteArrayList<Pair<RecordedRequest, Long>>()

    private val tanabataJpeg = syntheticJpeg(width = 800, height = 600)
    private val hisuiJpeg = syntheticJpeg(width = 640, height = 640)

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request to System.nanoTime()
                return routes[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        routes[ROBOTS] = { ok("User-agent: *\nAllow: /\nDisallow: */?\n") }
        routes[SeedData.TANABATA_PHOTO_PATH] = { jpeg(tanabataJpeg) }
        routes[SeedData.HISUI_PHOTO_PATH] = { jpeg(hisuiJpeg) }
    }

    @After
    fun tearDown() {
        server.close()
        data.close()
    }

    private suspend fun seedTeas() {
        data.teas.saveTea(SeedData.tanabata)
        data.teas.saveTea(SeedData.hisui)
    }

    private fun downloader(settings: FakeSettingsRepository = this.settings) = SeedPhotoDownloader(
        client = ShopClient.forTesting(baseUrl = server.url("/")),
        db = data.db,
        teas = data.teas,
        settings = settings,
        photoFiles = PhotoFiles(photosDir),
    )

    private suspend fun cover(teaId: String) = data.teas.observeTea(teaId).first()?.coverPhoto

    private fun requestedPaths() = requests.map { it.first.url.encodedPath }

    @Test
    fun happyPathStoresBothPhotosAsJpegCoversOfTheirTeas() = runTest {
        seedTeas()

        val result = downloader().downloadIfNeeded()

        assertThat(result).isEqualTo(SeedPhotoProgress.Done(stored = 2))
        val tanabata = checkNotNull(cover(SeedData.TANABATA_TEA_ID))
        assertThat(tanabata.owner).isEqualTo(PhotoOwner.OfTea(SeedData.TANABATA_TEA_ID))
        assertThat(tanabata.origin).isEqualTo(PhotoOrigin.CATALOG_COPY)
        assertThat(tanabata.widthPx to tanabata.heightPx).isEqualTo(800 to 600)
        val tanabataFile = File(photosDir, "${tanabata.id}.jpg")
        assertThat(tanabata.byteSize).isEqualTo(tanabataFile.length())
        assertThat(decodedSize(tanabataFile.readBytes())).isEqualTo(800 to 600)
        val hisui = checkNotNull(cover(SeedData.HISUI_TEA_ID))
        assertThat(hisui.origin).isEqualTo(PhotoOrigin.CATALOG_COPY)
        assertThat(hisui.widthPx to hisui.heightPx).isEqualTo(640 to 640)
        assertThat(decodedSize(File(photosDir, "${hisui.id}.jpg").readBytes())).isEqualTo(640 to 640)
        assertThat(photosDir.list()).asList().containsExactly("${tanabata.id}.jpg", "${hisui.id}.jpg")
    }

    @Test
    fun theRunIsRecordedBeforeTheFirstRequest() = runTest {
        seedTeas()
        var markedAtFirstRequest: Boolean? = null
        routes[ROBOTS] = {
            markedAtFirstRequest = runBlocking { settings.seedPhotosAttempted.first() }
            ok("")
        }

        downloader().downloadIfNeeded()

        assertThat(markedAtFirstRequest).isTrue()
    }

    @Test
    fun largePhotosAreScaledTo2048PxAndReencodedWithoutMetadata() = runTest {
        seedTeas()
        val withExif = withExifSegment(syntheticJpeg(width = 3000, height = 2000))
        routes[SeedData.TANABATA_PHOTO_PATH] = { jpeg(withExif) }

        assertThat(downloader().downloadIfNeeded()).isEqualTo(SeedPhotoProgress.Done(stored = 2))

        val tanabata = checkNotNull(cover(SeedData.TANABATA_TEA_ID))
        assertThat(tanabata.widthPx to tanabata.heightPx).isEqualTo(2048 to 1365)
        val stored = File(photosDir, "${tanabata.id}.jpg").readBytes()
        assertThat(decodedSize(stored)).isEqualTo(2048 to 1365)
        assertThat(stored.decodeToString(throwOnInvalidSequence = false)).doesNotContain("Exif")
    }

    @Test
    fun truncatedOversizedOrUndecodableImagesAreLeftOut() = runTest {
        seedTeas()
        val truncated = tanabataJpeg.copyOf(tanabataJpeg.size / 2)
        val garbage = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(GARBAGE_BYTES) { 7 } +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val huge = syntheticJpeg(width = 8200, height = 8)
        listOf(truncated, garbage, huge).forEach { body ->
            routes[SeedData.TANABATA_PHOTO_PATH] = { jpeg(body) }
            data.teas.setCoverPhoto(SeedData.HISUI_TEA_ID, null)

            assertThat(downloader(FakeSettingsRepository()).downloadIfNeeded())
                .isEqualTo(SeedPhotoProgress.Done(stored = 1))
            assertThat(cover(SeedData.TANABATA_TEA_ID)).isNull()
        }
    }

    @Test
    fun aMissingPhotoIsLeftOutAndTheRunGoesOn() = runTest {
        seedTeas()
        routes.remove(SeedData.TANABATA_PHOTO_PATH)

        assertThat(downloader().downloadIfNeeded()).isEqualTo(SeedPhotoProgress.Done(stored = 1))
        assertThat(cover(SeedData.HISUI_TEA_ID)).isNotNull()
    }

    @Test
    fun refusalsAndServerErrorsStopTheRun() = runTest {
        seedTeas()
        val stops = listOf(
            MockResponse.Builder().code(403).build() to FailureCause.HTTP_ERROR,
            MockResponse.Builder().code(500).build() to FailureCause.HTTP_ERROR,
            MockResponse.Builder().code(503).build() to FailureCause.HTTP_ERROR,
            MockResponse.Builder().code(403).addHeader("CF-RAY", "x").addHeader("Server", "cloudflare")
                .body("Sorry, you have been blocked").build() to FailureCause.BLOCKED,
            MockResponse.Builder().code(503).addHeader("Retry-After", "0").build() to FailureCause.RETRY_AFTER,
        )
        stops.forEach { (response, cause) ->
            requests.clear()
            routes[SeedData.TANABATA_PHOTO_PATH] = { response }

            assertThat(downloader(FakeSettingsRepository()).downloadIfNeeded()).isEqualTo(Failed(cause))
            assertThat(requestedPaths()).containsExactly(ROBOTS, SeedData.TANABATA_PHOTO_PATH).inOrder()
        }
        assertThat(cover(SeedData.TANABATA_TEA_ID)).isNull()
        assertThat(cover(SeedData.HISUI_TEA_ID)).isNull()
    }

    @Test
    fun aForbiddenRobotsTxtStopsInsteadOfAllowingAll() = runTest {
        seedTeas()
        routes[ROBOTS] = { MockResponse.Builder().code(403).build() }

        assertThat(downloader().downloadIfNeeded()).isEqualTo(Failed(FailureCause.HTTP_ERROR))
        assertThat(requestedPaths()).containsExactly(ROBOTS)
    }

    @Test
    fun aRobotsTxtWithAByteOrderMarkIsStillRespected() = runTest {
        seedTeas()
        routes[ROBOTS] = { ok("${0xFEFF.toChar()}User-agent: *\nDisallow: /media/\n") }

        assertThat(downloader().downloadIfNeeded()).isEqualTo(SeedPhotoProgress.Done(stored = 0))
        assertThat(requestedPaths()).containsExactly(ROBOTS)
    }

    @Test
    fun aStorageErrorFailsQuietly() = runTest {
        seedTeas()
        val notADirectory = folder.newFile("photos-is-a-file")
        val downloader = SeedPhotoDownloader(
            client = ShopClient.forTesting(baseUrl = server.url("/")),
            db = data.db,
            teas = data.teas,
            settings = settings,
            photoFiles = PhotoFiles(notADirectory),
        )

        val result = downloader.downloadIfNeeded()

        assertThat(result).isEqualTo(Failed(FailureCause.STORAGE))
        assertThat(downloader.progress.value).isEqualTo(Failed(FailureCause.STORAGE))
        assertThat(requestedPaths()).containsExactly(ROBOTS, SeedData.TANABATA_PHOTO_PATH).inOrder()
        assertThat(cover(SeedData.TANABATA_TEA_ID)).isNull()
        assertThat(settings.seedPhotosAttempted.first()).isTrue()
    }

    @Test
    fun readsRobotsTxtFirstThenTheTwoImageUrlsOfTheSpec() = runTest {
        seedTeas()

        downloader().downloadIfNeeded()

        assertThat(requestedPaths()).containsExactly(
            "/robots.txt",
            "/media/ab/15/63/1727307323/gtbiosenchatanabataaiya.jpg",
            "/media/7c/c3/7c/1727308250/gtbiosenchahisui.jpg",
        ).inOrder()
    }

    @Test
    fun requestsUseOneConnectionAtMostTwicePerSecondWithTheHonestUserAgent() = runTest {
        seedTeas()

        downloader().downloadIfNeeded()

        assertThat(requests.map { it.first.connectionIndex }).containsExactly(0, 0, 0)
        requests.map { it.second }.zipWithNext { a, b -> TimeUnit.NANOSECONDS.toMillis(b - a) }
            .forEach { gapMs -> assertThat(gapMs).isAtLeast(MIN_GAP_MS) }
        requests.forEach { assertThat(it.first.headers["User-Agent"]).isEqualTo("UmamiT/test (private app)") }
    }

    @Test
    fun progressGoesFromIdleThroughEachPhotoToDone() = runTest {
        seedTeas()
        val downloader = downloader()
        val seen = mutableListOf<SeedPhotoProgress>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { downloader.progress.toList(seen) }

        downloader.downloadIfNeeded()

        assertThat(seen).containsExactly(
            SeedPhotoProgress.Idle,
            SeedPhotoProgress.Running(current = 1, total = 2),
            SeedPhotoProgress.Running(current = 2, total = 2),
            SeedPhotoProgress.Done(stored = 2),
        ).inOrder()
    }

    @Test
    fun skipMidwayKeepsTheFirstPhotoAndThePlaceholderForTheRest() = runTest {
        seedTeas()
        val secondRequested = CountDownLatch(1)
        val release = CountDownLatch(1)
        routes[SeedData.HISUI_PHOTO_PATH] = {
            secondRequested.countDown()
            release.await(BLOCK_SECONDS, TimeUnit.SECONDS)
            jpeg(hisuiJpeg)
        }
        val downloader = downloader()

        try {
            val run = async(Dispatchers.Default) { downloader.downloadIfNeeded() }
            runInterruptible(Dispatchers.IO) { secondRequested.await(BLOCK_SECONDS, TimeUnit.SECONDS) }
            downloader.skip()

            assertThat(run.await()).isEqualTo(SeedPhotoProgress.Skipped)
        } finally {
            release.countDown()
        }
        assertThat(downloader.progress.value).isEqualTo(SeedPhotoProgress.Skipped)
        assertThat(cover(SeedData.TANABATA_TEA_ID)).isNotNull()
        assertThat(cover(SeedData.HISUI_TEA_ID)).isNull()
        assertThat(photosDir.list()).hasLength(1)
        // Nothing more goes out after the skip, not even once the rate-limit gap has passed.
        Thread.sleep(AFTER_SKIP_MS)
        assertThat(requestedPaths())
            .containsExactly(ROBOTS, SeedData.TANABATA_PHOTO_PATH, SeedData.HISUI_PHOTO_PATH).inOrder()
        assertThat(settings.seedPhotosAttempted.first()).isTrue()
    }

    @Test
    fun skipBeforeTheStartSendsNoRequest() = runTest {
        seedTeas()
        val downloader = downloader()

        downloader.skip()

        assertThat(downloader.downloadIfNeeded()).isEqualTo(SeedPhotoProgress.Skipped)
        assertThat(requests).isEmpty()
        assertThat(settings.seedPhotosAttempted.first()).isTrue()
    }

    @Test
    fun offlineFailsQuietlyAndKeepsThePlaceholders() = runTest {
        seedTeas()
        server.close()

        val result = downloader().downloadIfNeeded()

        assertThat(result).isEqualTo(Failed(FailureCause.NETWORK))
        assertThat(cover(SeedData.TANABATA_TEA_ID)).isNull()
        assertThat(cover(SeedData.HISUI_TEA_ID)).isNull()
        assertThat(settings.seedPhotosAttempted.first()).isTrue()
    }

    @Test
    fun http429StopsAfterThatResponse() = runTest {
        seedTeas()
        routes[SeedData.TANABATA_PHOTO_PATH] = { MockResponse.Builder().code(429).build() }

        val result = downloader().downloadIfNeeded()

        assertThat(result).isEqualTo(Failed(FailureCause.TOO_MANY_REQUESTS))
        assertThat(requestedPaths()).containsExactly(ROBOTS, SeedData.TANABATA_PHOTO_PATH).inOrder()
        assertThat(cover(SeedData.TANABATA_TEA_ID)).isNull()
        assertThat(cover(SeedData.HISUI_TEA_ID)).isNull()
    }

    @Test
    fun retryAfterStopsAfterThatResponseEvenOnSuccess() = runTest {
        seedTeas()
        routes[SeedData.TANABATA_PHOTO_PATH] = {
            MockResponse.Builder().addHeader("Retry-After", "30").addHeader("Content-Type", "image/jpeg")
                .body(Buffer().write(tanabataJpeg)).build()
        }

        val result = downloader().downloadIfNeeded()

        assertThat(result).isEqualTo(Failed(FailureCause.RETRY_AFTER))
        assertThat(requestedPaths()).containsExactly(ROBOTS, SeedData.TANABATA_PHOTO_PATH).inOrder()
        assertThat(cover(SeedData.TANABATA_TEA_ID)).isNull()
        assertThat(cover(SeedData.HISUI_TEA_ID)).isNull()
    }

    @Test
    fun challengePageStopsAfterThatResponseAndKeepsWhatWasStored() = runTest {
        seedTeas()
        routes[SeedData.HISUI_PHOTO_PATH] = {
            MockResponse.Builder().code(403).body("<html><title>Just a moment...</title></html>").build()
        }

        val result = downloader().downloadIfNeeded()

        assertThat(result).isEqualTo(Failed(FailureCause.CHALLENGE))
        assertThat(requests).hasSize(3)
        assertThat(cover(SeedData.TANABATA_TEA_ID)).isNotNull()
        assertThat(cover(SeedData.HISUI_TEA_ID)).isNull()
    }

    @Test
    fun challengeOnRobotsTxtStopsBeforeAnyImage() = runTest {
        seedTeas()
        routes[ROBOTS] = { MockResponse.Builder().code(403).addHeader("cf-mitigated", "challenge").build() }

        val result = downloader().downloadIfNeeded()

        assertThat(result).isEqualTo(Failed(FailureCause.CHALLENGE))
        assertThat(requestedPaths()).containsExactly(ROBOTS)
    }

    @Test
    fun robotsDisallowSkipsTheDownload() = runTest {
        seedTeas()
        routes[ROBOTS] = { ok("User-agent: *\nDisallow: /media/\n") }

        val result = downloader().downloadIfNeeded()

        assertThat(result).isEqualTo(SeedPhotoProgress.Done(stored = 0))
        assertThat(requestedPaths()).containsExactly(ROBOTS)
        assertThat(cover(SeedData.TANABATA_TEA_ID)).isNull()
        assertThat(cover(SeedData.HISUI_TEA_ID)).isNull()
    }

    @Test
    fun robotsDisallowOfOnePathSkipsOnlyThatPhoto() = runTest {
        seedTeas()
        routes[ROBOTS] = { ok("User-agent: *\nDisallow: /media/7c/\n") }

        val result = downloader().downloadIfNeeded()

        assertThat(result).isEqualTo(SeedPhotoProgress.Done(stored = 1))
        assertThat(requestedPaths()).containsExactly(ROBOTS, SeedData.TANABATA_PHOTO_PATH).inOrder()
        assertThat(cover(SeedData.HISUI_TEA_ID)).isNull()
    }

    @Test
    fun anUnusableRobotsTxtCountsAsCompleteDisallow() = runTest {
        seedTeas()
        routes[ROBOTS] = { MockResponse.Builder().code(400).build() }

        val result = downloader().downloadIfNeeded()

        assertThat(result).isEqualTo(SeedPhotoProgress.Done(stored = 0))
        assertThat(requestedPaths()).containsExactly(ROBOTS)
    }

    @Test
    fun missingRobotsTxtAllowsTheDownload() = runTest {
        seedTeas()
        routes.remove(ROBOTS)

        assertThat(downloader().downloadIfNeeded()).isEqualTo(SeedPhotoProgress.Done(stored = 2))
    }

    @Test
    fun anImageThatIsNotAJpegIsLeftOut() = runTest {
        seedTeas()
        routes[SeedData.TANABATA_PHOTO_PATH] = { ok("<html>not an image</html>") }

        val result = downloader().downloadIfNeeded()

        assertThat(result).isEqualTo(SeedPhotoProgress.Done(stored = 1))
        assertThat(cover(SeedData.TANABATA_TEA_ID)).isNull()
        assertThat(cover(SeedData.HISUI_TEA_ID)).isNotNull()
    }

    @Test
    fun deletedSeedTeasAndExistingCoversAreNotDownloaded() = runTest {
        data.teas.saveTea(SeedData.hisui)

        assertThat(downloader().downloadIfNeeded()).isEqualTo(SeedPhotoProgress.Done(stored = 1))
        assertThat(requestedPaths()).containsExactly(ROBOTS, SeedData.HISUI_PHOTO_PATH).inOrder()
        requests.clear()

        // A second run (the marker reset by hand) finds the cover already set: no request at all.
        assertThat(downloader(FakeSettingsRepository()).downloadIfNeeded()).isEqualTo(SeedPhotoProgress.Done(0))
        assertThat(requests).isEmpty()
    }

    /** S1 of the #27 slice review: the seeder has not committed yet when the setup starts the download. */
    @Test
    fun aDownloadBeforeTheSeedTeasExistSendsNothingAndKeepsTheRunForLater() = runTest {
        val downloader = downloader()

        assertThat(downloader.downloadIfNeeded()).isEqualTo(SeedPhotoProgress.Idle)

        assertThat(requests).isEmpty()
        assertThat(downloader.isNeeded()).isTrue()
        assertThat(settings.seedPhotosAttempted.first()).isFalse()

        // Once the seed data is in, the same downloader runs the download.
        seedTeas()
        assertThat(downloader.downloadIfNeeded()).isEqualTo(SeedPhotoProgress.Done(stored = 2))
        assertThat(downloader.isNeeded()).isFalse()
    }

    @Test
    fun afterDoneItNeverRunsAgain() = runTest {
        seedTeas()
        downloader().downloadIfNeeded()
        requests.clear()

        val again = downloader()

        assertThat(again.isNeeded()).isFalse()
        assertThat(again.downloadIfNeeded()).isEqualTo(SeedPhotoProgress.Idle)
        assertThat(requests).isEmpty()
    }

    @Test
    fun afterFailedItNeverRunsAgain() = runTest {
        seedTeas()
        routes[ROBOTS] = { MockResponse.Builder().code(429).build() }
        assertThat(downloader().downloadIfNeeded()).isEqualTo(Failed(FailureCause.TOO_MANY_REQUESTS))
        routes[ROBOTS] = { ok("") }
        requests.clear()

        assertThat(downloader().downloadIfNeeded()).isEqualTo(SeedPhotoProgress.Idle)
        assertThat(requests).isEmpty()
        assertThat(cover(SeedData.TANABATA_TEA_ID)).isNull()
    }

    @Test
    fun afterSkippedItNeverRunsAgain() = runTest {
        seedTeas()
        downloader().apply { skip() }.downloadIfNeeded()

        assertThat(downloader().downloadIfNeeded()).isEqualTo(SeedPhotoProgress.Idle)
        assertThat(requests).isEmpty()
    }

    @Test
    fun aSecondCallOnTheSameDownloaderDoesNotStartAnotherRun() = runTest {
        seedTeas()
        val downloader = downloader()
        downloader.downloadIfNeeded()
        requests.clear()

        assertThat(downloader.downloadIfNeeded()).isEqualTo(SeedPhotoProgress.Done(stored = 2))
        assertThat(requests).isEmpty()
    }

    private fun ok(body: String) = MockResponse.Builder().body(body).build()

    private fun jpeg(bytes: ByteArray) =
        MockResponse.Builder().addHeader("Content-Type", "image/jpeg").body(Buffer().write(bytes)).build()

    private companion object {
        const val ROBOTS = "/robots.txt"
        const val MIN_GAP_MS = 480L
        const val BLOCK_SECONDS = 10L
        const val AFTER_SKIP_MS = 700L
        const val GARBAGE_BYTES = 200
    }
}

/** A synthetic one-colour JPEG of the given size, encoded on the spot: no shop photo (D-016). */
internal fun syntheticJpeg(width: Int, height: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(74, 86, 67)) }
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
}

/** [jpeg] with an APP1 „Exif" segment after SOI, as camera and shop images carry. */
internal fun withExifSegment(jpeg: ByteArray): ByteArray {
    // "Exif\0\0", a big-endian TIFF header pointing at an empty IFD, then a marker string.
    val payload = "Exif\u0000\u0000MM\u0000*\u0000\u0000\u0000\u0008\u0000\u0000\u0000\u0000\u0000\u0000"
        .encodeToByteArray() + "synthetic-metadata".encodeToByteArray()
    val length = payload.size + 2
    val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    return jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
}

/** The decoded pixel size of [bytes]. */
internal fun decodedSize(bytes: ByteArray): Pair<Int, Int> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    return bounds.outWidth to bounds.outHeight
}
