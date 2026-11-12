package com.umamit.feature.journal.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionStart
import com.umamit.core.data.SessionTasting
import com.umamit.core.data.TastingWrite
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeRecipeOffers
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.feature.journal.JournalSessions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * The session page's view model (SCR-16, BRW-06, D-100; the rating and notes writes of D-112) on the fakes of
 * core:testing: the session as stored (05 §3.6, §3.7) with its step table, and the four states of its tea and recipe.
 * An invented tea (D-016).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionPageViewModelTest {
    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val archive = FakeTeaArchive(sessions)

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        collection.teas.saveTea(Tea(TEA, "Sencha Morgentau", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: String = PAST, tasting: SessionTasting = sessions.tasting) = SessionPageViewModel(
        SavedStateHandle(mapOf("sessionId" to id)),
        sessions.journal,
        FakeRecipeOffers(),
        collection.teas,
        archive,
        tasting,
    )

    /** The fake's writes, the first rating and the first notes write held at [gate] like a slow Room transaction. */
    private class SlowFirstWrite(private val delegate: SessionTasting) : SessionTasting by delegate {
        val gate = CompletableDeferred<Unit>()
        private var ratingHeld = false
        private var notesHeld = false

        override suspend fun setRating(sessionId: String, rating: Double?): TastingWrite {
            if (!ratingHeld) {
                ratingHeld = true
                gate.await()
            }
            return delegate.setRating(sessionId, rating)
        }

        override suspend fun setNotes(sessionId: String, notes: String?): TastingWrite {
            if (!notesHeld) {
                notesHeld = true
                gate.await()
            }
            return delegate.setNotes(sessionId, notes)
        }
    }

    private val SessionPageViewModel.page get() = (state.value as SessionPageUiState.Ready).page

    /**
     * A rinse, an infusion set to 75 s in the Schritte-Sheet that ran into overtime, a skipped infusion and one never
     * started (kept from v0.4.0, Q-19).
     */
    private suspend fun brewed(teaId: String? = TEA) {
        val snapshot = SessionSnapshot(
            teaId = teaId,
            teaName = "Sencha Morgentau",
            teaSecondaryName = "Frühlingsernte",
            teaType = "Sencha",
            styleKey = "sencha",
            category = TeaCategory.GREEN,
            profileId = "gone",
            profileName = "Kräftig",
            method = BrewMethod.KYUSU,
            vesselId = "kyusu",
            vesselName = "Kyusu 250 ml",
            leafGrams = 4.5,
            waterMl = 180,
            baseTempC = 65,
        )
        val steps = listOf(
            PlannedStep(StepKind.RINSE, 10, 80),
            PlannedStep(StepKind.INFUSION, 75, null),
            PlannedStep(StepKind.INFUSION, 20, 70),
            PlannedStep(StepKind.INFUSION, 30, 75),
        )
        sessions.sessions.startSession(SessionStart(PAST, snapshot, steps, 10, STARTED, "Europe/Berlin"))
        sessions.sessions.recordStep(PAST, 0, record(10, 10))
        sessions.sessions.recordStep(PAST, 1, record(75, 95))
        sessions.sessions.recordStep(PAST, 2, record(20, 0, skipped = true))
        sessions.sessions.completeSession(PAST, autoFinished = false)
    }

    private fun record(planned: Int, actual: Int, skipped: Boolean = false) =
        StepRecord(planned, actual, 0, 0, STARTED, STARTED + 1_000, skipped = skipped, overtimeAutoAck = false)

    /** *Löschen* (D-102): the finished session goes with its BREW stock event, and the host hears `Deleted`. */
    @Test
    fun `delete removes the session with its stock deduction and emits Deleted`() = runTest {
        collection.stock.addEvent(InventoryEvent("p", TEA, InventoryEventType.PURCHASE, 50.0, occurredAtMillis = 1L))
        brewed()
        assertThat(collection.stock.observeStock(TEA).first().remainingGrams).isEqualTo(45.5)
        val vm = viewModel()
        assertThat(vm.page.inProgress).isFalse()

        vm.delete()
        val event = vm.events.first()

        assertThat(event).isEqualTo(SessionPageEvent.Deleted)
        assertThat(sessions.sessions.getSession(PAST)).isNull()
        assertThat(collection.stock.observeStock(TEA).first().remainingGrams).isEqualTo(50.0)
        assertThat(vm.state.value).isEqualTo(SessionPageUiState.NotFound)
    }

    @Test
    fun `the page shows the session as stored, with its local start, ratio and step table`() = runTest {
        brewed()

        val page = viewModel().page

        assertThat(page.teaName).isEqualTo("Sencha Morgentau")
        assertThat(page.teaSecondaryName).isEqualTo("Frühlingsernte")
        assertThat(page.startedAt).isEqualTo(LocalDateTime.of(2026, 10, 22, 9, 12))
        assertThat(page.method).isEqualTo(BrewMethod.KYUSU)
        assertThat(page.vesselName).isEqualTo("Kyusu 250 ml")
        assertThat(page.leafGrams).isEqualTo(4.5)
        assertThat(page.waterMl).isEqualTo(180)
        assertThat(page.ratioPer100Ml).isEqualTo(2.5)
        assertThat(page.steps).containsExactly(
            SessionPageStep(StepKind.RINSE, null, 10, 10, 80, skipped = false),
            SessionPageStep(StepKind.INFUSION, 1, 75, 95, 65, skipped = false),
            SessionPageStep(StepKind.INFUSION, 2, 20, 0, 70, skipped = true),
            SessionPageStep(StepKind.INFUSION, 3, 30, null, 75, skipped = false),
        ).inOrder()
        assertThat(page.steps.last().brewed).isFalse()
    }

    @Test
    fun `a tea in the collection links to its detail and can be brewed again, also without its recipe`() = runTest {
        brewed()

        val page = viewModel().page

        assertThat(page.tea).isEqualTo(SessionTea.AVAILABLE)
        assertThat(page.teaId).isEqualTo(TEA)
        assertThat(page.canBrewAgain).isTrue()
    }

    @Test
    fun `an archived tea greys out So nochmal bruehen until Wiederherstellen`() = runTest {
        brewed()
        archive.archive(TEA)
        val vm = viewModel()

        assertThat(vm.page.tea).isEqualTo(SessionTea.ARCHIVED)
        assertThat(vm.page.canBrewAgain).isFalse()

        vm.restore()

        assertThat(collection.teas.getTea(TEA)!!.isArchived).isFalse()
        assertThat(vm.page.tea).isEqualTo(SessionTea.AVAILABLE)
        assertThat(vm.page.canBrewAgain).isTrue()
    }

    @Test
    fun `a deleted tea leaves the page readable, without a tea link and without the button`() = runTest {
        brewed()
        val vm = viewModel()

        archive.delete(TEA)

        assertThat(vm.page.tea).isEqualTo(SessionTea.DELETED)
        assertThat(vm.page.teaId).isNull()
        assertThat(vm.page.teaName).isEqualTo("Sencha Morgentau")
        assertThat(vm.page.canBrewAgain).isFalse()
        assertThat(vm.page.steps).hasSize(4)
    }

    @Test
    fun `a session without a tea row reads as a deleted tea`() = runTest {
        brewed(teaId = "never-there")

        assertThat(viewModel().page.tea).isEqualTo(SessionTea.DELETED)
    }

    @Test
    fun `an unknown session shows the not-found state`() {
        assertThat(viewModel("nothing").state.value).isEqualTo(SessionPageUiState.NotFound)
    }

    @Test
    fun `the rating is saved at once and shown on the page`() = runTest {
        brewed()
        val vm = viewModel()
        assertThat(vm.page.rating).isNull()

        vm.setRating(3.5)
        assertThat(vm.page.rating).isEqualTo(3.5)
        assertThat(sessions.sessions.getSession(PAST)!!.rating).isEqualTo(3.5)

        vm.setRating(null)
        assertThat(vm.page.rating).isNull()
    }

    @Test
    fun `taste values are saved at once on their infusion, Zuruecksetzen clears them in order with the writes`() =
        runTest {
            brewed()
            val vm = viewModel()
            // The rinse and the skipped infusion have no chip (D-114).
            assertThat(vm.page.infusions.map { it.position }).containsExactly(1)
            assertThat(vm.page.infusions.single().taste.setCount).isEqualTo(0)

            vm.infusionTasting.setTaste(1, TasteAxis.UMAMI, 4)
            vm.infusionTasting.setTaste(1, TasteAxis.BODY, 0)
            vm.infusionTasting.setNote(1, "  Weich  ")
            val first = vm.page.infusions.single()
            assertThat(first.taste.umami).isEqualTo(4)
            assertThat(first.taste.body).isEqualTo(0)
            assertThat(first.taste.aroma).isNull()
            assertThat(first.note).isEqualTo("Weich")
            assertThat(first.hasData).isTrue()

            vm.infusionTasting.clearTaste(1)
            assertThat(vm.page.infusions.single().taste.setCount).isEqualTo(0)
            assertThat(vm.page.infusions.single().note).isEqualTo("Weich")
        }

    /** The half-step gesture is a quick second tap (4,0 then 3,5): a slow first write never overtakes it. */
    @Test
    fun `rating writes are stored in tap order even when the first write is slow`() = runTest {
        brewed()
        val slow = SlowFirstWrite(sessions.tasting)
        val vm = viewModel(tasting = slow)

        vm.setRating(4.0)
        vm.setRating(3.5)
        assertThat(sessions.sessions.getSession(PAST)!!.rating).isNull()
        slow.gate.complete(Unit)
        advanceUntilIdle()

        assertThat(sessions.sessions.getSession(PAST)!!.rating).isEqualTo(3.5)
        assertThat(vm.page.rating).isEqualTo(3.5)
    }

    @Test
    fun `the notes are saved 1 s after the last key, not before`() = runTest {
        brewed()
        val vm = viewModel()

        vm.onNotesChanged("Mild")
        advanceTimeBy(900)
        vm.onNotesChanged("Mild und cremig")
        advanceTimeBy(900)
        assertThat(sessions.tasting.writes).isEqualTo(0)
        advanceTimeBy(200)

        assertThat(sessions.tasting.writes).isEqualTo(1)
        assertThat(vm.page.notes).isEqualTo("Mild und cremig")
    }

    @Test
    fun `leaving the field saves a pending note at once and a second flush writes nothing`() = runTest {
        brewed()
        val vm = viewModel()

        vm.onNotesChanged("Frisch")
        vm.flushNotes()
        vm.flushNotes()
        advanceTimeBy(2_000)

        assertThat(sessions.tasting.writes).isEqualTo(1)
        assertThat(vm.page.notes).isEqualTo("Frisch")
    }

    @Test
    fun `clearing the note removes it`() = runTest {
        brewed()
        val vm = viewModel()
        vm.onNotesChanged("Frisch")
        vm.flushNotes()

        vm.onNotesChanged("")
        vm.flushNotes()

        assertThat(vm.page.notes).isNull()
    }

    @Test
    fun `a pending note is saved when the view model is cleared, the write completes (rule 8)`() = runTest {
        brewed()
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { viewModel() } })[
            SessionPageViewModel::class,
        ]

        vm.onNotesChanged("Kurz vor dem Verlassen")
        store.clear()

        assertThat(sessions.sessions.getSession(PAST)!!.notes).isEqualTo("Kurz vor dem Verlassen")
    }

    /** Like Room, the write suspends; the cleared scope does not cancel it on the way (NonCancellable, rule 8). */
    @Test
    fun `a note write suspended when the view model is cleared still completes (rule 8)`() = runTest {
        brewed()
        val slow = SlowFirstWrite(sessions.tasting)
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { viewModel(tasting = slow) } })[
            SessionPageViewModel::class,
        ]

        vm.onNotesChanged("Kurz vor dem Verlassen")
        store.clear()
        assertThat(sessions.sessions.getSession(PAST)!!.notes).isNull()
        slow.gate.complete(Unit)
        advanceUntilIdle()

        assertThat(sessions.sessions.getSession(PAST)!!.notes).isEqualTo("Kurz vor dem Verlassen")
    }

    private companion object {
        const val TEA = "morgentau"
        const val PAST = "past"
        val STARTED = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 9, 12))
    }
}
