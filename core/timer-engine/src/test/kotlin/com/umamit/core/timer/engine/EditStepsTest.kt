package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.ReplacePendingSteps
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification
import org.junit.Assert.assertThrows
import org.junit.Test

/** `editSteps` from the Schritte-Sheet (TMR-11, SCR-31): only pending steps and the session's pre-alert. */
class EditStepsTest {

    @Test
    fun `pending steps are replaced while the started and done steps stay`() {
        val driver = sessionWith(infusion(60), infusion(20), infusion(30)).apply {
            send(TimerEvent.Start)
            clocks.advanceSeconds(60)
            send(TimerEvent.DeadlineReached)
            send(TimerEvent.Poured)
            send(TimerEvent.Start)
        }
        val before = driver.state as TimerState.Running
        driver.clocks.advanceSeconds(5)

        val effects = driver.send(edit(infusion(60), infusion(20), infusion(45, tempC = 70), infusion(90)))

        val running = driver.state as TimerState.Running
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Accepted)
        assertThat(running.stepIndex).isEqualTo(1)
        assertThat(running.deadlineElapsedMs).isEqualTo(before.deadlineElapsedMs)
        assertThat(running.session.steps.map { it.status })
            .containsExactly(StepStatus.DONE, StepStatus.STARTED, StepStatus.PENDING, StepStatus.PENDING).inOrder()
        assertThat(running.session.steps.drop(2).map { it.planned })
            .containsExactly(infusion(45, tempC = 70), infusion(90)).inOrder()
        assertThat(running.session.steps[0].record).isEqualTo(before.session.steps[0].record)
        assertThat(running.session.lastInteractionWallMs).isEqualTo(driver.clocks.wall)
        assertThat(effects).containsExactly(
            ReplacePendingSteps(SESSION_ID, fromIndex = 2, listOf(infusion(45, tempC = 70), infusion(90))),
            Persist,
            BrewTimerEngine.dueTimes(running).let(TimerEffect::ScheduleAlarms),
            UpdateNotification,
        ).inOrder()
    }

    @Test
    fun `in Ready the waiting step itself is pending and can be changed`() {
        val driver = sessionWith(infusion(60), infusion(20))

        driver.send(edit(infusion(75), infusion(20)))

        val ready = driver.state as TimerState.Ready
        assertThat(ready.stepIndex).isEqualTo(0)
        assertThat(BrewTimerEngine.display(ready, driver.clocks.now())!!.remainingSeconds).isEqualTo(75)
    }

    @Test
    fun `Ready re-points to the next pending step when its step was removed`() {
        val driver = sessionWith(rinse(10), infusion(60), infusion(20)).apply { send(TimerEvent.SkipStep) }

        driver.send(edit(rinse(10), infusion(20)))

        val ready = driver.state as TimerState.Ready
        assertThat(ready.stepIndex).isEqualTo(1)
        assertThat(ready.session.steps[1].plannedSeconds).isEqualTo(20)
    }

    @Test
    fun `Ready goes to Finished when every pending step is removed`() {
        val driver = sessionWith(infusion(60), infusion(20)).apply { send(TimerEvent.SkipStep) }

        val effects = driver.send(edit(infusion(60)))

        assertThat(driver.state).isInstanceOf(TimerState.Finished::class.java)
        assertThat(effects.first()).isEqualTo(ReplacePendingSteps(SESSION_ID, fromIndex = 1, emptyList()))
    }

    @Test
    fun `Finished waits in Ready again when steps are added`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.SkipStep) }

        driver.send(edit(infusion(60), infusion(40)))

        val ready = driver.state as TimerState.Ready
        assertThat(ready.stepIndex).isEqualTo(1)
    }

    @Test
    fun `the pre-alert choice sets or clears the session override`() {
        val driver = sessionWith(infusion(60))
        val plan = listOf(infusion(60))

        driver.send(TimerEvent.EditSteps(plan, PreAlertChoice.Seconds(20)))
        assertThat(preAlert(driver)).isEqualTo(PreAlertSettings(sessionOverrideSeconds = 20, inheritedSeconds = 10))
        assertThat(preAlert(driver).effectiveSeconds).isEqualTo(20)

        driver.send(TimerEvent.EditSteps(plan, PreAlertChoice.Off))
        assertThat(preAlert(driver)).isEqualTo(PreAlertSettings(sessionOverrideSeconds = 0, inheritedSeconds = 10))
        assertThat(preAlert(driver).effectiveSeconds).isEqualTo(0)

        driver.send(TimerEvent.EditSteps(plan, PreAlertChoice.Inherit))
        assertThat(preAlert(driver)).isEqualTo(PreAlertSettings(sessionOverrideSeconds = null, inheritedSeconds = 10))
        assertThat(preAlert(driver).effectiveSeconds).isEqualTo(10)
    }

    @Test
    fun `the pre-alert choice of a session reads back from its settings`() {
        assertThat(PreAlertChoice.of(PreAlertSettings(null, 10))).isEqualTo(PreAlertChoice.Inherit)
        assertThat(PreAlertChoice.of(PreAlertSettings(0, 10))).isEqualTo(PreAlertChoice.Off)
        assertThat(PreAlertChoice.of(PreAlertSettings(15, 10))).isEqualTo(PreAlertChoice.Seconds(15))
        assertThrows(IllegalArgumentException::class.java) { PreAlertChoice.Seconds(0) }
    }

    @Test
    fun `the inherited pre-alert is the recipe value, else the global setting`() {
        assertThat(PreAlertSettings.inherited(recipeSeconds = 15, globalSeconds = 10))
            .isEqualTo(PreAlertSettings(sessionOverrideSeconds = null, inheritedSeconds = 15))
        assertThat(PreAlertSettings.inherited(recipeSeconds = 0, globalSeconds = 10).effectiveSeconds).isEqualTo(0)
        assertThat(PreAlertSettings.inherited(recipeSeconds = null, globalSeconds = 25).effectiveSeconds).isEqualTo(25)
    }

    @Test
    fun `changing a done step is rejected and changes nothing`() {
        val driver = sessionWith(infusion(60), infusion(20)).apply { send(TimerEvent.SkipStep) }

        assertRejected(driver, edit(infusion(90), infusion(20)), RejectionReason.StartedStepChanged)
        assertRejected(
            driver,
            edit(PlannedStep(StepKind.WHISK, 60, 60), infusion(20)),
            RejectionReason.StartedStepChanged,
        )
        assertRejected(driver, edit(infusion(60, tempC = 65), infusion(20)), RejectionReason.StartedStepChanged)
    }

    @Test
    fun `changing or removing the started step is rejected`() {
        val driver = sessionWith(infusion(60), infusion(20)).apply { send(TimerEvent.Start) }

        assertRejected(driver, edit(infusion(45), infusion(20)), RejectionReason.StartedStepChanged)
        assertRejected(driver, edit(infusion(20)), RejectionReason.StartedStepChanged)
    }

    @Test
    fun `a plan without a non-rinse step is rejected`() {
        val driver = sessionWith(rinse(10), infusion(60))

        assertRejected(driver, edit(rinse(10)), RejectionReason.NoBrewStepLeft)
        assertRejected(driver, edit(rinse(10), rinse(15)), RejectionReason.NoBrewStepLeft)
    }

    @Test
    fun `a done infusion keeps a plan of pending rinses valid`() {
        val driver = sessionWith(infusion(60), infusion(20)).apply { send(TimerEvent.SkipStep) }

        driver.send(edit(infusion(60), rinse(10)))

        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Accepted)
    }

    @Test
    fun `editSteps and addStep work in every active state of a brew session`() {
        val states = listOf(
            sessionWith(infusion(60), infusion(20)),
            sessionWith(infusion(60), infusion(20)).apply { send(TimerEvent.Start) },
            sessionWith(infusion(60), infusion(20)).apply {
                send(TimerEvent.Start)
                send(TimerEvent.Pause)
            },
            sessionWith(infusion(60), infusion(20)).apply {
                send(TimerEvent.Start)
                clocks.advanceSeconds(60)
                send(TimerEvent.DeadlineReached)
            },
        )
        states.forEach { driver ->
            val phase = (driver.state as TimerState.Active).phase
            driver.send(edit(infusion(60), infusion(25)))
            assertThat(driver.state.let { (it as TimerState.Active).phase }).isEqualTo(phase)
            driver.send(TimerEvent.AddStep)
            assertThat((driver.state as TimerState.Active).session.steps.map { it.plannedSeconds })
                .containsExactly(60, 25, 40).inOrder()
        }
    }

    private fun edit(vararg steps: PlannedStep) = TimerEvent.EditSteps(steps.toList(), PreAlertChoice.Inherit)

    private fun preAlert(driver: EngineDriver) = (driver.state as TimerState.Active).session.preAlert

    private fun assertRejected(driver: EngineDriver, event: TimerEvent, reason: RejectionReason) {
        val before = driver.state
        val effects = driver.send(event)
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Rejected(reason))
        assertThat(driver.state).isSameInstanceAs(before)
        assertThat(effects).isEmpty()
    }
}
