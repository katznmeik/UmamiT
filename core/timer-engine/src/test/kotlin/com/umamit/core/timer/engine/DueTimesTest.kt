package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The due-time set (04 §6.2): what the controller schedules for each state. */
class DueTimesTest {

    @Test
    fun `only a running step has its deadline due`() {
        val driver = sessionWith(infusion(60), infusion(20))
        assertThat(BrewTimerEngine.dueTimes(TimerState.Idle)).isEqualTo(DueTimes.NONE)
        assertThat(BrewTimerEngine.dueTimes(driver.state).deadlineElapsedMs).isNull()

        driver.send(TimerEvent.Start)
        assertThat(BrewTimerEngine.dueTimes(driver.state))
            .isEqualTo(
                DueTimes(
                    preAlertAtElapsedMs = START_ELAPSED_MS + 50_000,
                    deadlineElapsedMs = START_ELAPSED_MS + 60_000,
                ),
            )

        driver.send(TimerEvent.Pause)
        assertThat(BrewTimerEngine.dueTimes(driver.state).deadlineElapsedMs).isNull()

        driver.send(TimerEvent.Resume)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        assertThat(BrewTimerEngine.dueTimes(driver.state).deadlineElapsedMs).isNull()
    }

    @Test
    fun `an unplayed pre-alert is due, a played one is not (at most once)`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        val deadline = driver.deadline

        assertThat(BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs).isEqualTo(deadline - 10_000)

        driver.advanceTo(deadline - 10_000)
        driver.send(TimerEvent.PreAlertReached)

        assertThat(BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs).isNull()
    }

    @Test
    fun `after an ignored early deadline event the deadline is still due`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advance(59_999)

        driver.send(TimerEvent.DeadlineReached)

        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)
        assertThat(BrewTimerEngine.dueTimes(driver.state).deadlineElapsedMs).isEqualTo(START_ELAPSED_MS + 60_000)
    }

    @Test
    fun `dueEvents lists the time-based events that have come, in the order the controller applies them`() {
        val running = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        val deadline = running.deadline
        assertThat(BrewTimerEngine.dueEvents(running.state, running.clocks.now())).isEmpty()

        running.advanceTo(deadline - 10_000)
        assertThat(BrewTimerEngine.dueEvents(running.state, running.clocks.now()))
            .containsExactly(TimerEvent.PreAlertReached)

        // After a doze both are due: the deadline first, so no tick sounds at the deadline.
        running.advanceTo(deadline + 3)
        assertThat(BrewTimerEngine.dueEvents(running.state, running.clocks.now()))
            .containsExactly(TimerEvent.DeadlineReached, TimerEvent.PreAlertReached).inOrder()

        running.send(TimerEvent.DeadlineReached)
        running.advanceTo(deadline + 10 * 60_000L)
        assertThat(BrewTimerEngine.dueEvents(running.state, running.clocks.now()))
            .containsExactly(TimerEvent.OvertimeTimeout)
    }

    @Test
    fun `dueEvents of a waiting session puts the timeout that ends it before idle demote`() {
        val waiting = sessionWith(infusion(60))
        assertThat(BrewTimerEngine.dueEvents(waiting.state, waiting.clocks.now())).isEmpty()

        waiting.clocks.advance(30 * 60_000L)
        assertThat(BrewTimerEngine.dueEvents(waiting.state, waiting.clocks.now()))
            .containsExactly(TimerEvent.IdleDemote)

        waiting.clocks.wall = MIDNIGHT_AFTER_START
        assertThat(BrewTimerEngine.dueEvents(waiting.state, waiting.clocks.now()))
            .containsExactly(TimerEvent.MidnightTimeout, TimerEvent.IdleDemote).inOrder()

        val quick = EngineDriver().apply { send(TimerEvent.StartQuickTimer(60, globalPreAlertSeconds = 10)) }
        quick.clocks.advance(30 * 60_000L)
        assertThat(BrewTimerEngine.dueEvents(quick.state, quick.clocks.now()))
            .containsExactly(TimerEvent.QuickTimerTimeout)
        assertThat(BrewTimerEngine.dueEvents(TimerState.Idle, quick.clocks.now())).isEmpty()
    }
}
