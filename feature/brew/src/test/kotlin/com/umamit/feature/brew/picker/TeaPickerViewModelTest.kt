package com.umamit.feature.brew.picker

import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.ClockReading
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionStart
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Freshness
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeCollectionList
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The tea picker (SCR-03): *Zuletzt*, *Favoriten*, *Alle* and the search (GEN-10). */
@OptIn(ExperimentalCoroutinesApi::class)
class TeaPickerViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun tea(
        id: String,
        name: String,
        favorite: Boolean = false,
        archived: Boolean = false,
        type: String = "Sencha",
        style: String? = "sencha",
        category: TeaCategory? = null,
    ) = Tea(
        id = id,
        name = name,
        category = category ?: if (style == "herbal") TeaCategory.HERBAL else TeaCategory.GREEN,
        form = TeaForm.LOOSE_LEAF,
        styleKey = style,
        teaType = type,
        isFavorite = favorite,
        isArchived = archived,
    )

    private suspend fun brewed(sessionId: String, teaId: String, name: String, at: Long) {
        val snapshot = SessionSnapshot(
            teaId, name, null, null, null, null, null, null, BrewMethod.KYUSU, null, null, 5.0, 250, 65,
        )
        sessions.sessions.startSession(
            SessionStart(sessionId, snapshot, listOf(PlannedStep(StepKind.INFUSION, 60, 65)), 10, at, "UTC"),
        )
        sessions.sessions.recordStep(sessionId, 0, StepRecord(60, 60, 0, 0, at, at + 60_000, false, false))
        sessions.sessions.completeSession(sessionId, autoFinished = false)
    }

    private val archive = FakeTeaArchive(sessions)

    private val clock = FakeClock()
    private val today: LocalDate get() = Instant.ofEpochMilli(clock.wall).atZone(clock.zone).toLocalDate()

    private fun viewModel() = TeaPickerViewModel(
        collection.teas,
        sessions.sessions,
        archive,
        PhotoFiles(folder.newFolder("photos")),
        FakeCollectionList(sessions),
        clock,
    )

    /** A brew session of [teaId] waits in READY: the active session (E15). */
    private suspend fun activeSession(teaId: String, name: String) {
        val snapshot = SessionSnapshot(
            teaId, name, null, null, null, null, null, null, BrewMethod.KYUSU, null, null, 5.0, 250, 65,
        )
        val steps = listOf(PlannedStep(StepKind.INFUSION, 60, 65))
        val start = TimerEvent.StartSession(
            "active",
            steps,
            PreAlertSettings.inherited(null, 10),
            longStepsOnly = false,
        )
        val ready = BrewTimerEngine.reduce(TimerState.Idle, start, ClockReading(1_000, 1_000, ZoneOffset.UTC, 1)).state
        sessions.sessions.startSession(SessionStart("active", snapshot, steps, 10, 1_000, "UTC"))
        sessions.activeBrew.persist(TimerSlot.BREW, BrewTimerEngine.snapshot(ready))
    }

    private suspend fun seed() {
        collection.teas.saveTea(tea("h", "Bio Sencha Hisui", favorite = true))
        collection.teas.saveTea(tea("t", "Bio Sencha Tanabata"))
        collection.teas.saveTea(tea("k", "Kōridashi Gyokuro", type = "Gyokuro"))
        collection.teas.saveTea(tea("old", "Alter Bancha", archived = true))
    }

    @Test
    fun `Alle lists the teas that are not archived, Favoriten the favourites, Zuletzt the recently brewed`() =
        runTest(dispatcher) {
            seed()
            brewed("s1", "t", "Bio Sencha Tanabata", at = 1_000)
            brewed("s2", "h", "Bio Sencha Hisui", at = 2_000)
            brewed("s3", "t", "Bio Sencha Tanabata", at = 3_000)
            brewed("s4", "old", "Alter Bancha", at = 4_000)
            val vm = viewModel()
            advanceUntilIdle()

            val state = vm.state.value
            assertThat(state.all.map { it.id }).containsExactly("h", "t", "k").inOrder()
            assertThat(state.favorites.map { it.id }).containsExactly("h")
            // Distinct, newest first, archived teas left out.
            assertThat(state.recent.map { it.id }).containsExactly("t", "h").inOrder()
            assertThat(state.isEmpty).isFalse()
        }

    @Test
    fun `the search matches word prefixes, ignoring case and diacritics, after 150 ms`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        advanceUntilIdle()

        vm.setQuery("kor")
        advanceTimeBy(100)
        assertThat(vm.state.value.results).isNull()
        advanceTimeBy(100)
        assertThat(vm.state.value.results!!.map { it.id }).containsExactly("k")

        vm.setQuery("SENCHA")
        advanceUntilIdle()
        assertThat(vm.state.value.results!!.map { it.id }).containsExactly("h", "t").inOrder()

        vm.setQuery("encha")
        advanceUntilIdle()
        assertThat(vm.state.value.results).isEmpty()

        vm.setQuery("")
        advanceUntilIdle()
        assertThat(vm.state.value.results).isNull()
    }

    @Test
    fun `each tea carries its freshness label, none unopened or empty`() = runTest(dispatcher) {
        // Sencha: 45 days, Frisch on days 0–29, Bald genießen on 30–44.
        collection.teas.saveTea(tea("f", "Frühnebel").copy(openedDate = today.minusDays(29)))
        collection.teas.saveTea(tea("s", "Spätlicht").copy(openedDate = today.minusDays(30)))
        collection.teas.saveTea(tea("u", "Versiegelt"))
        collection.teas.saveTea(tea("e", "Ausgetrunken").copy(openedDate = today.minusDays(3)))
        collection.stock.addEvent(InventoryEvent("e1", "e", InventoryEventType.INITIAL, 0.0, occurredAtMillis = 1L))
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.all.associate { it.id to it.freshness }).containsExactly(
            "f",
            Freshness.FRESH,
            "s",
            Freshness.DRINK_SOON,
            "u",
            null,
            "e",
            null,
        )
    }

    @Test
    fun `an empty collection is empty`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.isEmpty).isTrue()
    }

    @Test
    fun `removing a tea archives it, it leaves every section and undo brings it back`() = runTest(dispatcher) {
        seed()
        brewed("s1", "h", "Bio Sencha Hisui", at = 1_000)
        val vm = viewModel()
        advanceUntilIdle()

        vm.remove("h")
        advanceUntilIdle()

        val removed = vm.state.value
        assertThat(archive.archived).containsExactly("h")
        assertThat(removed.all.map { it.id }).containsExactly("t", "k").inOrder()
        assertThat(removed.recent).isEmpty()
        assertThat(removed.favorites).isEmpty()
        // The snackbar „Bio Sencha Hisui archiviert" · *Rückgängig* (SCR-03, D-093 Q21).
        assertThat(removed.removed?.name).isEqualTo("Bio Sencha Hisui")

        vm.undoRemove()
        advanceUntilIdle()

        val restored = vm.state.value
        assertThat(restored.all.map { it.id }).containsExactly("h", "t", "k").inOrder()
        assertThat(restored.recent.map { it.id }).containsExactly("h")
        assertThat(restored.favorites.map { it.id }).containsExactly("h")
        assertThat(restored.removed).isNull()
    }

    @Test
    fun `the snackbar goes when it times out, and the tea stays removed`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        advanceUntilIdle()
        vm.remove("t")
        advanceUntilIdle()

        vm.dismissRemoved()
        advanceUntilIdle()

        assertThat(vm.state.value.removed).isNull()
        assertThat(vm.state.value.all.map { it.id }).containsExactly("h", "k").inOrder()
    }

    @Test
    fun `the tea of the active brew session can't be removed`() = runTest(dispatcher) {
        seed()
        activeSession("t", "Bio Sencha Tanabata")
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.all.associate { it.id to it.removable })
            .containsExactly("h", true, "t", false, "k", true)

        vm.remove("t")
        advanceUntilIdle()

        assertThat(archive.archived).isEmpty()
        assertThat(vm.state.value.all.map { it.id }).contains("t")
        assertThat(vm.state.value.removed).isNull()
    }

    /** Synthetic: two green teas and one herbal tea (D-097 picker groups). */
    private suspend fun seedKinds() {
        collection.teas.saveTea(tea("t", "Bio Sencha Tanabata", favorite = true))
        collection.teas.saveTea(tea("m", "Minze Zitrone", type = "Kräutertee", style = "herbal"))
        collection.teas.saveTea(tea("k", "Kōridashi Gyokuro", type = "Gyokuro", style = "gyokuro"))
    }

    private fun List<PickerGroup>.ids() = map { group -> group.kind to group.teas.map { it.id } }

    @Test
    fun `Alle is grouped by kind, green teas before herbal teas, A to Z within`() = runTest(dispatcher) {
        seedKinds()
        brewed("s1", "m", "Minze Zitrone", at = 1_000)
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.allGroups.ids()).containsExactly(
            TeaKind.GREEN to listOf("t", "k"),
            TeaKind.HERBAL to listOf("m"),
        ).inOrder()
        assertThat(state.showGroupHeaders).isTrue()
        // Zuletzt and Favoriten stay ungrouped.
        assertThat(state.recent.map { it.id }).containsExactly("m")
        assertThat(state.favorites.map { it.id }).containsExactly("t")
    }

    @Test
    fun `teas added without a catalog group by their Kategorie in the D-100 order`() = runTest(dispatcher) {
        collection.teas.saveTea(tea("a", "Anderer", style = null, category = TeaCategory.OTHER))
        collection.teas.saveTea(tea("y", "Gelber", style = null, category = TeaCategory.YELLOW))
        collection.teas.saveTea(tea("p", "Puerh", style = "puerh", category = TeaCategory.DARK))
        collection.teas.saveTea(tea("b", "Schwarzer", style = "black", category = TeaCategory.BLACK))
        collection.teas.saveTea(tea("o", "Oolong", style = "oolong", category = TeaCategory.OOLONG))
        collection.teas.saveTea(tea("w", "Weisser", style = "white", category = TeaCategory.WHITE))
        collection.teas.saveTea(tea("g", "Gruener", style = null, category = TeaCategory.GREEN))
        collection.teas.saveTea(tea("h", "Kraeuter", style = null, category = TeaCategory.HERBAL))
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.allGroups.ids()).containsExactly(
            TeaKind.GREEN to listOf("g"),
            TeaKind.WHITE to listOf("w"),
            TeaKind.OOLONG to listOf("o"),
            TeaKind.BLACK to listOf("b"),
            TeaKind.PUERH to listOf("p"),
            TeaKind.HERBAL to listOf("h"),
            TeaKind.OTHER to listOf("a", "y"),
        ).inOrder()
    }

    @Test
    fun `with green teas only there is one group and no headers`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.allGroups.ids()).containsExactly(TeaKind.GREEN to listOf("h", "t", "k"))
        assertThat(state.showGroupHeaders).isFalse()
    }

    @Test
    fun `search results are grouped the same way, with headers only for two kinds`() = runTest(dispatcher) {
        seedKinds()
        collection.teas.saveTea(tea("z", "Bio Zitronengras", type = "Kräutertee", style = "herbal"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.setQuery("bio")
        advanceUntilIdle()
        assertThat(vm.state.value.resultGroups!!.ids()).containsExactly(
            TeaKind.GREEN to listOf("t"),
            TeaKind.HERBAL to listOf("z"),
        ).inOrder()
        assertThat(vm.state.value.showResultHeaders).isTrue()

        vm.setQuery("gyokuro")
        advanceUntilIdle()
        assertThat(vm.state.value.resultGroups!!.ids()).containsExactly(TeaKind.GREEN to listOf("k"))
        assertThat(vm.state.value.showResultHeaders).isFalse()

        vm.setQuery("")
        advanceUntilIdle()
        assertThat(vm.state.value.resultGroups).isNull()
    }

    @Test
    fun `a tea removed from its group comes back into it with undo`() = runTest(dispatcher) {
        seedKinds()
        val vm = viewModel()
        advanceUntilIdle()

        vm.remove("m")
        advanceUntilIdle()
        assertThat(vm.state.value.allGroups.ids()).containsExactly(TeaKind.GREEN to listOf("t", "k"))
        assertThat(vm.state.value.showGroupHeaders).isFalse()

        vm.undoRemove()
        advanceUntilIdle()
        assertThat(vm.state.value.allGroups.ids()).containsExactly(
            TeaKind.GREEN to listOf("t", "k"),
            TeaKind.HERBAL to listOf("m"),
        ).inOrder()
    }
}
