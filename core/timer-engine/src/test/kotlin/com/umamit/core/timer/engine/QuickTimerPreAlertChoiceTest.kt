package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The quick timer's *Vorwarnung* choice from SCR-07 (TMR-22, 04 §5, §10; D-097 point 5, #116): null *Automatisch*
 * with B = the global setting, 0 *Aus*, or an explicit 5–30 s as set, with the skip rules of 04 §5.
 */
class QuickTimerPreAlertChoiceTest {

    /** A quick timer of [seconds] with the global *Vorwarnung* [base] and the choice [choice]; the lead it gets. */
    private data class Case(val seconds: Int, val base: Int, val choice: Int?, val lead: Int?)

    private val cases = listOf(
        // *Automatisch*: min(B, 5 · ⌊d / 15⌋) (TMR-23).
        Case(120, base = 10, choice = null, lead = 10),
        Case(30, base = 10, choice = null, lead = 10),
        Case(20, base = 10, choice = null, lead = 5),
        Case(120, base = 0, choice = null, lead = null),
        // *Aus*, whatever B.
        Case(120, base = 10, choice = 0, lead = null),
        Case(120, base = 30, choice = 0, lead = null),
        // An explicit value as set, never capped by B or the automatic third (D-096).
        Case(120, base = 10, choice = 20, lead = 20),
        Case(120, base = 0, choice = 20, lead = 20),
        Case(30, base = 10, choice = 25, lead = 25),
        Case(60, base = 10, choice = 5, lead = 5),
        // Too short: d < N + 5 s (E7).
        Case(20, base = 10, choice = 20, lead = null),
        Case(24, base = 10, choice = 20, lead = null),
        Case(25, base = 10, choice = 20, lead = 20),
        // A long step never has one (TMR-18).
        Case(3_600, base = 10, choice = 20, lead = null),
        Case(3_599, base = 10, choice = 30, lead = 30),
        Case(600, base = 10, choice = 20, lead = 20), // a hot step of 10–59 min is short (D-098)
        Case(599, base = 10, choice = 30, lead = 30),
    )

    @Test
    fun `the quick timer gets the lead of its choice, waiting and running`() {
        cases.forEach { case ->
            val (seconds, base, choice) = case
            val lead = case.lead
            val label = "quick timer of $seconds s, B = $base, choice $choice"
            val driver = EngineDriver()

            driver.send(TimerEvent.StartQuickTimer(seconds, globalPreAlertSeconds = base, preAlertSeconds = choice))

            assertWithMessage(label).that(driver.outcome).isEqualTo(TransitionOutcome.Accepted)
            assertWithMessage("$label: ready").that(BrewTimerEngine.preAlertLeadSeconds(driver.state)).isEqualTo(lead)
            driver.send(TimerEvent.Start)
            assertWithMessage("$label: running").that(BrewTimerEngine.preAlertLeadSeconds(driver.state))
                .isEqualTo(lead)
            assertWithMessage("$label: due").that(BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs)
                .isEqualTo(lead?.let { driver.deadline - it * 1_000L })
        }
    }

    @Test
    fun `SCR-07 shows before Start the lead the quick timer will get`() {
        cases.forEach { case ->
            val (seconds, base, choice) = case
            assertWithMessage("quick timer of $seconds s, B = $base, choice $choice")
                .that(BrewTimerEngine.quickTimerPreAlertLeadSeconds(seconds, base, choice)).isEqualTo(case.lead)
        }
    }

    @Test
    fun `after process death the restored quick timer keeps its choice, and its tick plays once`() {
        // (choice, expected lead) for a 2:00 quick timer with B = 10 s.
        listOf(null to 10, 0 to null, 20 to 20, 30 to 30).forEach { (choice, lead) ->
            val label = "choice $choice"
            val before = EngineDriver().apply {
                send(TimerEvent.StartQuickTimer(120, globalPreAlertSeconds = 10, choice))
                send(TimerEvent.Start)
            }
            val deadline = before.deadline
            val stored = BrewTimerEngine.snapshot(before.state)
            before.clocks.advanceSeconds(60)

            // A new process: the state comes back from its snapshot only (04 §7.2).
            val after = EngineDriver(before.clocks, BrewTimerEngine.restore(stored))
            after.send(TimerEvent.Recover)

            assertWithMessage(label).that(after.state).isEqualTo(before.state)
            assertWithMessage(label).that(BrewTimerEngine.preAlertLeadSeconds(after.state)).isEqualTo(lead)
            assertWithMessage(label).that(BrewTimerEngine.dueTimes(after.state).preAlertAtElapsedMs)
                .isEqualTo(lead?.let { deadline - it * 1_000L })
            if (lead != null) {
                after.advanceTo(deadline - lead * 1_000L)
                assertWithMessage(label).that(after.send(TimerEvent.PreAlertReached)).contains(TimerEffect.PlayPreAlert)
                assertWithMessage(label).that(after.send(TimerEvent.PreAlertReached)).isEmpty()
            }
        }
    }

    @Test
    fun `a resumed quick timer schedules its pre-alert from the choice and the new deadline (E6)`() {
        val driver = EngineDriver().apply {
            send(TimerEvent.StartQuickTimer(120, globalPreAlertSeconds = 10, preAlertSeconds = 25))
            send(TimerEvent.Start)
            clocks.advanceSeconds(30)
            send(TimerEvent.Pause)
            clocks.advanceSeconds(45)
        }

        driver.send(TimerEvent.Resume)

        assertWithMessage("due").that(BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs)
            .isEqualTo(driver.deadline - 25_000)
    }

    @Test
    fun `a choice other than Automatisch, Aus or 5 to 30 s is rejected as InvalidQuickTimer, never thrown`() {
        listOf(-5, -1, 1, 4, 31, 35, 60).forEach { choice ->
            val driver = EngineDriver()

            val effects = driver.send(TimerEvent.StartQuickTimer(120, globalPreAlertSeconds = 10, choice))

            assertWithMessage("choice $choice").that(effects).isEmpty()
            assertWithMessage("choice $choice").that(driver.state).isEqualTo(TimerState.Idle)
            assertWithMessage("choice $choice").that(driver.outcome)
                .isEqualTo(TransitionOutcome.Rejected(RejectionReason.InvalidQuickTimer))
            assertWithMessage(
                "choice $choice: no lead",
            ).that(BrewTimerEngine.quickTimerPreAlertLeadSeconds(120, 10, choice))
                .isNull()
        }
        listOf(null, 0, 5, 30).forEach { choice ->
            val driver = EngineDriver()
            driver.send(TimerEvent.StartQuickTimer(120, globalPreAlertSeconds = 10, choice))
            assertWithMessage("choice $choice").that(driver.outcome).isEqualTo(TransitionOutcome.Accepted)
        }
    }
}
