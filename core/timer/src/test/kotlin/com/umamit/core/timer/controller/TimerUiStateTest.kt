package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.ClockReading
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.TimerDisplay
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerState
import org.junit.Test
import java.time.ZoneId

/** The UI state the controller publishes (04 §5): display rounding and the values the screens need. */
class TimerUiStateTest {
    private var now = ClockReading(elapsedMs = 1_000_000, wallMs = 1_700_000_000_000, ZoneId.of("Europe/Berlin"), 1)

    private fun advance(ms: Long) {
        now = now.copy(elapsedMs = now.elapsedMs + ms, wallMs = now.wallMs + ms)
    }

    private fun TimerState.on(event: TimerEvent) = BrewTimerEngine.reduce(this, event, now).state

    private val ready = TimerState.Idle.on(
        TimerEvent.StartSession(
            sessionId = "s",
            steps = listOf(infusion(60), infusion(90, tempC = 65)),
            preAlert = PreAlertSettings.inherited(recipeSeconds = null, globalSeconds = 10),
            longStepsOnly = false,
        ),
    )

    private fun ui(state: TimerState) = uiStateOf(state, "Gyokuro", now) as TimerUiState.Active

    @Test
    fun `each step's own pre-alert and the selector's choice are published for the Schritte-Sheet (SCR-31)`() {
        assertThat(ui(ready).preAlert).isEqualTo(PreAlertChoice.Inherit)

        val edited = ready.on(TimerEvent.EditSteps(listOf(infusion(60, preAlert = 20), infusion(30, preAlert = 0))))

        assertThat(ui(edited).steps.map { it.preAlertSeconds }).containsExactly(20, 0).inOrder()
        // The single selector until #92 shows the first step that has not ended.
        assertThat(ui(edited).preAlert).isEqualTo(PreAlertChoice.Seconds(20))
        assertThat(ui(edited.on(TimerEvent.SkipStep)).preAlert).isEqualTo(PreAlertChoice.Off)
    }

    @Test
    fun `remaining time rounds up and overtime rounds down, as in 04 §5`() {
        val running = ready.on(TimerEvent.Start)
        val ui = ui(running)

        assertThat(ui.display(now.elapsedMs + 100)).isEqualTo(TimerDisplay(60, 0))
        assertThat(ui.display(now.elapsedMs + 59_001)).isEqualTo(TimerDisplay(1, 0))
        assertThat(ui.display(now.elapsedMs + 61_000)).isEqualTo(TimerDisplay(0, 0))

        advance(60_000)
        val alerting = running.on(TimerEvent.DeadlineReached)
        assertThat(ui(alerting).display(now.elapsedMs + 7_999)).isEqualTo(TimerDisplay(0, 7))
    }

    @Test
    fun `the display agrees with the engine's in every phase`() {
        val states = buildList {
            add(ready)
            add(ready.on(TimerEvent.Start).also { advance(12_345) })
            add(last().on(TimerEvent.Pause))
            add(last().on(TimerEvent.Resume).also { advance(60_000) })
            add(last().on(TimerEvent.DeadlineReached).also { advance(3_210) })
            add(last().on(TimerEvent.Poured).on(TimerEvent.SkipStep))
        }

        states.forEach { state ->
            assertThat(ui(state).display(now.elapsedMs)).isEqualTo(BrewTimerEngine.display(state, now))
        }
        assertThat(states.map { (it as TimerState.Active).phase }).containsExactly(
            TimerPhase.READY,
            TimerPhase.RUNNING,
            TimerPhase.PAUSED,
            TimerPhase.RUNNING,
            TimerPhase.ALERTING,
            TimerPhase.FINISHED,
        ).inOrder()
    }

    @Test
    fun `READY shows the step, its kettle temperature and its pre-alert`() {
        val ui = ui(ready.on(TimerEvent.Start).on(TimerEvent.EndStepNow))

        assertThat(ui.phase).isEqualTo(TimerPhase.READY)
        assertThat(ui.stepIndex).isEqualTo(1)
        assertThat(ui.kettleTempC).isEqualTo(65)
        assertThat(ui.preAlertLeadSeconds).isEqualTo(10)
        assertThat(ui.teaName).isEqualTo("Gyokuro")
        assertThat(ui.alerting).isFalse()
    }

    @Test
    fun `RUNNING carries the deadline on both clocks and the pre-alert until it played`() {
        val running = ready.on(TimerEvent.Start)
        val ui = ui(running)

        assertThat(ui.deadlineElapsedMs).isEqualTo(now.elapsedMs + 60_000)
        assertThat(ui.deadlineWallMs).isEqualTo(now.wallMs + 60_000)
        assertThat(ui.preAlertAtElapsedMs).isEqualTo(now.elapsedMs + 50_000)
        assertThat(ui.preAlertPlayed).isFalse()

        advance(50_000)
        val played = ui(running.on(TimerEvent.PreAlertReached))
        assertThat(played.preAlertAtElapsedMs).isNull()
        assertThat(played.preAlertPlayed).isTrue()
    }

    @Test
    fun `Idle has no active state`() {
        assertThat(uiStateOf(TimerState.Idle, null, now)).isEqualTo(TimerUiState.Idle)
    }
}
