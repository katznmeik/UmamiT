package com.umamit.feature.home

import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.LowStockRepository
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SeedData
import com.umamit.core.data.SessionStart
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.LowStockCard
import com.umamit.core.model.LowStockTea
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeLowStockRepository
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.testing.lowStockCard
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
import com.umamit.core.timer.reliability.ReliabilityProblem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/** Start's view model (SCR-02; BRW-11, TMR-21, GEN-06, GEN-09) with fake repositories and a fake controller. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val starter = FakeBrewStarter()
    private val reliability = FakeTimerReliability()
    private val lowStock = FakeLowStockRepository()
    private var now = 5_000L
    private val clock = object : MonotonicClock {
        override fun elapsedMs() = now

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

    /**
     * A completed session of [teaId] started at [at] with [seconds] steps at 65 °C, of which the
     * first [poured] were brewed (all by default); the rest were never started.
     */
    private suspend fun brewed(
        id: String,
        teaId: String,
        name: String,
        at: Long,
        vararg seconds: Int,
        poured: Int = seconds.size,
    ) {
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
        (0 until poured).forEach {
            sessions.sessions.recordStep(id, it, StepRecord(seconds[it], seconds[it], 0, 0, at, at + 1, false, false))
        }
        sessions.sessions.completeSession(id, autoFinished = false)
    }

    private fun viewModel(brewStarter: BrewStarter = starter) = HomeViewModel(
        collection.teas,
        sessions.sessions,
        brewStarter,
        clock,
        PhotoFiles(folder.newFolder("photos")),
        reliability,
        lowStock,
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
    fun `a removed tea keeps its name and photo under Zuletzt gebrueht, like the journal (D-093 Q13)`() = runTest {
        seed()
        val photos = PhotoFiles(folder.newFolder("photos"))
        photos.write("cover", byteArrayOf(1, 2, 3))
        collection.teas.savePhoto(Photo("cover", PhotoOwner.OfTea(SeedData.TANABATA_TEA_ID), PhotoOrigin.OWN, 1, 1, 3))
        collection.teas.setCoverPhoto(SeedData.TANABATA_TEA_ID, "cover")
        brewed("s1", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 1_000, 45)
        collection.teas.saveTea(collection.teas.getTea(SeedData.TANABATA_TEA_ID)!!.copy(isArchived = true))

        val state = HomeViewModel(
            collection.teas,
            sessions.sessions,
            starter,
            clock,
            photos,
            reliability,
            lowStock,
        ).state.value

        assertThat(state.brewAgain).isEmpty()
        assertThat(state.recent.single().teaName).isEqualTo("Bio Sencha Tanabata")
        assertThat(state.recent.single().photoPath).isEqualTo(photos.file("cover").path)
    }

    @Test
    fun `a Knapp tea fills the nudge slot and Ausblenden hides it (INV-10)`() = runTest {
        seed()
        val card = lowStockCard(grams = 14.0)
        lowStock.card.value = card
        val vm = viewModel()

        assertThat(vm.state.value.lowStock).isEqualTo(card)

        vm.hideLowStock()

        assertThat(lowStock.hidden).containsExactly(card)
        assertThat(vm.state.value.lowStock).isNull()
    }

    @Test
    fun `a stock read that hangs or fails never holds back Start or its alert (rule 8)`() = runTest {
        seed()
        val hanging = object : LowStockRepository {
            override fun observeCard(): Flow<LowStockCard?> = flow { awaitCancellation() }

            override suspend fun hide(card: LowStockCard) = Unit

            override suspend fun reminderTeas(): List<LowStockTea> = emptyList()
        }
        val failing = object : LowStockRepository {
            override fun observeCard(): Flow<LowStockCard?> = flow { throw IOException("synthetic") }

            override suspend fun hide(card: LowStockCard) = Unit

            override suspend fun reminderTeas(): List<LowStockTea> = emptyList()
        }
        listOf(hanging, failing).forEach { repository ->
            val vm = HomeViewModel(
                collection.teas,
                sessions.sessions,
                starter,
                clock,
                PhotoFiles(folder.newFolder()),
                reliability,
                repository,
            )

            assertThat(vm.state.value.loading).isFalse()
            assertThat(vm.state.value.lowStock).isNull()
        }
    }

    @Test
    fun `no Knapp tea leaves the nudge slot empty`() = runTest {
        seed()

        assertThat(viewModel().state.value.lowStock).isNull()
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
    fun `a session finished after two of three shows and brews again only its two poured steps (Q-19, D-062)`() =
        runTest {
            seed()
            // From v0.4.0 the never-started third row is kept, without an actual time.
            brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60, 10, 30, poured = 2)
            val vm = viewModel()

            assertThat(vm.state.value.recent.single().infusions).isEqualTo(2)
            assertThat(vm.state.value.recent.single().firstStepSeconds).isEqualTo(60)

            vm.brewAgain(SeedData.HISUI_TEA_ID)

            // A session finished early is brewed again with its started steps (BRW-11, D-062).
            assertThat(starter.starts.single().steps.map { it.plannedSeconds }).containsExactly(60, 10).inOrder()
        }

    @Test
    fun `a double tap on a tile starts once and leaves no question on Start (S6)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        brewed("s2", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 2_000, 45)
        val vm = viewModel()

        vm.brewAgain(SeedData.HISUI_TEA_ID)
        now += 200
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        now += 200
        vm.brewAgain(SeedData.TANABATA_TEA_ID)

        assertThat(starter.starts).hasSize(1)
        assertThat(vm.state.value.question).isNull()
        assertThat(vm.events.first()).isEqualTo(HomeEvent.OpenTimer)
    }

    @Test
    fun `a second tap while the first start is in flight is ignored (S6)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        val gate = CompletableDeferred<Unit>()
        val gated = object : BrewStarter by starter {
            override suspend fun startSession(request: BrewStart): TransitionOutcome {
                gate.await()
                return starter.startSession(request)
            }
        }
        val vm = viewModel(gated)

        vm.brewAgain(SeedData.HISUI_TEA_ID)
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        gate.complete(Unit)

        assertThat(starter.starts).hasSize(1)
        assertThat(vm.state.value.question).isNull()
    }

    @Test
    fun `brewing again while a session is active asks first (TMR-21)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        brewed("s2", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 2_000, 45)
        val vm = viewModel()
        vm.brewAgain(SeedData.TANABATA_TEA_ID)
        now += LATER_MS

        vm.brewAgain(SeedData.HISUI_TEA_ID)
        assertThat(vm.state.value.question).isEqualTo(HomeQuestion("Bio Sencha Tanabata"))

        vm.confirmRestart()
        assertThat(vm.state.value.question).isNull()
        assertThat(starter.replaced).isEqualTo(1)
        assertThat(vm.state.value.active?.teaName).isEqualTo("Bio Sencha Hisui")
    }

    @Test
    fun `brewing again while a quick timer runs starts alongside without asking (TMR-21 AC1, D-098)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        starter.state.value = FakeBrewStarter.quickRunningState(90)
        val vm = viewModel()

        vm.brewAgain(SeedData.HISUI_TEA_ID)

        assertThat(vm.state.value.question).isNull()
        assertThat(starter.starts).hasSize(1)
        assertThat(starter.replaced).isEqualTo(0)
        assertThat(starter.activeIn(TimerSlot.QUICK)?.steps?.single()?.plannedSeconds).isEqualTo(90)
    }

    @Test
    fun `a Schnelltimer alone leaves Aufguss starten and Nochmal bruehen on Start (SCR-02, D-098)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        starter.state.value = FakeBrewStarter.quickRunningState(90)

        val state = viewModel().state.value

        // No brew replaces the card: the bar shows the quick timer.
        assertThat(state.active).isNull()
        assertThat(state.brewAgain.map { it.id }).containsExactly(SeedData.HISUI_TEA_ID)
    }

    @Test
    fun `with both timers the card shows the brew, also with the Schnelltimer in front (SCR-02, D-098)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        val vm = viewModel()
        vm.brewAgain(SeedData.HISUI_TEA_ID)

        starter.startQuickTimer(90)

        val brew = checkNotNull(starter.activeIn(TimerSlot.BREW))
        assertThat((starter.state.value as TimerUiState.Active).slot).isEqualTo(TimerSlot.QUICK)
        assertThat(vm.state.value.active).isEqualTo(brew)
        // The brew changes behind the Schnelltimer (its notification's *Start*): the card follows the brew.
        starter.other.value = brew.copy(phase = TimerPhase.RUNNING, deadlineElapsedMs = 60_000, deadlineWallMs = 1)
        assertThat(vm.state.value.active?.phase).isEqualTo(TimerPhase.RUNNING)
        // A ringing quick alert still closes a question and counts as Start's alert (rule 8).
        starter.state.value = FakeBrewStarter.quickRunningState(90)
            .copy(phase = TimerPhase.ALERTING, deadlineElapsedMs = 1, deadlineWallMs = 1)
        assertThat(vm.state.value.alertKey).isNotNull()
        assertThat(vm.state.value.active?.slot).isEqualTo(TimerSlot.BREW)
    }

    @Test
    fun `Abbrechen keeps the active session`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        val vm = viewModel()
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        now += LATER_MS
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
        now += LATER_MS

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
        now += LATER_MS
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        val active = starter.state.value as com.umamit.core.timer.controller.TimerUiState.Active

        starter.state.value = active.copy(phase = TimerPhase.ALERTING, deadlineElapsedMs = 1, deadlineWallMs = 1)

        assertThat(vm.state.value.question).isNull()
        assertThat(vm.state.value.alertKey).isNotNull()
    }

    @Test
    fun `the banner follows the checks when read again, and Beheben asks for the missing one (TMR-31)`() {
        val vm = viewModel()
        assertThat(vm.state.value.reliabilityProblem).isNull()

        reliability.set(ReliabilityCheck.Notifications, CheckState.Missing)
        vm.refreshReliability()
        vm.fixReliability()

        assertThat(vm.state.value.reliabilityProblem).isEqualTo(ReliabilityProblem.Unreliable)
        assertThat(reliability.opened).containsExactly(ReliabilityCheck.Notifications)
    }

    @Test
    fun `the alarm volume at 0 makes the banner say so, and Beheben asks for the sound settings (D-098)`() {
        val vm = viewModel()

        reliability.set(ReliabilityCheck.AlarmVolume, CheckState.Missing)
        vm.refreshReliability()
        vm.fixReliability()

        assertThat(vm.state.value.reliabilityProblem).isEqualTo(ReliabilityProblem.AlarmVolumeOff)
        assertThat(reliability.opened).containsExactly(ReliabilityCheck.AlarmVolume)
    }

    private companion object {
        /** A deliberate later tap, past the double-tap window. */
        const val LATER_MS = 5_000L
    }
}
