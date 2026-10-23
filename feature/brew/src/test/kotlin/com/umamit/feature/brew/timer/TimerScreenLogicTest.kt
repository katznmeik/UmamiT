package com.umamit.feature.brew.timer

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.KettleWater
import com.umamit.core.model.TimerPhase
import com.umamit.feature.brew.timer.TimerMenuItem.AddStep
import com.umamit.feature.brew.timer.TimerMenuItem.AdjustBrew
import com.umamit.feature.brew.timer.TimerMenuItem.CancelQuickTimer
import com.umamit.feature.brew.timer.TimerMenuItem.DiscardSession
import com.umamit.feature.brew.timer.TimerMenuItem.EditSteps
import com.umamit.feature.brew.timer.TimerMenuItem.EndStepNow
import com.umamit.feature.brew.timer.TimerMenuItem.FinishSession
import com.umamit.feature.brew.timer.TimerMenuItem.SkipStep
import com.umamit.feature.brew.timer.TimerMenuItem.TimerSettings
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Test

/** The pure rules of SCR-05: overflow per state, announcements, ticks and progress. */
class TimerScreenLogicTest {

    @Test
    fun `the quick timer's overflow offers Abbrechen until the alert, and the timer settings (SCR-07)`() {
        for (phase in listOf(TimerPhase.READY, TimerPhase.RUNNING, TimerPhase.PAUSED)) {
            assertThat(TimerStates.quick(phase).menuItems()).containsExactly(CancelQuickTimer, TimerSettings).inOrder()
        }
        // On the alert *Fertig* ends it; the engine has no cancel there (04 sec 10).
        assertThat(TimerStates.quick(TimerPhase.ALERTING).menuItems()).containsExactly(TimerSettings)
    }

    @Test
    fun `a cold step reads Kaltes Wasser, an ice brew Eiswuerfel, a hot one the kettle (BRW-10)`() {
        assertThat(TimerStates.coldReady(iceBrew = false).kettleWater()).isEqualTo(KettleWater.COLD)
        assertThat(TimerStates.coldReady(iceBrew = true).kettleWater()).isEqualTo(KettleWater.ICE)
        assertThat(TimerStates.ready().kettleWater()).isEqualTo(KettleWater.HOT)
        assertThat(TimerStates.quick(TimerPhase.READY).kettleWater()).isNull()
    }

    @Test
    fun `the overflow offers exactly the items of the SCR-05 table per state`() {
        assertThat(TimerStates.ready().menuItems()).containsExactly(
            AdjustBrew,
            SkipStep,
            EditSteps,
            AddStep,
            FinishSession,
            DiscardSession,
            TimerSettings,
        ).inOrder()
        assertThat(TimerStates.running().menuItems()).containsExactly(
            EndStepNow,
            EditSteps,
            AddStep,
            FinishSession,
            DiscardSession,
            TimerSettings,
        ).inOrder()
        assertThat(TimerStates.paused().menuItems()).containsExactly(
            EndStepNow,
            EditSteps,
            AddStep,
            FinishSession,
            DiscardSession,
            TimerSettings,
        ).inOrder()
        // D-055: nothing that opens the Schritte-Sheet while the alert plays.
        assertThat(TimerStates.alerting().menuItems()).containsExactly(
            FinishSession,
            DiscardSession,
            TimerSettings,
        ).inOrder()
        assertThat(TimerStates.finished().menuItems()).containsExactly(
            EditSteps,
            AddStep,
            FinishSession,
            DiscardSession,
            TimerSettings,
        ).inOrder()
    }

    @Test
    fun `Bruehen anpassen is offered only before the first step started`() {
        assertThat(TimerStates.ready(stepIndex = 1).menuItems()).doesNotContain(AdjustBrew)
    }

    @Test
    fun `the caption switches to Gleich fertig at the pre-alert, by time or by the played tick (TMR-23)`() {
        val running = TimerStates.running(remainingMs = 12_000)

        assertThat(running.preAlertReached(NOW)).isFalse()
        assertThat(running.preAlertReached(NOW + 2_000)).isTrue()
        assertThat(TimerStates.running(remainingMs = 12_000, preAlertPlayed = true).preAlertReached(NOW)).isTrue()
    }

