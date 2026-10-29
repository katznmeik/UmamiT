package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToLong

/** The first-start seed data (M1-07, WARE-06) checked against the table in 08 §2.5a. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SeedDataSeederTest {
    private val data = TestDatabase(now = FIRST_START)
    private var settings = FakeSettingsRepository()
    private val seeder get() = SeedDataSeeder(
        data.db,
        data.teas,
        data.recipes,
        data.vessels,
        data.stock,
        settings,
        data.time,
    )

    @After
    fun tearDown() = data.close()

    private suspend fun seededTea(catalogKey: String): Tea =
        data.teas.observeTeas().first().single { it.tea.catalogKey == catalogKey }.tea

    @Test
    fun freshDatabaseGetsTanabataWithItsFactualValuesOnly() = runTest {
        seeder.seedIfNeeded()

        val tanabata = seededTea("TG0213")
        assertThat(tanabata).isEqualTo(
            Tea(
                id = tanabata.id,
                name = "Bio Sencha Tanabata",
                category = TeaCategory.GREEN,
                form = TeaForm.LOOSE_LEAF,
                catalogKey = "TG0213",
                styleKey = "sencha",
                teaType = "Sencha",
                umamiLevel = UmamiLevel.MEDIUM,
                isFlavored = false,
                isOrganic = true,
                originCountry = "JP",
                originRegion = "Kagoshima (Kyushu)",
                cultivar = "Blend, hauptsächlich Yabukita",
                harvestSeason = HarvestSeason.SUMMER,
                shadingDays = null,
                processingNotes = "Asamushi (leicht gedämpft)",
                vendor = "Tee-Kontor Kiel",
                productUrl = "https://www.tee-kontor-kiel.de/bio-sencha-tanabata/TG0213",
            ),
        )
    }

    @Test
    fun freshDatabaseGetsHisuiWithItsFactualValuesOnly() = runTest {
        seeder.seedIfNeeded()

        val hisui = seededTea("TG0557")
        assertThat(hisui).isEqualTo(
            Tea(
                id = hisui.id,
                name = "Bio Sencha Hisui",
                category = TeaCategory.GREEN,
                form = TeaForm.LOOSE_LEAF,
                catalogKey = "TG0557",
                styleKey = "sencha",
                teaType = "Sencha",
                umamiLevel = UmamiLevel.LOW,
                isFlavored = false,
                isOrganic = true,
                originCountry = "JP",
                originRegion = "Kirishima, Kagoshima (Kyushu)",
                cultivar = "Okumidori (100 %)",
                harvestSeason = HarvestSeason.FIRST_FLUSH,
                shadingDays = 6,
                processingNotes = "Asamushi (leicht gedämpft)",
                vendor = "Tee-Kontor Kiel",
                productUrl = "https://www.tee-kontor-kiel.de/bio-sencha-hisui/TG0557",
            ),
        )
        assertThat(data.teas.observeTeas().first()).hasSize(2)
    }

    @Test
    fun tanabataGetsKyusuAsDefaultAndWestlich() = runTest {
        seeder.seedIfNeeded()
        val tea = seededTea("TG0213")
        val kyusu = vesselId("Kyusu 250 ml")
        val mug = vesselId("Becher mit Sieb 400 ml")

        assertThat(recipesOf(tea)).containsExactly(
            Recipe(
                id = "",
                teaId = tea.id,
                name = "Kyusu",
                method = BrewMethod.KYUSU,
                leafGrams = 5.0,
                waterMl = 250,
                baseTempC = 65,
                steps = listOf(infusion(45), infusion(10), infusion(10)),
                vesselId = kyusu,
                isDefault = true,
                position = 0,
                source = RecipeSource.CATALOG_TABLE,
            ),
            Recipe(
                id = "",
                teaId = tea.id,
                name = "Westlich",
                method = BrewMethod.WESTERN,
                leafGrams = 4.0,
                waterMl = 400,
                baseTempC = 70,
                steps = listOf(infusion(60), infusion(15, tempC = 75)),
                vesselId = mug,
                position = 1,
                source = RecipeSource.CATALOG_TABLE,
            ),
        ).inOrder()
    }

    @Test
    fun hisuiGetsKyusuAsDefaultWestlichAndKaltaufguss() = runTest {
        seeder.seedIfNeeded()
        val tea = seededTea("TG0557")
        val kyusu = vesselId("Kyusu 250 ml")
        val mug = vesselId("Becher mit Sieb 400 ml")

        assertThat(recipesOf(tea)).containsExactly(
            Recipe(
                id = "",
                teaId = tea.id,
                name = "Kyusu",
                method = BrewMethod.KYUSU,
                leafGrams = 5.0,
                waterMl = 250,
                baseTempC = 65,
                steps = listOf(infusion(60), infusion(10), infusion(10)),
                vesselId = kyusu,
                isDefault = true,
                position = 0,
                source = RecipeSource.CATALOG_TABLE,
            ),
            Recipe(
                id = "",
                teaId = tea.id,
                name = "Westlich",
                method = BrewMethod.WESTERN,
                leafGrams = 4.0,
                waterMl = 400,
                baseTempC = 65,
                steps = listOf(infusion(60), infusion(15, tempC = 70)),
                vesselId = mug,
                position = 1,
                source = RecipeSource.CATALOG_TABLE,
            ),
            Recipe(
                id = "",
                teaId = tea.id,
                name = "Kaltaufguss",
                method = BrewMethod.COLD_BREW,
                leafGrams = 10.0,
                waterMl = 1000,
                baseTempC = 5,
                steps = listOf(infusion(4 * 60 * 60)),
                position = 2,
                source = RecipeSource.STYLE_DEFAULT,
            ),
        ).inOrder()
    }

    @Test
    fun eachTeaGetsAnInitialHundredGramsAtThePricePaidDatedToTheFirstStart() = runTest {
        seeder.seedIfNeeded()

        for ((key, price) in listOf("TG0213" to 850L, "TG0557" to 1790L)) {
            val tea = seededTea(key)
            val events = data.stock.observeEvents(tea.id).first()
            assertThat(events.map { it.copy(id = "") }).containsExactly(
                InventoryEvent(
                    id = "",
                    teaId = tea.id,
                    type = InventoryEventType.INITIAL,
                    deltaGrams = 100.0,
                    occurredAtMillis = FIRST_START,
                    priceCents = price,
                    currency = "EUR",
                    packageLabel = "100 g",
                ),
            )
            assertThat(data.stock.observeStock(tea.id).first().remainingGrams).isWithin(1e-9).of(100.0)
            // Unopened: the first session sets the opened date (INV-06).
            assertThat(tea.openedDate).isNull()
        }
    }

    @Test
    fun costPerCupMatchesTheTableForBothRecipesOfEachTea() = runTest {
        seeder.seedIfNeeded()

        // 08 §2.5a: Kyusu with 3 infusions, Becher („Westlich") with 2.
        val expectedCentsPerCup = mapOf(
            ("TG0213" to "Kyusu") to 14L,
            ("TG0213" to "Westlich") to 17L,
            ("TG0557" to "Kyusu") to 30L,
            ("TG0557" to "Westlich") to 36L,
        )
        for ((key, centsPerCup) in expectedCentsPerCup) {
            val tea = seededTea(key.first)
            val recipe = recipesOf(tea).single { it.name == key.second }
            val costPerGram = data.stock.observeStock(tea.id).first().costPerGramCents!!
            val cups = recipe.steps.count { it.kind == StepKind.INFUSION }

            assertThat((recipe.leafGrams * costPerGram / cups).roundToLong()).isEqualTo(centsPerCup)
        }
    }

    @Test
    fun seedingSetsTheSeedMarker() = runTest {
        assertThat(seeder.seedIfNeeded()).isTrue()

        assertThat(settings.seedDataCreated.first()).isTrue()
    }

    @Test
    fun runningTheSeederTwiceCreatesNothingNew() = runTest {
        seeder.seedIfNeeded()
        val afterFirst = collection()

        assertThat(seeder.seedIfNeeded()).isFalse()

        assertThat(collection()).isEqualTo(afterFirst)
    }

    @Test
    fun aDeletedSeedTeaDoesNotComeBack() = runTest {
        seeder.seedIfNeeded()
        data.teas.deleteTea(seededTea("TG0213").id)
        val afterDelete = collection()

        assertThat(seeder.seedIfNeeded()).isFalse()

        assertThat(collection()).isEqualTo(afterDelete)
        assertThat(data.teas.observeTeas().first().map { it.tea.catalogKey }).containsExactly("TG0557")
    }

    @Test
    fun aRestoredDatabaseWithDataIsNotSeededEvenWithoutTheMarker() = runTest {
        // A device restore brings the database back; the marker may be missing (05 §8).
        data.teas.saveTea(tea("own", name = "Gyokuro Asahi"))

        assertThat(seeder.seedIfNeeded()).isFalse()

        assertThat(data.teas.observeTeas().first().map { it.tea.id }).containsExactly("own")
        assertThat(data.vessels.observeVessels().first()).isEmpty()
        // The marker is written, so later starts skip the check.
        assertThat(settings.seedDataCreated.first()).isTrue()
    }

    @Test
    fun aDatabaseWithOnlyAVesselIsNotSeeded() = runTest {
        data.vessels.saveVessel(Vessel("v1", "Shiboridashi", VesselType.SHIBORIDASHI, 120))

        assertThat(seeder.seedIfNeeded()).isFalse()

        assertThat(data.teas.observeTeas().first()).isEmpty()
    }

    @Test
    fun anEmptyDatabaseIsNotSeededOnceTheMarkersAreSet() = runTest {
        // Only the v0.2.1 vessels are added to an install seeded before (SeedVesselsTest).
        settings = FakeSettingsRepository(seeded = true, seedVesselsV2Added = true)

        assertThat(seeder.seedIfNeeded()).isFalse()

        assertThat(collection()).isEqualTo(Collection(emptyList(), emptyList(), emptyList(), emptyList()))
    }

    private data class Collection(
        val teas: List<Tea>,
        val vessels: List<Vessel>,
        val recipes: List<Recipe>,
        val events: List<InventoryEvent>,
    )

    private suspend fun collection(): Collection {
        val teas = data.teas.observeTeas().first().map { it.tea }
        return Collection(
            teas = teas,
            vessels = data.vessels.observeVessels().first(),
            recipes = teas.flatMap { data.recipes.observeRecipes(it.id).first() },
            events = teas.flatMap { data.stock.observeEvents(it.id).first() },
        )
    }

    private suspend fun vesselId(name: String): String =
        data.vessels.observeVessels().first().single { it.name == name }.id

    private suspend fun recipesOf(tea: Tea): List<Recipe> =
        data.recipes.observeRecipes(tea.id).first().map { it.copy(id = "") }

    private fun infusion(seconds: Int, tempC: Int? = null) = RecipeStep(StepKind.INFUSION, seconds, tempC)

    private companion object {
        /** 21. Okt. 2026, 08:00 UTC. */
        const val FIRST_START = 1_792_569_600_000L
    }
}
