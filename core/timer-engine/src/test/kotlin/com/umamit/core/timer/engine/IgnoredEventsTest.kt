package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Idempotency (04 §2.4): an event the current state does not allow leaves the
 * state unchanged, returns no effects and is reported as ignored or rejected.
 */
class IgnoredEventsTest {

    private val allEvents: List<TimerEvent> = listOf(
        startSession(infusion(30)),
        TimerEvent.Start,
        TimerEvent.Pause,
        TimerEvent.Resume,
        TimerEvent.DeadlineReached,
        TimerEvent.AlertCompleted,
        TimerEvent.Poured,
        TimerEvent.EndStepNow,
        TimerEvent.SkipStep,
        TimerEvent.FinishSession,
        TimerEvent.Discard,
        TimerEvent.StartQuickTimer(60, globalPreAlertSeconds = 10),
        TimerEvent.AddStep,
        TimerEvent.EditSteps(listOf(infusion(60), infusion(25))),
        TimerEvent.Cancel,
        TimerEvent.QuickTimerTimeout,
        TimerEvent.PreAlertReached,
        TimerEvent.OvertimeTimeout,
        TimerEvent.IdleDemote,
        TimerEvent.MidnightTimeout,
    )

    /** What every active state of a brew session allows (`recover` is covered in RecoveryTest). */
    private val sessionEvents = arrayOf(
        TimerEvent.FinishSession,
        TimerEvent.Discard,
        TimerEvent.AddStep,
        TimerEvent.EditSteps(emptyList()),
    )

    private class Case(val name: String, val driver: () -> EngineDriver, val allowed: Set<Class<out TimerEvent>>)

    /**
     * Every state of 04 §3.1, plus Running past its deadline before the
     * deadline event, and states at the due time of each timeout.
     */
    private val cases = listOf(
        Case(
            "Idle",
            { EngineDriver() },
            setOf(TimerEvent.StartSession::class.java, TimerEvent.StartQuickTimer::class.java),
        ),
        Case(
            "Ready",
            { sessionWith(infusion(60), infusion(20)) },
            allowed(TimerEvent.Start, TimerEvent.SkipStep, *sessionEvents),
        ),
        Case(
            "Running before the deadline",
            { running().apply { clocks.advanceSeconds(10) } },
            allowed(TimerEvent.Pause, TimerEvent.EndStepNow, *sessionEvents),
        ),
        Case(
            "Running at the pre-alert",
            { running().apply { clocks.advance(50_000) } },
            allowed(TimerEvent.Pause, TimerEvent.EndStepNow, TimerEvent.PreAlertReached, *sessionEvents),
        ),
        Case(
            "Running past the deadline, pre-alert not processed",
            { running().apply { clocks.advance(60_300) } },
            allowed(TimerEvent.DeadlineReached, TimerEvent.EndStepNow, TimerEvent.PreAlertReached, *sessionEvents),
        ),
        Case(
            "Ready idle for 30 min",
            { sessionWith(infusion(60), infusion(20)).apply { clocks.advance(30 * 60_000L) } },
            allowed(TimerEvent.Start, TimerEvent.SkipStep, TimerEvent.IdleDemote, *sessionEvents),
        ),
        Case(
            "Paused past midnight",
            { running().apply { send(TimerEvent.Pause) }.apply { clocks.advance(14 * 3_600_000L) } },
            allowed(
                TimerEvent.Resume,
                TimerEvent.EndStepNow,
                TimerEvent.IdleDemote,
                TimerEvent.MidnightTimeout,
                *sessionEvents,
            ),
        ),
        Case(
            "Alerting at the overtime timeout",
            { alerting().apply { clocks.advance(10 * 60_000L) } },
            allowed(TimerEvent.AlertCompleted, TimerEvent.Poured, TimerEvent.OvertimeTimeout, *sessionEvents),
        ),
        Case(
            "Paused",
            { running().apply { send(TimerEvent.Pause) } },
            allowed(TimerEvent.Resume, TimerEvent.EndStepNow, *sessionEvents),
        ),
        Case(
            "Alerting",
            { alerting() },
            allowed(TimerEvent.AlertCompleted, TimerEvent.Poured, *sessionEvents),
        ),
        Case(
            "Alerting after the alert played",
            { alerting().apply { send(TimerEvent.AlertCompleted) } },
            allowed(TimerEvent.Poured, *sessionEvents),
        ),
        Case(
            "Finished",
            { sessionWith(infusion(60)).apply { send(TimerEvent.SkipStep) } },
            allowed(*sessionEvents),
        ),
    )

