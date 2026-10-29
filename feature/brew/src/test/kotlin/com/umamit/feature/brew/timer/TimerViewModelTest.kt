package com.umamit.feature.brew.timer

import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SeedData
import com.umamit.core.data.SessionStart
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The timer's view model over a fake [BrewTimer]: it forwards events and reads the settings and checks. */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {

    private class FakeBrewTimer(initial: TimerUiState) : BrewTimer {
        override val state = MutableStateFlow(initial)
        val sent = mutableListOf<TimerEvent>()

        override suspend fun send(event: TimerEvent): TransitionOutcome {
            sent += event
            return TransitionOutcome.Accepted
        }
    }

    private val clock = object : MonotonicClock {
        override fun elapsedMs() = 42L

        override fun bootCount() = 1
    }
    private val timer = FakeBrewTimer(TimerStates.running())
    private val reliability = FakeTimerReliability()
    private val settings = FakeSettingsRepository(UserSettings(keepScreenOn = false, dimWhileSteeping = false))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val photos by lazy { PhotoFiles(folder.newFolder("photos")) }

    private fun viewModel() =
        TimerViewModel(timer, clock, settings, reliability, sessions.sessions, collection.teas, photos)

    private suspend fun startHisuiSession(id: String) {
        val snapshot = SessionSnapshot(
            teaId = SeedData.HISUI_TEA_ID,
            teaName = "Bio Sencha Hisui",
            teaSecondaryName = null,
            teaType = "Sencha",
            styleKey = "sencha",
            category = null,
            profileId = null,
            profileName = "Kyusu",
            method = BrewMethod.KYUSU,
            vesselId = SeedData.KYUSU_VESSEL_ID,
            vesselName = "Kyusu",
            leafGrams = 5.0,
            waterMl = 250,
            baseTempC = 65,
        )
        sessions.sessions.startSession(
            SessionStart(id, snapshot, listOf(PlannedStep(StepKind.INFUSION, 60, 65)), 15, 1_000, "UTC"),
        )
    }

    @Test
    fun `the brew's seal gets the tea's style and cover photo, no seal for the quick timer (D-095)`() = runTest {
        collection.teas.saveTea(SeedData.hisui)
        startHisuiSession("s1")
        val vm = viewModel()
        // No cover photo yet: the seal shows the leaf in the style's colour.
        assertThat(vm.teaSeal.value).isEqualTo(TimerTeaSeal(styleKey = "sencha", photoPath = null))

        collection.teas.savePhoto(Photo("p1", PhotoOwner.OfTea(SeedData.HISUI_TEA_ID), PhotoOrigin.OWN, 10, 10, 3))
        photos.write("p1", byteArrayOf(1, 2, 3))
        collection.teas.setCoverPhoto(SeedData.HISUI_TEA_ID, "p1")
        assertThat(vm.teaSeal.value).isEqualTo(TimerTeaSeal(styleKey = "sencha", photoPath = photos.file("p1").path))

        timer.state.value = TimerStates.quick(TimerPhase.RUNNING)
        assertThat(vm.teaSeal.value).isNull()
    }

    @Test
    fun `the state is the controller's state and events go to the controller`() {
        val vm = viewModel()

        vm.send(TimerEvent.Pause)
        timer.state.value = TimerUiState.Idle

        assertThat(timer.sent).containsExactly(TimerEvent.Pause)
        assertThat(vm.state.value).isEqualTo(TimerUiState.Idle)
        assertThat(vm.nowElapsedMs()).isEqualTo(42L)
    }

    @Test
    fun `the screen settings follow SET-17 and SET-25`() {
        val vm = viewModel()
        val collected: StateFlow<TimerScreenSettings> = vm.screenSettings

        assertThat(collected.value).isEqualTo(TimerScreenSettings(keepScreenOn = false, dimWhileSteeping = false))
    }

    @Test
    fun `the reliability checks are read again on resume, and Beheben opens the failing setting (TMR-31)`() {
        val vm = viewModel()
        assertThat(vm.reliabilityProblem.value).isFalse()

        reliability.set(ReliabilityCheck.FullScreenAlert, CheckState.Missing)
        vm.refreshReliability()
        vm.fixReliability()

        assertThat(vm.reliabilityProblem.value).isTrue()
        assertThat(reliability.opened).containsExactly(ReliabilityCheck.FullScreenAlert)
    }
}
