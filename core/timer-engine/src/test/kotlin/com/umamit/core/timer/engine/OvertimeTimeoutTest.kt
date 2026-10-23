package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.RecordStep
import com.umamit.core.timer.engine.TimerEffect.ScheduleAlarms
import com.umamit.core.timer.engine.TimerEffect.StartAlert
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopAlert
import com.umamit.core.timer.engine.TimerEffect.StopForeground
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification
import org.junit.Test

/**
 * The overtime timeout (04 §3.2, §5.1): an alert left unacknowledged for
 * 10 min counts as poured on time, with the auto-ack flag.
 */
class OvertimeTimeoutTest {

    private val tenMinutes = 10 * 60_000L

    /** A session whose first 60 s step has alerted 4 s ago (started after a 20 s pause). */
    private fun alerting(vararg steps: PlannedStep = arrayOf(infusion(60), infusion(20))) = sessionWith(*steps).apply {
        send(TimerEvent.Start)
        clocks.advanceSeconds(20)
        send(TimerEvent.Pause)
        clocks.advanceSeconds(20)
        send(TimerEvent.Resume)
        clocks.advanceSeconds(steps.first().plannedSeconds - 20L + 4)
        send(TimerEvent.DeadlineReached)
    }

    @Test
    fun `the overtime timeout is due 10 min after the deadline while alerting`() {
        val driver = alerting()
        val alert = driver.state as TimerState.Alerting

        assertThat(BrewTimerEngine.dueTimes(alert))
            .isEqualTo(DueTimes(overtimeTimeoutAtElapsedMs = alert.deadlineElapsedMs + tenMinutes))
        driver.send(TimerEvent.AlertCompleted)
        assertThat(BrewTimerEngine.dueTimes(driver.state).overtimeTimeoutAtElapsedMs)
            .isEqualTo(alert.deadlineElapsedMs + tenMinutes)
    }

    @Test
    fun `the timeout records a pour on time with the auto-ack flag and waits for the next step`() {
        val driver = alerting()
        val alert = driver.state as TimerState.Alerting
        driver.advanceTo(alert.deadlineElapsedMs + tenMinutes - 1)
        assertThat(driver.send(TimerEvent.OvertimeTimeout)).isEmpty()
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)
        driver.advanceTo(alert.deadlineElapsedMs + tenMinutes)

        val effects = driver.send(TimerEvent.OvertimeTimeout)

        val ready = driver.state as TimerState.Ready
        val record = StepRecord(
            plannedSeconds = 60,
            actualSeconds = 60,
            pausedSeconds = 20,
            overtimeSeconds = 0,
            startedAtWallMs = START_WALL_MS,
            endedAtWallMs = alert.deadlineWallMs,
            skipped = false,
            overtimeAutoAck = true,
        )
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Accepted)
        assertThat(ready.stepIndex).isEqualTo(1)
        assertThat(driver.recordOf(0)).isEqualTo(record)
        assertThat(ready.session.lastInteractionWallMs).isEqualTo(alert.session.lastInteractionWallMs)
        assertThat(effects).containsExactly(
            RecordStep(SESSION_ID, 0, record),
            Persist,
            ScheduleAlarms(BrewTimerEngine.dueTimes(ready)),
            StopAlert,
            StartForeground,
            UpdateNotification,
        ).inOrder()
        assertThat(driver.send(TimerEvent.OvertimeTimeout)).isEmpty()
    }

    @Test
    fun `the timeout of the last step finishes the plan`() {
        val driver = alerting(infusion(60))
        driver.advanceTo((driver.state as TimerState.Alerting).deadlineElapsedMs + tenMinutes + 5_000)

        driver.send(TimerEvent.OvertimeTimeout)

        assertThat(driver.state).isInstanceOf(TimerState.Finished::class.java)
        assertThat(driver.recordOf(0).overtimeAutoAck).isTrue()
    }

    @Test
    fun `the timeout ends the quick timer`() {
        val driver = EngineDriver().apply {
            send(TimerEvent.StartQuickTimer(60, globalPreAlertSeconds = 10))
            send(TimerEvent.Start)
            clocks.advanceSeconds(60)
            send(TimerEvent.DeadlineReached)
            clocks.advance(tenMinutes)
        }

        val effects = driver.send(TimerEvent.OvertimeTimeout)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        assertThat(effects).containsExactly(Persist, NO_ALARMS, StopAlert, StopForeground).inOrder()
    }

    @Test
    fun `a pour, early end or finish 10 min or more after the deadline records the timeout's auto-ack`() {
        // (event, time after the deadline, auto-ack): live sessions agree with recovery (D-048 O6).
        val cases = listOf(
            Triple(TimerEvent.Poured, tenMinutes - 1, false),
            Triple(TimerEvent.Poured, tenMinutes, true),
            Triple(TimerEvent.FinishSession, tenMinutes, true),
        )
        cases.forEach { (event, afterDeadline, autoAck) ->
            val driver = alerting()
            val alert = driver.state as TimerState.Alerting
            driver.advanceTo(alert.deadlineElapsedMs + afterDeadline)

            val record = driver.send(event).filterIsInstance<RecordStep>().single().record

            val label = "$event at deadline + $afterDeadline ms"
            assertWithMessage(label).that(record.overtimeAutoAck).isEqualTo(autoAck)
            assertWithMessage(label).that(record.overtimeSeconds).isEqualTo(if (autoAck) 0 else 600)
            assertWithMessage(label).that(record.endedAtWallMs)
                .isEqualTo(if (autoAck) alert.deadlineWallMs else driver.clocks.wall)
        }
    }

    @Test
    fun `ending a running step 10 min or more after its deadline, before the deadline event, is an auto-ack`() {
        val driver = sessionWith(infusion(60), infusion(20)).apply { send(TimerEvent.Start) }
        val deadline = driver.deadline
        driver.advanceTo(deadline + tenMinutes)

        driver.send(TimerEvent.EndStepNow)

        assertThat(driver.recordOf(0).overtimeAutoAck).isTrue()
        assertThat(driver.recordOf(0).actualSeconds).isEqualTo(60)
    }

    @Test
    fun `a deadline event 10 min or more late applies the timeout instead of alerting`() {
        val driver = sessionWith(infusion(60), infusion(20)).apply { send(TimerEvent.Start) }
        driver.advanceTo(driver.deadline + tenMinutes)

        val effects = driver.send(TimerEvent.DeadlineReached)

        assertThat(driver.state).isInstanceOf(TimerState.Ready::class.java)
        assertThat(driver.recordOf(0).overtimeAutoAck).isTrue()
        assertThat(effects).doesNotContain(StartAlert)
    }
}
