package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.umamit.core.timer.engine.TimerEffect.CancelAlarms
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.RecordStep
import com.umamit.core.timer.engine.TimerEffect.ScheduleAlarms
import com.umamit.core.timer.engine.TimerEffect.StartAlert
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopAlert
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification
import org.junit.Assert.assertThrows
import org.junit.Test

/** The step lifecycle of 04 §3.2 for a multi-step session. */
class StepLifecycleTest {

    @Test
    fun `startSession creates the session plan and waits in Ready for the first step`() {
        val driver = EngineDriver()

        val effects = driver.send(startSession(rinse(10), infusion(60), infusion(20)))

        val ready = driver.state as TimerState.Ready
        assertThat(ready.stepIndex).isEqualTo(0)
        assertThat(ready.readySinceWallMs).isEqualTo(START_WALL_MS)
        assertThat(ready.session.sessionId).isEqualTo(SESSION_ID)
        assertThat(ready.session.steps.map { it.status })
            .containsExactly(StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING).inOrder()
        assertThat(ready.session.steps.map { it.plannedSeconds }).containsExactly(10, 60, 20).inOrder()
        assertThat(effects).containsExactly(Persist, StartForeground, UpdateNotification).inOrder()
    }

    @Test
    fun `a session needs at least one step and every step a positive duration`() {
        assertThrows(IllegalArgumentException::class.java) { TimerEvent.StartSession(SESSION_ID, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { infusion(0) }
    }

    @Test
    fun `start counts down from the planned duration with a deadline on the monotonic clock`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.clocks.advanceSeconds(30)

        val effects = driver.send(TimerEvent.Start)

        val running = driver.state as TimerState.Running
        assertThat(running.stepIndex).isEqualTo(0)
        assertThat(running.deadlineElapsedMs).isEqualTo(START_ELAPSED_MS + 30_000 + 60_000)
        assertThat(running.deadlineWallMs).isEqualTo(START_WALL_MS + 30_000 + 60_000)
        assertThat(running.stepStartedWallMs).isEqualTo(START_WALL_MS + 30_000)
        assertThat(running.pausedTotalMs).isEqualTo(0)
        assertThat(running.session.steps[0].status).isEqualTo(StepStatus.RUNNING)
        assertThat(effects).containsExactly(
            Persist,
            ScheduleAlarms(deadlineElapsedMs = running.deadlineElapsedMs, preAlertAtElapsedMs = null),
            UpdateNotification,
        ).inOrder()
    }

    @Test
    fun `pause freezes the remaining time and cancels the alarms`() {
        val driver = sessionWith(infusion(60))
        driver.send(TimerEvent.Start)
        driver.clocks.advance(25_400)

        val effects = driver.send(TimerEvent.Pause)

        val paused = driver.state as TimerState.Paused
        assertThat(paused.remainingMs).isEqualTo(34_600)
        assertThat(paused.pausedSinceElapsedMs).isEqualTo(START_ELAPSED_MS + 25_400)
        assertThat(paused.pausedSinceWallMs).isEqualTo(START_WALL_MS + 25_400)
        assertThat(effects).containsExactly(Persist, CancelAlarms, UpdateNotification).inOrder()

        driver.clocks.advanceSeconds(600)
        assertThat((driver.state as TimerState.Paused).remainingMs).isEqualTo(34_600)
    }

    @Test
    fun `resume restores the remaining time as a new deadline and adds the pause to the paused total`() {
        val driver = sessionWith(infusion(60))
        driver.send(TimerEvent.Start)
        driver.clocks.advance(25_400)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(90)

        val effects = driver.send(TimerEvent.Resume)

        val running = driver.state as TimerState.Running
        val now = START_ELAPSED_MS + 25_400 + 90_000
        assertThat(running.deadlineElapsedMs).isEqualTo(now + 34_600)
        assertThat(running.deadlineWallMs).isEqualTo(START_WALL_MS + 25_400 + 90_000 + 34_600)
        assertThat(running.pausedTotalMs).isEqualTo(90_000)
        assertThat(running.stepStartedWallMs).isEqualTo(START_WALL_MS)
        assertThat(effects).containsExactly(
            Persist,
            ScheduleAlarms(deadlineElapsedMs = now + 34_600, preAlertAtElapsedMs = null),
            UpdateNotification,
        ).inOrder()
    }

    @Test
    fun `the deadline switches the step to Alerting and starts the alert`() {
        val driver = sessionWith(infusion(60))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)

        val effects = driver.send(TimerEvent.DeadlineReached)

        val alerting = driver.state as TimerState.Alerting
        assertThat(alerting.deadlineElapsedMs).isEqualTo(START_ELAPSED_MS + 60_000)
        assertThat(alerting.deadlineWallMs).isEqualTo(START_WALL_MS + 60_000)
        assertThat(alerting.session.steps[0].status).isEqualTo(StepStatus.RUNNING)
        assertThat(effects).containsExactly(Persist, StartAlert, UpdateNotification).inOrder()
    }

