package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.StartAlert
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopAlert
import com.umamit.core.timer.engine.TimerEffect.StopForeground
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification
import org.junit.Test

/**
 * The held quick alert (04 §3.3, §9.3; D-098, PO Q2, R2-Q2, S1): a quick timer whose deadline comes while the
 * brew alerts waits in ALERTING without a sound (`alertHold`), and rings when the controller releases it
 * (`alertRingStart`). Its overtime runs from its own deadline; its 10-min silent end counts from when it starts
 * ringing, and a held alert has none. The brew is never held.
 */
class HeldAlertTest {

    private val tenMinutes = 10 * 60_000L

    @Test
    fun `a quick deadline held while the brew alerts waits without a sound and without a silent end`() {
        val driver = runningQuick(60)
        driver.clocks.advanceSeconds(60)
        val deadline = START_ELAPSED_MS + 60_000

        val effects = driver.send(TimerEvent.AlertHold)

        val held = driver.state as TimerState.Alerting
        assertThat(held.held).isTrue()
        assertThat(held.alertPlayed).isFalse()
        assertThat(held.deadlineElapsedMs).isEqualTo(deadline)
        assertThat(effects).containsExactly(Persist, NO_ALARMS, StartForeground, UpdateNotification).inOrder()
        assertThat(BrewTimerEngine.dueTimes(held)).isEqualTo(DueTimes.NONE)
    }

    @Test
    fun `a held alert waits 12 min, then rings with its overtime from the deadline and ends 10 min later`() {
        val driver = runningQuick(60)
        driver.clocks.advanceSeconds(60)
        val deadline = START_ELAPSED_MS + 60_000
        driver.send(TimerEvent.AlertHold)
        driver.clocks.advanceSeconds(12 * 60)

        // Held for 12 min: no silent end came, nothing is due.
        assertThat(BrewTimerEngine.dueEvents(driver.state, driver.clocks.now())).isEmpty()
        assertThat(driver.send(TimerEvent.OvertimeTimeout)).isEmpty()

        val effects = driver.send(TimerEvent.AlertRingStart)

        val ringing = driver.state as TimerState.Alerting
        val ringStart = deadline + 12 * 60_000
        assertThat(ringing.held).isFalse()
        assertThat(ringing.ringingSinceElapsedMs).isEqualTo(ringStart)
        assertThat(ringing.ringingSinceWallMs).isEqualTo(START_WALL_MS + 60_000 + 12 * 60_000)
        assertThat(effects).containsExactly(
            Persist,
            TimerEffect.ScheduleAlarms(DueTimes(overtimeTimeoutAtElapsedMs = ringStart + tenMinutes)),
            StartForeground,
            StartAlert,
            UpdateNotification,
        ).inOrder()
        // „+12:00": the overtime counts from its own deadline.
        assertThat(driver.display()).isEqualTo(TimerDisplay(remainingSeconds = 0, overtimeSeconds = 12 * 60))

        driver.clocks.advance(tenMinutes - 1)
        assertThat(driver.send(TimerEvent.OvertimeTimeout)).isEmpty()
        driver.clocks.advance(1)
        assertThat(
            driver.send(TimerEvent.OvertimeTimeout),
        ).containsExactly(Persist, NO_ALARMS, StopAlert, StopForeground)
            .inOrder()
        assertThat(driver.state).isEqualTo(TimerState.Idle)
    }

    @Test
    fun `a quick alert that rings at its deadline rings since its deadline, as before v0,3,0`() {
        val driver = runningQuick(60)
        driver.clocks.advanceSeconds(61)

        driver.send(TimerEvent.DeadlineReached)

        val ringing = driver.state as TimerState.Alerting
        assertThat(ringing.ringingSinceElapsedMs).isEqualTo(START_ELAPSED_MS + 60_000)
        assertThat(BrewTimerEngine.dueTimes(ringing).overtimeTimeoutAtElapsedMs)
            .isEqualTo(START_ELAPSED_MS + 60_000 + tenMinutes)
    }

