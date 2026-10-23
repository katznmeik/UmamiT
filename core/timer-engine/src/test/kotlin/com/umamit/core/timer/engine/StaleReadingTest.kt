package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.common.ClockReading
import org.junit.Test

/**
 * A clock reading older than the state (S1 of the #27 slice review): a
 * controller that read the clocks before it took the event lock. 04 §4 forbids
 * that, but the engine still never produces a state that `restore` refuses,
 * so such a slip cannot lose the session and its alert at the next process
 * start (D-054).
 */
class StaleReadingTest {

    /** [this] reading moved back by [ms] on both clocks, on the same boot. */
    private fun ClockReading.earlierBy(ms: Long) = copy(elapsedMs = elapsedMs - ms, wallMs = wallMs - ms)

    private fun EngineDriver.sendRead(event: TimerEvent, now: ClockReading): TimerTransition =
        BrewTimerEngine.reduce(state, event, now).also { state = it.state }

    private fun assertRoundTrip(label: String, state: TimerState) {
        assertWithMessage("$label: snapshot round trip")
            .that(BrewTimerEngine.restore(BrewTimerEngine.snapshot(state))).isEqualTo(state)
    }

    @Test
    fun `a pause read before the start keeps at most the planned time`() {
        val driver = sessionWith(infusion(60))
        driver.clocks.advance(1_000)
        driver.send(TimerEvent.Start)

        driver.sendRead(TimerEvent.Pause, driver.clocks.now().earlierBy(500))

        val paused = driver.state as TimerState.Paused
        assertThat(paused.remainingMs).isEqualTo(60_000)
        assertRoundTrip("pause", paused)
    }

    @Test
    fun `a resume read before the pause adds no negative pause`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advance(10_000)
        driver.send(TimerEvent.Pause)

        driver.sendRead(TimerEvent.Resume, driver.clocks.now().earlierBy(1_000))

        val running = driver.state as TimerState.Running
        assertThat(running.pausedTotalMs).isEqualTo(0)
        assertRoundTrip("resume", running)
    }

    @Test
    fun `ending a paused step read before the pause records no negative pause`() {
        val driver = sessionWith(infusion(60), infusion(30)).apply { send(TimerEvent.Start) }
        driver.clocks.advance(10_000)
        driver.send(TimerEvent.Pause)
        driver.clocks.advance(4_000)
        driver.send(TimerEvent.Resume)
        driver.clocks.advance(5_000)
        driver.send(TimerEvent.Pause)

        driver.sendRead(TimerEvent.EndStepNow, driver.clocks.now().earlierBy(3_000))

        assertThat(driver.recordOf(0).pausedSeconds).isEqualTo(4)
        assertRoundTrip("end", driver.state)
    }

    @Test
    fun `every event read up to a minute too early still restores`() {
        // The events of a short session, every other one read older than the state.
        val events = listOf(
            TimerEvent.Start, TimerEvent.Pause, TimerEvent.Resume, TimerEvent.Pause, TimerEvent.Resume,
            TimerEvent.EndStepNow, TimerEvent.Start, TimerEvent.Pause, TimerEvent.EndStepNow,
        )
        listOf(1L, 999L, 9_000L, 60_000L).forEach { staleMs ->
            listOf(0, 1).forEach { parity ->
                val driver = sessionWith(infusion(60), infusion(30), infusion(45))
                events.forEachIndexed { n, event ->
                    driver.clocks.advance(7_000)
                    val now = driver.clocks.now().let { if (n % 2 == parity) it.earlierBy(staleMs) else it }
                    driver.sendRead(event, now)
                    assertRoundTrip("$staleMs ms stale, parity $parity, event $n ($event)", driver.state)
                }
            }
        }
    }
}
