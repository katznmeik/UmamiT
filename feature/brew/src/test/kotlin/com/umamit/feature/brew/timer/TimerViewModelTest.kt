package com.umamit.feature.brew.timer

import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.timer.controller.TimerUiState
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
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

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

    private fun viewModel() = TimerViewModel(timer, clock, settings, reliability)

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
