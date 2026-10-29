package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The pre-alert stored with each step from umamit.db v2 (TMR-23, PRF-03, 04 §5, D-093 point 3; #90): a step
 * carries *Automatisch* (null), *Aus* (0) or an explicit lead, and the engine uses the step's own value, else the
 * automatic rule min(B, 5 · ⌊d / 15⌋) with the session's frozen B.
 */
class StepPreAlertTest {

    /** The lead of each step of [driver]'s plan as the engine shows it when the step is current. */
    private fun leadsOfPlan(driver: EngineDriver): List<Int?> {
        val count = (driver.state as TimerState.Active).session.steps.size
        return (0 until count).map {
            BrewTimerEngine.preAlertLeadSeconds(driver.state).also { driver.send(TimerEvent.SkipStep) }
        }
    }

    @Test
    fun `each step's own value gives its lead, and Automatisch steps follow the automatic rule`() {
        // B = 10 s. (step, expected lead or null)
        val plan = listOf(
            infusion(60).withPreAlert(20) to 20,
            infusion(20).withPreAlert(null) to 5,
            infusion(30).withPreAlert(0) to null,
            infusion(19).withPreAlert(15) to null, // E7: shorter than N + 5 s
            infusion(20).withPreAlert(15) to 15,
            infusion(34).withPreAlert(30) to null,
            infusion(35).withPreAlert(30) to 30, // explicit values are not capped by B
            rinse(15).withPreAlert(10) to 10,
            infusion(600).withPreAlert(10) to null, // long step (TMR-18)
            infusion(14).withPreAlert(null) to null,
        )
        val driver = EngineDriver().apply {
            send(TimerEvent.StartSession(SESSION_ID, plan.map { it.first }, PRE_ALERT, longStepsOnly = false))
        }

        assertWithMessage("leads").that(leadsOfPlan(driver)).isEqualTo(plan.map { it.second })
    }

    @Test
    fun `the snapshot stores each step's value in its row and restores the same leads`() {
        val plan = listOf(infusion(60).withPreAlert(20), infusion(20), infusion(30).withPreAlert(0))
        val ready = EngineDriver().apply {
            send(TimerEvent.StartSession(SESSION_ID, plan, PRE_ALERT, longStepsOnly = false))
        }
        val running = EngineDriver(state = ready.state).apply { send(TimerEvent.Start) }

        val snapshot = checkNotNull(BrewTimerEngine.snapshot(running.state))

        assertWithMessage("rows").that(snapshot.steps.map { it.preAlertSeconds }).containsExactly(20, null, 0).inOrder()
        assertWithMessage("B").that(snapshot.activeBrew.preAlertInheritedSeconds).isEqualTo(10)
        assertWithMessage("running, restored").that(BrewTimerEngine.restore(snapshot)).isEqualTo(running.state)
        val restoredReady = BrewTimerEngine.restore(BrewTimerEngine.snapshot(ready.state))
        assertWithMessage("leads").that(leadsOfPlan(EngineDriver(state = restoredReady)))
            .containsExactly(20, 5, null).inOrder()
    }

    @Test
    fun `an edit sets each pending step's value and the running step's, which moves its tick`() {
        val driver = EngineDriver().apply {
            send(
                TimerEvent.StartSession(SESSION_ID, listOf(infusion(60), infusion(20), infusion(30)), PRE_ALERT, false),
            )
            send(TimerEvent.Start)
        }
        val deadline = driver.deadline
        driver.clocks.advanceSeconds(2)

        val effects = driver.send(
            TimerEvent.EditSteps(
                listOf(infusion(60).withPreAlert(15), infusion(20).withPreAlert(0), infusion(30).withPreAlert(null)),
            ),
        )

        assertWithMessage("outcome").that(driver.outcome).isEqualTo(TransitionOutcome.Accepted)
        val steps = (driver.state as TimerState.Active).session.steps
        assertWithMessage("values").that(steps.map { it.preAlertSeconds }).containsExactly(15, 0, null).inOrder()
        assertWithMessage("running lead").that(BrewTimerEngine.preAlertLeadSeconds(driver.state)).isEqualTo(15)
        assertWithMessage(
            "alarms",
        ).that(effects).contains(TimerEffect.ScheduleAlarms(DueTimes(deadline - 15_000, deadline)))
        // Only the pending suffix is replaced as data; the running step's row follows with the Persist.
        assertWithMessage("data").that(effects).contains(
            TimerEffect.ReplacePendingSteps(SESSION_ID, 1, listOf(infusion(20).withPreAlert(0), infusion(30))),
        )
    }

    @Test
    fun `an edit may not change an ended step, nor the running step's time or temperature`() {
        // Step 0 poured with its explicit 20 s, step 1 running.
        val start = listOf(infusion(60).withPreAlert(20), infusion(40), infusion(30))
        val cases = listOf(
            "the ended step's value" to listOf(infusion(60).withPreAlert(5), infusion(40), infusion(30)),
            "the ended step back to Automatisch" to listOf(infusion(60), infusion(40), infusion(30)),
            "the running step's time" to listOf(infusion(60).withPreAlert(20), infusion(45), infusion(30)),
            "the running step's temperature" to
                listOf(infusion(60).withPreAlert(20), infusion(40, tempC = 70), infusion(30)),
        )
        cases.forEach { (label, edit) ->
            val driver = EngineDriver().apply {
                send(TimerEvent.StartSession(SESSION_ID, start, PRE_ALERT, longStepsOnly = false))
                send(TimerEvent.Start)
                clocks.advanceSeconds(60)
                send(TimerEvent.DeadlineReached)
                send(TimerEvent.Poured)
                send(TimerEvent.Start)
            }
            val before = driver.state

            val effects = driver.send(TimerEvent.EditSteps(edit))

            assertWithMessage(label).that(driver.outcome)
                .isEqualTo(TransitionOutcome.Rejected(RejectionReason.StartedStepChanged))
            assertWithMessage(label).that(effects).isEmpty()
            assertWithMessage(label).that(driver.state).isEqualTo(before)
        }
    }

    @Test
    fun `a planned step's lead, as the Schritte-Sheet and the setup show it, is the one the session gives it`() {
        // B = 10 s. (step, expected lead or null), from TMR-23 and 04 §5 (#92).
        val plan = listOf(
            infusion(60) to 10,
            infusion(20) to 5,
            infusion(14) to null,
            infusion(30).withPreAlert(0) to null,
            infusion(19).withPreAlert(15) to null, // E7: shorter than N + 5 s
            infusion(35).withPreAlert(30) to 30,
            rinse(10).withPreAlert(5) to null, // a rinse under 15 s
            infusion(599) to 10,
            infusion(600) to null, // long step (TMR-18)
            infusion(600).withPreAlert(10) to null,
        )

        val leads = plan.map { (step, _) ->
            BrewTimerEngine.stepPreAlertLeadSeconds(step, inheritedSeconds = 10, longStepsOnly = false)
        }

        assertWithMessage("leads").that(leads).isEqualTo(plan.map { it.second })
        val driver = EngineDriver().apply {
            send(TimerEvent.StartSession(SESSION_ID, plan.map { it.first }, PRE_ALERT, longStepsOnly = false))
        }
        assertWithMessage("as in the session").that(leadsOfPlan(driver)).isEqualTo(leads)
    }

    @Test
    fun `in a cold or ice brew no planned step has a lead, and every step is a long step`() {
        val steps = listOf(infusion(60), infusion(60).withPreAlert(10), infusion(4 * 3_600))

        steps.forEach { step ->
            assertWithMessage("$step: lead")
                .that(BrewTimerEngine.stepPreAlertLeadSeconds(step, inheritedSeconds = 10, longStepsOnly = true))
                .isNull()
            assertWithMessage("$step: long").that(BrewTimerEngine.isLongStep(step, longStepsOnly = true)).isTrue()
        }
        assertWithMessage("9:59").that(BrewTimerEngine.isLongStep(infusion(599), longStepsOnly = false)).isFalse()
        assertWithMessage("10:00").that(BrewTimerEngine.isLongStep(infusion(600), longStepsOnly = false)).isTrue()
    }

    @Test
    fun `an added step starts as Automatisch, whatever the other steps have`() {
        val driver = EngineDriver().apply {
            send(TimerEvent.StartSession(SESSION_ID, listOf(infusion(60).withPreAlert(30)), PRE_ALERT, false))
            send(TimerEvent.AddStep)
        }

        val steps = (driver.state as TimerState.Active).session.steps

        assertWithMessage("values").that(steps.map { it.preAlertSeconds }).containsExactly(30, null).inOrder()
    }
}
