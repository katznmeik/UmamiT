package com.umamit.feature.collection.recipe

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UserSettings
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Rezept bearbeiten (SCR-12; PRF-01, PRF-03, PRF-04, WARE-02–WARE-04, TEA-17, D-100) on fake repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeEditorViewModelTest {
    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository(UserSettings(preAlertSeconds = 15))

    private val tea = Tea(
        id = TEA,
        name = "Morgennebel",
        category = TeaCategory.GREEN,
        form = TeaForm.LOOSE_LEAF,
        styleKey = "sencha",
        recipeCheckReason = RecipeCheckReason.FROM_TEXT,
    )
    private val kyusu = Recipe(
        id = "kyusu",
        teaId = TEA,
        name = "Kyusu",
        method = BrewMethod.KYUSU,
        leafGrams = 5.0,
        waterMl = 250,
        baseTempC = 70,
        steps = listOf(
            RecipeStep(StepKind.RINSE, 10),
            RecipeStep(StepKind.INFUSION, 60, preAlertSeconds = 10),
            RecipeStep(StepKind.INFUSION, 30, tempC = 80, note = "kürzer"),
        ),
        vesselId = "kyusu250",
        isDefault = true,
        position = 0,
        source = RecipeSource.CATALOG_TABLE,
    )
    private val cold = Recipe(
        id = "kalt",
        teaId = TEA,
        name = "Kaltaufguss",
        method = BrewMethod.COLD_BREW,
        leafGrams = 10.0,
        waterMl = 1000,
        baseTempC = 10,
        steps = listOf(RecipeStep(StepKind.INFUSION, 21_600)),
        position = 1,
    )
    private val kyusu250 = Vessel("kyusu250", "Kyusu 250 ml", VesselType.KYUSU, capacityMl = 300, usableMl = 250)
    private val small = Vessel("kyusu180", "Kyusu 180 ml", VesselType.KYUSU, capacityMl = 220, usableMl = 180)
    private val mug = Vessel("becher", "Becher", VesselType.MUG_INFUSER, capacityMl = 400, isFavorite = true)
    private val old = Vessel("alt", "Alte Kanne", VesselType.WESTERN_TEAPOT, capacityMl = 800, isArchived = true)

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        collection.teas.saveTea(tea)
        listOf(kyusu250, small, mug, old).forEach { collection.vessels.saveVessel(it) }
        collection.recipes.saveRecipe(kyusu)
        collection.recipes.saveRecipe(cold)
        // The fake clears „Rezept prüfen" on every save, so the tea is flagged again for TEA-17.
        collection.teas.saveTea(tea)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(recipeId: String? = null, saved: SavedStateHandle = SavedStateHandle()) =
        RecipeEditorViewModel(
            saved.apply {
                set(RecipeEditorViewModel.TEA_ID, TEA)
                if (recipeId != null) set(RecipeEditorViewModel.RECIPE_ID, recipeId)
            },
            collection.recipes,
            collection.teas,
            collection.vessels,
            settings,
        )

    private val RecipeEditorViewModel.ready get() = state.value as RecipeEditorUiState.Ready

    @Test
    fun `a stored recipe shows its values, unchanged`() {
        val vm = viewModel("kyusu")
        val form = vm.ready.form
        assertThat(vm.ready.isNew).isFalse()
        assertThat(form.name).isEqualTo("Kyusu")
        assertThat(form.leaf).isEqualTo("5,0")
        assertThat(form.water).isEqualTo("250")
        assertThat(form.ratioPer100Ml).isEqualTo(2.0)
        assertThat(form.steps.map { it.kind }).containsExactly(StepKind.RINSE, StepKind.INFUSION, StepKind.INFUSION)
        assertThat(form.shownTempC(1)).isEqualTo(70)
        assertThat(form.shownTempC(2)).isEqualTo(80)
        assertThat(vm.ready.isStoredDefault).isTrue()
        assertThat(vm.ready.canDelete).isTrue()
        assertThat(vm.ready.hasChanges).isFalse()
    }

    @Test
    fun `Rezept hinzufuegen copies the default with an empty name and stores nothing before Speichern`() = runTest {
        val vm = viewModel()
        assertThat(vm.ready.isNew).isTrue()
        assertThat(vm.ready.form.name).isEmpty()
        assertThat(vm.ready.form.leaf).isEqualTo("5,0")
        assertThat(vm.ready.form.steps).hasSize(3)
        assertThat(vm.ready.form.isDefault).isFalse()
        assertThat(vm.ready.canDelete).isFalse()
        assertThat(collection.recipes.observeRecipes(TEA).first()).hasSize(2)

        vm.save()
        assertThat(vm.ready.errors).containsExactly(RecipeFieldError.NameEmpty)
        assertThat(collection.recipes.observeRecipes(TEA).first()).hasSize(2)

        vm.update { it.copy(name = "  Zweiter Kyusu ") }
        assertThat(vm.ready.errors).isEmpty()
        vm.save()

        val stored = collection.recipes.observeRecipes(TEA).first()
        assertThat(stored.map { it.name }).containsExactly("Kyusu", "Kaltaufguss", "Zweiter Kyusu").inOrder()
        val added = stored.last()
        assertThat(added.id).isNotEqualTo("kyusu")
        assertThat(added.position).isEqualTo(2)
        assertThat(added.isDefault).isFalse()
        assertThat(added.source).isEqualTo(RecipeSource.USER)
        assertThat(added.steps).isEqualTo(kyusu.steps)
        assertThat(vm.events.first()).isEqualTo(RecipeEditorEvent.Saved(added.id))
    }

    @Test
    fun `the errors show only after Speichern, at the fields`() {
        val vm = viewModel("kyusu")
        vm.update { it.copy(name = "", leaf = "0,05", water = "247") }
        assertThat(vm.ready.errors).isEmpty()
        assertThat(vm.ready.hasChanges).isTrue()
        vm.save()
        assertThat(vm.ready.errors)
            .containsExactly(RecipeFieldError.NameEmpty, RecipeFieldError.Leaf, RecipeFieldError.Water)
        vm.update { it.copy(name = "x".repeat(41)) }
        assertThat(vm.ready.errors).contains(RecipeFieldError.NameTooLong)
    }

    @Test
    fun `leaf, water, temperatures and durations stay within the setup's limits (PRF-03, PRF-04)`() {
        assertThat(RecipeForm.parseLeaf("0,1")).isEqualTo(0.1)
        assertThat(RecipeForm.parseLeaf("100")).isEqualTo(100.0)
        assertThat(RecipeForm.parseLeaf("4.5")).isEqualTo(4.5)
        assertThat(RecipeForm.parseLeaf("0")).isNull()
        assertThat(RecipeForm.parseLeaf("100,1")).isNull()
        assertThat(RecipeForm.parseLeaf("4,55")).isNull()
        assertThat(RecipeForm.parseWater("10")).isEqualTo(10)
        assertThat(RecipeForm.parseWater("3000")).isEqualTo(3000)
        assertThat(RecipeForm.parseWater("5")).isNull()
        assertThat(RecipeForm.parseWater("3005")).isNull()
        assertThat(RecipeForm.parseWater("252")).isNull()

        val form = RecipeForm.of(kyusu)
        assertThat(form.copy(baseTempC = 100).withBaseStep(up = true).baseTempC).isEqualTo(100)
        assertThat(form.copy(baseTempC = 5).withBaseStep(up = false).baseTempC).isEqualTo(5)
        assertThat(form.withBaseStep(up = true).baseTempC).isEqualTo(75)
        // A step at the base temperature follows the base; once moved it has its own.
        assertThat(form.withBaseStep(up = true).shownTempC(1)).isEqualTo(75)
        assertThat(form.withStepTemp(1, up = false).steps[1].tempC).isEqualTo(65)
        assertThat(form.withStepDuration(1, up = true).steps[1].durationSeconds).isEqualTo(65)
        assertThat(form.withStepDuration(0, up = false).withStepDuration(0, up = false).steps[0].durationSeconds)
            .isEqualTo(5)
        assertThat(form.withStepDurationSet(1, 100_000).steps[1].durationSeconds).isEqualTo(86_400)
        assertThat(form.withStepDurationSet(1, 659).steps[1].durationSeconds).isEqualTo(600)
    }

    @Test
    fun `a vessel sets the water to its usable volume and scales the leaf, as the setup (WARE-03, WARE-04)`() {
        val vm = viewModel("kyusu")
        vm.selectVessel("kyusu180")
        assertThat(vm.ready.form.vesselId).isEqualTo("kyusu180")
        assertThat(vm.ready.form.water).isEqualTo("180")
        assertThat(vm.ready.form.leaf).isEqualTo("3,6")
        assertThat(vm.ready.form.ratioPer100Ml).isEqualTo(2.0)

        // Without a usable volume, the capacity.
        vm.selectVessel("becher")
        assertThat(vm.ready.form.water).isEqualTo("400")
        assertThat(vm.ready.form.leaf).isEqualTo("8,0")

        // *Kein Gefäß* keeps leaf and water.
        vm.selectVessel(null)
        assertThat(vm.ready.form.vesselId).isNull()
        assertThat(vm.ready.form.water).isEqualTo("400")

        // A leaf that isn't valid yet stays; the water is set.
        vm.update { it.copy(leaf = "") }
        vm.selectVessel("kyusu250")
        assertThat(vm.ready.form.water).isEqualTo("250")
        assertThat(vm.ready.form.leaf).isEmpty()
    }

    @Test
    fun `the vessel list shows favourites first and hides archived vessels, but a recipe keeps its archived one`() =
        runTest {
            collection.recipes.saveRecipe(cold.copy(vesselId = "alt"))
            val vm = viewModel("kalt")
            assertThat(vm.ready.vessels.map { it.id }).containsExactly("becher", "kyusu180", "kyusu250").inOrder()
            assertThat(vm.ready.selectedVessel).isEqualTo(old)
            // An archived vessel is not offered.
            vm.selectVessel("alt")
            assertThat(vm.ready.form.vesselId).isNull()
        }

    @Test
    fun `steps are reordered, and the new order is stored`() = runTest {
        val vm = viewModel("kyusu")
        vm.update { it.withStepMoved(from = 2, to = 0) }
        assertThat(vm.ready.form.steps.map { it.note }).containsExactly("kürzer", "", "").inOrder()
        assertThat(vm.ready.hasChanges).isTrue()
        vm.update { it.withStepMoved(from = 0, to = 2) }
        assertThat(vm.ready.hasChanges).isFalse()
        vm.update { it.withStepMoved(from = 0, to = 1) }
        vm.save()
        assertThat(collection.recipes.getRecipe("kyusu")!!.steps.map { it.kind to it.durationSeconds })
            .containsExactly(StepKind.INFUSION to 60, StepKind.RINSE to 10, StepKind.INFUSION to 30).inOrder()
    }

    @Test
    fun `each step has a kind, and at least one step brews (PRF-04)`() = runTest {
        val vm = viewModel("kalt")
        val form = vm.ready.form
        assertThat(form.canBeRinse(0)).isFalse()
        assertThat(form.canDelete(0)).isFalse()
        assertThat(form.withStepKind(0, StepKind.RINSE)).isEqualTo(form)
        assertThat(form.withoutStep(0)).isEqualTo(form)

        vm.update { it.withStepKind(0, StepKind.WHISK).withAddedStep() }
        vm.update { it.withStepKind(1, StepKind.RINSE) }
        assertThat(vm.ready.form.canDelete(0)).isFalse()
        assertThat(vm.ready.form.canDelete(1)).isTrue()
        vm.update { it.withStepMoved(1, 0) }
        vm.save()
        assertThat(collection.recipes.getRecipe("kalt")!!.steps.map { it.kind })
            .containsExactly(StepKind.RINSE, StepKind.WHISK).inOrder()

        // A form of rinses only (e.g. restored) is refused.
        vm.update { it.copy(steps = it.steps.map { s -> s.copy(kind = StepKind.RINSE) }) }
        vm.save()
        assertThat(vm.ready.errors).containsExactly(RecipeFieldError.OnlyRinse)
    }

    @Test
    fun `steps are added and deleted, up to 30`() {
        val vm = viewModel("kyusu")
        vm.update { it.withAddedStep() }
        val added = vm.ready.form.steps.last()
        // The timer's *Weiterer Aufguss* (TMR-10): the last brewing step's kind and temperature, 1.5 × its time.
        assertThat(added.kind).isEqualTo(StepKind.INFUSION)
        assertThat(added.durationSeconds).isEqualTo(45)
        assertThat(added.tempC).isEqualTo(80)
        assertThat(added.preAlertSeconds).isNull()
        assertThat(vm.ready.form.steps.map { it.key }.toSet()).hasSize(4)

        vm.update { it.withoutStep(0) }
        assertThat(vm.ready.form.steps.map { it.kind }).doesNotContain(StepKind.RINSE)
        repeat(40) { vm.update { it.withAddedStep() } }
        assertThat(vm.ready.form.steps).hasSize(RecipeForm.MAX_STEPS)
        assertThat(vm.ready.form.canAddStep).isFalse()
    }

    @Test
    fun `each step keeps its own Vorwarnung, and the recipe's is the base of Automatisch (PRF-03, TMR-23)`() = runTest {
        val vm = viewModel("kyusu")
        assertThat(vm.ready.preAlertBaseSeconds).isEqualTo(15)
        vm.update { it.copy(preAlertSeconds = 20) }
        assertThat(vm.ready.preAlertBaseSeconds).isEqualTo(20)
        vm.update { it.withStepPreAlert(1, 0).withStepPreAlert(2, 25) }
        vm.save()
        val stored = collection.recipes.getRecipe("kyusu")!!
        assertThat(stored.preAlertSeconds).isEqualTo(20)
        assertThat(stored.steps.map { it.preAlertSeconds }).containsExactly(null, 0, 25).inOrder()
    }

    @Test
    fun `a step at the base temperature stores none of its own, and its note is trimmed`() = runTest {
        val vm = viewModel("kyusu")
        vm.update { it.withStepTemp(2, up = false).withStepTemp(2, up = false).withStepNote(1, "  sanft ") }
        assertThat(vm.ready.form.steps[2].tempC).isEqualTo(70)
        vm.save()
        val steps = collection.recipes.getRecipe("kyusu")!!.steps
        assertThat(steps[2].tempC).isNull()
        assertThat(steps[1].note).isEqualTo("sanft")
        assertThat(steps[2].note).isEqualTo("kürzer")
    }

    @Test
    fun `Als Standard makes the recipe the tea's only default`() = runTest {
        val vm = viewModel("kalt")
        assertThat(vm.ready.isStoredDefault).isFalse()
        vm.update { it.copy(isDefault = true) }
        vm.save()
        val stored = collection.recipes.observeRecipes(TEA).first()
        assertThat(stored.filter { it.isDefault }.map { it.id }).containsExactly("kalt")
    }

    @Test
    fun `deleting the default makes the next recipe the default, and the last recipe stays (PRF-01)`() = runTest {
        val vm = viewModel("kyusu")
        vm.delete()
        assertThat(vm.events.first()).isEqualTo(RecipeEditorEvent.Deleted)
        val left = collection.recipes.observeRecipes(TEA).first()
        assertThat(left.map { it.id to it.isDefault }).containsExactly("kalt" to true)

        val last = viewModel("kalt")
        assertThat(last.ready.canDelete).isFalse()
        last.delete()
        assertThat(last.ready.message).isEqualTo(RecipeEditorMessage.LastRecipe)
        last.messageShown()
        assertThat(last.ready.message).isNull()
        assertThat(collection.recipes.observeRecipes(TEA).first()).hasSize(1)
    }

    @Test
    fun `Speichern clears the recipe check (TEA-17)`() = runTest {
        assertThat(collection.teas.getTea(TEA)!!.recipeCheckReason).isEqualTo(RecipeCheckReason.FROM_TEXT)
        val vm = viewModel("kalt")
        vm.update { it.copy(notes = "Über Nacht im Kühlschrank.") }
        vm.save()
        assertThat(collection.teas.getTea(TEA)!!.recipeCheckReason).isNull()
        assertThat(collection.recipes.getRecipe("kalt")!!.notes).isEqualTo("Über Nacht im Kühlschrank.")
    }

    @Test
    fun `an eleventh recipe is refused with a notice`() = runTest {
        (2 until Recipe.MAX_PER_TEA).forEach { i ->
            collection.recipes.saveRecipe(cold.copy(id = "r$i", name = "Rezept $i", position = i))
        }
        val vm = viewModel()
        vm.update { it.copy(name = "Elftes") }
        vm.save()
        assertThat(vm.ready.message).isEqualTo(RecipeEditorMessage.LimitReached)
        assertThat(collection.recipes.observeRecipes(TEA).first()).hasSize(Recipe.MAX_PER_TEA)
    }

    @Test
    fun `the form survives a process death`() {
        val saved = SavedStateHandle()
        viewModel("kyusu", saved).update { it.copy(name = "Kyusu, kürzer").withStepMoved(0, 1) }
        val restored = viewModel("kyusu", saved)
        assertThat(restored.ready.form.name).isEqualTo("Kyusu, kürzer")
        assertThat(restored.ready.form.steps.first().kind).isEqualTo(StepKind.INFUSION)
        assertThat(restored.ready.hasChanges).isTrue()
    }

    @Test
    fun `a recipe that is gone, or of another tea, reads as not found`() {
        assertThat(viewModel("nope").state.value).isEqualTo(RecipeEditorUiState.NotFound)
        val other = RecipeEditorViewModel(
            SavedStateHandle(
                mapOf(RecipeEditorViewModel.TEA_ID to "anderer", RecipeEditorViewModel.RECIPE_ID to "kalt"),
            ),
            collection.recipes,
            collection.teas,
            collection.vessels,
            settings,
        )
        assertThat(other.state.value).isEqualTo(RecipeEditorUiState.NotFound)
        val noTea = RecipeEditorViewModel(
            SavedStateHandle(mapOf(RecipeEditorViewModel.TEA_ID to "anderer")),
            collection.recipes,
            collection.teas,
            collection.vessels,
            settings,
        )
        assertThat(noTea.state.value).isEqualTo(RecipeEditorUiState.NotFound)
    }

    private companion object {
        const val TEA = "morgennebel"
    }
}
