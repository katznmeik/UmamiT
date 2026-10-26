package com.umamit.core.data.catalog

import android.content.Context
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.JournalRepository
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.PhotoTranscoder
import com.umamit.core.data.RoomJournalRepository
import com.umamit.core.data.SeedData
import com.umamit.core.data.SeedDataSeeder
import com.umamit.core.data.SessionStart
import com.umamit.core.data.TestDatabase
import com.umamit.core.data.infusion
import com.umamit.core.data.snapshotOf
import com.umamit.core.data.syntheticJpeg
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.database.entity.StagedCatalogTeaEntity
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.model.Tea
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * CAT-15 on both databases (#70; spec #62 seam 3): after a successful refresh the seed teas
 * Tanabata and Hisui get the catalog's description and, without a cover, its photo as their own
 * JPEG (D-076); their recipes, stock and journal stay as they were. Each part of each seed tea is
 * done once (D-090): a part the refresh couldn't give (no catalog entry, no description, no photo)
 * is tried again after every later successful refresh, a done one never again.
 * An in-memory `umamit.db` with the seed data, an in-memory `catalog.db`, both photo folders in
 * a temporary directory.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class CatalogSeedLinkTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val settings = FakeSettingsRepository()
    private val data = TestDatabase(settings = settings)
    private val catalogDb = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val catalog = catalogDb.catalogDao()
    private lateinit var catalogPhotos: CatalogPhotoFiles
    private lateinit var photoFiles: PhotoFiles
    private lateinit var journal: JournalRepository

    /** The settings file's done parts, kept across [linker]s as across process starts. */
    private val done = MutableStateFlow(emptySet<String>())

    @Before
    fun setUp() {
        catalogPhotos = CatalogPhotoFiles(folder.newFolder("catalog"))
        photoFiles = PhotoFiles(folder.newFolder("photos"))
        journal = RoomJournalRepository(data.db, photoFiles)
    }

    @After
    fun tearDown() {
        data.close()
        catalogDb.close()
    }

    private fun linker(marker: SeedTeaLinkMarker = memoryMarker()) = RoomCatalogSeedLinker(
        db = data.db,
        teas = data.teas,
        catalog = catalog,
        catalogPhotos = catalogPhotos,
        photoFiles = photoFiles,
        marker = marker,
    )

    private fun memoryMarker() = object : SeedTeaLinkMarker {
        override val done = this@CatalogSeedLinkTest.done

        override suspend fun markDone(parts: Set<String>) {
            done.value += parts
        }
    }

    /** Every part of both seed teas. */
    private val allParts = setOf(
        "${SeedData.TANABATA_TEA_ID}/description",
        "${SeedData.TANABATA_TEA_ID}/cover",
        "${SeedData.HISUI_TEA_ID}/description",
        "${SeedData.HISUI_TEA_ID}/cover",
    )

    private suspend fun seed() {
        SeedDataSeeder(data.db, data.teas, data.recipes, data.vessels, data.stock, settings, data.time).seedIfNeeded()
    }

    /** A swapped-in catalog with these teas, each with a description and a photo (synthetic, D-016). */
    private suspend fun catalogWith(
        vararg keys: String,
        description: (String) -> String? = { "Erfundener Text zu $it." },
    ) {
        catalog.clearStaging()
        for (key in keys) {
            catalog.stage(StagedCatalogTeaEntity(entry(key, description(key))))
            val photo = requireNotNull(PhotoTranscoder.transcodeForCatalog(syntheticJpeg(1600, 1200)))
            catalogPhotos.write(key, photo.full.bytes, photo.thumbnail.bytes)
        }
        catalog.swap(emptyList(), log())
    }

    private fun entry(key: String, description: String?) = CatalogTeaEntity(
        key = key,
        name = "Erfundener Tee $key",
        secondaryName = null,
        productUrl = "https://shop.example/tee/$key",
        shopCategory = null,
        teaType = "Sencha",
        breadcrumb = null,
        descriptionHtml = description?.let { "<p>$it</p>" },
        descriptionText = description,
        basePriceCentsPer100g = 1240,
        available = true,
        photoFile = "$key.webp",
        firstSeenAt = 1_000,
        lastSeenAt = 1_000,
    )

    private fun log() = CatalogRefreshLogEntity(
        startedAt = 1_000,
        finishedAt = 2_000,
        status = CatalogRefreshStatus.SUCCESS,
        newCount = 0,
        changedCount = 0,
        discontinuedCount = 0,
        flaggedCount = 0,
        photosCount = 0,
        parserVersion = 4,
        errorSummary = null,
    )

    /** Everything of the collection CAT-15 must not touch: recipes, stock events and journal sessions. */
    private suspend fun untouchable(): List<Any> = listOf(
        data.recipes.observeRecipes(SeedData.TANABATA_TEA_ID).first(),
        data.recipes.observeRecipes(SeedData.HISUI_TEA_ID).first(),
        data.stock.observeEvents(SeedData.TANABATA_TEA_ID).first(),
        data.stock.observeEvents(SeedData.HISUI_TEA_ID).first(),
        journal.observeSessions().first().map { it.session },
    )

    private suspend fun brewTanabata() {
        data.sessions.startSession(
            SessionStart(
                id = "s1",
                snapshot = snapshotOf(teaId = SeedData.TANABATA_TEA_ID),
                steps = listOf(infusion(60)),
                preAlertSeconds = 10,
                startedAtMs = 500,
                zoneId = "Europe/Berlin",
            ),
        )
    }

    private suspend fun tea(id: String): Tea = requireNotNull(data.teas.getTea(id))

    @Test
    fun theSeedTeasGetTheCatalogsDescriptionAndPhotoAndNothingElseChanges() = runTest {
        seed()
        brewTanabata()
        catalogWith("TG0213", "TG0557")
        val teasBefore = listOf(tea(SeedData.TANABATA_TEA_ID), tea(SeedData.HISUI_TEA_ID))
        val before = untouchable()

        val keys = linker().linkSeedTeas(withPhotos = true)

        assertThat(keys).containsExactly("TG0213", "TG0557")
        for (old in teasBefore) {
            val now = tea(old.id)
            assertThat(now.description).isEqualTo("Erfundener Text zu ${old.catalogKey}.")
            // Every other field as it was: name, recipes' flag, product URL, … (CAT-13).
            assertThat(now.copy(description = null, coverPhotoId = null)).isEqualTo(old)
            val cover = requireNotNull(data.teas.observeTea(old.id).first()?.coverPhoto)
            assertThat(cover.origin).isEqualTo(PhotoOrigin.CATALOG_COPY)
            assertThat(cover.owner).isEqualTo(PhotoOwner.OfTea(old.id))
            // The collection's own JPEG (05 §3.9), so restore detection keeps it (D-076).
            val bytes = photoFiles.file(cover.id).readBytes()
            assertThat(
                BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    .also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }.outMimeType,
            )
                .isEqualTo("image/jpeg")
            assertThat(cover.widthPx to cover.heightPx).isEqualTo(1200 to 900)
        }
        assertThat(untouchable()).isEqualTo(before)
        assertThat(done.value).isEqualTo(allParts)
    }

    @Test
    fun aSeedTeaWithACoverKeepsItAndGetsTheDescriptionOnly() = runTest {
        seed()
        // The first start's seed photo download gave Hisui its cover (ONB-06).
        photoFiles.write("seed-photo", syntheticJpeg(100, 100))
        data.teas.savePhoto(
            Photo("seed-photo", PhotoOwner.OfTea(SeedData.HISUI_TEA_ID), PhotoOrigin.CATALOG_COPY, 100, 100, 3),
        )
        data.teas.setCoverPhoto(SeedData.HISUI_TEA_ID, "seed-photo")
        catalogWith("TG0213", "TG0557")

        linker().linkSeedTeas(withPhotos = true)

        val hisui = tea(SeedData.HISUI_TEA_ID)
        assertThat(hisui.coverPhotoId).isEqualTo("seed-photo")
        assertThat(hisui.description).isEqualTo("Erfundener Text zu TG0557.")
        assertThat(photoFiles.dir.list()?.count { it.endsWith(".jpg") }).isEqualTo(2)
    }

    @Test
    fun theLinkHappensOnceAndNeverOverwritesALaterEdit() = runTest {
        seed()
        catalogWith("TG0213", "TG0557")
        linker().linkSeedTeas(withPhotos = true)
        // Later the PO empties Tanabata's description (M3's editor) and the catalog's text changes.
        data.teas.saveTea(tea(SeedData.TANABATA_TEA_ID).copy(description = null))
        catalogWith("TG0213", "TG0557") { "Ein neuer Text zu $it." }
        val files = photoFiles.dir.list()?.toSet()

        val keys = linker().linkSeedTeas(withPhotos = true)

        assertThat(keys).isEmpty()
        assertThat(tea(SeedData.TANABATA_TEA_ID).description).isNull()
        assertThat(tea(SeedData.HISUI_TEA_ID).description).isEqualTo("Erfundener Text zu TG0557.")
        assertThat(photoFiles.dir.list()?.toSet()).isEqualTo(files)
    }

    @Test
    fun aRunCutShortBeforeItsMarkerChangesNothingTheSecondTime() = runTest {
        // The process died after the link but before the marker was written: the next refresh links again.
        seed()
        catalogWith("TG0213", "TG0557")
        linker(
            marker = object : SeedTeaLinkMarker {
                override val done = MutableStateFlow(emptySet<String>())

                override suspend fun markDone(parts: Set<String>) = Unit
            },
        ).linkSeedTeas(withPhotos = true)
        val teas = listOf(tea(SeedData.TANABATA_TEA_ID), tea(SeedData.HISUI_TEA_ID))
        val files = photoFiles.dir.list()?.toSet()

        linker().linkSeedTeas(withPhotos = true)

        assertThat(listOf(tea(SeedData.TANABATA_TEA_ID), tea(SeedData.HISUI_TEA_ID))).isEqualTo(teas)
        assertThat(photoFiles.dir.list()?.toSet()).isEqualTo(files)
        assertThat(done.value).isEqualTo(allParts)
    }

    @Test
    fun aSeedStillToComeIsLinkedByTheFirstRefreshAfterIt() = runTest {
        // The first start's seed failed and is retried at the next app start (D-075).
        catalogWith("TG0213", "TG0557")

        assertThat(linker().linkSeedTeas(withPhotos = true)).isEmpty()
        assertThat(done.value).isEmpty()

        seed()
        assertThat(linker().linkSeedTeas(withPhotos = true)).containsExactly("TG0213", "TG0557")
        assertThat(done.value).isEqualTo(allParts)
    }

    @Test
    fun aSeedTeaMissingFromTheCatalogIsLinkedByALaterRefreshThatHoldsIt() = runTest {
        // S2 (a): Tanabata's page couldn't be read on the first refresh, so the catalog lacks it.
        seed()
        catalogWith("TG0557")

        assertThat(linker().linkSeedTeas(withPhotos = true)).containsExactly("TG0557")
        assertThat(tea(SeedData.TANABATA_TEA_ID).description).isNull()
        assertThat(
            done.value,
        ).containsNoneOf("${SeedData.TANABATA_TEA_ID}/description", "${SeedData.TANABATA_TEA_ID}/cover")

        catalogWith("TG0213", "TG0557")
        val hisui = tea(SeedData.HISUI_TEA_ID)

        assertThat(linker().linkSeedTeas(withPhotos = true)).containsExactly("TG0213")
        assertThat(tea(SeedData.TANABATA_TEA_ID).description).isEqualTo("Erfundener Text zu TG0213.")
        assertThat(tea(SeedData.TANABATA_TEA_ID).coverPhotoId).isNotNull()
        assertThat(tea(SeedData.HISUI_TEA_ID)).isEqualTo(hisui)
        assertThat(done.value).isEqualTo(allParts)
    }

    @Test
    fun aCoverTheRefreshCouldNotGiveIsAddedByALaterOneWithoutTouchingTheDescription() = runTest {
        // S2 (b): the first refresh stored no photo of the seed teas (one failed, or it was cut short).
        seed()
        catalogWith("TG0213", "TG0557")
        catalogPhotos.retainOnly(emptySet())

        linker().linkSeedTeas(withPhotos = true)

        assertThat(tea(SeedData.TANABATA_TEA_ID).description).isEqualTo("Erfundener Text zu TG0213.")
        assertThat(tea(SeedData.TANABATA_TEA_ID).coverPhotoId).isNull()
        // The PO edits the description meanwhile; the next refresh brings the photos.
        data.teas.saveTea(tea(SeedData.TANABATA_TEA_ID).copy(description = "Mein Text."))
        catalogWith("TG0213", "TG0557") { "Ein neuer Text zu $it." }

        assertThat(linker().linkSeedTeas(withPhotos = true)).containsExactly("TG0213", "TG0557")
        val tanabata = tea(SeedData.TANABATA_TEA_ID)
        assertThat(tanabata.description).isEqualTo("Mein Text.")
        assertThat(tanabata.coverPhotoId).isNotNull()
        assertThat(tea(SeedData.HISUI_TEA_ID).description).isEqualTo("Erfundener Text zu TG0557.")
        assertThat(done.value).isEqualTo(allParts)
    }

    @Test
    fun withPhotosOffTheDescriptionIsEnough() = runTest {
        // SET-22 off: no catalog photo is expected, so the seed teas are done without a cover.
        seed()
        catalogWith("TG0213", "TG0557")
        catalogPhotos.retainOnly(emptySet())

        linker().linkSeedTeas(withPhotos = false)
        catalogWith("TG0213", "TG0557")

        assertThat(done.value).isEqualTo(allParts)
        assertThat(linker().linkSeedTeas(withPhotos = true)).isEmpty()
        assertThat(tea(SeedData.TANABATA_TEA_ID).coverPhotoId).isNull()
    }

    @Test
    fun aSeedTeaWithADescriptionOfItsOwnKeepsIt() = runTest {
        seed()
        // A restored Hisui with the PO's own text: never overwritten; without a cover it gets the photo.
        data.teas.saveTea(tea(SeedData.HISUI_TEA_ID).copy(description = "Mein eigener Text."))
        catalogWith("TG0213", "TG0557")

        linker().linkSeedTeas(withPhotos = true)

        val hisui = tea(SeedData.HISUI_TEA_ID)
        assertThat(hisui.description).isEqualTo("Mein eigener Text.")
        assertThat(hisui.coverPhotoId).isNotNull()
        assertThat(done.value).isEqualTo(allParts)
    }

    @Test
    fun aCatalogEntryWithoutDescriptionOrPhotoGivesWhatItHasAndTheRestLater() = runTest {
        seed()
        catalogWith("TG0213", "TG0557") { null }
        catalogPhotos.retainOnly(setOf("TG0213"))

        linker().linkSeedTeas(withPhotos = true)

        val tanabata = tea(SeedData.TANABATA_TEA_ID)
        assertThat(tanabata.description).isNull()
        assertThat(tanabata.coverPhotoId).isNotNull()
        val hisui = tea(SeedData.HISUI_TEA_ID)
        assertThat(hisui.description).isNull()
        assertThat(hisui.coverPhotoId).isNull()
        assertThat(done.value).containsExactly("${SeedData.TANABATA_TEA_ID}/cover")

        catalogWith("TG0213", "TG0557")
        linker().linkSeedTeas(withPhotos = true)

        assertThat(tea(SeedData.TANABATA_TEA_ID).coverPhotoId).isEqualTo(tanabata.coverPhotoId)
        assertThat(tea(SeedData.TANABATA_TEA_ID).description).isEqualTo("Erfundener Text zu TG0213.")
        assertThat(tea(SeedData.HISUI_TEA_ID).coverPhotoId).isNotNull()
        assertThat(done.value).isEqualTo(allParts)
    }
}
