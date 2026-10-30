package com.umamit.core.data.catalog

import android.content.Context
import android.database.SQLException
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.ArchiveResult
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.RoomTeaArchive
import com.umamit.core.data.SeedData
import com.umamit.core.data.StockRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.data.TestClocks
import com.umamit.core.data.TestDatabase
import com.umamit.core.data.TimerDriver
import com.umamit.core.data.decodedSize
import com.umamit.core.data.infusion
import com.umamit.core.data.sessionStart
import com.umamit.core.data.snapshotOf
import com.umamit.core.data.startEvent
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogPackageEntity
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.database.entity.StagedCatalogPackageEntity
import com.umamit.core.database.entity.StagedCatalogTeaEntity
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel
import com.umamit.core.timer.engine.TimerEvent
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
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * *In Sammlung aufnehmen* and *Nachkauf erfassen* (CAT-12, CAT-13, TEA-01, INV-01, D-073) on
 * both databases in memory: a synthetic catalog tea (D-016) with its catalog photo in a
 * temporary folder becomes an independent collection tea that the tea picker lists and the
 * timer brews with stock deduction; an owned catalog key, the seed teas' included, records a
 * repurchase.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AddFromCatalogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val data = TestDatabase(now = NOW)
    private val catalogDb = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private lateinit var catalogPhotos: CatalogPhotoFiles
    private lateinit var photoFiles: PhotoFiles
    private lateinit var add: RoomAddFromCatalog

    @Before
    fun setUp() {
        catalogPhotos = CatalogPhotoFiles(folder.newFolder("catalog"))
        photoFiles = PhotoFiles(folder.newFolder("photos"))
        add = RoomAddFromCatalog(
            db = data.db,
            teas = data.teas,
            recipes = data.recipes,
            stock = data.stock,
            catalog = catalogDb.catalogDao(),
            catalogPhotos = catalogPhotos,
            photoFiles = photoFiles,
            time = clock,
        )
    }

    @After
    fun tearDown() {
        data.close()
        catalogDb.close()
    }

    private val clock = object : com.umamit.core.common.WallClock {
        override fun nowMs(): Long = NOW

        override fun zone(): ZoneId = BERLIN
    }

    private suspend fun catalog(vararg teas: Pair<CatalogTeaEntity, List<CatalogPackageEntity>>) {
        val dao = catalogDb.catalogDao()
        teas.forEach { (tea, packages) ->
            dao.stage(StagedCatalogTeaEntity(tea))
            dao.stagePackages(packages.map(::StagedCatalogPackageEntity))
        }
        dao.swap(
            emptyList(),
            CatalogRefreshLogEntity(
                startedAt = NOW - 1,
                finishedAt = NOW - 1,
                status = CatalogRefreshStatus.SUCCESS,
                newCount = teas.size,
                changedCount = 0,
                discontinuedCount = 0,
                flaggedCount = 0,
                photosCount = 0,
                parserVersion = 4,
                errorSummary = null,
            ),
        )
    }

    private fun purchase(key: String = KEY, grams: Double = 100.0, priceCents: Long? = 1240, on: LocalDate = TODAY) =
        CatalogPurchase(catalogKey = key, grams = grams, priceCents = priceCents, purchasedOn = on)

    @Test
    fun `adding copies the tea with its recipes, flag, stock and cover photo`() = runTest {
        catalog(sencha() to packages())
        catalogPhotos.write(KEY, syntheticWebp(1200, 900), syntheticWebp(341, 256))

        val result = add.add(purchase())

        val teaId = (result as AddFromCatalogResult.Added).teaId
        val tea = data.teas.getTea(teaId)!!
        assertThat(tea.name).isEqualTo("Morgennebel Sencha")
        assertThat(tea.secondaryName).isEqualTo("朝霧")
        assertThat(tea.catalogKey).isEqualTo(KEY)
        assertThat(tea.productUrl).isEqualTo(PRODUCT_URL)
        assertThat(tea.vendor).isEqualTo("Tee-Kontor Kiel")
        assertThat(tea.category).isEqualTo(TeaCategory.GREEN)
        assertThat(tea.form).isEqualTo(TeaForm.LOOSE_LEAF)
        assertThat(tea.styleKey).isEqualTo("sencha")
        assertThat(tea.teaType).isEqualTo("Sencha")
        // The shop's Umami scale of 4 (08 §2.6).
        assertThat(tea.umamiLevel).isEqualTo(UmamiLevel.VERY_HIGH)
        assertThat(tea.isOrganic).isTrue()
        assertThat(tea.isFlavored).isFalse()
        assertThat(tea.originCountry).isEqualTo("JP")
        assertThat(tea.originRegion).isEqualTo("Kagoshima")
        assertThat(tea.cultivar).isEqualTo("Yabukita")
        // 08 §2.3's conversion table.
        assertThat(tea.harvestYear).isEqualTo(2026)
        assertThat(tea.harvestSeason).isEqualTo(HarvestSeason.FIRST_FLUSH)
        assertThat(tea.shadingDays).isEqualTo(6)
        assertThat(tea.processingNotes).isEqualTo("Chumushi")
        assertThat(tea.description).isEqualTo("Ein erfundener Tee.\n\nZubereitung: 4 g auf 100 ml bei 60 °C.")
        // The flag travels with the tea (CAT-05).
        assertThat(tea.recipeCheckReason).isEqualTo(RecipeCheckReason.FROM_TEXT)

        val recipes = data.recipes.observeRecipes(teaId).first()
        assertThat(recipes.map { it.name }).containsExactly("Kyusu", "Kaltaufguss").inOrder()
        val kyusu = recipes.first()
        assertThat(kyusu.isDefault).isTrue()
        assertThat(kyusu.method).isEqualTo(BrewMethod.KYUSU)
        assertThat(kyusu.leafGrams).isEqualTo(4.0)
        assertThat(kyusu.waterMl).isEqualTo(100)
        assertThat(kyusu.baseTempC).isEqualTo(60)
        assertThat(kyusu.source).isEqualTo(RecipeSource.CATALOG_TEXT)
        assertThat(kyusu.steps).isEqualTo(KYUSU.steps)
        assertThat(recipes.last().isDefault).isFalse()
        assertThat(recipes.last().source).isEqualTo(RecipeSource.STYLE_DEFAULT)

        val event = data.stock.observeEvents(teaId).first().single()
        assertThat(event.type).isEqualTo(InventoryEventType.INITIAL)
        assertThat(event.deltaGrams).isEqualTo(100.0)
        assertThat(event.priceCents).isEqualTo(1240)
        assertThat(event.currency).isEqualTo("EUR")
        assertThat(event.packageLabel).isEqualTo("100 g")
        // Bought today: now.
        assertThat(event.occurredAtMillis).isEqualTo(NOW)

        // The catalog photo, copied as the collection's own JPEG (05 §3.9, D-076).
        val cover = data.teas.observeTea(teaId).first()!!.coverPhoto!!
        assertThat(cover.origin).isEqualTo(PhotoOrigin.CATALOG_COPY)
        assertThat(cover.widthPx to cover.heightPx).isEqualTo(1200 to 900)
        val file = photoFiles.file(cover.id)
        assertThat(file.isFile).isTrue()
        assertThat(cover.byteSize).isEqualTo(file.length())
        val bytes = file.readBytes()
        assertThat(bytes.take(2)).containsExactly(0xFF.toByte(), 0xD8.toByte()).inOrder()
        assertThat(decodedSize(bytes)).isEqualTo(1200 to 900)
        // The catalog's own files stay where they are.
        assertThat(catalogPhotos.exists(KEY)).isTrue()
    }

    @Test
    fun `the new tea is in the tea picker's data at once and brews with stock deduction`() = runTest {
        catalog(sencha() to packages())
        val teaId = (add.add(purchase()) as AddFromCatalogResult.Added).teaId

        // The tea picker lists every tea of the collection with its default recipe (SCR-03).
        val listed = data.teas.observeTeas().first().single { it.tea.id == teaId }
        assertThat(listed.defaultRecipe!!.name).isEqualTo("Kyusu")

        val recipe = listed.defaultRecipe!!
        val clocks = TestClocks(wall = NOW + 60_000)
        val driver = TimerDriver(data, clocks) { id, steps, preAlert ->
            sessionStart(id, steps, preAlert, clocks, snapshotOf(teaId = teaId, leafGrams = recipe.leafGrams))
        }
        driver.send(startEvent(infusion(60)))
        driver.send(TimerEvent.Start)
        clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.send(TimerEvent.Poured)
        driver.send(TimerEvent.FinishSession)

        // 100 g bought, 4 g brewed (INV-02).
        assertThat(data.stock.observeStock(teaId).first().remainingGrams).isWithin(1e-9).of(96.0)
    }

    @Test
    fun `an owned catalog key records a repurchase instead of a second tea`() = runTest {
        catalog(sencha() to packages())
        val teaId = (add.add(purchase()) as AddFromCatalogResult.Added).teaId
        assertThat(add.observeOwner(KEY).first()).isEqualTo(teaId)

        val again = add.add(purchase(grams = 250.0, priceCents = 2990, on = LocalDate.of(2026, 10, 20)))

        assertThat(again).isEqualTo(AddFromCatalogResult.Repurchased(teaId))
        assertThat(data.teas.observeTeas().first().map { it.tea.id }).containsExactly(teaId)
        val events = data.stock.observeEvents(teaId).first()
        assertThat(events.map { it.type }).containsExactly(InventoryEventType.PURCHASE, InventoryEventType.INITIAL)
        val repurchase = events.single { it.type == InventoryEventType.PURCHASE }
        assertThat(repurchase.deltaGrams).isEqualTo(250.0)
        assertThat(repurchase.priceCents).isEqualTo(2990)
        assertThat(repurchase.packageLabel).isEqualTo("250 g")
        // A day before today: that day at noon in the phone's zone.
        assertThat(repurchase.occurredAtMillis)
            .isEqualTo(ZonedDateTime.of(2026, 10, 20, 12, 0, 0, 0, BERLIN).toInstant().toEpochMilli())
        assertThat(data.stock.observeStock(teaId).first().remainingGrams).isEqualTo(350.0)
        // Recipes and cover stay as they were.
        assertThat(data.recipes.observeRecipes(teaId).first()).hasSize(2)
    }

    @Test
    fun `the seed teas Tanabata and Hisui are owned, so they take repurchases`() = runTest {
        SeedData.vessels.forEach { data.vessels.saveVessel(it) }
        SeedData.teas.forEach { data.teas.saveTea(it) }
        SeedData.recipes.forEach { data.recipes.saveRecipe(it) }
        catalog(
            sencha(key = "TG0213", name = "Synthetischer Sencha A") to emptyList(),
            sencha(key = "TG0557", name = "Synthetischer Sencha B") to emptyList(),
        )

        assertThat(add.observeOwner("TG0213").first()).isEqualTo(SeedData.TANABATA_TEA_ID)
        assertThat(add.observeOwner("TG0557").first()).isEqualTo(SeedData.HISUI_TEA_ID)
        assertThat(add.add(purchase(key = "TG0557", priceCents = null)))
            .isEqualTo(AddFromCatalogResult.Repurchased(SeedData.HISUI_TEA_ID))

        assertThat(data.teas.observeTeas().first()).hasSize(2)
        val hisui = data.stock.observeEvents(SeedData.HISUI_TEA_ID).first().single()
        assertThat(hisui.type).isEqualTo(InventoryEventType.PURCHASE)
        assertThat(hisui.priceCents).isNull()
        // The seed tea itself is not touched (CAT-13).
        assertThat(data.teas.getTea(SeedData.HISUI_TEA_ID)).isEqualTo(SeedData.hisui)
    }

    @Test
    fun `a removed tea is no longer im Besitz and the catalog offers to add it again`() = runTest {
        catalog(sencha() to packages())
        val teaId = (add.add(purchase()) as AddFromCatalogResult.Added).teaId
        val owned = RoomOwnedCatalogKeys(data.db.teaDao())
        val listed = RoomCatalogRepository(catalogDb.catalogDao(), catalogPhotos, owned)
        assertThat(listed.observeCatalog().first().teas.single().owned).isTrue()

        assertThat(RoomTeaArchive(data.db, data.time).archive(teaId)).isEqualTo(ArchiveResult.Archived)

        // No „im Besitz" badge or filter hit, and the detail offers *In Sammlung aufnehmen* (TEA-04, CAT-10).
        assertThat(listed.observeCatalog().first().teas.single().owned).isFalse()
        assertThat(owned.observeOwned().first()).isEmpty()
        assertThat(add.observeOwner(KEY).first()).isNull()
        // The refresher still keeps a discontinued catalog tea for it (CAT-07 AC4): it stays linked.
        assertThat(owned.observe().first()).containsExactly(KEY)
    }

    @Test
    fun `adding a removed tea again brings back the same tea with a purchase, never a second one`() = runTest {
        catalog(sencha() to packages())
        val teaId = (add.add(purchase()) as AddFromCatalogResult.Added).teaId
        RoomTeaArchive(data.db, data.time).archive(teaId)

        val again = add.add(purchase(grams = 250.0, priceCents = 2990))

        // D-085, D-093 Q6: un-archived, its package a PURCHASE; recipes and earlier stock stay.
        assertThat(again).isEqualTo(AddFromCatalogResult.Repurchased(teaId))
        assertThat(data.teas.observeTeas().first().map { it.tea.id }).containsExactly(teaId)
        assertThat(data.teas.getTea(teaId)!!.isArchived).isFalse()
        assertThat(data.teas.observeOwnedTeas().first().map { it.tea.id }).containsExactly(teaId)
        assertThat(data.stock.observeEvents(teaId).first().map { it.type })
            .containsExactly(InventoryEventType.INITIAL, InventoryEventType.PURCHASE)
        assertThat(data.stock.observeStock(teaId).first().remainingGrams).isEqualTo(350.0)
        assertThat(data.recipes.observeRecipes(teaId).first()).hasSize(2)
        assertThat(add.observeOwner(KEY).first()).isEqualTo(teaId)
    }

    @Test
    fun `a tea without a usable catalog photo is added without a cover`() = runTest {
        catalog(sencha() to packages(), sencha(key = "XY0200", name = "Ohne Foto") to emptyList())
        // The catalog row names a photo file that is no image.
        folder.root.resolve("catalog/$KEY.webp").writeText("kein Bild")

        val withBrokenPhoto = (add.add(purchase()) as AddFromCatalogResult.Added).teaId
        val withoutPhoto = (add.add(purchase(key = "XY0200")) as AddFromCatalogResult.Added).teaId

        assertThat(data.teas.getTea(withBrokenPhoto)!!.coverPhotoId).isNull()
        assertThat(data.teas.getTea(withoutPhoto)!!.coverPhotoId).isNull()
        assertThat(photoFiles.dir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `a catalog tea without derived recipes gets its style's recipes`() = runTest {
        catalog(sencha(recipes = null, styleKey = "gyokuro") to emptyList())

        val teaId = (add.add(purchase()) as AddFromCatalogResult.Added).teaId

        val recipes = data.recipes.observeRecipes(teaId).first()
        assertThat(recipes).isNotEmpty()
        assertThat(recipes.count { it.isDefault }).isEqualTo(1)
        assertThat(recipes.first { it.isDefault }.method).isEqualTo(BrewMethod.KYUSU)
        assertThat(recipes.map { it.source }.distinct()).containsExactly(RecipeSource.STYLE_DEFAULT)
    }

    @Test
    fun `an unexpected error writes nothing and ends as failed instead of crashing`() = runTest {
        // Rule 8: the add runs in the process the timer runs in; a bug becomes „Failed" (D-090).
        catalog(sencha() to packages())
        val broken = object : TeaRepository by data.teas {
            override suspend fun saveTea(tea: Tea) = throw IllegalStateException("a bug")
        }
        val add = RoomAddFromCatalog(
            db = data.db,
            teas = broken,
            recipes = data.recipes,
            stock = data.stock,
            catalog = catalogDb.catalogDao(),
            catalogPhotos = catalogPhotos,
            photoFiles = photoFiles,
            time = clock,
        )

        assertThat(add.add(purchase())).isEqualTo(AddFromCatalogResult.Failed)
        assertThat(data.teas.observeTeas().first()).isEmpty()
        assertThat(photoFiles.dir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `adding a marked tea removes its mark`() = runTest {
        // CAT-18 AC2, 05 §3.13: *In Sammlung aufnehmen* ends the Merkliste entry; other marks stay.
        catalog(sencha() to packages(), sencha(key = "XY0200", name = "Abendrot Sencha") to emptyList())
        data.bookmarks.mark(KEY)
        data.bookmarks.mark("XY0200")

        assertThat(add.add(purchase())).isInstanceOf(AddFromCatalogResult.Added::class.java)

        assertThat(data.bookmarks.observeMarked().first()).containsExactly("XY0200")
    }

    @Test
    fun `a failed add keeps the mark`() = runTest {
        // The mark goes in the add's transaction: a failure after the tea was written rolls both back.
        catalog(sencha() to packages())
        data.bookmarks.mark(KEY)
        val broken = object : StockRepository by data.stock {
            override suspend fun addEvent(event: InventoryEvent) = throw SQLException("disk full")
        }
        val add = RoomAddFromCatalog(
            db = data.db,
            teas = data.teas,
            recipes = data.recipes,
            stock = broken,
            catalog = catalogDb.catalogDao(),
            catalogPhotos = catalogPhotos,
            photoFiles = photoFiles,
            time = clock,
        )

        assertThat(add.add(purchase())).isEqualTo(AddFromCatalogResult.Failed)

        assertThat(data.teas.observeTeas().first()).isEmpty()
        assertThat(data.bookmarks.isMarked(KEY)).isTrue()
    }

    @Test
    fun `adding a removed tea that was marked again brings it back and removes the mark`() = runTest {
        // A removed (archived) tea is not in the collection, so it can be marked (CAT-18 AC2).
        catalog(sencha() to packages())
        val teaId = (add.add(purchase()) as AddFromCatalogResult.Added).teaId
        RoomTeaArchive(data.db, data.time).archive(teaId)
        data.bookmarks.mark(KEY)

        assertThat(add.add(purchase())).isEqualTo(AddFromCatalogResult.Repurchased(teaId))

        assertThat(data.bookmarks.isMarked(KEY)).isFalse()
    }

    @Test
    fun `a key the catalog doesn't hold adds nothing`() = runTest {
        assertThat(add.add(purchase(key = "XY9999"))).isEqualTo(AddFromCatalogResult.NotInCatalog)
        assertThat(data.teas.observeTeas().first()).isEmpty()
    }

    @Test
    fun `a Kraeutertee is added as a herbal tea with its recipe and stock`() = runTest {
        // v0.2.1, D-093: the shop's Kräutertees are added and repurchased as green teas are (CAT-10).
        val herbal = sencha(key = "XK0200", name = "Wiesenzauber", recipes = null, styleKey = "herbal").copy(
            shopCategory = "Tee › Kräutertee › Wiesenmischungen",
            teaType = "Wiesenmischungen",
            breadcrumb = "/tee/kraeutertee/wiesenmischungen/",
            originCountry = null,
            tasteScalesJson = null,
            shading = null,
            steaming = null,
        )
        catalog(herbal to packages("XK0200"))

        val teaId = (add.add(purchase(key = "XK0200")) as AddFromCatalogResult.Added).teaId

        val tea = data.teas.getTea(teaId)!!
        assertThat(tea.category).isEqualTo(TeaCategory.HERBAL)
        assertThat(tea.styleKey).isEqualTo("herbal")
        assertThat(tea.teaType).isEqualTo("Wiesenmischungen")
        assertThat(tea.umamiLevel).isEqualTo(UmamiLevel.NONE)
        assertThat(tea.form).isEqualTo(TeaForm.LOOSE_LEAF)
        val recipe = data.recipes.observeRecipes(teaId).first().single()
        assertThat(recipe.method).isEqualTo(BrewMethod.WESTERN)
        assertThat(recipe.leafGrams to recipe.waterMl).isEqualTo(2.0 to 250)
        assertThat(recipe.baseTempC).isEqualTo(100)
        assertThat(recipe.steps).containsExactly(RecipeStep(StepKind.INFUSION, 360))
        assertThat(data.stock.observeEvents(teaId).first().single().type).isEqualTo(InventoryEventType.INITIAL)

        // Repurchase works as for a green tea (CAT-13).
        assertThat(add.add(purchase(key = "XK0200"))).isInstanceOf(AddFromCatalogResult.Repurchased::class.java)
    }

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
        val TODAY: LocalDate = LocalDate.of(2026, 10, 25)
        val NOW: Long = ZonedDateTime.of(2026, 10, 25, 9, 30, 0, 0, BERLIN).toInstant().toEpochMilli()
        const val KEY = "XY0100"
        const val PRODUCT_URL = "https://shop.example/morgennebel-sencha/XY0100"
        const val VARIANT = "https://shop.example/morgennebel-sencha"

        val KYUSU = RecipeTemplate(
            name = "Kyusu",
            method = BrewMethod.KYUSU,
            leafGrams = 4.0,
            waterMl = 100,
            baseTempC = 60,
            steps = listOf(RecipeStep(StepKind.INFUSION, 90), RecipeStep(StepKind.INFUSION, 20, tempC = 65)),
            source = RecipeSource.CATALOG_TEXT,
            isDefault = true,
        )
        val COLD = RecipeTemplate(
            name = "Kaltaufguss",
            method = BrewMethod.COLD_BREW,
            leafGrams = 10.0,
            waterMl = 1000,
            baseTempC = 5,
            steps = listOf(RecipeStep(StepKind.INFUSION, 4 * 3600)),
            source = RecipeSource.STYLE_DEFAULT,
        )

        fun sencha(
            key: String = KEY,
            name: String = "Morgennebel Sencha",
            recipes: List<RecipeTemplate>? = listOf(KYUSU, COLD),
            styleKey: String = "sencha",
        ) = CatalogTeaEntity(
            key = key,
            name = name,
            secondaryName = "朝霧".takeIf { key == KEY },
            productUrl = if (key == KEY) PRODUCT_URL else "https://shop.example/tee/$key",
            shopCategory = "Tee › Grüner Tee › Japan › Sencha",
            teaType = "Sencha",
            styleKey = styleKey,
            originCountry = "Japan",
            originRegion = "Kagoshima",
            cultivar = "Yabukita",
            harvest = "Frühlingsernte 2026",
            shading = "6 Tage",
            steaming = "Chumushi",
            isOrganic = true,
            tasteScalesJson = """{"Umami":4,"Bitterstoffe":1}""",
            breadcrumb = "/tee/gruener-tee/japan/sencha/",
            descriptionText = "Ein erfundener Tee.\n\nZubereitung: 4 g auf 100 ml bei 60 °C.",
            derivedRecipesJson = recipes?.let(DerivedRecipesColumn::json),
            recipeCheckReason = RecipeCheckReason.FROM_TEXT.takeIf { key == KEY },
            basePriceCentsPer100g = 1240,
            available = true,
            photoFile = "$key.webp".takeIf { key == KEY },
            firstSeenAt = NOW - 1,
            lastSeenAt = NOW - 1,
        )

        fun packages(key: String = KEY) = listOf(
            CatalogPackageEntity(key, "$key-10", "10 g", 10.0, null, false, null, "$VARIANT/$key-10", null),
            CatalogPackageEntity(key, key, "100 g", 100.0, 1240, false, null, PRODUCT_URL, true),
            CatalogPackageEntity(key, "$key-250", "250 g · 3 %", 250.0, 3007, true, 3, "$VARIANT/$key-250", null),
        )

        fun syntheticWebp(width: Int, height: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(74, 86, 67))
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out)
            return out.toByteArray()
        }
    }
}
