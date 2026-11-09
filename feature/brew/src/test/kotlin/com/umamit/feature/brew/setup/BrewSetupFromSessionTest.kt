package com.umamit.feature.brew.setup

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.SaveRecipeResult
import com.umamit.core.data.SessionStart
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.TimerSlot
import com.umamit.core.model.UserSettings
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * *So nochmal brühen* (BRW-06, D-100): the setup opened on a past session `pastSessionId` holds the session as
 * brewed, each step at its set time (never the measured one), never-started steps included, with its kind,
 * temperature and Vorwarnung; the session's recipe while it exists, else *Ohne Rezept*, whose *Im Rezept speichern*
 * saves a new recipe named after the old one. Fakes of core:testing; an invented tea (D-016).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrewSetupFromSessionTest {
    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository(UserSettings(preAlertSeconds = 10))
    private val sessions = FakeSessions(collection, settings)
    private val starter = FakeBrewStarter()

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        collection.teas.saveTea(Tea(TEA, "Sencha Morgentau", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        collection.vessels.saveVessel(Vessel(KYUSU, "Kyusu 250 ml", VesselType.KYUSU, 300, 250))
        collection.vessels.saveVessel(Vessel(OLD_KYUSU, "Alte Kyusu", VesselType.KYUSU, 200, isArchived = true))
        collection.recipes.saveRecipe(recipe(STANDARD, "Standard", isDefault = true))
        collection.recipes.saveRecipe(recipe(STRONG, "Kräftig"))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun recipe(id: String, name: String, isDefault: Boolean = false) = Recipe(
        id = id,
        teaId = TEA,
        name = name,
        method = BrewMethod.KYUSU,
        leafGrams = 5.0,
        waterMl = 250,
        baseTempC = 70,
        steps = listOf(RecipeStep(StepKind.INFUSION, 60)),
        vesselId = KYUSU,
        isDefault = isDefault,
        position = if (isDefault) 0 else 1,
    )

    private fun viewModel(pastSessionId: String = PAST, recipes: RecipeRepository = collection.recipes) =
        BrewSetupViewModel(
            SavedStateHandle(mapOf("pastSessionId" to pastSessionId)),
            collection.teas,
            recipes,
            collection.vessels,
            collection.stock,
            sessions.sessions,
            settings,
            starter,
            FakeCatalogRepository(),
        )

    private fun viewModelFor(recipeId: String) = BrewSetupViewModel(
        SavedStateHandle(mapOf("teaId" to TEA, "recipeId" to recipeId)),
        collection.teas,
        collection.recipes,
        collection.vessels,
        collection.stock,
        sessions.sessions,
        settings,
        starter,
        FakeCatalogRepository(),
    )

    private val BrewSetupViewModel.ready get() = state.value as BrewSetupUiState.Ready

    /**
     * A finished session of [profileId]: a rinse, an infusion whose set time the Schritte-Sheet changed to 75 s and
     * that ran 20 s into overtime, and a third step never started (kept from v0.4.0, Q-19).
     */
    private suspend fun brewed(
        profileId: String?,
        profileName: String? = "Kräftig",
        vesselId: String = OLD_KYUSU,
        method: BrewMethod = BrewMethod.KYUSU,
        preAlertSeconds: Int = 10,
    ) {
        val snapshot = SessionSnapshot(
            teaId = TEA,
            teaName = "Sencha Morgentau",
            teaSecondaryName = null,
            teaType = "Sencha",
            styleKey = "sencha",
            category = TeaCategory.GREEN,
            profileId = profileId,
            profileName = profileName,
            method = method,
            vesselId = vesselId,
            vesselName = "Alte Kyusu",
            leafGrams = 4.5,
            waterMl = 180,
            baseTempC = 65,
        )
        val planned = listOf(
            PlannedStep(StepKind.RINSE, 10, 80, preAlertSeconds = 0),
            PlannedStep(StepKind.INFUSION, 60, null),
            PlannedStep(StepKind.INFUSION, 30, 75, preAlertSeconds = 15),
        )
        sessions.sessions.startSession(SessionStart(PAST, snapshot, planned, preAlertSeconds, 0, "Europe/Berlin"))
        sessions.sessions.recordStep(PAST, 0, record(10, 10))
        // *Schritte bearbeiten* while infusion 1 waited: 60 s became 75 s (04 §4, the session's set time).
        sessions.sessions.replacePendingSteps(
            PAST,
            1,
            listOf(PlannedStep(StepKind.INFUSION, 75, null), PlannedStep(StepKind.INFUSION, 30, 75, 15)),
        )
        sessions.sessions.recordStep(PAST, 1, record(75, 95, overtime = 20))
        sessions.sessions.completeSession(PAST, autoFinished = false)
    }

    private fun record(planned: Int, actual: Int, overtime: Int = 0) =
        StepRecord(planned, actual, 0, overtime, 1_000, 2_000, skipped = false, overtimeAutoAck = false)

    @Test
    fun `the setup holds the session as brewed, every step at its set time with kind, temperature and Vorwarnung`() =
        runTest {
            brewed(profileId = STRONG)

            val ready = viewModel().ready

            assertThat(ready.teaId).isEqualTo(TEA)
            assertThat(ready.steps).containsExactly(
                PlannedStep(StepKind.RINSE, 10, 80, preAlertSeconds = 0),
                PlannedStep(StepKind.INFUSION, 75, 65),
                PlannedStep(StepKind.INFUSION, 30, 75, preAlertSeconds = 15),
            ).inOrder()
            assertThat(ready.waterMl).isEqualTo(180)
            assertThat(ready.leafGrams).isEqualTo(4.5)
            assertThat(ready.baseTempC).isEqualTo(65)
            assertThat(ready.method).isEqualTo(BrewMethod.KYUSU)
        }

    @Test
    fun `the session's recipe is selected while it exists, and Im Rezept speichern writes into it`() = runTest {
        brewed(profileId = STRONG)
        val vm = viewModel()

        assertThat(vm.ready.selectedRecipeId).isEqualTo(STRONG)
        assertThat(vm.ready.withoutRecipe).isFalse()
        assertThat(vm.ready.canSaveToRecipe).isTrue()

        vm.saveToRecipe()

        val saved = collection.recipes.getRecipe(STRONG)!!
        assertThat(saved.waterMl).isEqualTo(180)
        assertThat(saved.steps.map { it.durationSeconds }).containsExactly(10, 75, 30).inOrder()
        assertThat(collection.recipes.observeRecipes(TEA).first()).hasSize(2)
    }

    @Test
    fun `Im Rezept speichern into the session's recipe stores its base and method, so every temperature stays (F3)`() =
        runTest {
            // The recipe brews Kyusu at 70 degrees; the session brewed Gongfu at 65 degrees.
            brewed(profileId = STRONG, method = BrewMethod.GONGFU)
            val vm = viewModel()

            vm.saveToRecipe()

            val saved = collection.recipes.getRecipe(STRONG)!!
            assertThat(saved.baseTempC).isEqualTo(65)
            assertThat(saved.method).isEqualTo(BrewMethod.GONGFU)
            assertThat(saved.steps).containsExactly(
                RecipeStep(StepKind.RINSE, 10, 80, preAlertSeconds = 0),
                RecipeStep(StepKind.INFUSION, 75),
                RecipeStep(StepKind.INFUSION, 30, 75, preAlertSeconds = 15),
            ).inOrder()
            // The save settles: the setup now equals the recipe.
            assertThat(vm.ready.canSaveToRecipe).isFalse()
            assertThat(viewModelFor(STRONG).ready.steps.map { it.tempC }).containsExactly(80, 65, 75).inOrder()
        }

    @Test
    fun `a double tap on Im Rezept speichern with Ohne Rezept creates one recipe, however slow the save (F4)`() =
        runTest {
            brewed(profileId = "deleted-recipe", profileName = "Mein Morgen")
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            // A save that suspends, as on Room: the second tap comes while the first one is still writing.
            val slowRecipes = object : RecipeRepository by collection.recipes {
                override suspend fun saveRecipe(recipe: Recipe): SaveRecipeResult {
                    yield()
                    return collection.recipes.saveRecipe(recipe)
                }
            }
            val vm = viewModel(recipes = slowRecipes)
            advanceUntilIdle()

            vm.saveToRecipe()
            vm.saveToRecipe()
            advanceUntilIdle()

            assertThat(collection.recipes.observeRecipes(TEA).first().count { it.name == "Mein Morgen" }).isEqualTo(1)
            assertThat(vm.ready.canSaveToRecipe).isFalse()
        }

    @Test
    fun `Ohne Rezept keeps the session's own Vorwarnung base, and the new recipe keeps it (F11)`() = runTest {
        brewed(profileId = "deleted-recipe", profileName = "Mein Morgen", preAlertSeconds = 20)
        val vm = viewModel()

        assertThat(vm.ready.preAlertBaseSeconds).isEqualTo(20)
        vm.startBrew()
        assertThat(starter.starts.single().recipePreAlertSeconds).isEqualTo(20)

        vm.saveToRecipe()

        val created = collection.recipes.observeRecipes(TEA).first().single { it.name == "Mein Morgen" }
        assertThat(created.preAlertSeconds).isEqualTo(20)
        assertThat(vm.ready.preAlertBaseSeconds).isEqualTo(20)
    }

    @Test
    fun `an archived vessel of the session reads archiviert`() = runTest {
        brewed(profileId = STRONG)

        val ready = viewModel().ready

        assertThat(ready.selectedVesselId).isEqualTo(OLD_KYUSU)
        assertThat(ready.archivedVessel?.name).isEqualTo("Alte Kyusu")
    }

    @Test
    fun `without the session's recipe the setup shows Ohne Rezept, and Im Rezept speichern saves a new recipe`() =
        runTest {
            brewed(profileId = "deleted-recipe", profileName = "Mein Morgen")
            val vm = viewModel()

            assertThat(vm.ready.selectedRecipeId).isNull()
            assertThat(vm.ready.withoutRecipe).isTrue()
            assertThat(vm.ready.canSaveToRecipe).isTrue()
            assertThat(vm.ready.recipes.map { it.name }).containsExactly("Standard", "Kräftig")

            vm.saveToRecipe()

            val recipes = collection.recipes.observeRecipes(TEA).first()
            val created = recipes.single { it.name == "Mein Morgen" }
            assertThat(recipes).hasSize(3)
            assertThat(created.isDefault).isFalse()
            assertThat(created.waterMl).isEqualTo(180)
            assertThat(created.leafGrams).isEqualTo(4.5)
            assertThat(created.baseTempC).isEqualTo(65)
            assertThat(created.vesselId).isEqualTo(OLD_KYUSU)
            assertThat(created.steps).containsExactly(
                RecipeStep(StepKind.RINSE, 10, 80, preAlertSeconds = 0),
                RecipeStep(StepKind.INFUSION, 75),
                RecipeStep(StepKind.INFUSION, 30, 75, preAlertSeconds = 15),
            ).inOrder()
            // The new recipe is the selected one, so the setup reads „Im Rezept gespeichert".
            assertThat(vm.ready.selectedRecipeId).isEqualTo(created.id)
            assertThat(vm.ready.canSaveToRecipe).isFalse()
            assertThat(collection.recipes.getRecipe(STANDARD)!!.isDefault).isTrue()
        }

    @Test
    fun `Ohne Rezept comes back after another recipe was chosen`() = runTest {
        brewed(profileId = null, profileName = null)
        val vm = viewModel()
        val asBrewed = vm.ready.steps

        vm.selectRecipe(STANDARD)
        assertThat(vm.ready.steps).isNotEqualTo(asBrewed)
        vm.selectWithoutRecipe()

        assertThat(vm.ready.selectedRecipeId).isNull()
        assertThat(vm.ready.steps).isEqualTo(asBrewed)
    }

    @Test
    fun `a recipe-less session's new recipe is named after the tea`() = runTest {
        brewed(profileId = null, profileName = null)
        val vm = viewModel()

        vm.saveToRecipe()

        assertThat(collection.recipes.observeRecipes(TEA).first().map { it.name }).contains("Sencha Morgentau")
    }

    @Test
    fun `at ten recipes Ohne Rezept offers no Im Rezept speichern`() = runTest {
        (2 until Recipe.MAX_PER_TEA).forEach { collection.recipes.saveRecipe(recipe("r$it", "Rezept $it")) }
        brewed(profileId = null)

        assertThat(viewModel().ready.canSaveToRecipe).isFalse()
    }

    @Test
    fun `Bruehen starten hands the controller the set times, never the measured time`() = runTest {
        brewed(profileId = "deleted-recipe")
        val vm = viewModel()

        vm.startBrew()

        val start = starter.starts.single()
        assertThat(start.steps.map { it.plannedSeconds }).containsExactly(10, 75, 30).inOrder()
        assertThat(start.snapshot.profileId).isNull()
        assertThat(start.snapshot.leafGrams).isEqualTo(4.5)
        assertThat(vm.events.first()).isEqualTo(BrewSetupEvent.OpenTimer(TimerSlot.BREW))
    }

    @Test
    fun `a session whose tea is gone shows the not-found state`() = runTest {
        brewed(profileId = STRONG)
        collection.teas.deleteTea(TEA)

        assertThat(viewModel().state.value).isEqualTo(BrewSetupUiState.NotFound)
    }

    private companion object {
        const val TEA = "morgentau"
        const val KYUSU = "kyusu"
        const val OLD_KYUSU = "alte-kyusu"
        const val STANDARD = "standard"
        const val STRONG = "kraeftig"
        const val PAST = "past"
    }
}
