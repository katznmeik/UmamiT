package com.umamit.feature.brew.quick

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.SessionKind
import com.umamit.feature.brew.setup.ActiveSessionQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * SCR-07's view model over a fake start seam: chips, the *Vorwarnung* choice remembered in the settings (TMR-22,
 * D-097), *Start*, and the second-timer question (TMR-21).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickTimerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val starter = FakeBrewStarter()
    private val saved = SavedStateHandle()
    private val settings = FakeSettingsRepository(UserSettings(preAlertSeconds = 10))
    private val events = mutableListOf<QuickTimerEvent>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = QuickTimerViewModel(saved, starter, settings).also { vm ->
        vm.events.onEach { events += it }.launchIn(backgroundScope)
    }

    private fun brewSession(phase: TimerPhase) = FakeBrewStarter.quickRunningState(60).copy(
        kind = SessionKind.Brew("s1"),
        teaName = "Bio Sencha Hisui",
        phase = phase,
    )

    @Test
    fun `it opens with 1_00, and a chip selects its duration`() = runTest(dispatcher) {
        val vm = viewModel()
        assertThat(vm.state.value.durationSeconds).isEqualTo(60)

        vm.select(180)

        assertThat(vm.state.value.durationSeconds).isEqualTo(180)
        assertThat(starter.quickStarts).isEmpty()
    }

    @Test
    fun `the chosen duration survives process death (GEN-07)`() = runTest(dispatcher) {
        viewModel().select(300)

        assertThat(viewModel().state.value.durationSeconds).isEqualTo(300)
    }

    @Test
    fun `Start runs the quick timer with the chosen duration and opens the timer`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.select(90)

        vm.start()

        assertThat(starter.quickStarts).containsExactly(90)
        assertThat(events).containsExactly(QuickTimerEvent.OpenTimer)
    }

    @Test
    fun `it opens with the remembered Vorwarnung and the global setting as B (D-097)`() = runTest(dispatcher) {
        settings.update { it.copy(preAlertSeconds = 15, quickTimerPreAlert = 20) }

        val state = viewModel().state.value

        assertThat(state.preAlertChoice).isEqualTo(PreAlertChoice.Seconds(20))
        assertThat(state.preAlertBaseSeconds).isEqualTo(15)
    }

    @Test
    fun `without a remembered choice it opens with Automatisch`() = runTest(dispatcher) {
        assertThat(viewModel().state.value.preAlertChoice).isEqualTo(PreAlertChoice.Inherit)
    }

    @Test
    fun `a choice is remembered for the next quick timer, also after an app restart`() = runTest(dispatcher) {
        viewModel().selectPreAlert(PreAlertChoice.Off)

        assertThat(settings.settings.first().quickTimerPreAlert).isEqualTo(0)
        // Leaving and reopening the screen: a new view model with a new saved state reads the setting.
        val reopened = QuickTimerViewModel(SavedStateHandle(), starter, settings)
        assertThat(reopened.state.value.preAlertChoice).isEqualTo(PreAlertChoice.Off)

        reopened.selectPreAlert(PreAlertChoice.Inherit)
        assertThat(settings.settings.first().quickTimerPreAlert).isNull()
    }

    @Test
    fun `Start runs the quick timer with the chosen Vorwarnung`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.select(120)
        vm.selectPreAlert(PreAlertChoice.Seconds(20))

        vm.start()

        assertThat(starter.quickStarts).containsExactly(120)
        assertThat(starter.quickPreAlerts).containsExactly(20)
    }

    @Test
    fun `a choice that could not be stored still applies to this quick timer`() = runTest(dispatcher) {
        settings.failUpdates = true
        val vm = viewModel()

        vm.selectPreAlert(PreAlertChoice.Seconds(25))
        vm.start()

        assertThat(vm.state.value.preAlertChoice).isEqualTo(PreAlertChoice.Seconds(25))
        assertThat(starter.quickPreAlerts).containsExactly(25)
        assertThat(settings.settings.first().quickTimerPreAlert).isNull()
    }

    @Test
    fun `Start while a session is active asks first (TMR-21)`() = runTest(dispatcher) {
        starter.state.value = brewSession(TimerPhase.RUNNING)
        val vm = viewModel()

        vm.start()

        assertThat(vm.state.value.question).isEqualTo(ActiveSessionQuestion("Bio Sencha Hisui"))
        assertThat(events).isEmpty()
    }

    @Test
    fun `Beenden und neu starten replaces the session with the quick timer`() = runTest(dispatcher) {
        starter.state.value = brewSession(TimerPhase.RUNNING)
        val vm = viewModel()
        vm.select(120)
        vm.selectPreAlert(PreAlertChoice.Off)
        vm.start()

        vm.confirmRestart()

        assertThat(starter.replaced).isEqualTo(1)
        assertThat(starter.quickStarts).containsExactly(120)
        assertThat(starter.quickPreAlerts).containsExactly(0)
        assertThat(vm.state.value.question).isNull()
        assertThat(events).containsExactly(QuickTimerEvent.OpenTimer)
    }

    @Test
    fun `Abbrechen keeps the session`() = runTest(dispatcher) {
        starter.state.value = brewSession(TimerPhase.RUNNING)
        val vm = viewModel()
        vm.start()

        vm.dismissQuestion()

        assertThat(vm.state.value.question).isNull()
        assertThat(starter.replaced).isEqualTo(0)
        assertThat((starter.state.value as TimerUiState.Active).kind).isEqualTo(SessionKind.Brew("s1"))
    }

    @Test
    fun `Start while another timer alerts opens the alerting timer instead of asking (rule 8)`() = runTest(dispatcher) {
        starter.state.value = brewSession(TimerPhase.ALERTING)
        val vm = viewModel()

        vm.start()

        assertThat(vm.state.value.question).isNull()
        assertThat(starter.quickStarts).isEmpty()
        assertThat(events).containsExactly(QuickTimerEvent.OpenTimer)
    }

    @Test
    fun `a new alert closes the question (rule 8)`() = runTest(dispatcher) {
        starter.state.value = brewSession(TimerPhase.RUNNING)
        val vm = viewModel()
        vm.start()

        starter.state.value = brewSession(TimerPhase.ALERTING)

        assertThat(vm.state.value.question).isNull()
        assertThat(vm.state.value.alertKey).isNotNull()
    }
}