    @Test
    fun `events a state does not allow change nothing and return no effects`() {
        cases.forEach { case ->
            allEvents.filterNot { it.javaClass in case.allowed }.forEach { event ->
                val driver = case.driver()
                val before = driver.state

                val transition = BrewTimerEngine.reduce(before, event, driver.clocks.now())

                val label = "${case.name} + $event"
                assertWithMessage("$label: state").that(transition.state).isSameInstanceAs(before)
                assertWithMessage("$label: effects").that(transition.effects).isEmpty()
                assertWithMessage("$label: outcome").that(transition.outcome)
                    .isNotEqualTo(TransitionOutcome.Accepted)
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

                val label = "${case.name} + $event"
                assertWithMessage("$label: state").that(transition.state).isNotEqualTo(before)
                assertWithMessage("$label: effects").that(transition.effects).isNotEmpty()
                assertWithMessage("$label: outcome").that(transition.outcome).isEqualTo(TransitionOutcome.Accepted)
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
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)
    }

    @Test
    fun `a late deadline event after Abgegossen is ignored`() {
        val driver = alerting(infusion(60), infusion(20))
        driver.send(TimerEvent.Poured)
        val ready = driver.state

        assertThat(driver.send(TimerEvent.DeadlineReached)).isEmpty()
        assertThat(driver.state).isSameInstanceAs(ready)
    }

    @Test
    fun `a stale deadline event of step 1 is ignored while step 2 runs`() {
        val driver = alerting(infusion(60), infusion(120))
        driver.send(TimerEvent.Poured)
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(1) // step 1's deadline is long past, step 2's is not

        assertThat(driver.send(TimerEvent.DeadlineReached)).isEmpty()
        val running = driver.state as TimerState.Running
        assertThat(running.stepIndex).isEqualTo(1)
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
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)
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
    fun `pause 1 ms before the deadline, resume, and the deadline arrives 1 ms later`() {
        val driver = running()
        driver.advanceTo(driver.deadline - 1)
        driver.send(TimerEvent.Pause)
        assertThat((driver.state as TimerState.Paused).remainingMs).isEqualTo(1)
        driver.clocks.advanceSeconds(20)
        assertThat(driver.send(TimerEvent.DeadlineReached)).isEmpty()

        driver.send(TimerEvent.Resume)
        driver.clocks.advance(1)
        driver.send(TimerEvent.DeadlineReached)

        assertThat(driver.state).isInstanceOf(TimerState.Alerting::class.java)
        driver.send(TimerEvent.Poured)
        val record = driver.recordOf(0)
        assertThat(record.actualSeconds).isEqualTo(60)
        assertThat(record.pausedSeconds).isEqualTo(20)
        assertThat(record.overtimeSeconds).isEqualTo(0)
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
    fun `starting a second session while one is active is rejected (GEN-03, TMR-21)`() {
        val driver = running()
        val before = driver.state

        val effects = driver.send(
            TimerEvent.StartSession("session-2", listOf(infusion(90)), PRE_ALERT, longStepsOnly = false),
        )

        assertThat(driver.state).isSameInstanceAs(before)
        assertThat(effects).isEmpty()
        assertThat(driver.outcome)
            .isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(BREW)))
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