    @Test
    fun `a ringing quick alert preempted by the brew stops its sound and is held again, never reported played`() {
        val driver = runningQuick(60)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.clocks.advanceSeconds(20)
        driver.send(TimerEvent.AlertCompleted)

        val effects = driver.send(TimerEvent.AlertHold)

        val held = driver.state as TimerState.Alerting
        assertThat(held.held).isTrue()
        assertThat(held.alertPlayed).isFalse()
        assertThat(effects).containsExactly(Persist, NO_ALARMS, StopAlert, UpdateNotification).inOrder()
        // While held its player's report is not taken: a held alert is never reported completed.
        assertThat(driver.send(TimerEvent.AlertCompleted)).isEmpty()
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)
        // A second hold changes nothing.
        assertThat(driver.send(TimerEvent.AlertHold)).isEmpty()
    }

    @Test
    fun `Fertig on a held alert ends the quick timer without stopping a sound it never played`() {
        val driver = runningQuick(60)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.AlertHold)

        val effects = driver.send(TimerEvent.Poured)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        assertThat(effects).containsExactly(Persist, NO_ALARMS, StopForeground).inOrder()
    }

    @Test
    fun `a hold however late after the deadline holds instead of ending the quick timer silently`() {
        val driver = runningQuick(60)
        driver.clocks.advanceSeconds(60 + 25 * 60)

        driver.send(TimerEvent.AlertHold)

        assertThat((driver.state as TimerState.Alerting).held).isTrue()
    }

    @Test
    fun `a hold before the deadline, a ring start that is not held and both on Idle are ignored`() {
        val driver = runningQuick(60)
        driver.clocks.advanceSeconds(59)
        assertThat(driver.send(TimerEvent.AlertHold)).isEmpty()
        assertThat(driver.send(TimerEvent.AlertRingStart)).isEmpty()
        driver.clocks.advanceSeconds(1)
        driver.send(TimerEvent.DeadlineReached)
        assertThat(driver.send(TimerEvent.AlertRingStart)).isEmpty()
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)

        val idle = EngineDriver()
        assertThat(idle.send(TimerEvent.AlertHold)).isEmpty()
        assertThat(idle.send(TimerEvent.AlertRingStart)).isEmpty()
    }

    @Test
    fun `the brew is never held`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(60)
        assertThat(driver.send(TimerEvent.AlertHold)).isEmpty()
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)

        driver.send(TimerEvent.DeadlineReached)
        val alerting = driver.state as TimerState.Alerting
        assertThat(driver.send(TimerEvent.AlertHold)).isEmpty()
        assertThat(driver.state).isSameInstanceAs(alerting)
        // Its silent end stays 10 min after its deadline.
        assertThat(BrewTimerEngine.dueTimes(alerting))
            .isEqualTo(DueTimes(overtimeTimeoutAtElapsedMs = START_ELAPSED_MS + 60_000 + tenMinutes))
    }

    @Test
    fun `held and ringing alerts survive the snapshot round trip`() {
        val driver = runningQuick(60)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.AlertHold)
        val held = driver.state
        val heldRow = BrewTimerEngine.snapshot(held)!!.activeBrew
        assertThat(heldRow.alertRingingSinceElapsedMs).isNull()
        assertThat(heldRow.alertRingingSinceWallMs).isNull()
        assertThat(BrewTimerEngine.restore(BrewTimerEngine.snapshot(held))).isEqualTo(held)

        driver.clocks.advanceSeconds(90)
        driver.send(TimerEvent.AlertRingStart)
        val ringing = driver.state
        val ringingRow = BrewTimerEngine.snapshot(ringing)!!.activeBrew
        assertThat(ringingRow.alertRingingSinceElapsedMs).isEqualTo(START_ELAPSED_MS + 150_000)
        assertThat(ringingRow.alertRingingSinceWallMs).isEqualTo(START_WALL_MS + 150_000)
        assertThat(BrewTimerEngine.restore(BrewTimerEngine.snapshot(ringing))).isEqualTo(ringing)
    }

    @Test
    fun `an alerting brew row stored without its ringing start rings since its deadline`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        val alerting = driver.state as TimerState.Alerting
        val snapshot = BrewTimerEngine.snapshot(alerting)!!
        val withoutRinging = snapshot.copy(
            activeBrew = snapshot.activeBrew.copy(alertRingingSinceElapsedMs = null, alertRingingSinceWallMs = null),
        )

        val restored = BrewTimerEngine.restore(withoutRinging) as TimerState.Alerting

        assertThat(restored).isEqualTo(alerting)
        assertThat(restored.held).isFalse()
    }

    @Test
    fun `a stored held alert that was reported played, or with half a ringing start, is refused`() {
        val driver = runningQuick(60)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.AlertHold)
        val row = BrewTimerEngine.snapshot(driver.state)!!

        val played = row.copy(activeBrew = row.activeBrew.copy(alertPlayed = true))
        val halfRinging = row.copy(activeBrew = row.activeBrew.copy(alertRingingSinceElapsedMs = START_ELAPSED_MS))

        assertThrows { BrewTimerEngine.restore(played) }
        assertThrows { BrewTimerEngine.restore(halfRinging) }
    }

    @Test
    fun `recovery while the brew alerts holds a quick timer past its deadline, however late`() {
        val driver = runningQuick(60)
        driver.clocks.advanceSeconds(60 + 25 * 60)

        val effects = driver.send(TimerEvent.RecoverHeld)

        assertThat((driver.state as TimerState.Alerting).held).isTrue()
        assertThat(effects).containsExactly(Persist, NO_ALARMS, StartForeground, UpdateNotification).inOrder()
    }

    @Test
    fun `recovery while the brew alerts holds a ringing quick alert again without a sound of its own`() {
        val driver = runningQuick(60)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.send(TimerEvent.AlertCompleted)
        driver.clocks.advanceSeconds(30)

        val effects = driver.send(TimerEvent.RecoverHeld)

        val held = driver.state as TimerState.Alerting
        assertThat(held.held).isTrue()
        assertThat(held.alertPlayed).isFalse()
        assertThat(effects).containsExactly(Persist, NO_ALARMS, StartForeground, UpdateNotification).inOrder()
    }

    @Test
    fun `recovery while the brew alerts restores a quick timer before its deadline and a brew as usual`() {
        val quick = runningQuick(60)
        quick.clocks.advanceSeconds(30)
        val running = quick.state
        quick.send(TimerEvent.RecoverHeld)
        assertThat(quick.state).isEqualTo(running)

        val brew = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        brew.clocks.advanceSeconds(61)
        val effects = brew.send(TimerEvent.RecoverHeld)
        assertThat((brew.state as TimerState.Alerting).held).isFalse()
        assertThat(effects).contains(StartAlert)
    }

    @Test
    fun `a held alert recovers held, and one released counts its silent end from its ringing start`() {
        val driver = runningQuick(60)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.AlertHold)
        driver.clocks.advanceSeconds(30 * 60)

        // The process died while held: it stays held, silent and without an end, for the controller to release.
        val heldEffects = driver.send(TimerEvent.Recover)
        assertThat((driver.state as TimerState.Alerting).held).isTrue()
        assertThat(heldEffects).containsExactly(Persist, NO_ALARMS, StartForeground, UpdateNotification).inOrder()

        driver.send(TimerEvent.AlertRingStart)
        // Killed 3 min into its ring, after a reboot 5 min later: the alert replays, 2 min of it left.
        driver.clocks.advanceSeconds(3 * 60)
        driver.clocks.reboot(offMs = 4 * 60_000L, upMs = 60_000L)
        val replay = driver.send(TimerEvent.Recover)
        val ringing = driver.state as TimerState.Alerting
        assertThat(replay).contains(StartAlert)
        assertThat(BrewTimerEngine.dueTimes(ringing).overtimeTimeoutAtElapsedMs)
            .isEqualTo(driver.clocks.elapsed + 2 * 60_000L)

        driver.clocks.advanceSeconds(2 * 60)
        driver.send(TimerEvent.OvertimeTimeout)
        assertThat(driver.state).isEqualTo(TimerState.Idle)
    }

    private fun assertThrows(block: () -> Unit) {
        val thrown = runCatching(block).exceptionOrNull()
        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    }

    /** A quick timer of [seconds] with *Aus*, started at [START_ELAPSED_MS]. */
    private fun runningQuick(seconds: Int) = EngineDriver().apply {
        send(TimerEvent.StartQuickTimer(seconds, globalPreAlertSeconds = 0, preAlertSeconds = 0))
        send(TimerEvent.Start)
    }
}
