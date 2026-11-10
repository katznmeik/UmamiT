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

    @Test
    fun `of two ringing alerts the one that rang first keeps the sound and the other is held`() {
        val first = brewAt(seconds = 60, held = false)
        val later = brewAt(seconds = 90, held = false)

        assertThat(AlertArbitration.settle(mapOf(TimerSlot.BREW to later, TimerSlot.BREW_2 to first)))
            .isEqualTo(AlertArbitration.SlotEvent(TimerSlot.BREW, TimerEvent.AlertHold))
        assertThat(AlertArbitration.settle(mapOf(TimerSlot.BREW to first, TimerSlot.BREW_2 to later)))
            .isEqualTo(AlertArbitration.SlotEvent(TimerSlot.BREW_2, TimerEvent.AlertHold))
        // Rang at the same moment: the slot order decides (BREW, BREW_2, QUICK).
        assertThat(AlertArbitration.settle(mapOf(TimerSlot.BREW to first, TimerSlot.BREW_2 to first)))
            .isEqualTo(AlertArbitration.SlotEvent(TimerSlot.BREW_2, TimerEvent.AlertHold))
        assertThat(AlertArbitration.settle(mapOf(TimerSlot.BREW to first, TimerSlot.BREW_2 to held(90)))).isNull()
    }

    @Test
    fun `a brew never preempts a brew, so a brew deadline while another brew rings is held`() {
        val ringing = mapOf(TimerSlot.BREW to brewAt(seconds = 60, held = false))

        assertThat(AlertArbitration.eventFor(TimerSlot.BREW_2, TimerEvent.DeadlineReached, ringing))
            .isEqualTo(TimerEvent.AlertHold)
        assertThat(AlertArbitration.eventFor(TimerSlot.BREW_2, TimerEvent.Recover, ringing))
            .isEqualTo(TimerEvent.RecoverHeld)
        assertThat(AlertArbitration.eventFor(TimerSlot.BREW_2, TimerEvent.Poured, ringing)).isEqualTo(TimerEvent.Poured)
        // Its own alert never holds a slot.
        assertThat(AlertArbitration.eventFor(TimerSlot.BREW, TimerEvent.DeadlineReached, ringing))
            .isEqualTo(TimerEvent.DeadlineReached)
        // A held alert holds nothing.
        val held = mapOf(TimerSlot.BREW to held(60))
        assertThat(AlertArbitration.eventFor(TimerSlot.BREW_2, TimerEvent.DeadlineReached, held))
            .isEqualTo(TimerEvent.DeadlineReached)
    }

    @Test
    fun `a brew preempts a ringing quick alert, and a quick deadline is held while any brew rings`() {
        val quickRinging = quickAt(held = false)
        val brew2Ringing = brewAt(seconds = 90, held = false)

        assertThat(
            AlertArbitration.eventFor(
                TimerSlot.BREW_2,
                TimerEvent.DeadlineReached,
                mapOf(TimerSlot.QUICK to quickRinging),
            ),
        ).isEqualTo(TimerEvent.DeadlineReached)
        // The quick alert rang first, yet the brew keeps the sound.
        assertThat(AlertArbitration.settle(mapOf(TimerSlot.BREW_2 to brew2Ringing, TimerSlot.QUICK to quickRinging)))
            .isEqualTo(AlertArbitration.SlotEvent(TimerSlot.QUICK, TimerEvent.AlertHold))
        assertThat(
            AlertArbitration.eventFor(
                TimerSlot.QUICK,
                TimerEvent.DeadlineReached,
                mapOf(TimerSlot.BREW_2 to brew2Ringing),
            ),
        ).isEqualTo(TimerEvent.AlertHold)
    }

    @Test
    fun `a release goes to the held slot whose deadline came first, a brew before a quick timer at the same time`() {
        assertThat(AlertArbitration.settle(mapOf(TimerSlot.BREW to held(90), TimerSlot.BREW_2 to held(60))))
            .isEqualTo(AlertArbitration.SlotEvent(TimerSlot.BREW_2, TimerEvent.AlertRingStart))
        assertThat(
            AlertArbitration.settle(
                mapOf(
                    TimerSlot.BREW to TimerState.Idle,
                    TimerSlot.BREW_2 to held(90),
                    TimerSlot.QUICK to quickAt(true),
                ),
            ),
        ).isEqualTo(AlertArbitration.SlotEvent(TimerSlot.QUICK, TimerEvent.AlertRingStart))
        assertThat(AlertArbitration.settle(mapOf(TimerSlot.BREW_2 to held(60), TimerSlot.QUICK to quickAt(true))))
            .isEqualTo(AlertArbitration.SlotEvent(TimerSlot.BREW_2, TimerEvent.AlertRingStart))
        // While one rings, the held ones wait.
        assertThat(
            AlertArbitration.settle(
                mapOf(
                    TimerSlot.BREW to brewAt(90, held = false),
                    TimerSlot.BREW_2 to held(60),
                    TimerSlot.QUICK to quickAt(true),
                ),
            ),
        ).isNull()
    }

    @Test
    fun `an alert whose sound has ended holds nothing, and the held alert behind it rings (D-109)`() {
        val brewPlayed = played(brewAt(seconds = 60, held = false))
        val quickPlayed = played(quickAt(held = false))

        // A deadline behind an alert that no longer sounds rings at once, for a brew and the quick timer alike.
        listOf(TimerSlot.BREW_2, TimerSlot.QUICK).forEach { slot ->
            assertThat(AlertArbitration.eventFor(slot, TimerEvent.DeadlineReached, mapOf(TimerSlot.BREW to brewPlayed)))
                .isEqualTo(TimerEvent.DeadlineReached)
            assertThat(AlertArbitration.eventFor(slot, TimerEvent.Recover, mapOf(TimerSlot.BREW to brewPlayed)))
                .isEqualTo(TimerEvent.Recover)
        }
        assertThat(AlertArbitration.quickEvent(TimerEvent.DeadlineReached, brewPlayed))
            .isEqualTo(TimerEvent.DeadlineReached)
        // The keeper's sound ended: the held alert is released, the keeper stays alerting as it is.
        assertThat(AlertArbitration.settle(mapOf(TimerSlot.BREW to brewPlayed, TimerSlot.BREW_2 to held(90))))
            .isEqualTo(AlertArbitration.SlotEvent(TimerSlot.BREW_2, TimerEvent.AlertRingStart))
        assertThat(AlertArbitration.settle(brewPlayed, quickAt(held = true))).isEqualTo(TimerEvent.AlertRingStart)
        // One sounds, one has sounded: nothing to hold.
        assertThat(AlertArbitration.settle(mapOf(TimerSlot.BREW to brewPlayed, TimerSlot.BREW_2 to brewAt(90, false))))
            .isNull()
        assertThat(AlertArbitration.settle(mapOf(TimerSlot.BREW to brewAt(90, false), TimerSlot.QUICK to quickPlayed)))
            .isNull()
        assertThat(AlertArbitration.settle(brewPlayed, quickPlayed)).isNull()
    }

    @Test
    fun `a pre-alert waits for the sound of any other slot, and plays once that has ended`() {
        val sound = PlayerSound(RING_MS)
        sound.alertStarted(TimerSlot.BREW_2)

        assertThat(sound.preAlertDue(TimerSlot.BREW, deadlineElapsedMs = 20_000, nowElapsedMs = 0)).isFalse()
        assertThat(sound.preAlertDue(TimerSlot.QUICK, deadlineElapsedMs = 30_000, nowElapsedMs = 0)).isFalse()
        assertThat(sound.mayStop(TimerSlot.BREW, nowElapsedMs = 0)).isFalse()

        sound.alertEnded(TimerSlot.BREW_2)
        // One plays (the slot order first); the other waits for it to ring out.
        assertThat(sound.release(1_000) { if (it == TimerSlot.BREW) 20_000L else 30_000L })
            .containsExactly(PlayerSound.Waiting(TimerSlot.BREW, 20_000))
        assertThat(sound.waitingPreAlerts).containsExactly(PlayerSound.Waiting(TimerSlot.QUICK, 30_000))
        assertThat(sound.nextReleaseAtElapsedMs()).isEqualTo(1_000 + RING_MS)
        assertThat(sound.release(1_000 + RING_MS) { 30_000L })
            .containsExactly(PlayerSound.Waiting(TimerSlot.QUICK, 30_000))
    }

    /** A brew session's alert at the end of its one infusion of [seconds]: ringing since its deadline, or held. */
    private fun brewAt(seconds: Int, held: Boolean): TimerState = sessionWith(infusion(seconds)).apply {
        send(TimerEvent.Start)
        clocks.advanceSeconds(seconds.toLong())
        send(if (held) TimerEvent.AlertHold else TimerEvent.DeadlineReached)
    }.state

    /** [alert] after the player reported its sound's end (`alertCompleted`): it still alerts, without sound. */
    private fun played(alert: TimerState): TimerState =
        BrewTimerEngine.reduce(alert, TimerEvent.AlertCompleted, VirtualClocks().now()).state
            .also { assertThat((it as TimerState.Alerting).alertPlayed).isTrue() }

    /** A brew alert held since its deadline [seconds] after the start. */
    private fun held(seconds: Int): TimerState = brewAt(seconds, held = true)

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
