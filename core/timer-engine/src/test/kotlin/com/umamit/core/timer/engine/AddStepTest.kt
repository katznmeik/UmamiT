package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.StepKind
import org.junit.Test

/** *Weiterer Aufguss* (TMR-10): the default step and the `addStep` event. */
class AddStepTest {

    @Test
    fun `the added step takes the last temperature and the last duration plus 50 percent`() {
        val added = BrewTimerEngine.defaultAddedStep(listOf(infusion(60, tempC = 60), infusion(20, tempC = 65)))

        assertThat(added).isEqualTo(PlannedStep(StepKind.INFUSION, 30, tempC = 65))
    }

    @Test
    fun `below 10 min the duration is rounded to 5 s, half up`() {
        // (last duration, expected): 15 → 22.5 → 25; 20 → 30; 25 → 37.5 → 40; 22 → 33 → 35; 21 → 31.5 → 30
        val cases = mapOf(15 to 25, 20 to 30, 25 to 40, 22 to 35, 21 to 30, 395 to 595, 398 to 595)
        cases.forEach { (last, expected) ->
            assertThat(BrewTimerEngine.defaultAddedStep(listOf(infusion(last))).plannedSeconds)
                .isEqualTo(expected)
        }
    }

    @Test
    fun `from 10 min on the duration is rounded to whole minutes, half up`() {
        // 400 → 600 (10:00); 410 → 615 → 10 min; 420 → 630 → 11 min (half up); 1200 → 1800; 3600 → 5400
        val cases = mapOf(400 to 600, 410 to 600, 420 to 660, 1_200 to 1_800, 3_600 to 5_400)
        cases.forEach { (last, expected) ->
            assertThat(BrewTimerEngine.defaultAddedStep(listOf(infusion(last))).plannedSeconds)
                .isEqualTo(expected)
        }
    }

    @Test
    fun `the duration is clamped to 5 s and 24 h`() {
        assertThat(BrewTimerEngine.defaultAddedStep(listOf(infusion(1))).plannedSeconds).isEqualTo(5)
        assertThat(BrewTimerEngine.defaultAddedStep(listOf(infusion(2))).plannedSeconds).isEqualTo(5)
        assertThat(BrewTimerEngine.defaultAddedStep(listOf(infusion(5))).plannedSeconds).isEqualTo(10)
        assertThat(BrewTimerEngine.defaultAddedStep(listOf(infusion(86_400))).plannedSeconds).isEqualTo(86_400)
        assertThat(BrewTimerEngine.defaultAddedStep(listOf(infusion(57_600))).plannedSeconds).isEqualTo(86_400)
        assertThat(BrewTimerEngine.defaultAddedStep(listOf(infusion(57_599))).plannedSeconds).isEqualTo(86_400)
        assertThat(BrewTimerEngine.defaultAddedStep(listOf(infusion(50_000))).plannedSeconds).isEqualTo(75_000)
    }

    @Test
    fun `a trailing rinse is not the base, the last non-rinse step is`() {
        val added = BrewTimerEngine.defaultAddedStep(listOf(infusion(40, tempC = 70), rinse(10)))

        assertThat(added).isEqualTo(PlannedStep(StepKind.INFUSION, 60, tempC = 70))
    }

    @Test
    fun `a whisk session adds another whisk, a rinse-only plan an infusion`() {
        assertThat(BrewTimerEngine.defaultAddedStep(listOf(whisk(20))).kind).isEqualTo(StepKind.WHISK)
        assertThat(BrewTimerEngine.defaultAddedStep(listOf(rinse(10))))
            .isEqualTo(PlannedStep(StepKind.INFUSION, 15, tempC = 70))
    }

    @Test
    fun `an unknown temperature stays unknown`() {
        assertThat(BrewTimerEngine.defaultAddedStep(listOf(infusion(60, tempC = null))).tempC).isNull()
    }

    @Test
    fun `addStep from Finished appends the default step and waits in Ready for it`() {
        val driver = sessionWith(infusion(60), infusion(20)).apply {
            send(TimerEvent.SkipStep)
            send(TimerEvent.SkipStep)
        }
        driver.clocks.advanceSeconds(120)

        val effects = driver.send(TimerEvent.AddStep)

        val ready = driver.state as TimerState.Ready
        assertThat(ready.stepIndex).isEqualTo(2)
        assertThat(ready.readySinceWallMs).isEqualTo(START_WALL_MS + 120_000)
        assertThat(ready.session.steps[2]).isEqualTo(
            SessionStep(StepKind.INFUSION, 30, tempC = 60, StepStatus.PENDING, record = null),
        )
        assertThat(ready.session.lastInteractionWallMs).isEqualTo(START_WALL_MS + 120_000)
        assertThat(effects).containsExactly(
            TimerEffect.ReplacePendingSteps(SESSION_ID, fromIndex = 2, listOf(infusion(30))),
            TimerEffect.Persist,
            NO_ALARMS,
            TimerEffect.UpdateNotification,
        ).inOrder()
    }

    @Test
    fun `addStep while a step runs appends after the pending steps and keeps the running step`() {
        val driver = sessionWith(infusion(60), infusion(20)).apply { send(TimerEvent.Start) }
        val running = driver.state as TimerState.Running

        val effects = driver.send(TimerEvent.AddStep)

        val after = driver.state as TimerState.Running
        assertThat(after).isEqualTo(
            running.copy(session = running.session.copy(steps = running.session.steps + pending(infusion(30)))),
        )
        assertThat(effects.first())
            .isEqualTo(TimerEffect.ReplacePendingSteps(SESSION_ID, fromIndex = 1, listOf(infusion(20), infusion(30))))
    }

    private fun pending(step: PlannedStep) =
        SessionStep(step.kind, step.plannedSeconds, step.tempC, StepStatus.PENDING, null)
}
