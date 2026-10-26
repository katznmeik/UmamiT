package com.umamit.core.data.catalog

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.catalog.parser.SanityCheck
import com.umamit.core.catalog.parser.SyntheticShop
import com.umamit.core.data.decodedSize
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.data.syntheticJpeg
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.network.ShopClient
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The refresh's detail fields and photos against a simulated shop (#66; CAT-03, CAT-08, SET-22,
 * 08 §2.3, §2.8, D-074): MockWebServer serving synthetic pages and synthetic JPEGs (D-016), an
 * in-memory `catalog.db`, the photo folder in a temporary directory.
 *
 * The shop: a Sencha (a large landscape photo), a Gyokuro (a small portrait photo) and a black
 * tea (out of scope; its photo must never be fetched). The client's request gap is shortened.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class CatalogRefresherDetailTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val db = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.catalogDao()
    private val clock = FakeClock()
    private val settings = FakeSettingsRepository()
    private lateinit var photos: CatalogPhotoFiles

    private val routes = mutableMapOf<String, () -> MockResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val listed = mutableListOf<String>()
    private val base get() = server.url("/").toString().removeSuffix("/")

    @Before
    fun setUp() {
        photos = CatalogPhotoFiles(folder.newFolder("catalog"))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return routes[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        routes[ROBOTS] = { text(SyntheticShop.robotsTxt()) }
        routes[SITEMAP] = { text(SyntheticShop.urlSet(listed.map { "$base$it" })) }
        product(SENCHA, "XY0100", SyntheticShop.SENCHA_PATH, photo = "sencha")
        product(GYOKURO, "XY0200", GYOKURO_PATH, photo = "gyokuro")
        product(BLACK, "XY0300", SyntheticShop.BLACK_TEA_PATH, photo = "assam")
        routes[photoRoute("sencha")] = { jpeg(syntheticJpeg(2400, 1600)) }
        routes[photoRoute("gyokuro")] = { jpeg(syntheticJpeg(600, 900)) }
        routes[photoRoute("assam")] = { jpeg(syntheticJpeg(100, 100)) }
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    @Suppress("LongParameterList")
    private fun product(
        path: String,
        sku: String,
        breadcrumb: List<Pair<String, String>>,
        photo: String,
        description: String = "<p>Ein erfundener Tee.</p><p>Zubereitung: 4 g auf 100 ml bei 60 °C.</p>",
    ) {
        listed += path
        routes[path] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    title = "Erfundener Tee $sku – 100g",
                    sku = sku,
                    canonicalPath = path,
                    breadcrumb = breadcrumb,
                    photoPath = SyntheticShop.photoPath(photo),
                    description = description,
                    properties = listOf("Anbaugebiete" to listOf("Japan"), "Aroma" to listOf("naturbelassen")),
                    badges = listOf("Bio"),
                    invertedBadges = listOf("Dämpfung: Chumushi"),
                    scales = listOf("Umami" to 4, "Bitterstoffe" to 1),
                    brewRows = listOf("japan" to listOf("~4g/100ml", "~60°C", "~90 Sek", "~20 Sek", "-")),
                ),
            )
        }
    }

    private fun refresher() = CatalogRefresher(
        client = ShopClient.forTesting(baseUrl = server.url("/"), requestIntervalMillis = 0),
        dao = dao,
        clock = clock,
        diagnostics = DiagnosticsLog(folder.newFolder(), maxBytes = 100_000, dispatcher = Dispatchers.IO),
        settings = settings,
        photos = photos,
        sanity = SanityCheck(firstImportMinimum = 1),
    )

    private fun paths() = requests.map { it.url.encodedPath }

    private suspend fun photosOff() = settings.update { it.copy(catalogDownloadPhotos = false) }

    @Test
    fun theDetailFieldsAreStored() = runTest {
        refresher().refresh()

        val tea = requireNotNull(dao.tea("XY0100"))
        assertThat(tea.originCountry).isEqualTo("Japan")
        assertThat(tea.aroma).isEqualTo("naturbelassen")
        assertThat(tea.isOrganic).isTrue()
        assertThat(tea.isFlavored).isFalse()
        assertThat(tea.steaming).isEqualTo("Chumushi")
        assertThat(tea.tasteScalesJson).isEqualTo("""{"Umami":4,"Bitterstoffe":1}""")
        assertThat(tea.brewTableRaw).isEqualTo(
            """[{"kind":"japan","cells":[{"label":"Menge","text":"~4g/100ml"},""" +
                """{"label":"Temperatur","text":"~60°C"},{"label":"1. Aufguss","text":"~90 Sek"},""" +
                """{"label":"2. Aufguss","text":"~20 Sek"},{"label":"3. Aufguss","text":"-"}]}]""",
        )
        assertThat(tea.brewTextRaw).isEqualTo("Zubereitung: 4 g auf 100 ml bei 60 °C.")
        assertThat(
            tea.descriptionHtml,
        ).isEqualTo("<p>Ein erfundener Tee.</p><p>Zubereitung: 4 g auf 100 ml bei 60 °C.</p>")
        assertThat(tea.descriptionText).isEqualTo("Ein erfundener Tee.\n\nZubereitung: 4 g auf 100 ml bei 60 °C.")
        assertThat(tea.photoUrl).isEqualTo("$base${SyntheticShop.photoPath("sencha")}")
    }

    @Test
    fun eachGreenTeasPhotoIsStoredAtTheTargetSizeWithAThumbnail() = runTest {
        val seen = mutableListOf<CatalogRefreshProgress>()

        val outcome = refresher().refresh { seen += it }

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 2))
        assertThat(decodedSize(photos.photo("XY0100").readBytes())).isEqualTo(1200 to 800)
        assertThat(decodedSize(photos.thumbnail("XY0100").readBytes())).isEqualTo(384 to 256)
        // Never upscaled: the small portrait photo keeps its size; its thumbnail has a 256 px short side.
        assertThat(decodedSize(photos.photo("XY0200").readBytes())).isEqualTo(600 to 900)
        assertThat(decodedSize(photos.thumbnail("XY0200").readBytes())).isEqualTo(256 to 384)
        assertThat(photos.photo("XY0100").readBytes().isWebP()).isTrue()
        assertThat(dao.tea("XY0100")?.photoFile).isEqualTo("XY0100.webp")
        assertThat(dao.tea("XY0200")?.photoFile).isEqualTo("XY0200.webp")
        // The pages first, then the green teas' photos; never the black tea's.
        assertThat(paths()).containsExactly(
            ROBOTS,
            SITEMAP,
            SENCHA,
            GYOKURO,
            BLACK,
            photoRoute("sencha"),
            photoRoute("gyokuro"),
        ).inOrder()
        assertThat(seen.last().photos).isEqualTo(2)
        assertThat(dao.refreshLog().single().photosCount).isEqualTo(2)
        assertThat(folder.root.resolve("catalog").list()?.toList()).containsExactly(
            "XY0100.webp",
            "XY0100_thumb.webp",
            "XY0200.webp",
            "XY0200_thumb.webp",
        )
    }

    @Test
    fun noPhotoIsFetchedWhenTheSettingIsOff() = runTest {
        photosOff()

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 2))
        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SENCHA, GYOKURO, BLACK).inOrder()
        assertThat(dao.tea("XY0100")?.photoFile).isNull()
        assertThat(folder.root.resolve("catalog").list()?.toList()).isEmpty()
    }

    @Test
    fun turningTheSettingOffKeepsThePhotosThereAre() = runTest {
        refresher().refresh()
        requests.clear()
        photosOff()

        refresher().refresh()

        // The black tea is on the skip list by now.
        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SENCHA, GYOKURO).inOrder()
        assertThat(dao.tea("XY0100")?.photoFile).isEqualTo("XY0100.webp")
        assertThat(photos.exists("XY0100")).isTrue()
    }

    @Test
    fun aMissingOrUnreadablePhotoLeavesOnlyThatTeaWithoutOne() = runTest {
        routes.remove(photoRoute("sencha"))
        routes[photoRoute("gyokuro")] = { jpeg("kein Bild".toByteArray()) }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 2, newTeas = 2))
        assertThat(dao.tea("XY0100")?.photoFile).isNull()
        assertThat(dao.tea("XY0200")?.photoFile).isNull()
        assertThat(dao.refreshLog().single().photosCount).isEqualTo(0)
    }

    @Test
    fun anOversizedPhotoLeavesOnlyThatTeaWithoutOneAndTheRefreshGoesOn() = runTest {
        // Over the client's image limit, declared or not: only this URL is left out, no stop (D-090, D-091).
        routes[photoRoute("sencha")] = { jpeg(ByteArray(IMAGE_OVER_LIMIT)) }
        routes[photoRoute("gyokuro")] = {
            MockResponse.Builder().addHeader("Content-Type", "image/jpeg")
                .chunkedBody(Buffer().write(ByteArray(IMAGE_OVER_LIMIT)), CHUNK_BYTES).build()
        }
        product(NEW_TEA, "XY0150", SyntheticShop.SENCHA_PATH, photo = "neu")
        routes[photoRoute("neu")] = { jpeg(syntheticJpeg(800, 600)) }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 3, newTeas = 3))
        assertThat(dao.tea("XY0100")?.photoFile).isNull()
        assertThat(dao.tea("XY0200")?.photoFile).isNull()
        // The photo after the first oversized one is still fetched and stored, and so is the next one.
        assertThat(paths().filter { it.startsWith("/media/") })
            .containsExactly(photoRoute("sencha"), photoRoute("neu"), photoRoute("gyokuro"))
            .inOrder()
        assertThat(dao.tea("XY0150")?.photoFile).isEqualTo("XY0150.webp")
    }

    @Test
    fun aPngPhotoLargerThanAPageIsStored() = runTest {
        // Full live run 1 (counts and sizes only, D-091): every one of the 27 photos missing was a
        // PNG, 10 of them over the page limit of 5 MB.
        val png = noisePng(width = 1400, height = 1400)
        assertThat(png.size.toLong()).isGreaterThan(OVER_LIMIT.toLong())
        routes[photoRoute("sencha")] = { image(png, "image/png") }

        refresher().refresh()

        assertThat(dao.tea("XY0100")?.photoFile).isEqualTo("XY0100.webp")
        assertThat(decodedSize(photos.photo("XY0100").readBytes())).isEqualTo(1200 to 1200)
        assertThat(decodedSize(photos.thumbnail("XY0100").readBytes())).isEqualTo(256 to 256)
    }

    @Test
    fun aPhotoThatFailedForGoodIsNotAskedForAgainWhileItsUrlIsTheSame() = runTest {
        // Full live run 1 (counts only, D-091): the photos that failed were asked for on every refresh.
        routes[photoRoute("sencha")] = { jpeg("kein Bild".toByteArray()) }
        routes[photoRoute("gyokuro")] = { jpeg(ByteArray(IMAGE_OVER_LIMIT)) }
        product(NEW_TEA, "XY0150", SyntheticShop.SENCHA_PATH, photo = "neu")
        routes[photoRoute("neu")] = { MockResponse.Builder().code(404).build() }
        refresher().refresh()
        requests.clear()

        refresher().refresh()

        // Undecodable and too large: not asked for again; a 404 may be gone for a moment only.
        assertThat(paths().filter { it.startsWith("/media/") }).containsExactly(photoRoute("neu"))
        assertThat(dao.tea("XY0100")?.photoFile).isNull()

        // The shop's media URL changes with the image (`?ts=`): a new one is asked for.
        listed -= SENCHA
        product(SENCHA, "XY0100", SyntheticShop.SENCHA_PATH, photo = "sencha-neu")
        routes[photoRoute("sencha-neu")] = { jpeg(syntheticJpeg(800, 600)) }
        requests.clear()

        refresher().refresh()

        assertThat(paths().filter { it.startsWith("/media/") })
            .containsExactly(photoRoute("sencha-neu"), photoRoute("neu")).inOrder()
        assertThat(dao.tea("XY0100")?.photoFile).isEqualTo("XY0100.webp")
    }

    @Test
    fun aFailedRunRemembersNoFailedPhoto() = runTest {
        // A stop after the photos: the run keeps nothing, so its failed photos are asked for again.
        routes[photoRoute("sencha")] = { jpeg("kein Bild".toByteArray()) }
        listed += GUIDE
        routes[GUIDE] = { MockResponse.Builder().code(429).build() }
        assertThat(refresher().refresh()).isInstanceOf(CatalogRefreshOutcome.Blocked::class.java)
        routes.remove(GUIDE)
        requests.clear()

        refresher().refresh()

        assertThat(paths()).contains(photoRoute("sencha"))
    }

    @Test
    fun anOversizedProductPageLeavesOnlyThatTeaOut() = runTest {
        routes[GYOKURO] = { text("<html>" + "x".repeat(OVER_LIMIT) + "</html>") }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 1, newTeas = 1))
        assertThat(dao.tea("XY0200")).isNull()
        assertThat(paths()).contains(photoRoute("sencha"))
    }

    @Test
    fun aPhotoRobotsTxtDisallowsIsNeverFetched() = runTest {
        routes[ROBOTS] = { text(SyntheticShop.robotsTxt("Disallow: /media/")) }

        refresher().refresh()

        assertThat(paths().filter { it.startsWith("/media/") }).isEmpty()
        assertThat(dao.tea("XY0100")?.photoFile).isNull()
    }

    @Test
    fun aStopOnAPhotoEndsTheRunAndKeepsTheOldCatalogAndItsPhotos() = runTest {
        refresher().refresh()
        val before = dao.observeList().first()
        requests.clear()
        product(NEW_TEA, "XY0150", SyntheticShop.SENCHA_PATH, photo = "neu")
        routes[photoRoute("neu")] = { jpeg(syntheticJpeg(800, 600)) }
        // The Gyokuro has a new photo now; the shop refuses it.
        listed -= GYOKURO
        product(GYOKURO, "XY0200", GYOKURO_PATH, photo = "gyokuro-neu")
        routes[photoRoute("gyokuro-neu")] = { MockResponse.Builder().code(429).build() }

        val outcome = refresher().refresh()

        assertThat(outcome).isInstanceOf(CatalogRefreshOutcome.Blocked::class.java)
        assertThat(dao.observeList().first()).isEqualTo(before)
        // The Sencha's unchanged photo is not fetched again (#70); nothing after the stop; the new
        // tea's photo, fetched before it, is gone with the run.
        assertThat(paths().filter { it.startsWith("/media/") })
            .containsExactly(photoRoute("neu"), photoRoute("gyokuro-neu"))
            .inOrder()
        assertThat(paths().last()).isEqualTo(photoRoute("gyokuro-neu"))
        assertThat(photos.exists("XY0150")).isFalse()
        assertThat(folder.root.resolve("catalog").list()?.filter { "XY0150" in it }).isEmpty()
        assertThat(photos.exists("XY0100")).isTrue()
        assertThat(photos.exists("XY0200")).isTrue()
    }

    @Test
    fun theThumbnailIsInTheListAndThePhotoInTheDetail() = runTest {
        refresher().refresh()

        val repository = RoomCatalogRepository(dao, photos)
        val sencha = repository.observeCatalog().first().teas.first { it.key == "XY0100" }
        assertThat(sencha.thumbnailPath).isEqualTo(photos.thumbnail("XY0100").path)
        val detail = requireNotNull(repository.observeTea("XY0100").first())
        assertThat(detail.photoPath).isEqualTo(photos.photo("XY0100").path)
        assertThat(detail.tasteScales.map { it.label to it.leaves }).containsExactly("Umami" to 4, "Bitterstoffe" to 1)
        assertThat(detail.brewTable.single().cells.first().text).isEqualTo("~4g/100ml")
        assertThat(detail.productUrl).isEqualTo("$base$SENCHA")
        assertThat(repository.observeTea("XY0300").first()).isNull()
    }

    @Test
    fun eachTeasStyleRecipesAndRecipeCheckAreStored() = runTest {
        // The Gyokuro's prose contradicts its grid (60 °C): „Rezept prüfen" (CAT-05).
        listed -= GYOKURO
        product(
            GYOKURO,
            "XY0200",
            GYOKURO_PATH,
            photo = "gyokuro",
            description = "<p>Zubereitung: 4 g auf 100 ml bei 80 °C.</p>",
        )

        refresher().refresh()

        val sencha = requireNotNull(dao.tea("XY0100"))
        assertThat(sencha.styleKey).isEqualTo("sencha")
        assertThat(sencha.recipeCheckReason).isNull()
        val gyokuro = requireNotNull(dao.tea("XY0200"))
        assertThat(gyokuro.styleKey).isEqualTo("gyokuro")
        assertThat(gyokuro.recipeCheckReason).isEqualTo(RecipeCheckReason.TABLE_TEXT_CONFLICT)
        assertThat(dao.refreshLog().last().flaggedCount).isEqualTo(1)

        val repository = RoomCatalogRepository(dao, photos)
        val detail = requireNotNull(repository.observeTea("XY0100").first())
        assertThat(detail.recipes).containsExactly(
            RecipeTemplate(
                name = "Kyusu",
                method = BrewMethod.KYUSU,
                leafGrams = 4.0,
                waterMl = 100,
                baseTempC = 60,
                steps = listOf(RecipeStep(StepKind.INFUSION, 90), RecipeStep(StepKind.INFUSION, 20)),
                source = RecipeSource.CATALOG_TABLE,
                isDefault = true,
            ),
        )
        assertThat(detail.recipeCheckReason).isNull()
        assertThat(requireNotNull(repository.observeTea("XY0200").first()).recipeCheckReason)
            .isEqualTo(RecipeCheckReason.TABLE_TEXT_CONFLICT)
        val list = repository.observeCatalog().first().teas.associate { it.key to it.recipeCheckReason }
        assertThat(list).containsExactly("XY0100", null, "XY0200", RecipeCheckReason.TABLE_TEXT_CONFLICT)
    }

    private fun photoRoute(name: String) = SyntheticShop.photoPath(name).substringBefore('?')

    private fun text(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()

    private fun jpeg(bytes: ByteArray) = image(bytes, "image/jpeg")

    private fun image(bytes: ByteArray, type: String) =
        MockResponse.Builder().addHeader("Content-Type", type).body(Buffer().write(bytes)).build()

    /** A synthetic PNG of random pixels, which PNG can't compress (D-016). */
    private fun noisePng(width: Int, height: Int): ByteArray {
        val random = java.util.Random(PNG_SEED)
        val pixels = IntArray(width * height) { random.nextInt() or OPAQUE }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun ByteArray.isWebP() = size > WEBP_HEADER &&
        String(copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF" &&
        String(copyOfRange(8, WEBP_HEADER), Charsets.US_ASCII) == "WEBP"

    private companion object {
        const val ROBOTS = "/robots.txt"
        const val SITEMAP = "/sitemap.xml"
        const val SENCHA = "/morgennebel-sencha/XY0100"
        const val GYOKURO = "/tautropfen-gyokuro/XY0200"
        const val BLACK = "/abendglut-assam/XY0300"
        const val NEW_TEA = "/fruehlingshauch-sencha/XY0150"
        const val GUIDE = "/tee-info/sencha-erklaert"
        const val WEBP_HEADER = 12

        /** One byte over the shop client's 5 MB body limit of a page. */
        const val OVER_LIMIT = 5 * 1024 * 1024 + 1

        /** One byte over its 16 MB body limit of an image (D-091). */
        const val IMAGE_OVER_LIMIT = 16 * 1024 * 1024 + 1
        const val CHUNK_BYTES = 64 * 1024
        const val PNG_SEED = 91L
        const val OPAQUE = -0x1000000

        val GYOKURO_PATH = listOf(
            "Tee" to "/tee/",
            "Japantee" to "/tee/japantee/",
            "Gyokuro" to "/tee/japantee/gyokuro/",
        )
    }
}
