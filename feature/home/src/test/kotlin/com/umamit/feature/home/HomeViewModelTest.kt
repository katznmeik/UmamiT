package com.umamit.feature.home

import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SeedData
import com.umamit.core.data.SessionStart
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Start's view model (SCR-02; BRW-11, TMR-21, GEN-06, GEN-09) with fake repositories and a fake controller. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val starter = FakeBrewStarter()
    private val clock = object : MonotonicClock {
        override fun elapsedMs() = 5_000L

        override fun bootCount() = 1
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun seed() {
        SeedData.teas.forEach { collection.teas.saveTea(it) }
        SeedData.vessels.forEach { collection.vessels.saveVessel(it) }
        SeedData.recipes.forEach { collection.recipes.saveRecipe(it) }
    }

    private fun snapshot(teaId: String, name: String, leaf: Double = 5.0, water: Int = 250) = SessionSnapshot(
        teaId = teaId,
        teaName = name,
        teaSecondaryName = null,
        teaType = "Sencha",
        styleKey = "sencha",
        category = null,
        profileId = null,
        profileName = "Kyusu",
        method = BrewMethod.KYUSU,
        vesselId = SeedData.KYUSU_VESSEL_ID,
        vesselName = "Kyusu",
        leafGrams = leaf,
        waterMl = water,
        baseTempC = 65,
    )

    /** A completed session of [teaId] started at [at] with [seconds] steps at 65 °C. */
    private suspend fun brewed(id: String, teaId: String, name: String, at: Long, vararg seconds: Int) {
        sessions.sessions.startSession(
            SessionStart(
                id,
                snapshot(teaId, name),
                seconds.map {
                    PlannedStep(StepKind.INFUSION, it, 65)
                },
                15,
                at,
                "UTC",
            ),
        )
        seconds.indices.forEach {
            sessions.sessions.recordStep(id, it, StepRecord(seconds[it], seconds[it], 0, 0, at, at + 1, false, false))
        }
        sessions.sessions.completeSession(id, autoFinished = false)
    }

    private fun viewModel() = HomeViewModel(
        collection.teas,
        sessions.sessions,
        starter,
        clock,
        PhotoFiles(folder.newFolder("photos")),
    )

    @Test
    fun `an empty collection shows the empty state (GEN-09)`() {
        val state = viewModel().state.value

        assertThat(state.loading).isFalse()
        assertThat(state.collectionEmpty).isTrue()
    }

    @Test
    fun `Nochmal bruehen shows the three most recently brewed distinct teas, Zuletzt the last three sessions`() =
        runTest {
            seed()
            collection.teas.saveTea(SeedData.hisui.copy(id = "third", name = "Gyokuro"))
            collection.teas.saveTea(SeedData.hisui.copy(id = "fourth", name = "Bancha"))
            brewed("s1", "fourth", "Bancha", at = 1_000, 60)
            brewed("s2", "third", "Gyokuro", at = 2_000, 90)
            brewed("s3", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 3_000, 45, 10)
            brewed("s4", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 4_000, 60, 10, 10)
            brewed("s5", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 5_000, 45)

            val state = viewModel().state.value

            assertThat(state.brewAgain.map { it.name })
                .containsExactly("Bio Sencha Tanabata", "Bio Sencha Hisui", "Gyokuro").inOrder()
            assertThat(state.recent.map { it.sessionId }).containsExactly("s5", "s4", "s3").inOrder()
            assertThat(state.recent[1].infusions).isEqualTo(3)
            assertThat(state.recent[1].firstStepSeconds).isEqualTo(60)
            assertThat(state.recent[1].tempC).isEqualTo(65)
        }

    @Test
    fun `an archived or deleted tea is not offered again`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        brewed("s2", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 2_000, 45)
        collection.teas.saveTea(SeedData.tanabata.copy(isArchived = true))

        assertThat(viewModel().state.value.brewAgain.map { it.id }).containsExactly(SeedData.HISUI_TEA_ID)
    }

    @Test
    fun `Favoriten lists the favourite teas`() = runTest {
        seed()
        collection.teas.saveTea(SeedData.hisui.copy(isFavorite = true))

        assertThat(viewModel().state.value.favorites.map { it.id }).containsExactly(SeedData.HISUI_TEA_ID)
    }

    @Test
    fun `one tap on a tea brews it again with its last session and opens the timer in READY (BRW-11)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60, 10, 10)
        val vm = viewModel()

        vm.brewAgain(SeedData.HISUI_TEA_ID)

        val start = starter.starts.single()
        assertThat(start.snapshot.teaName).isEqualTo("Bio Sencha Hisui")
        assertThat(start.steps.map { it.plannedSeconds }).containsExactly(60, 10, 10).inOrder()
        assertThat(start.recipePreAlertSeconds).isEqualTo(15)
        assertThat((starter.state.value as com.umamit.core.timer.controller.TimerUiState.Active).phase)
            .isEqualTo(TimerPhase.READY)
        assertThat(vm.events.first()).isEqualTo(HomeEvent.OpenTimer)
        assertThat(vm.state.value.active?.teaName).isEqualTo("Bio Sencha Hisui")
    }

    @Test
    fun `brewing again while a session is active asks first (TMR-21)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        brewed("s2", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 2_000, 45)
        val vm = viewModel()
        vm.brewAgain(SeedData.TANABATA_TEA_ID)

        vm.brewAgain(SeedData.HISUI_TEA_ID)
        assertThat(vm.state.value.question).isEqualTo(HomeQuestion("Bio Sencha Tanabata"))

        vm.confirmRestart()
        assertThat(vm.state.value.question).isNull()
        assertThat(starter.replaced).isEqualTo(1)
        assertThat(vm.state.value.active?.teaName).isEqualTo("Bio Sencha Hisui")
    }

    @Test
    fun `Abbrechen keeps the active session`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        val vm = viewModel()
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        vm.brewAgain(SeedData.HISUI_TEA_ID)

        vm.dismissQuestion()

        assertThat(vm.state.value.question).isNull()
        assertThat(starter.starts).hasSize(1)
    }

    @Test
    fun `brewing again while the active step alerts opens the timer instead of asking (rule 8)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        val vm = viewModel()
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        assertThat(vm.events.first()).isEqualTo(HomeEvent.OpenTimer)
        val active = starter.state.value as com.umamit.core.timer.controller.TimerUiState.Active
        starter.state.value = active.copy(phase = TimerPhase.ALERTING, deadlineElapsedMs = 1, deadlineWallMs = 1)

        vm.brewAgain(SeedData.HISUI_TEA_ID)

        assertThat(vm.state.value.question).isNull()
        assertThat(vm.events.first()).isEqualTo(HomeEvent.OpenTimer)
        assertThat(starter.starts).hasSize(1)
    }

    @Test
    fun `an alert closes the question (rule 8)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        val vm = viewModel()
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        val active = starter.state.value as com.umamit.core.timer.controller.TimerUiState.Active

        starter.state.value = active.copy(phase = TimerPhase.ALERTING, deadlineElapsedMs = 1, deadlineWallMs = 1)

        assertThat(vm.state.value.question).isNull()
        assertThat(vm.state.value.alertKey).isNotNull()
    }
}
