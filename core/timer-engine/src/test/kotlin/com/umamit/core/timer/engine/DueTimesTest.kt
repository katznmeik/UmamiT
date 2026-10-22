package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The due-time set (04 §6.2): what the controller schedules for each state. */
class DueTimesTest {

    @Test
    fun `only a running step has its deadline due`() {
        val driver = sessionWith(infusion(60), infusion(20))
        assertThat(BrewTimerEngine.dueTimes(TimerState.Idle)).isEqualTo(DueTimes.NONE)
        assertThat(BrewTimerEngine.dueTimes(driver.state)).isEqualTo(DueTimes.NONE)

        driver.send(TimerEvent.Start)
        assertThat(BrewTimerEngine.dueTimes(driver.state))
            .isEqualTo(DueTimes(preAlertAtElapsedMs = null, deadlineElapsedMs = START_ELAPSED_MS + 60_000))

        driver.send(TimerEvent.Pause)
        assertThat(BrewTimerEngine.dueTimes(driver.state)).isEqualTo(DueTimes.NONE)

        driver.send(TimerEvent.Resume)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        assertThat(BrewTimerEngine.dueTimes(driver.state)).isEqualTo(DueTimes.NONE)
    }

    @Test
    fun `an unplayed pre-alert is due, a played one is not (at most once)`() {
        val running = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }.state as TimerState.Running
        val withPreAlert = running.copy(preAlertAtElapsedMs = running.deadlineElapsedMs - 10_000)

        assertThat(BrewTimerEngine.dueTimes(withPreAlert).preAlertAtElapsedMs)
            .isEqualTo(running.deadlineElapsedMs - 10_000)
        assertThat(BrewTimerEngine.dueTimes(withPreAlert.copy(preAlertPlayed = true)).preAlertAtElapsedMs).isNull()
    }

    @Test
    fun `after an ignored early deadline event the deadline is still due`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advance(59_999)

        driver.send(TimerEvent.DeadlineReached)

        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)
        assertThat(BrewTimerEngine.dueTimes(driver.state).deadlineElapsedMs).isEqualTo(START_ELAPSED_MS + 60_000)
    }
}
