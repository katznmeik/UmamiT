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
import com.umamit.core.timer.controller.TimerToEnd
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.SessionKind
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
    fun `Zuletzt gebrueht shows the six most recently brewed distinct teas, each with its last session (D-110)`() =
        runTest {
            seed()
            // Seven teas brewed, oldest first: the oldest of them is not shown.
            (1..5).forEach { n ->
                collection.teas.saveTea(SeedData.hisui.copy(id = "tea$n", name = "Tee $n"))
                brewed("old$n", "tea$n", "Tee $n", at = n * 100L, 90)
            }
            brewed("s3", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 3_000, 45, 10)
            brewed("s4", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 4_000, 60, 10, 10)
            brewed("s5", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 5_000, 45)

            val state = viewModel().state.value

            assertThat(state.recent.map { it.teaName })
                .containsExactly("Bio Sencha Tanabata", "Bio Sencha Hisui", "Tee 5", "Tee 4", "Tee 3", "Tee 2")
                .inOrder()
            // A tea brewed twice shows once, with its last session.
            assertThat(state.recent.map { it.sessionId })
                .containsExactly("s5", "s4", "old5", "old4", "old3", "old2").inOrder()
            assertThat(state.recent[0].teaId).isEqualTo(SeedData.TANABATA_TEA_ID)
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

        assertThat(viewModel().state.value.recent.map { it.teaId }).containsExactly(SeedData.HISUI_TEA_ID)
    }

    @Test
    fun `a removed tea leaves Zuletzt gebrueht, a kept one shows its cover photo (BRW-11, D-110)`() = runTest {
        seed()
        val photos = PhotoFiles(folder.newFolder("photos"))
        photos.write("cover", byteArrayOf(1, 2, 3))
        collection.teas.savePhoto(Photo("cover", PhotoOwner.OfTea(SeedData.TANABATA_TEA_ID), PhotoOrigin.OWN, 1, 1, 3))
        collection.teas.setCoverPhoto(SeedData.TANABATA_TEA_ID, "cover")
        brewed("s1", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 1_000, 45)

        val home = HomeViewModel(
            collection.teas,
            sessions.sessions,
            starter,
            clock,
            photos,
            reliability,
            lowStock,
        )

        assertThat(home.state.value.recent.single().teaName).isEqualTo("Bio Sencha Tanabata")
        assertThat(home.state.value.recent.single().photoPath).isEqualTo(photos.file("cover").path)

        // Removed, the tea cannot be brewed again from Start; the journal keeps its sessions (D-093 Q13).
        collection.teas.saveTea(collection.teas.getTea(SeedData.TANABATA_TEA_ID)!!.copy(isArchived = true))

        assertThat(home.state.value.recent).isEmpty()
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
        assertThat(vm.events.first()).isEqualTo(HomeEvent.OpenTimer(TimerSlot.BREW))
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
    fun `a double tap on a row starts once and leaves no question on Start (S6)`() = runTest {
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
        assertThat(vm.events.first()).isEqualTo(HomeEvent.OpenTimer(TimerSlot.BREW))
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
    fun `brewing again another tea while one brew is active starts the second brew and opens it (D-108)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        brewed("s2", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 2_000, 45)
        val vm = viewModel()
        vm.brewAgain(SeedData.TANABATA_TEA_ID)
        assertThat(vm.events.first()).isEqualTo(HomeEvent.OpenTimer(TimerSlot.BREW))
        now += LATER_MS

        vm.brewAgain(SeedData.HISUI_TEA_ID)

        assertThat(vm.state.value.question).isNull()
        assertThat(starter.replaced).isEqualTo(0)
        assertThat(starter.activeIn(TimerSlot.BREW_2)?.teaName).isEqualTo("Bio Sencha Hisui")
        // The new brew opens, not the one in BREW (D-108).
        assertThat(vm.events.first()).isEqualTo(HomeEvent.OpenTimer(TimerSlot.BREW_2))
        // The card stays the first brew's; the bar shows both.
        assertThat(vm.state.value.active?.teaName).isEqualTo("Bio Sencha Tanabata")
    }

    @Test
    fun `brewing again a tea with an active session opens that session, asking nothing (D-108)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        brewed("s2", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 2_000, 45)
        val vm = viewModel()
        vm.brewAgain(SeedData.TANABATA_TEA_ID)
        now += LATER_MS
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        vm.events.first()
        vm.events.first()
        now += LATER_MS

        vm.brewAgain(SeedData.HISUI_TEA_ID)

        assertThat(vm.state.value.question).isNull()
        assertThat(starter.starts).hasSize(2)
        assertThat(vm.events.first()).isEqualTo(HomeEvent.OpenTimer(TimerSlot.BREW_2))
    }

    @Test
    fun `a third start asks which timer to end and ends only the chosen one (TMR-21 AC5)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        brewed("s2", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 2_000, 45)
        starter.state.value = FakeBrewStarter.quickRunningState(90)
        val vm = viewModel()
        vm.brewAgain(SeedData.TANABATA_TEA_ID)
        vm.events.first()
        now += LATER_MS

        vm.brewAgain(SeedData.HISUI_TEA_ID)
        assertThat(vm.state.value.question).isEqualTo(
            HomeQuestion(
                listOf(
                    TimerToEnd(TimerSlot.BREW, "Bio Sencha Tanabata", "session-1"),
                    TimerToEnd(TimerSlot.QUICK, null),
                ),
            ),
        )

        vm.endTimerAndStart(TimerSlot.QUICK)

        assertThat(vm.state.value.question).isNull()
        assertThat(starter.replaced).isEqualTo(1)
        assertThat(starter.activeIn(TimerSlot.QUICK)).isNull()
        assertThat(starter.activeIn(TimerSlot.BREW)?.teaName).isEqualTo("Bio Sencha Tanabata")
        assertThat(starter.activeIn(TimerSlot.BREW_2)?.teaName).isEqualTo("Bio Sencha Hisui")
        assertThat(vm.events.first()).isEqualTo(HomeEvent.OpenTimer(TimerSlot.BREW_2))
    }

    @Test
    fun `an answer for a session that ended meanwhile ends no other session and asks again`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        brewed("s2", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 2_000, 45)
        starter.state.value = FakeBrewStarter.quickRunningState(90)
        val vm = viewModel()
        vm.brewAgain(SeedData.TANABATA_TEA_ID)
        vm.events.first()
        now += LATER_MS
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        // While the question is open, Tanabata's session ends and another session takes the brew slot.
        val tanabata = checkNotNull(starter.activeIn(TimerSlot.BREW))
        val replacement = tanabata.copy(kind = SessionKind.Brew("session-9"), teaName = "Kamairicha")
        if (starter.state.value == tanabata) starter.state.value = replacement else starter.other.value = replacement

        vm.endTimerAndStart(TimerSlot.BREW)

        assertThat(starter.replaced).isEqualTo(0)
        assertThat(starter.activeIn(TimerSlot.BREW)?.teaName).isEqualTo("Kamairicha")
        assertThat(vm.state.value.question?.timers?.map { it.sessionId }).containsExactly("session-9", null).inOrder()
    }

    @Test
    fun `ending the first of two brews for a third starts it in the freed slot (TMR-21 AC5)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        brewed("s2", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 2_000, 45)
        brewed("s3", KAMAIRICHA_TEA_ID, "Kamairicha", at = 3_000, 30)
        val vm = viewModel()
        vm.brewAgain(SeedData.TANABATA_TEA_ID)
        now += LATER_MS
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        now += LATER_MS
        vm.brewAgain(KAMAIRICHA_TEA_ID)
        assertThat(vm.state.value.question?.timers?.map { it.teaName })
            .containsExactly("Bio Sencha Tanabata", "Bio Sencha Hisui").inOrder()

        vm.endTimerAndStart(TimerSlot.BREW)

        assertThat(starter.activeIn(TimerSlot.BREW)?.teaName).isEqualTo("Kamairicha")
        assertThat(starter.activeIn(TimerSlot.BREW_2)?.teaName).isEqualTo("Bio Sencha Hisui")
        assertThat(vm.state.value.active?.teaName).isEqualTo("Kamairicha")
    }

    @Test
    fun `the card shows the second brew once the first has ended, and opens it (SCR-02, D-108)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        val vm = viewModel()
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        val second = checkNotNull(starter.activeIn(TimerSlot.BREW)).copy(inSlot = TimerSlot.BREW_2)

        starter.state.value = second

        assertThat(vm.state.value.active).isEqualTo(second)
        assertThat(vm.state.value.active?.slot).isEqualTo(TimerSlot.BREW_2)
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
    fun `Aufguss starten stays while fewer than two timers are active (SCR-02, D-108)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        brewed("s2", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 2_000, 45)
        val vm = viewModel()
        assertThat(vm.state.value.twoTimersActive).isFalse()

        // One brew: its card, and *Aufguss starten* for the second.
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        assertThat(vm.state.value.active).isNotNull()
        assertThat(vm.state.value.twoTimersActive).isFalse()

        // Two brews: no third start from the card; *Nochmal brühen* asks „Welchen Timer beenden?".
        now += LATER_MS
        vm.brewAgain(SeedData.TANABATA_TEA_ID)
        assertThat(starter.activeIn(TimerSlot.BREW_2)).isNotNull()
        assertThat(vm.state.value.twoTimersActive).isTrue()
    }

    @Test
    fun `a brew and a Schnelltimer are two timers too (SCR-02, D-108)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        val vm = viewModel()
        starter.startQuickTimer(90)
        assertThat(vm.state.value.twoTimersActive).isFalse()

        vm.brewAgain(SeedData.HISUI_TEA_ID)

        assertThat(vm.state.value.twoTimersActive).isTrue()
    }

    @Test
    fun `a Schnelltimer alone leaves Aufguss starten and Nochmal bruehen on Start (SCR-02, D-098)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        starter.state.value = FakeBrewStarter.quickRunningState(90)

        val state = viewModel().state.value

        // No brew replaces the card: the bar shows the quick timer.
        assertThat(state.active).isNull()
        assertThat(state.recent.map { it.teaId }).containsExactly(SeedData.HISUI_TEA_ID)
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
    fun `Abbrechen keeps both active timers`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        brewed("s2", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 2_000, 45)
        starter.state.value = FakeBrewStarter.quickRunningState(90)
        val vm = viewModel()
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        now += LATER_MS
        vm.brewAgain(SeedData.TANABATA_TEA_ID)
        assertThat(vm.state.value.question).isNotNull()

        vm.dismissQuestion()

        assertThat(vm.state.value.question).isNull()
        assertThat(starter.starts).hasSize(1)
        assertThat(starter.replaced).isEqualTo(0)
        assertThat(starter.activeIn(TimerSlot.QUICK)).isNotNull()
    }

    @Test
    fun `a third start while a step alerts opens the alerting timer instead of asking (rule 8)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        brewed("s2", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 2_000, 45)
        starter.state.value = FakeBrewStarter.quickRunningState(90)
        val vm = viewModel()
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        assertThat(vm.events.first()).isEqualTo(HomeEvent.OpenTimer(TimerSlot.BREW))
        val active = starter.state.value as TimerUiState.Active
        starter.state.value = active.copy(phase = TimerPhase.ALERTING, deadlineElapsedMs = 1, deadlineWallMs = 1)
        now += LATER_MS

        vm.brewAgain(SeedData.TANABATA_TEA_ID)

        assertThat(vm.state.value.question).isNull()
        // The timer in front: the alerting one.
        assertThat(vm.events.first()).isEqualTo(HomeEvent.OpenTimer(null))
        assertThat(starter.starts).hasSize(1)
    }

    @Test
    fun `an alert closes the question (rule 8)`() = runTest {
        seed()
        brewed("s1", SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", at = 1_000, 60)
        brewed("s2", SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", at = 2_000, 45)
        starter.state.value = FakeBrewStarter.quickRunningState(90)
        val vm = viewModel()
        vm.brewAgain(SeedData.HISUI_TEA_ID)
        now += LATER_MS
        vm.brewAgain(SeedData.TANABATA_TEA_ID)
        assertThat(vm.state.value.question).isNotNull()
        val active = starter.state.value as TimerUiState.Active

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
        const val KAMAIRICHA_TEA_ID = "kamairicha"
    }
}
