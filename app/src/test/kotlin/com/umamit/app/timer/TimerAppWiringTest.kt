package com.umamit.app.timer

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerUiStep
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.feature.brew.timer.BrewTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app side of SCR-05: the lock-screen flags and the active brew bar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class TimerAppWiringTest {

    private fun active(phase: TimerPhase, deadline: Long? = null, plannedSeconds: Int = 60, longStep: Boolean = false) =
        TimerUiState.Active(
            kind = SessionKind.Brew("s"),
            teaName = "Hisui",
            phase = phase,
            stepIndex = 0,
            steps = listOf(TimerUiStep(StepKind.INFUSION, plannedSeconds, 70, StepStatus.STARTED)),
            deadlineElapsedMs = deadline,
            deadlineWallMs = deadline,
            pausedRemainingMs = null,
            preAlertLeadSeconds = null,
            preAlertAtElapsedMs = null,
            preAlertPlayed = false,
            longStep = longStep,
            kettleTempC = 70,
            demoted = false,
            finishWouldDiscard = true,
            preAlertBaseSeconds = 10,
        )

    @Test
    fun `only the timer shows over the lock screen, and its alert turns the screen on (SCR-05, TMR-06)`() {
        val running = active(TimerPhase.RUNNING, deadline = 1_000)
        val alerting = active(TimerPhase.ALERTING, deadline = 1_000)

        assertThat(lockScreenFlags(TimerUiState.Idle, timerVisible = false)).isEqualTo(LockScreenFlags(false, false))
        assertThat(lockScreenFlags(running, timerVisible = false)).isEqualTo(LockScreenFlags(false, false))
        assertThat(lockScreenFlags(running, timerVisible = true)).isEqualTo(LockScreenFlags(true, false))
        assertThat(lockScreenFlags(alerting, timerVisible = false)).isEqualTo(LockScreenFlags(false, false))
        assertThat(lockScreenFlags(alerting, timerVisible = true)).isEqualTo(LockScreenFlags(true, true))
        assertThat(lockScreenFlags(TimerUiState.Idle, timerVisible = true)).isEqualTo(LockScreenFlags(false, false))
    }

    /** One timer in front and in its slot, or the two slots set apart ([showBoth]). */
    private class FakeTimer(initial: TimerUiState) : BrewTimer {
        override val state = MutableStateFlow(initial)
        private val slots = TimerSlot.entries.associateWith { MutableStateFlow<TimerUiState>(TimerUiState.Idle) }

        init {
            show(initial)
        }

        fun show(ui: TimerUiState) {
            slots.forEach { (slot, flow) ->
                flow.value = if ((ui as? TimerUiState.Active)?.slot == slot) ui else TimerUiState.Idle
            }
            state.value = ui
        }

        fun showBoth(brew: TimerUiState, quick: TimerUiState, front: TimerUiState) {
            slots.getValue(TimerSlot.BREW).value = brew
            slots.getValue(TimerSlot.QUICK).value = quick
            state.value = front
        }

        override fun state(slot: TimerSlot) = slots.getValue(slot)

        override suspend fun send(slot: TimerSlot, event: TimerEvent) = TransitionOutcome.Ignored
    }

    private fun TestScope.clock() = object : MonotonicClock {
        override fun elapsedMs() = testScheduler.currentTime

        override fun bootCount() = 1
    }

    @Test
    fun `the bar follows the controller's state and counts down each second`() = runTest {
        val timer = FakeTimer(TimerUiState.Idle)
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val source = TimerActiveBrewSource(timer, clock(), resources, backgroundScope)
        val shown = mutableListOf<ActiveBrew?>()
        backgroundScope.launch { source.activeBrew.collect { shown += it } }
        runCurrent()

        timer.show(active(TimerPhase.RUNNING, deadline = 60_000))
        runCurrent()
        advanceTimeBy(1_001)

        assertThat(shown.map { it?.remainingSeconds }).containsExactly(null, 60, 59).inOrder()
        assertThat(shown.last()?.step).isEqualTo("Aufguss 1/1")
        assertThat(source.progress()).isWithin(1e-3f).of(1f / 60)

        timer.show(TimerUiState.Idle)
        runCurrent()
        assertThat(shown.last()).isNull()
    }

    @Test
    fun `a source built while a step alerts has the alerting bar before any collection (B1, rule 8)`() = runTest {
        advanceTimeBy(67_000)
        val timer = FakeTimer(active(TimerPhase.ALERTING, deadline = 60_000))
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val source = TimerActiveBrewSource(timer, clock(), resources, backgroundScope)

        val first = source.activeBrew.value
        assertThat(first?.alerting).isTrue()
        assertThat(first?.overtimeSeconds).isEqualTo(7)
        assertThat(source.progress()).isEqualTo(1f)
    }

    @Test
    fun `a long step's bar progress holds still between its 10 s updates (D-055 S9)`() = runTest {
        val timer = FakeTimer(active(TimerPhase.RUNNING, deadline = 3_600_000, plannedSeconds = 3_600, longStep = true))
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val source = TimerActiveBrewSource(timer, clock(), resources, backgroundScope)
        backgroundScope.launch { source.activeBrew.collect {} }
        runCurrent()

        advanceTimeBy(1_001)
        val first = source.progress()
        advanceTimeBy(8_000)
        assertThat(source.progress()).isEqualTo(first)
        advanceTimeBy(1_000)
        assertThat(source.progress()).isGreaterThan(first)
    }

    @Test
    fun `with a brew and a quick timer the bar has the brew first and the quick timer beside it (D-098)`() = runTest {
        val timer = FakeTimer(TimerUiState.Idle)
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val brew = active(TimerPhase.RUNNING, deadline = 60_000)
        val quick = active(TimerPhase.RUNNING, deadline = 134_000, plannedSeconds = 300)
            .copy(kind = SessionKind.Quick, teaName = null)
        // The quick timer is in front of the controller (used last): the bar keeps the brew first.
        timer.showBoth(brew = brew, quick = quick, front = quick)
        val source = TimerActiveBrewSource(timer, clock(), resources, backgroundScope)
        backgroundScope.launch { source.activeBrew.collect {} }
        backgroundScope.launch { source.alongside.collect {} }
        runCurrent()

        assertThat(source.activeBrew.value?.title).isEqualTo("Hisui")
        assertThat(source.alongside.value?.title).isEqualTo("Schnelltimer")
        assertThat(source.alongside.value?.remainingSeconds).isEqualTo(134)
        assertThat(source.alongsideProgress()).isWithin(1e-3f).of(166f / 300)

        // The brew ends: the quick timer has the bar alone.
        timer.show(quick)
        runCurrent()
        assertThat(source.activeBrew.value?.title).isEqualTo("Schnelltimer")
        assertThat(source.alongside.value).isNull()
    }
}
