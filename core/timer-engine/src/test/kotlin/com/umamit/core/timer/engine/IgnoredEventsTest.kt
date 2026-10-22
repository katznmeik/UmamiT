package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Idempotency (04 §2.4): an event the current state does not allow leaves the
 * state unchanged and returns no effects.
 */
class IgnoredEventsTest {

    private val allEvents: List<TimerEvent> = listOf(
        startSession(infusion(30)),
        TimerEvent.Start,
        TimerEvent.Pause,
        TimerEvent.Resume,
        TimerEvent.DeadlineReached,
        TimerEvent.Poured,
        TimerEvent.EndStepNow,
        TimerEvent.SkipStep,
        TimerEvent.FinishSession,
        TimerEvent.Discard,
    )

    private class Case(val name: String, val driver: () -> EngineDriver, val allowed: Set<Class<out TimerEvent>>)

    /** Every state of 04 §3.1; Running is mid-step, before its deadline. */
    private val cases = listOf(
        Case("Idle", { EngineDriver() }, setOf(TimerEvent.StartSession::class.java)),
        Case(
            "Ready",
            { sessionWith(infusion(60), infusion(20)) },
            allowed(TimerEvent.Start, TimerEvent.SkipStep, TimerEvent.FinishSession, TimerEvent.Discard),
        ),
        Case(
            "Running",
            { running().apply { clocks.advanceSeconds(10) } },
            allowed(TimerEvent.Pause, TimerEvent.EndStepNow, TimerEvent.FinishSession, TimerEvent.Discard),
        ),
        Case(
            "Paused",
            { running().apply { send(TimerEvent.Pause) } },
            allowed(TimerEvent.Resume, TimerEvent.EndStepNow, TimerEvent.FinishSession, TimerEvent.Discard),
        ),
        Case(
            "Alerting",
            { alerting() },
            allowed(TimerEvent.Poured, TimerEvent.FinishSession, TimerEvent.Discard),
        ),
        Case(
            "Finished",
            { sessionWith(infusion(60)).apply { send(TimerEvent.SkipStep) } },
            allowed(TimerEvent.FinishSession, TimerEvent.Discard),
        ),
    )

    @Test
    fun `events a state does not allow change nothing and return no effects`() {
        cases.forEach { case ->
            allEvents.filterNot { it.javaClass in case.allowed }.forEach { event ->
                val driver = case.driver()
                val before = driver.state

                val transition = BrewTimerEngine.reduce(before, event, driver.clocks.now())

                assertWithMessage("${case.name} + $event: state").that(transition.state).isSameInstanceAs(before)
                assertWithMessage("${case.name} + $event: effects").that(transition.effects).isEmpty()
            }
        }
    }

    @Test
    fun `events a state allows change it and return effects`() {
        cases.forEach { case ->
            allEvents.filter { it.javaClass in case.allowed }.forEach { event ->
                val driver = case.driver()
                val before = driver.state

                val transition = BrewTimerEngine.reduce(before, event, driver.clocks.now())

                assertWithMessage("${case.name} + $event: state").that(transition.state).isNotEqualTo(before)
                assertWithMessage("${case.name} + $event: effects").that(transition.effects).isNotEmpty()
            }
        }
    }

    @Test
    fun `a second Abgegossen is ignored and never skips a step (E5)`() {
        val driver = alerting(infusion(60), infusion(20), infusion(30))
        driver.send(TimerEvent.Poured)
        val afterFirst = driver.state

        val effects = driver.send(TimerEvent.Poured)

        assertThat(driver.state).isSameInstanceAs(afterFirst)
        assertThat((driver.state as TimerState.Ready).stepIndex).isEqualTo(1)
        assertThat(effects).isEmpty()
    }

    @Test
    fun `a late deadline event after Abgegossen is ignored`() {
        val driver = alerting(infusion(60), infusion(20))
        driver.send(TimerEvent.Poured)
        val ready = driver.state

        val effects = driver.send(TimerEvent.DeadlineReached)

        assertThat(driver.state).isSameInstanceAs(ready)
        assertThat(effects).isEmpty()
    }

    @Test
    fun `a second deadline event while alerting is ignored`() {
        val driver = alerting()

        assertThat(driver.send(TimerEvent.DeadlineReached)).isEmpty()
        assertThat(driver.state).isInstanceOf(TimerState.Alerting::class.java)
    }

    @Test
    fun `a deadline event before the deadline is ignored`() {
        val driver = running()
        driver.clocks.advance(59_999)
        val before = driver.state

        assertThat(driver.send(TimerEvent.DeadlineReached)).isEmpty()
        assertThat(driver.state).isSameInstanceAs(before)
    }

    @Test
    fun `a deadline event from before a pause is ignored after resume`() {
        val driver = running()
        driver.clocks.advanceSeconds(30)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(40)
        driver.send(TimerEvent.Resume)
        // The original deadline (start + 60 s) has passed, the new one (now + 30 s) has not.

        assertThat(driver.send(TimerEvent.DeadlineReached)).isEmpty()
        assertThat(driver.state).isInstanceOf(TimerState.Running::class.java)
    }

    @Test
    fun `a deadline event late after the deadline is accepted`() {
        val driver = running()
        driver.clocks.advanceSeconds(75)

        driver.send(TimerEvent.DeadlineReached)

        assertThat(driver.state).isInstanceOf(TimerState.Alerting::class.java)
    }

    @Test
    fun `pause at or after the deadline is ignored so the alert is not lost`() {
        val driver = running()
        driver.clocks.advanceSeconds(60)
        val before = driver.state

        assertThat(driver.send(TimerEvent.Pause)).isEmpty()
        assertThat(driver.state).isSameInstanceAs(before)
    }

    @Test
    fun `starting a second session while one is active is ignored (GEN-03)`() {
        val driver = running()
        val before = driver.state

        val effects = driver.send(TimerEvent.StartSession("session-2", listOf(infusion(90))))

        assertThat(driver.state).isSameInstanceAs(before)
        assertThat(effects).isEmpty()
    }

    private fun allowed(vararg events: TimerEvent): Set<Class<out TimerEvent>> = events.map { it.javaClass }.toSet()

    private fun running(vararg steps: PlannedStep = arrayOf(infusion(60), infusion(20))): EngineDriver =
        sessionWith(*steps).apply { send(TimerEvent.Start) }

    private fun alerting(vararg steps: PlannedStep = arrayOf(infusion(60), infusion(20))): EngineDriver =
        running(*steps).apply {
            clocks.advanceSeconds(steps.first().plannedSeconds.toLong())
            send(TimerEvent.DeadlineReached)
        }
}
