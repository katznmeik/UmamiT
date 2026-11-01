package com.umamit.feature.teaware

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.ClockReading
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
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselMaterial
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeVesselArchive
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerState
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
import java.time.ZoneOffset

/** The vessel editor (SCR-14; WARE-01, WARE-04, D-100) on fake repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class VesselEditorViewModelTest {
    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val archive = FakeVesselArchive(sessions)

    private val kyusu = Vessel(
        id = "k",
        name = "Kyusu 250 ml",
        type = VesselType.KYUSU,
        capacityMl = 300,
        usableMl = 250,
        material = VesselMaterial.UNGLAZED_CLAY,
        notes = "Tokoname",
        coverPhotoId = "photo-1",
    )

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        collection.vessels.saveVessel(kyusu)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(vesselId: String? = null, saved: SavedStateHandle = SavedStateHandle()) =
        VesselEditorViewModel(
            saved.apply { if (vesselId != null) set(VesselEditorViewModel.VESSEL_ID, vesselId) },
            collection.vessels,
            archive,
        )

    private val VesselEditorViewModel.ready get() = state.value as VesselEditorUiState.Ready

    @Test
    fun `a new vessel is stored with every field and its id is returned`() = runTest {
        val vm = viewModel()
        assertThat(vm.ready.isNew).isTrue()
        assertThat(vm.ready.hasChanges).isFalse()

        vm.update {
            it.copy(
                name = " Shiboridashi ",
                type = VesselType.SHIBORIDASHI,
                capacity = "150",
                usable = "120",
                material = VesselMaterial.PORCELAIN,
                notes = "Für Gyokuro",
                isFavorite = true,
            )
        }
        vm.save()

        val event = vm.events.first() as VesselEditorEvent.Saved
        assertThat(collection.vessels.getVessel(event.vesselId)).isEqualTo(
            Vessel(
                id = event.vesselId,
                name = "Shiboridashi",
                type = VesselType.SHIBORIDASHI,
                capacityMl = 150,
                usableMl = 120,
                material = VesselMaterial.PORCELAIN,
                notes = "Für Gyokuro",
                isFavorite = true,
            ),
        )
    }

    @Test
    fun `an edit keeps the vessel's id, photo and recipes, and a volume edit changes no recipe`() = runTest {
        collection.teas.saveTea(Tea("t", "Hisui", TeaCategory.GREEN, TeaForm.LOOSE_LEAF))
        val recipe = Recipe(
            id = "r",
            teaId = "t",
            name = "Kyusu",
            method = BrewMethod.KYUSU,
            vesselId = "k",
            leafGrams = 5.0,
            waterMl = 250,
            baseTempC = 60,
            steps = listOf(RecipeStep(StepKind.INFUSION, 60)),
            isDefault = true,
        )
        collection.recipes.saveRecipe(recipe)
        val vm = viewModel("k")
        assertThat(vm.ready.form.capacity).isEqualTo("300")
        assertThat(vm.ready.form.usable).isEqualTo("250")

        vm.update { it.copy(capacity = "350", usable = "", notes = "") }
        vm.save()

        assertThat(vm.events.first()).isEqualTo(VesselEditorEvent.Saved("k"))
        assertThat(collection.vessels.getVessel("k"))
            .isEqualTo(kyusu.copy(capacityMl = 350, usableMl = null, notes = null))
        assertThat(collection.recipes.getRecipe("r")!!.waterMl).isEqualTo(250)
        assertThat(collection.recipes.getRecipe("r")!!.vesselId).isEqualTo("k")
    }

    @Test
    fun `Speichern refuses an empty name, a volume outside 10 to 3000 ml and a usable volume above it`() = runTest {
        val vm = viewModel()

        vm.update { it.copy(name = "  ", capacity = "5", usable = "") }
        vm.save()
        assertThat(vm.ready.errors).containsExactly(VesselFieldError.NameEmpty, VesselFieldError.CapacityRange)

        vm.update { it.copy(name = "Kanne", capacity = "3001") }
        assertThat(vm.ready.errors).containsExactly(VesselFieldError.CapacityRange)

        vm.update { it.copy(capacity = "200", usable = "250") }
        vm.save()
        assertThat(vm.ready.errors).containsExactly(VesselFieldError.UsableRange)

        vm.update { it.copy(name = "K".repeat(VesselForm.MAX_NAME + 1), usable = "") }
        vm.save()
        assertThat(vm.ready.errors).containsExactly(VesselFieldError.NameTooLong)

        assertThat(collection.vessels.observeVessels().first().map { it.id }).containsExactly("k")
    }

    @Test
    fun `errors show only after the first Speichern`() = runTest {
        val vm = viewModel()

        vm.update { it.copy(name = "", capacity = "") }

        assertThat(vm.ready.errors).isEmpty()
    }

    @Test
    fun `changes are tracked against the stored vessel`() = runTest {
        val vm = viewModel("k")
        assertThat(vm.ready.hasChanges).isFalse()

        vm.update { it.copy(name = "Kyusu") }
        assertThat(vm.ready.hasChanges).isTrue()

        vm.update { it.copy(name = "Kyusu 250 ml") }
        assertThat(vm.ready.hasChanges).isFalse()
    }

    @Test
    fun `Archivieren archives the vessel and reports it, recipes keep it`() = runTest {
        val vm = viewModel("k")
        assertThat(vm.ready.archiveBlocked).isFalse()

        vm.archive()

        assertThat(vm.events.first()).isEqualTo(VesselEditorEvent.Archived("k", "Kyusu 250 ml"))
        assertThat(collection.vessels.getVessel("k")!!.isArchived).isTrue()
    }

    @Test
    fun `the vessel of the active brew session can't be archived`() = runTest {
        activeSessionWith(vesselId = "k")
        val vm = viewModel("k")

        assertThat(vm.ready.archiveBlocked).isTrue()
        vm.archive()

        assertThat(collection.vessels.getVessel("k")!!.isArchived).isFalse()
        assertThat(archive.archived).isEmpty()
    }

    @Test
    fun `an archived vessel offers Wiederherstellen`() = runTest {
        collection.vessels.saveVessel(kyusu.copy(isArchived = true))
        val vm = viewModel("k")
        assertThat(vm.ready.isArchived).isTrue()

        vm.restore()

        assertThat(collection.vessels.getVessel("k")!!.isArchived).isFalse()
        assertThat(vm.ready.isArchived).isFalse()
    }

    @Test
    fun `a vessel that is gone is not found`() = runTest {
        assertThat(viewModel("gone").state.value).isEqualTo(VesselEditorUiState.NotFound)
    }

    @Test
    fun `the form survives a process death`() = runTest {
        val saved = SavedStateHandle()
        viewModel("k", saved).update { it.copy(name = "Meine Kyusu") }

        val again = viewModel(saved = saved)

        assertThat(again.ready.form.name).isEqualTo("Meine Kyusu")
        assertThat(again.ready.hasChanges).isTrue()
    }

    /** A brew session with [vesselId] waits in READY: the active session (E10). */
    private suspend fun activeSessionWith(vesselId: String) {
        val snapshot = SessionSnapshot(
            null, "Hisui", null, null, null, null, null, null, BrewMethod.KYUSU, vesselId, "Kyusu", 5.0, 250, 60,
        )
        val steps = listOf(PlannedStep(StepKind.INFUSION, 60, 60))
        val start =
            TimerEvent.StartSession("active", steps, PreAlertSettings.inherited(null, 10), longStepsOnly = false)
        val ready = BrewTimerEngine.reduce(TimerState.Idle, start, ClockReading(1_000, 1_000, ZoneOffset.UTC, 1)).state
        sessions.sessions.startSession(SessionStart("active", snapshot, steps, 10, 1_000, "UTC"))
        sessions.activeBrew.persist(TimerSlot.BREW, BrewTimerEngine.snapshot(ready))
    }
}