    @Test
    fun `TalkBack announces state changes, the pre-alert, 10 s remaining and the alert`() {
        assertThat(TimerStates.ready().announcement(NOW)).isEqualTo(TimerAnnouncement.Ready)
        assertThat(TimerStates.running(remainingMs = 48_000).announcement(NOW)).isEqualTo(TimerAnnouncement.Running)
        assertThat(TimerStates.paused().announcement(NOW)).isEqualTo(TimerAnnouncement.Paused)
        assertThat(TimerStates.alerting().announcement(NOW)).isEqualTo(TimerAnnouncement.Alert)
        assertThat(TimerStates.finished().announcement(NOW)).isEqualTo(TimerAnnouncement.Finished)
    }

    @Test
    fun `a 10 s pre-alert and the 10 s mark are one announcement, other leads announce both in turn`() {
        val running = TimerStates.running(remainingMs = 48_000)
        // Lead 10 s: the pre-alert and the 10 s mark coincide.
        assertThat(running.announcement(NOW + 38_000)).isEqualTo(TimerAnnouncement.PreAlertTenSeconds)

        val lead20 = running.copy(preAlertLeadSeconds = 20, preAlertAtElapsedMs = NOW + 28_000)
        assertThat(lead20.announcement(NOW + 28_000)).isEqualTo(TimerAnnouncement.PreAlert)
        assertThat(lead20.announcement(NOW + 38_000)).isEqualTo(TimerAnnouncement.TenSeconds)

        val lead5 = running.copy(preAlertLeadSeconds = 5, preAlertAtElapsedMs = NOW + 43_000)
        assertThat(lead5.announcement(NOW + 38_000)).isEqualTo(TimerAnnouncement.TenSeconds)
        assertThat(lead5.announcement(NOW + 43_000)).isEqualTo(TimerAnnouncement.PreAlert)
    }

    @Test
    fun `a step of 10 s or less never announces 10 s remaining`() {
        val short = TimerStates.running(remainingMs = 8_000).let {
            it.copy(steps = it.steps.mapIndexed { i, s -> if (i == 0) s.copy(plannedSeconds = 10) else s })
        }.copy(preAlertLeadSeconds = null, preAlertAtElapsedMs = null)

        assertThat(short.announcement(NOW)).isEqualTo(TimerAnnouncement.Running)
    }

    @Test
    fun `the display ticks at each second boundary of the countdown and the overtime`() {
        // 48 000 ms left, pre-alert at 38 000: the digits change in 1 000 ms.
        assertThat(TimerStates.running(remainingMs = 48_000).nextTickDelayMs(NOW)).isEqualTo(1_000)
        assertThat(TimerStates.running(remainingMs = 47_400).nextTickDelayMs(NOW)).isEqualTo(400)
        // The pre-alert time comes before the next second boundary.
        assertThat(TimerStates.running(remainingMs = 48_000).nextTickDelayMs(NOW + 37_999)).isEqualTo(1)
        // Overtime 7 000 ms → +0:08 in 1 000 ms; 7 250 → in 750.
        assertThat(TimerStates.alerting(overtimeMs = 7_250).nextTickDelayMs(NOW)).isEqualTo(750)
        // Nothing moves in READY, PAUSED, FINISHED, nor past a deadline not yet handled.
        assertThat(TimerStates.ready().nextTickDelayMs(NOW)).isNull()
        assertThat(TimerStates.paused().nextTickDelayMs(NOW)).isNull()
        assertThat(TimerStates.finished().nextTickDelayMs(NOW)).isNull()
        assertThat(TimerStates.running(remainingMs = 1_000).nextTickDelayMs(NOW + 1_000)).isNull()
    }

    @Test
    fun `progress follows the step, and a long step moves only every 10 s (03 §3_6)`() {
        assertThat(TimerStates.ready().progressAt(NOW)).isEqualTo(0f)
        assertThat(TimerStates.running(remainingMs = 45_000).progressAt(NOW)).isWithin(1e-6f).of(0.25f)
        assertThat(TimerStates.paused(remainingMs = 30_000).progressAt(NOW)).isWithin(1e-6f).of(0.5f)
        assertThat(TimerStates.alerting().progressAt(NOW)).isEqualTo(1f)
        assertThat(TimerStates.finished().progressAt(NOW)).isEqualTo(1f)

        // 3 h step, 1 h elapsed + 9.9 s: drawn at the last 10 s boundary.
        val long = TimerStates.longRunning(remainingMs = 2 * 3_600_000L)
        val atBoundary = long.progressAt(NOW)
        assertThat(long.progressAt(NOW + 9_900)).isEqualTo(atBoundary)
        assertThat(long.progressAt(NOW + 10_000)).isGreaterThan(atBoundary)
    }
}
