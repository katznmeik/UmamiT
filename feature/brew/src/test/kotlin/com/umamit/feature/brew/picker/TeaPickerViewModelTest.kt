package com.umamit.feature.brew.picker

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionStart
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
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
    ) = Tea(
        id = id,
        name = name,
        category = TeaCategory.GREEN,
        form = TeaForm.LOOSE_LEAF,
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

    private fun viewModel() =
        TeaPickerViewModel(collection.teas, sessions.sessions, PhotoFiles(folder.newFolder("photos")))

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
    fun `an empty collection is empty`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.isEmpty).isTrue()
    }
}
