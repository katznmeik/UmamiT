package com.umamit.feature.journal.session

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionStart
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.feature.journal.JournalSessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * The session page's view model (SCR-16 read-only, BRW-06, D-100) on the fakes of core:testing: the session as
 * stored (05 §3.6, §3.7) with its step table, and the four states of its tea and recipe. An invented tea (D-016).
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

    private fun viewModel(id: String = PAST) =
        SessionPageViewModel(SavedStateHandle(mapOf("sessionId" to id)), sessions.journal, collection.teas, archive)

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

    private companion object {
        const val TEA = "morgentau"
        const val PAST = "past"
        val STARTED = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 9, 12))
    }
}
