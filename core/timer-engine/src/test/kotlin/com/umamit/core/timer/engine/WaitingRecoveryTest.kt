package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.timer.engine.TimerEffect.CompleteSession
import com.umamit.core.timer.engine.TimerEffect.DiscardSession
import com.umamit.core.timer.engine.TimerEffect.NotifySessionSaved
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.ScheduleAlarms
import com.umamit.core.timer.engine.TimerEffect.ShowSavedSheet
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopForeground
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification
import org.junit.Test

/**
 * The rows of the 04 §7.2 recovery table that #31 adds: the midnight row, and
 * no service for a demoted session or a long running step (D-048 notes, D-053).
 */
class WaitingRecoveryTest {

    private val thirtyMinutes = 30 * 60_000L

    private fun withDoneInfusion() = sessionWith(infusion(60), infusion(20)).apply {
        send(TimerEvent.Start)
        clocks.advanceSeconds(60)
        send(TimerEvent.DeadlineReached)
        send(TimerEvent.Poured)
    }

    @Test
    fun `a waiting session past its midnight is completed silently, with the notification`() {
        val driver = withDoneInfusion()
        driver.clocks.wall = MIDNIGHT_AFTER_START + 3_600_000L

        val effects = driver.send(TimerEvent.Recover)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        assertThat(effects).containsExactly(
            CompleteSession(SESSION_ID),
            Persist,
            NO_ALARMS,
            StopForeground,
            NotifySessionSaved(SESSION_ID),
        ).inOrder()
        assertThat(effects.none { it is ShowSavedSheet }).isTrue()
    }

    @Test
    fun `a waiting session past its midnight without a completed infusion is discarded silently`() {
        val driver = sessionWith(infusion(60))
        driver.clocks.reboot(offMs = 20 * 3_600_000L, upMs = 60_000)

        val effects = driver.send(TimerEvent.Recover)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        assertThat(effects).containsExactly(DiscardSession(SESSION_ID), Persist, NO_ALARMS, StopForeground).inOrder()
    }

    @Test
    fun `a waiting session 1 ms before its midnight is restored`() {
        val driver = withDoneInfusion().apply {
            clocks.advance(thirtyMinutes)
            send(TimerEvent.IdleDemote)
        }
        driver.clocks.wall = MIDNIGHT_AFTER_START - 1

        driver.send(TimerEvent.Recover)

        assertThat(driver.state).isInstanceOf(TimerState.Ready::class.java)
    }

    @Test
    fun `a demoted session is restored without the service`() {
        val driver = withDoneInfusion().apply {
            clocks.advance(thirtyMinutes)
            send(TimerEvent.IdleDemote)
        }
        val before = driver.state
        driver.clocks.advanceSeconds(600)

        val effects = driver.send(TimerEvent.Recover)

        assertThat(driver.state).isEqualTo(before)
        assertThat(effects)
            .containsExactly(Persist, ScheduleAlarms(BrewTimerEngine.dueTimes(before)), UpdateNotification).inOrder()
    }

    @Test
    fun `a waiting session idle for 30 min while the process was dead is demoted on recovery`() {
        listOf(
            "same boot" to { driver: EngineDriver -> driver.clocks.advance(thirtyMinutes) },
            "after a reboot" to { driver: EngineDriver -> driver.clocks.reboot(offMs = thirtyMinutes, upMs = 60_000) },
        ).forEach { (name, timePasses) ->
            val driver = withDoneInfusion()
            timePasses(driver)

            val effects = driver.send(TimerEvent.Recover)

            val ready = driver.state as TimerState.Ready
            assertWithMessage(name).that(ready.session.demoted).isTrue()
            assertWithMessage(name).that(effects).containsExactly(
                Persist,
                ScheduleAlarms(BrewTimerEngine.dueTimes(ready)),
                StopForeground,
                UpdateNotification,
            ).inOrder()
        }
    }

    @Test
    fun `a long running step is restored without the service`() {
        val driver = sessionWith(infusion(3600)).apply { send(TimerEvent.Start) }
        val before = driver.state as TimerState.Running
        driver.clocks.advanceSeconds(600)

        val effects = driver.send(TimerEvent.Recover)

        assertThat(driver.state).isEqualTo(before)
        assertThat(effects)
            .containsExactly(Persist, deadlineAlarm(before.deadlineElapsedMs), UpdateNotification).inOrder()
    }

    @Test
    fun `a long step whose deadline passed while the process was dead alerts with the service`() {
        val driver = sessionWith(infusion(3600)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(3700)

        val effects = driver.send(TimerEvent.Recover)

        assertThat(driver.state).isInstanceOf(TimerState.Alerting::class.java)
        assertThat(effects).contains(StartForeground)
    }

    @Test
    fun `a running step whose pre-alert passed while the process was dead keeps it due`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(55)

        driver.send(TimerEvent.Recover)
        assertThat(BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs).isEqualTo(START_ELAPSED_MS + 50_000)

        assertThat(driver.send(TimerEvent.PreAlertReached)).contains(TimerEffect.PlayPreAlert)
    }
}
