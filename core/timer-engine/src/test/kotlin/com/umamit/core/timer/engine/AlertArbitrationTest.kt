package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerSlot
import org.junit.Test

/**
 * The rules of 04 §9.3 (D-098) between the two slots: which events the quick slot gets ([AlertArbitration]) and
 * which sound the one player may play ([PlayerSound]).
 */
class AlertArbitrationTest {

    private val brewAlerting = alertingBrew()
    private val brewRunning = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }.state

    @Test
    fun `while the brew alerts, the quick deadline holds and its recovery recovers held`() {
        assertThat(AlertArbitration.quickEvent(TimerEvent.DeadlineReached, brewAlerting))
            .isEqualTo(TimerEvent.AlertHold)
        assertThat(AlertArbitration.quickEvent(TimerEvent.Recover, brewAlerting)).isEqualTo(TimerEvent.RecoverHeld)
        assertThat(AlertArbitration.quickEvent(TimerEvent.Pause, brewAlerting)).isEqualTo(TimerEvent.Pause)
        assertThat(AlertArbitration.quickEvent(TimerEvent.PreAlertReached, brewAlerting))
            .isEqualTo(TimerEvent.PreAlertReached)
    }

    @Test
    fun `while the brew does not alert, the quick slot gets its events as they are`() {
        listOf(TimerState.Idle, brewRunning).forEach { brew ->
            assertThat(AlertArbitration.quickEvent(TimerEvent.DeadlineReached, brew))
                .isEqualTo(TimerEvent.DeadlineReached)
            assertThat(AlertArbitration.quickEvent(TimerEvent.Recover, brew)).isEqualTo(TimerEvent.Recover)
        }
    }

    @Test
    fun `a ringing quick alert is held while the brew alerts and a held one rings once it no longer does`() {
        val ringing = quickAt(held = false)
        val held = quickAt(held = true)

        assertThat(AlertArbitration.settle(brewAlerting, ringing)).isEqualTo(TimerEvent.AlertHold)
        assertThat(AlertArbitration.settle(brewAlerting, held)).isNull()
        assertThat(AlertArbitration.settle(brewRunning, held)).isEqualTo(TimerEvent.AlertRingStart)
        assertThat(AlertArbitration.settle(TimerState.Idle, held)).isEqualTo(TimerEvent.AlertRingStart)
        assertThat(AlertArbitration.settle(brewRunning, ringing)).isNull()
        assertThat(AlertArbitration.settle(brewAlerting, TimerState.Idle)).isNull()
    }

    @Test
    fun `a pre-alert during the other slot's alert waits for its end, then plays with at least 1 s left`() {
        val sound = PlayerSound(preAlertRingMs = RING_MS)
        sound.alertStarted(TimerSlot.QUICK)

        assertThat(sound.preAlertDue(TimerSlot.BREW, deadlineElapsedMs = 10_000, nowElapsedMs = 0)).isFalse()
        assertThat(sound.release(5_000) { 10_000L }).isEmpty()

        sound.alertEnded(TimerSlot.QUICK)
        val played = sound.release(8_999) { 10_000L }

        assertThat(played).containsExactly(PlayerSound.Waiting(TimerSlot.BREW, 10_000))
        assertThat(sound.soundAt(8_999)).isEqualTo(PlayerSound.Sound.PreAlert(TimerSlot.BREW, 8_999 + RING_MS))
    }

    @Test
    fun `a waiting pre-alert with less than 1 s left, or whose step no longer counts down, is consumed silently`() {
        val late = PlayerSound(RING_MS).apply {
            alertStarted(TimerSlot.QUICK)
            preAlertDue(TimerSlot.BREW, deadlineElapsedMs = 10_000, nowElapsedMs = 0)
            alertEnded(TimerSlot.QUICK)
        }
        assertThat(late.release(9_001) { 10_000L }).isEmpty()
        assertThat(late.waitingPreAlerts).isEmpty()
        assertThat(late.soundAt(9_001)).isNull()

        val paused = PlayerSound(RING_MS).apply {
            alertStarted(TimerSlot.QUICK)
            preAlertDue(TimerSlot.BREW, deadlineElapsedMs = 10_000, nowElapsedMs = 0)
        }
        // The brew step was paused meanwhile: its pre-alert never comes back (at most once).
        assertThat(paused.release(1_000) { null }).isEmpty()
        assertThat(paused.waitingPreAlerts).isEmpty()
    }

    @Test
    fun `a pre-alert during the other slot's pre-alert waits until it has rung out`() {
        val sound = PlayerSound(RING_MS)
        assertThat(sound.preAlertDue(TimerSlot.QUICK, deadlineElapsedMs = 20_000, nowElapsedMs = 0)).isTrue()

        assertThat(sound.preAlertDue(TimerSlot.BREW, deadlineElapsedMs = 15_000, nowElapsedMs = 1_000)).isFalse()
        assertThat(sound.nextReleaseAtElapsedMs()).isEqualTo(RING_MS)
        assertThat(sound.release(RING_MS - 1) { 15_000L }).isEmpty()
        assertThat(sound.release(RING_MS) { 15_000L }).containsExactly(PlayerSound.Waiting(TimerSlot.BREW, 15_000))
        assertThat(sound.nextReleaseAtElapsedMs()).isNull()
    }

    @Test
    fun `an alert takes the player from the other slot's pre-alert, and a waiting pre-alert of its slot goes`() {
        val sound = PlayerSound(RING_MS)
        sound.preAlertDue(TimerSlot.QUICK, deadlineElapsedMs = 20_000, nowElapsedMs = 0)
        sound.preAlertDue(TimerSlot.BREW, deadlineElapsedMs = 2_000, nowElapsedMs = 500)

        sound.alertStarted(TimerSlot.BREW)

        assertThat(sound.soundAt(1_000)).isEqualTo(PlayerSound.Sound.Alert(TimerSlot.BREW))
        assertThat(sound.waitingPreAlerts).isEmpty()
    }

    @Test
    fun `a stop of one slot never silences the other slot's alert or pre-alert`() {
        val sound = PlayerSound(RING_MS)
        sound.alertStarted(TimerSlot.BREW)
        assertThat(sound.mayStop(TimerSlot.QUICK, nowElapsedMs = 0)).isFalse()
        assertThat(sound.soundAt(0)).isEqualTo(PlayerSound.Sound.Alert(TimerSlot.BREW))
        assertThat(sound.mayStop(TimerSlot.BREW, nowElapsedMs = 0)).isTrue()
        assertThat(sound.soundAt(0)).isNull()

        sound.preAlertDue(TimerSlot.BREW, deadlineElapsedMs = 30_000, nowElapsedMs = 0)
        assertThat(sound.mayStop(TimerSlot.QUICK, nowElapsedMs = 1_000)).isFalse()
        assertThat(sound.mayStop(TimerSlot.QUICK, nowElapsedMs = RING_MS)).isTrue()
    }

    private fun alertingBrew(): TimerState = sessionWith(infusion(60)).apply {
        send(TimerEvent.Start)
        clocks.advanceSeconds(60)
        send(TimerEvent.DeadlineReached)
    }.state

    private fun quickAt(held: Boolean): TimerState = EngineDriver().apply {
        send(TimerEvent.StartQuickTimer(60, globalPreAlertSeconds = 0))
        send(TimerEvent.Start)
        clocks.advanceSeconds(60)
        send(if (held) TimerEvent.AlertHold else TimerEvent.DeadlineReached)
    }.state

    private companion object {
        const val RING_MS = 3_450L
    }
}