    @Test
    fun `poured records the step, stops the alert and waits in Ready for the next step`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.clocks.advanceSeconds(3)

        val effects = driver.send(TimerEvent.Poured)

        val ready = driver.state as TimerState.Ready
        assertThat(ready.stepIndex).isEqualTo(1)
        assertThat(ready.readySinceWallMs).isEqualTo(START_WALL_MS + 63_000)
        assertThat(ready.session.steps.map { it.status }).containsExactly(StepStatus.DONE, StepStatus.PENDING).inOrder()
        val record = ready.session.steps[0].record!!
        assertThat(effects).containsExactly(
            RecordStep(SESSION_ID, 0, record),
            Persist,
            StopAlert,
            UpdateNotification,
        ).inOrder()
    }

    @Test
    fun `poured on the last step finishes the plan`() {
        val driver = sessionWith(infusion(60))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)

        driver.send(TimerEvent.Poured)

        val finished = driver.state as TimerState.Finished
        assertThat(finished.readySinceWallMs).isEqualTo(START_WALL_MS + 60_000)
        assertThat(finished.session.steps.single().status).isEqualTo(StepStatus.DONE)
        assertThat(driver.effects.first()).isInstanceOf(RecordStep::class.java)
    }

    @Test
    fun `ending a running step now records it without an alert and moves to the next step`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(40)

        val effects = driver.send(TimerEvent.EndStepNow)

        val ready = driver.state as TimerState.Ready
        assertThat(ready.stepIndex).isEqualTo(1)
        val record = ready.session.steps[0].record!!
        assertThat(effects).containsExactly(
            RecordStep(SESSION_ID, 0, record),
            Persist,
            CancelAlarms,
            UpdateNotification,
        ).inOrder()
    }

    @Test
    fun `ending a paused step now records it and finishes after the last step`() {
        val driver = sessionWith(infusion(60))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(10)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(10)

        val effects = driver.send(TimerEvent.EndStepNow)

        val finished = driver.state as TimerState.Finished
        val record = finished.session.steps.single().record!!
        assertThat(effects).containsExactly(RecordStep(SESSION_ID, 0, record), Persist, UpdateNotification).inOrder()
    }

    @Test
    fun `skipping a step marks it skipped and waits for the next one`() {
        val driver = sessionWith(rinse(10), infusion(60))

        val effects = driver.send(TimerEvent.SkipStep)

        val ready = driver.state as TimerState.Ready
        assertThat(ready.stepIndex).isEqualTo(1)
        assertThat(ready.session.steps[0].status).isEqualTo(StepStatus.SKIPPED)
        val record = ready.session.steps[0].record!!
        assertThat(effects).containsExactly(RecordStep(SESSION_ID, 0, record), Persist, UpdateNotification).inOrder()
    }

    @Test
    fun `skipping the last step finishes the plan`() {
        val driver = sessionWith(infusion(60))

        driver.send(TimerEvent.SkipStep)

        assertThat(driver.state).isInstanceOf(TimerState.Finished::class.java)
    }

    @Test
    fun `rinse, whisk and infusion steps all go through the same lifecycle`() {
        val driver = sessionWith(rinse(10), whisk(15), infusion(60))

        listOf(10L, 15L, 60L).forEach { seconds ->
            driver.send(TimerEvent.Start)
            assertThat(driver.state).isInstanceOf(TimerState.Running::class.java)
            driver.clocks.advanceSeconds(seconds)
            driver.send(TimerEvent.DeadlineReached)
            assertThat(driver.state).isInstanceOf(TimerState.Alerting::class.java)
            driver.send(TimerEvent.Poured)
        }

        val finished = driver.state as TimerState.Finished
        assertThat(finished.session.steps.map { it.status })
            .containsExactly(StepStatus.DONE, StepStatus.DONE, StepStatus.DONE).inOrder()
    }

    @Test
    fun `every user event counts as an interaction, the deadline does not`() {
        val driver = sessionWith(infusion(60), infusion(20))
        val interactions = listOf(TimerEvent.Start, TimerEvent.Pause, TimerEvent.Resume)
        interactions.forEach { event ->
            driver.clocks.advanceSeconds(5)
            driver.send(event)
            assertThat((driver.state as TimerState.Active).session.lastInteractionWallMs)
                .isEqualTo(driver.clocks.wall)
        }
        val lastInteraction = driver.clocks.wall
        driver.clocks.advanceSeconds(60)

        driver.send(TimerEvent.DeadlineReached)

        assertThat((driver.state as TimerState.Alerting).session.lastInteractionWallMs).isEqualTo(lastInteraction)
        driver.clocks.advanceSeconds(5)
        driver.send(TimerEvent.Poured)
        assertThat((driver.state as TimerState.Ready).session.lastInteractionWallMs).isEqualTo(driver.clocks.wall)
        driver.clocks.advanceSeconds(5)
        driver.send(TimerEvent.SkipStep)
        assertThat((driver.state as TimerState.Finished).session.lastInteractionWallMs).isEqualTo(driver.clocks.wall)
    }
}
