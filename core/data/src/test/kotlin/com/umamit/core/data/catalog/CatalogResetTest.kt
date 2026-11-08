package com.umamit.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SESSION_ID
import com.umamit.core.data.TestClocks
import com.umamit.core.data.TestDatabase
import com.umamit.core.data.TimerDriver
import com.umamit.core.data.infusion
import com.umamit.core.data.sessionStart
import com.umamit.core.data.snapshotOf
import com.umamit.core.data.startEvent
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.database.entity.StagedCatalogTeaEntity
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * *Aus Katalog zurücksetzen* (PRF-06, CAT-13, TEA-17, D-104) on both databases in memory: a synthetic collection
 * tea (D-016) linked to a synthetic catalog tea takes the catalog's current description and recipes, keeps its
 * own recipes and everything else, and a running brew of it is left alone (rule 8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogResetTest {
    private val data = TestDatabase(now = NOW)
    private val catalogDb = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val reset = RoomCatalogReset(data.db, data.teas, data.recipes, catalogDb.catalogDao())

    @After
    fun tearDown() {
        data.close()
        catalogDb.close()
    }

    private suspend fun catalog(entry: CatalogTeaEntity) {
        val dao = catalogDb.catalogDao()
        dao.stage(StagedCatalogTeaEntity(entry))
        dao.swap(
            emptyList(),
            CatalogRefreshLogEntity(
                startedAt = NOW - 1,
                finishedAt = NOW - 1,
                status = CatalogRefreshStatus.SUCCESS,
                newCount = 1,
                changedCount = 0,
                discontinuedCount = 0,
                flaggedCount = 0,
                photosCount = 0,
                parserVersion = 4,
                errorSummary = null,
            ),
        )
    }

    /** The collection tea as *In Sammlung aufnehmen* left it, plus [own] recipes the PO wrote. */
    private suspend fun collectionTea(
        catalogDefault: Boolean = true,
        own: Int = 1,
        oldCatalog: Int = 2,
    ): List<Recipe> {
        data.teas.saveTea(TEA)
        val recipes = (0 until oldCatalog).map { i ->
            recipe("old$i", RecipeSource.CATALOG_TABLE, i, isDefault = catalogDefault && i == 0)
        } + (0 until own).map { i ->
            recipe("own$i", RecipeSource.USER, oldCatalog + i, isDefault = !catalogDefault && i == 0)
        }
        recipes.forEach { data.recipes.saveRecipe(it) }
        // Saving a recipe clears „Rezept prüfen" (TEA-17): the flag is set after the recipes.
        data.teas.saveTea(TEA)
        return recipes
    }

    private suspend fun recipesNow() = data.recipes.observeRecipes(TEA_ID).first().sortedBy { it.position }

    @Test
    fun `the catalog's recipes replace the old catalog recipes, own recipes stay`() = runTest {
        catalog(entry())
        collectionTea(catalogDefault = true, own = 1)

        assertThat(reset.reset(TEA_ID)).isEqualTo(CatalogResetResult.Reset(leftOut = 0))

        val recipes = recipesNow()
        assertThat(recipes.map { it.name }).containsExactly("Kyusu neu", "Kalt neu", "Eigenes 0").inOrder()
        assertThat(recipes[0].source).isEqualTo(RecipeSource.CATALOG_TEXT)
        assertThat(recipes[0].leafGrams).isEqualTo(5.0)
        assertThat(recipes[0].steps).isEqualTo(NEW_KYUSU.steps)
        assertThat(recipes[1].source).isEqualTo(RecipeSource.STYLE_DEFAULT)
        assertThat(recipes[2].id).isEqualTo("own0")
        assertThat(recipes[2].source).isEqualTo(RecipeSource.USER)
        assertThat(recipes[2].leafGrams).isEqualTo(7.0)
        // The default was a catalog recipe: the catalog's new default takes its place.
        assertThat(recipes.filter { it.isDefault }.map { it.name }).containsExactly("Kyusu neu")
    }

    @Test
    fun `the PO's own default stays the default`() = runTest {
        catalog(entry())
        collectionTea(catalogDefault = false, own = 2)

        reset.reset(TEA_ID)

        val recipes = recipesNow()
        assertThat(recipes.map { it.name })
            .containsExactly("Kyusu neu", "Kalt neu", "Eigenes 0", "Eigenes 1").inOrder()
        assertThat(recipes.filter { it.isDefault }.map { it.id }).containsExactly("own0")
    }

    @Test
    fun `the description is replaced and Rezept pruefen cleared`() = runTest {
        catalog(entry())
        collectionTea()
        assertThat(data.teas.getTea(TEA_ID)!!.recipeCheckReason).isEqualTo(RecipeCheckReason.FROM_TEXT)

        reset.reset(TEA_ID)

        val tea = data.teas.getTea(TEA_ID)!!
        assertThat(tea.description).isEqualTo("Eine neue erfundene Beschreibung.")
        assertThat(tea.recipeCheckReason).isNull()
    }

    @Test
    fun `beyond 10 recipes the catalog recipes over the limit are left out, and the plan says so`() = runTest {
        catalog(entry(recipes = listOf(NEW_KYUSU, NEW_COLD, NEW_WESTERN)))
        collectionTea(catalogDefault = true, own = 8, oldCatalog = 2)

        assertThat(reset.observePlan(TEA_ID).first()).isEqualTo(CatalogResetPlan(leftOut = 1))
        assertThat(reset.reset(TEA_ID)).isEqualTo(CatalogResetResult.Reset(leftOut = 1))

        val recipes = recipesNow()
        assertThat(recipes).hasSize(Recipe.MAX_PER_TEA)
        assertThat(recipes.take(2).map { it.name }).containsExactly("Kyusu neu", "Kalt neu").inOrder()
        assertThat(recipes.drop(2).map { it.id }).containsExactly(*Array(8) { "own$it" }).inOrder()
        assertThat(recipes.filter { it.isDefault }.map { it.name }).containsExactly("Kyusu neu")
    }

    @Test
    fun `within the limit the plan leaves nothing out`() = runTest {
        catalog(entry())
        collectionTea(own = 1)

        assertThat(reset.observePlan(TEA_ID).first()).isEqualTo(CatalogResetPlan(leftOut = 0))
    }

    @Test
    fun `stock, photos, notes, favourite, archive flag and sessions stay untouched`() = runTest {
        catalog(entry())
        collectionTea()
        val photo = Photo("p1", PhotoOwner.OfTea(TEA_ID), PhotoOrigin.OWN, widthPx = 10, heightPx = 10, byteSize = 1)
        data.teas.savePhoto(photo)
        data.teas.setCoverPhoto(TEA_ID, "p1")
        val tea = data.teas.getTea(TEA_ID)!!.copy(notes = "Meine Notiz", isFavorite = true, isArchived = true)
        data.teas.saveTea(tea)
        data.stock.addEvent(
            InventoryEvent(
                id = "e1",
                teaId = TEA_ID,
                type = InventoryEventType.INITIAL,
                deltaGrams = 100.0,
                occurredAtMillis = NOW - 1,
            ),
        )
        val before = data.teas.getTea(TEA_ID)!!

        reset.reset(TEA_ID)

        val after = data.teas.getTea(TEA_ID)!!
        assertThat(after.copy(description = before.description, recipeCheckReason = before.recipeCheckReason))
            .isEqualTo(before)
        assertThat(data.stock.observeEvents(TEA_ID).first().map { it.id }).containsExactly("e1")
        assertThat(data.teas.observeTea(TEA_ID).first()!!.coverPhoto?.id).isEqualTo("p1")
    }

    @Test
    fun `a running brew of the tea keeps its session and its recipe link`() = runTest {
        catalog(entry())
        collectionTea(catalogDefault = true, own = 1, oldCatalog = 2)
        val snapshot = snapshotOf(teaId = TEA_ID).copy(profileId = "old1", profileName = "Alt 1")
        val driver = TimerDriver(data) { id, steps, preAlert ->
            sessionStart(id, steps, preAlert, TestClocks(), snapshot)
        }
        driver.send(startEvent(infusion(60)))

        assertThat(reset.reset(TEA_ID)).isEqualTo(CatalogResetResult.Reset(leftOut = 0))

        // The session's snapshot is its own (rule 8), and its recipe still exists: the reset never deletes it.
        val session = data.sessions.getSession(SESSION_ID)!!
        assertThat(session.snapshot).isEqualTo(snapshot)
        assertThat(data.recipes.observeActiveRecipeId().first()).isEqualTo("old1")
        assertThat(data.recipes.getRecipe("old1")).isNotNull()
        assertThat(recipesNow().map { it.name }).containsExactly("Kyusu neu", "Kalt neu", "Eigenes 0").inOrder()
    }

    @Test
    fun `a brew running through the reset completes as usual`() = runTest {
        catalog(entry())
        collectionTea(catalogDefault = true, own = 1, oldCatalog = 2)
        val snapshot = snapshotOf(teaId = TEA_ID).copy(profileId = "old1", profileName = "Alt 1")
        val driver = TimerDriver(data) { id, steps, preAlert ->
            sessionStart(id, steps, preAlert, TestClocks(), snapshot)
        }
        driver.send(startEvent(infusion(60)))
        driver.send(TimerEvent.Start)

        assertThat(reset.reset(TEA_ID)).isEqualTo(CatalogResetResult.Reset(leftOut = 0))
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.send(TimerEvent.Poured)
        driver.send(TimerEvent.FinishSession)

        val session = data.sessions.getSession(SESSION_ID)!!
        assertThat(session.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(session.snapshot.profileId).isEqualTo("old1")
        val brews = data.stock.observeEvents(TEA_ID).first().filter { it.type == InventoryEventType.BREW }
        assertThat(brews.map { it.sessionId }).containsExactly(SESSION_ID)
        assertThat(data.activeBrew.read(TimerSlot.BREW)).isNull()
    }

    @Test
    fun `the active brew's recipe keeps its row when it is not the first catalog recipe`() = runTest {
        // One new catalog recipe for two old ones: one old row goes, and it must not be the active one.
        catalog(entry(recipes = listOf(NEW_KYUSU)))
        collectionTea(catalogDefault = true, own = 1, oldCatalog = 2)
        val snapshot = snapshotOf(teaId = TEA_ID).copy(profileId = "old1", profileName = "Alt 1")
        val driver = TimerDriver(data) { id, steps, preAlert ->
            sessionStart(id, steps, preAlert, TestClocks(), snapshot)
        }
        driver.send(startEvent(infusion(60)))

        assertThat(reset.reset(TEA_ID)).isEqualTo(CatalogResetResult.Reset(leftOut = 0))

        val recipes = recipesNow()
        assertThat(recipes.map { it.id }).containsExactly("old1", "own0").inOrder()
        assertThat(recipes[0].name).isEqualTo("Kyusu neu")
        assertThat(data.recipes.getRecipe("old0")).isNull()
        assertThat(data.recipes.observeActiveRecipeId().first()).isEqualTo("old1")
    }

    @Test
    fun `a catalog tea without a description leaves the tea's own`() = runTest {
        catalog(entry().copy(descriptionText = null))
        collectionTea()

        assertThat(reset.reset(TEA_ID)).isEqualTo(CatalogResetResult.Reset(leftOut = 0))

        assertThat(data.teas.getTea(TEA_ID)!!.description).isEqualTo("Die alte Beschreibung.")
    }

    @Test
    fun `not offered without a catalog entry`() = runTest {
        // No catalog key at all.
        data.teas.saveTea(TEA.copy(catalogKey = null))
        assertThat(reset.observePlan(TEA_ID).first()).isNull()
        assertThat(reset.reset(TEA_ID)).isEqualTo(CatalogResetResult.NotInCatalog)

        // A key the catalog doesn't hold (any more).
        collectionTea()
        assertThat(reset.observePlan(TEA_ID).first()).isNull()
        assertThat(reset.reset(TEA_ID)).isEqualTo(CatalogResetResult.NotInCatalog)
        assertThat(recipesNow().map { it.id }).containsExactly("old0", "old1", "own0").inOrder()
        assertThat(data.teas.getTea(TEA_ID)!!.description).isEqualTo("Die alte Beschreibung.")

        // Once the catalog holds it, the reset is offered.
        catalog(entry())
        assertThat(reset.observePlan(TEA_ID).first()).isNotNull()
    }

    private companion object {
        const val TEA_ID = "t1"
        const val KEY = "XY0100"
        const val NOW: Long = 1_793_000_000_000L

        val TEA = Tea(
            id = TEA_ID,
            name = "Morgennebel Sencha",
            category = TeaCategory.GREEN,
            form = TeaForm.LOOSE_LEAF,
            catalogKey = KEY,
            styleKey = "sencha",
            description = "Die alte Beschreibung.",
            recipeCheckReason = RecipeCheckReason.FROM_TEXT,
        )

        fun recipe(id: String, source: RecipeSource, position: Int, isDefault: Boolean) = Recipe(
            id = id,
            teaId = TEA_ID,
            name = if (source == RecipeSource.USER) "Eigenes ${id.removePrefix("own")}" else "Alt ${id.last()}",
            method = BrewMethod.KYUSU,
            leafGrams = if (source == RecipeSource.USER) 7.0 else 3.0,
            waterMl = 100,
            baseTempC = 60,
            steps = listOf(RecipeStep(StepKind.INFUSION, 60)),
            isDefault = isDefault,
            position = position,
            source = source,
        )

        val NEW_KYUSU = RecipeTemplate(
            name = "Kyusu neu",
            method = BrewMethod.KYUSU,
            leafGrams = 5.0,
            waterMl = 120,
            baseTempC = 65,
            steps = listOf(RecipeStep(StepKind.INFUSION, 75), RecipeStep(StepKind.INFUSION, 15)),
            source = RecipeSource.CATALOG_TEXT,
            isDefault = true,
        )
        val NEW_COLD = RecipeTemplate(
            name = "Kalt neu",
            method = BrewMethod.COLD_BREW,
            leafGrams = 10.0,
            waterMl = 1000,
            baseTempC = 5,
            steps = listOf(RecipeStep(StepKind.INFUSION, 4 * 3600)),
            source = RecipeSource.STYLE_DEFAULT,
        )
        val NEW_WESTERN = RecipeTemplate(
            name = "Western neu",
            method = BrewMethod.WESTERN,
            leafGrams = 2.0,
            waterMl = 250,
            baseTempC = 70,
            steps = listOf(RecipeStep(StepKind.INFUSION, 120)),
            source = RecipeSource.CATALOG_TABLE,
        )

        fun entry(recipes: List<RecipeTemplate> = listOf(NEW_KYUSU, NEW_COLD)) = CatalogTeaEntity(
            key = KEY,
            name = "Morgennebel Sencha",
            secondaryName = null,
            productUrl = "https://shop.example/tee/$KEY",
            shopCategory = "Tee › Grüner Tee › Japan › Sencha",
            teaType = "Sencha",
            styleKey = "sencha",
            breadcrumb = "/tee/gruener-tee/japan/sencha/",
            descriptionText = "Eine neue erfundene Beschreibung.",
            derivedRecipesJson = DerivedRecipesColumn.json(recipes),
            basePriceCentsPer100g = null,
            available = true,
            firstSeenAt = NOW - 1,
            lastSeenAt = NOW - 1,
        )
    }
}
