package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId

/** E1: changing the wall clock or the time zone during a step has no effect on the countdown. */
class ClockChangeTest {

    @Test
    fun `changing the wall clock mid-step does not change the remaining time or the deadline`() {
        val driver = sessionWith(infusion(120)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(30)
        val deadline = (driver.state as TimerState.Running).deadlineElapsedMs

        driver.clocks.wall -= 3 * 3_600_000L

        assertThat(driver.display()!!.remainingSeconds).isEqualTo(90)
        driver.clocks.advanceSeconds(90)
        driver.send(TimerEvent.DeadlineReached)
        val alerting = driver.state as TimerState.Alerting
        assertThat(alerting.deadlineElapsedMs).isEqualTo(deadline)
    }

    @Test
    fun `changing the time zone mid-step does not change the remaining time`() {
        val driver = sessionWith(infusion(120)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(30)

        driver.clocks.zoneId = ZoneId.of("America/New_York")

        assertThat(driver.display()!!.remainingSeconds).isEqualTo(90)
        driver.send(TimerEvent.Pause)
        assertThat((driver.state as TimerState.Paused).remainingMs).isEqualTo(90_000)
    }

    @Test
    fun `a wall clock jump during a pause does not change the remaining time or the paused total`() {
        val driver = sessionWith(infusion(120)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(30)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(10)
        driver.clocks.wall += 86_400_000L

        driver.send(TimerEvent.Resume)

        val running = driver.state as TimerState.Running
        assertThat(running.deadlineElapsedMs - driver.clocks.elapsed).isEqualTo(90_000)
        assertThat(running.pausedTotalMs).isEqualTo(10_000)
    }

    @Test
    fun `the actual time is measured on the monotonic clock even when the wall clock jumps`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(20)
        driver.clocks.wall -= 600_000L
        driver.clocks.advanceSeconds(20)

        driver.send(TimerEvent.EndStepNow)

        val record = (driver.state as TimerState.Finished).session.steps.single().record!!
        assertThat(record.actualSeconds).isEqualTo(40)
    }
}
