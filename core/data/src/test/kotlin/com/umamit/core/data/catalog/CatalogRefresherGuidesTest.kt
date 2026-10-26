package com.umamit.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.catalog.parser.SanityCheck
import com.umamit.core.catalog.parser.SyntheticGuide
import com.umamit.core.catalog.parser.SyntheticShop
import com.umamit.core.data.decodedSize
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.data.syntheticJpeg
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.model.GuideBlock
import com.umamit.core.model.GuideInline
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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The refresh's *Tee-Info* guides against a simulated shop (#76; CAT-09, 08 §2.7, D-087):
 * synthetic articles and JPEGs (D-016) on MockWebServer, an in-memory `catalog.db`, the files
 * in a temporary folder.
 *
 * The shop: one Sencha (without a photo, so the guides' requests stand out), a Sencha guide with
 * a hero, a gallery image and links to the tea, the other guide and the web; a Matcha guide
 * without images; a black-tea article no keyword selects. The client's request gap is shortened.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class CatalogRefresherGuidesTest {
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
    private val listed = mutableListOf(SENCHA, SENCHA_GUIDE, MATCHA_GUIDE, BLACK_TEA_GUIDE)
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
        routes[SENCHA] = {
            text(
                SyntheticShop.productPage(
                    baseUrl = base,
                    sku = "XY0100",
                    canonicalPath = SENCHA,
                    photoPath = null,
                ),
            )
        }
        routes[SENCHA_GUIDE] = { text(senchaGuide()) }
        routes[MATCHA_GUIDE] = {
            text(
                SyntheticGuide.page(
                    baseUrl = base,
                    slug = "matcha-erklaert",
                    title = "Matcha erklärt",
                    jsonLd = null,
                    body = "<p>Ein erfundener Absatz über Matcha.</p>",
                ),
            )
        }
        routes[BLACK_TEA_GUIDE] = { text(SyntheticGuide.page(baseUrl = base, slug = "schwarztee-geschichte")) }
        routes[imageRoute("hero")] = { jpeg(syntheticJpeg(2400, 1600)) }
        routes[imageRoute("kanne")] = { jpeg(syntheticJpeg(800, 600)) }
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun senchaGuide(bodyBlock: Boolean = true) = SyntheticGuide.page(
        baseUrl = base,
        slug = "ein-guide-fuer-sencha",
        title = "Ein Guide für Sencha",
        gallery = listOf(SyntheticGuide.imagePath("hero"), SyntheticGuide.imagePath("kanne")),
        body = "<p>Probier den <a href=\"$base$SENCHA\">Morgennebel</a>, lies " +
            "<a href=\"$base$MATCHA_GUIDE\">Matcha erklärt</a> oder " +
            "<a href=\"https://example.org/tee\">eine fremde Seite</a>.</p>",
        bodyBlock = bodyBlock,
    )

    private fun refresher(limits: GuideLimits = GuideLimits.DEFAULT) = CatalogRefresher(
        client = ShopClient.forTesting(baseUrl = server.url("/"), requestIntervalMillis = 0),
        dao = dao,
        clock = clock,
        diagnostics = DiagnosticsLog(folder.newFolder(), maxBytes = 100_000, dispatcher = Dispatchers.IO),
        settings = settings,
        photos = photos,
        sanity = SanityCheck(firstImportMinimum = 1),
        guideLimits = limits,
    )

    /** The guides' requests: their pages and their images. */
    private fun guideRequests() = paths().filter { it.startsWith("/tee-info/") || it.startsWith("/media/gg/") }

    private fun repository() = RoomGuideRepository(dao, photos)

    private fun paths() = requests.map { it.url.encodedPath }

    @Test
    fun theSelectedGuidesAreStoredWithTheirImagesAndInAppLinks() = runTest {
        val seen = mutableListOf<CatalogRefreshProgress>()

        val outcome = refresher().refresh { seen += it }

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 1, newTeas = 1))
        // The tea first, then the guides by slug, then their images; never the black-tea article.
        assertThat(paths()).containsExactly(
            ROBOTS,
            SITEMAP,
            SENCHA,
            SENCHA_GUIDE,
            MATCHA_GUIDE,
            imageRoute("hero"),
            imageRoute("kanne"),
        ).inOrder()
        assertThat(seen.last().guides).isEqualTo(2)

        val guides = repository().observeGuides().first()
        assertThat(guides.map { it.title }).containsExactly("Ein Guide für Sencha", "Matcha erklärt").inOrder()
        val sencha = requireNotNull(repository().observeGuide("ein-guide-fuer-sencha").first())
        assertThat(sencha.url).isEqualTo("$base$SENCHA_GUIDE")
        assertThat(decodedSize(java.io.File(requireNotNull(sencha.heroPath)).readBytes())).isEqualTo(1200 to 800)
        val image = sencha.body.blocks.first() as GuideBlock.Image
        assertThat(decodedSize(java.io.File(image.source).readBytes())).isEqualTo(800 to 600)
        val links = (sencha.body.blocks.last() as GuideBlock.Paragraph).inlines
            .filterIsInstance<GuideInline.Span>().mapNotNull { it.link }
        assertThat(links).containsExactly(
            "umamit://catalog/XY0100",
            "umamit://guide/matcha-erklaert",
            "https://example.org/tee",
        ).inOrder()
        assertThat(
            dao.guides().first {
                it.slug == "ein-guide-fuer-sencha"
            }.linkedCatalogKeys,
        ).isEqualTo("[\"XY0100\"]")
        assertThat(requireNotNull(repository().observeGuide("matcha-erklaert").first()).heroPath).isNull()
    }

    @Test
    fun anOversizedGuideImageIsLeftOutAndTheRefreshGoesOn() = runTest {
        // Over the client's image limit: only this image is left out, no stop (D-090, D-091).
        routes[imageRoute("hero")] = { jpeg(ByteArray(IMAGE_OVER_LIMIT)) }

        val outcome = refresher().refresh()

        assertThat(outcome).isEqualTo(CatalogRefreshOutcome.Success(teas = 1, newTeas = 1))
        assertThat(paths().last()).isEqualTo(imageRoute("kanne"))
        val sencha = requireNotNull(repository().observeGuide("ein-guide-fuer-sencha").first())
        assertThat(sencha.heroPath).isNull()
        assertThat(sencha.body.blocks.filterIsInstance<GuideBlock.Image>()).hasSize(1)
    }

    @Test
    fun aLaterRefreshFetchesTheGuidesAgainButNotTheImagesItHas() = runTest {
        refresher().refresh()
        requests.clear()

        refresher().refresh()

        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SENCHA, SENCHA_GUIDE, MATCHA_GUIDE).inOrder()
        assertThat(requireNotNull(repository().observeGuide("ein-guide-fuer-sencha").first()).heroPath).isNotNull()
    }

    @Test
    fun aGuideImageThatFailedForGoodIsNotAskedForAgainWhileItsUrlIsTheSame() = runTest {
        // Full live run 1 (counts only, D-091): the 18 guide images missing were asked for on every refresh.
        routes[imageRoute("hero")] = { jpeg("kein Bild".toByteArray()) }
        refresher().refresh()
        requests.clear()

        refresher().refresh()

        assertThat(paths()).containsExactly(ROBOTS, SITEMAP, SENCHA, SENCHA_GUIDE, MATCHA_GUIDE).inOrder()
        val sencha = requireNotNull(repository().observeGuide("ein-guide-fuer-sencha").first())
        assertThat(sencha.heroPath).isNull()
        assertThat(sencha.body.blocks.filterIsInstance<GuideBlock.Image>()).hasSize(1)
    }

    @Test
    fun aStopWhileFetchingGuidesKeepsTheOldGuidesAndTheirImages() = runTest {
        refresher().refresh()
        val before = dao.guides()
        val files = guideFiles()
        routes[MATCHA_GUIDE] = { MockResponse.Builder().code(429).build() }
        routes[SENCHA_GUIDE] = { text(senchaGuide().replace("Probier", "Versuch")) }

        val outcome = refresher().refresh()

        assertThat(outcome).isInstanceOf(CatalogRefreshOutcome.Blocked::class.java)
        assertThat(dao.guides()).containsExactlyElementsIn(before)
        assertThat(guideFiles()).containsExactlyElementsIn(files)
    }

    @Test
    fun aGuideThatNoLongerReadsKeepsItsVersionAndOneThatIsGoneIsRemoved() = runTest {
        refresher().refresh()
        val sencha = dao.guides().first { it.slug == "ein-guide-fuer-sencha" }
        routes[SENCHA_GUIDE] = { text(senchaGuide(bodyBlock = false)) }
        routes.remove(MATCHA_GUIDE)
        clock.advanceBy(60_000)

        val outcome = refresher().refresh()

        assertThat(outcome).isInstanceOf(CatalogRefreshOutcome.Success::class.java)
        assertThat(dao.guides()).containsExactly(sencha)
        // Its images stay with it.
        assertThat(
            java.io.File(requireNotNull(repository().observeGuide(sencha.slug).first()?.heroPath)).isFile,
        ).isTrue()
    }

    @Test
    fun withoutAnySelectedGuideInTheSitemapTheGuidesAreKept() = runTest {
        refresher().refresh()
        val before = dao.guides()
        listed -= listOf(SENCHA_GUIDE, MATCHA_GUIDE)

        refresher().refresh()

        assertThat(dao.guides()).containsExactlyElementsIn(before)
    }

    @Test
    fun theDefaultBudgetIsFortyGuidesWithFourImagesEach() {
        // 08 §2.4, D-087: ≤ 200 guide requests on a first refresh (≈ 100 s at 2 requests/s).
        assertThat(GuideLimits.DEFAULT).isEqualTo(GuideLimits(maxGuides = 40, maxImagesPerGuide = 4))
        assertThat(GuideLimits.DEFAULT.maxRequests).isEqualTo(200)
    }

    @Test
    fun theGuideRequestsStayWithinTheCapOnAFirstAndALaterRefresh() = runTest {
        // Three selected guides with a hero and three gallery images each; the cap: two guides, two images.
        listed.clear()
        listed += SENCHA
        listOf("gyokuro-guide", "kabuse-guide", "shincha-guide").forEach { slug ->
            val path = "/tee-info/$slug"
            listed += path
            val images = (1..3).map { SyntheticGuide.imagePath("$slug-$it") }
            routes[path] = {
                text(
                    SyntheticGuide.page(
                        baseUrl = base,
                        slug = slug,
                        jsonLd = SyntheticGuide.blogPosting(
                            base,
                            slug,
                            slug,
                            "$base${SyntheticGuide.imagePath("$slug-hero")}",
                        ),
                        gallery = images,
                    ),
                )
            }
            (images + SyntheticGuide.imagePath("$slug-hero")).forEach { image ->
                routes[image.substringBefore('?')] = { jpeg(syntheticJpeg(400, 300)) }
            }
        }
        val limits = GuideLimits(maxGuides = 2, maxImagesPerGuide = 2)

        refresher(limits).refresh()

        assertThat(guideRequests().size).isAtMost(limits.maxRequests)
        assertThat(guideRequests()).containsExactly(
            "/tee-info/gyokuro-guide",
            "/tee-info/kabuse-guide",
            "/media/gg/hh/ii/1700000000/gyokuro-guide-hero.jpg",
            "/media/gg/hh/ii/1700000000/gyokuro-guide-1.jpg",
            "/media/gg/hh/ii/1700000000/kabuse-guide-hero.jpg",
            "/media/gg/hh/ii/1700000000/kabuse-guide-1.jpg",
        ).inOrder()
        assertThat(dao.guides().map { it.slug }).containsExactly("gyokuro-guide", "kabuse-guide")

        requests.clear()
        refresher(limits).refresh()

        // Later: the articles again, no image already stored.
        assertThat(guideRequests()).containsExactly("/tee-info/gyokuro-guide", "/tee-info/kabuse-guide").inOrder()
    }

    @Test
    fun noGuideImageIsFetchedWhenPhotosAreOff() = runTest {
        settings.update { it.copy(catalogDownloadPhotos = false) }

        refresher().refresh()

        assertThat(paths().filter { it.startsWith("/media/") }).isEmpty()
        val sencha = requireNotNull(repository().observeGuide("ein-guide-fuer-sencha").first())
        assertThat(sencha.heroPath).isNull()
        assertThat(sencha.body.blocks.filterIsInstance<GuideBlock.Image>()).isEmpty()
    }

    private fun guideFiles(): List<String> = photos.guideImages.dir.list()?.sorted().orEmpty()

    private fun imageRoute(name: String) = SyntheticGuide.imagePath(name).substringBefore('?')

    private fun text(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()

    private fun jpeg(bytes: ByteArray) =
        MockResponse.Builder().addHeader("Content-Type", "image/jpeg").body(Buffer().write(bytes)).build()

    private companion object {
        const val ROBOTS = "/robots.txt"
        const val SITEMAP = "/sitemap.xml"
        const val SENCHA = "/morgennebel-sencha/XY0100"
        const val SENCHA_GUIDE = "/tee-info/ein-guide-fuer-sencha"
        const val MATCHA_GUIDE = "/tee-info/matcha-erklaert"
        const val BLACK_TEA_GUIDE = "/tee-info/schwarztee-geschichte"

        /** One byte over the shop client's 16 MB body limit of an image (D-091). */
        const val IMAGE_OVER_LIMIT = 16 * 1024 * 1024 + 1
    }
}
