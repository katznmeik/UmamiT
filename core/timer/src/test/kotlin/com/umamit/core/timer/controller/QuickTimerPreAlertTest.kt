package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The quick timer's pre-alert on the controller's real path (TMR-22, 04 §10, §12; D-097 point 5, #109): SCR-07's
 * *Start* through the [BrewStarter] the app binds, with the global *Vorwarnung* B. Characterizes the acceptance
 * criteria of #109; on the code of v0.2.1 these pass, so they did not reproduce the PO's silent pre-alert. From
 * v0.2.2 SCR-07's choice (*Automatisch*, *Aus*, 5–30 s) comes with the start and survives process death (#116).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickTimerPreAlertTest {
    private val settings = FakeSettingsRepository(UserSettings(preAlertSeconds = 10))
    private val h = ControllerHarness(settings = settings)

    @After
    fun tearDown() = h.close()

    @Test
    fun `a running 2_00 quick timer schedules, shows and plays its pre-alert at 0_10 with the screen on`() = runTest {
        ControllerBrewStarter(h.controller).startQuickTimer(120)

        assertThat(h.active.phase).isEqualTo(TimerPhase.RUNNING)
        val deadline = h.active.deadlineElapsedMs!!
        assertThat(h.active.preAlertLeadSeconds).isEqualTo(10)
        assertThat(h.active.preAlertAtElapsedMs).isEqualTo(deadline - 10_000)
        assertThat(h.alarms.armed(TimerSlot.QUICK).preAlertAtElapsedMs).isEqualTo(deadline - 10_000)
        h.advanceSeconds(109)
        assertThat(h.alert.ticks).isEqualTo(0)
        h.advanceSeconds(1)
        assertThat(h.alert.ticks).isEqualTo(1)
        assertThat(h.active.preAlertPlayed).isTrue()
        assertThat(h.alarms.armed(TimerSlot.QUICK).preAlertAtElapsedMs).isNull()
        h.advanceSeconds(10)
        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(h.alert.ticks).isEqualTo(1)
    }

    @Test
    fun `with the screen off, the pre-alert alarm starts a new process that plays the tick once`() = runTest {
        ControllerBrewStarter(h.controller).startQuickTimer(120)
        val deadline = h.active.deadlineElapsedMs!!

        // The process dies; nothing in-process wakes it, only the exact alarm at 0:10 left (04 §6.2, E3).
        h.clock.advanceBy(110_000)
        val after = h.processRestarted()
        after.controller.onAlarm(TimerSlot.QUICK, TimerAlarmKind.PRE_ALERT)

        assertThat(after.active.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(after.active.preAlertLeadSeconds).isEqualTo(10)
        assertThat(after.alert.ticks).isEqualTo(1)
        assertThat(after.alert.scheduled.single()?.atMs).isEqualTo(deadline - 10_000)
        after.controller.onAlarm(TimerSlot.QUICK, TimerAlarmKind.PRE_ALERT)
        assertThat(after.alert.ticks).isEqualTo(1)
    }

    @Test
    fun `Vorwarnung Aus plays none and shows no mark`() = runTest {
        settings.update { it.copy(preAlertSeconds = 0) }
        ControllerBrewStarter(h.controller).startQuickTimer(120)

        assertThat(h.active.preAlertLeadSeconds).isNull()
        assertThat(h.alarms.armed(TimerSlot.QUICK).preAlertAtElapsedMs).isNull()
        h.advanceSeconds(120)
        assertThat(h.alert.ticks).isEqualTo(0)
        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
    }

    @Test
    fun `a quick timer started with 20 s schedules its pre-alert at deadline - 20 s and plays it (D-097)`() = runTest {
        ControllerBrewStarter(h.controller).startQuickTimer(120, preAlertSeconds = 20)

        val deadline = h.active.deadlineElapsedMs!!
        assertThat(h.active.preAlertLeadSeconds).isEqualTo(20)
        assertThat(h.active.preAlertAtElapsedMs).isEqualTo(deadline - 20_000)
        assertThat(h.alarms.armed(TimerSlot.QUICK).preAlertAtElapsedMs).isEqualTo(deadline - 20_000)
        h.advanceSeconds(99)
        assertThat(h.alert.ticks).isEqualTo(0)
        h.advanceSeconds(1)
        assertThat(h.alert.ticks).isEqualTo(1)
        assertThat(h.active.preAlertPlayed).isTrue()
        h.advanceSeconds(20)
        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(h.alert.ticks).isEqualTo(1)
    }

    @Test
    fun `the choice survives process death through umamit_db, and the alarm plays the tick at 0_20`() = runTest {
        ControllerBrewStarter(h.controller).startQuickTimer(120, preAlertSeconds = 20)
        val deadline = h.active.deadlineElapsedMs!!

        h.clock.advanceBy(100_000)
        val after = h.processRestarted()
        after.controller.onAlarm(TimerSlot.QUICK, TimerAlarmKind.PRE_ALERT)

        assertThat(after.active.preAlertLeadSeconds).isEqualTo(20)
        assertThat(after.alert.ticks).isEqualTo(1)
        assertThat(after.alert.scheduled.single()?.atMs).isEqualTo(deadline - 20_000)
    }

    @Test
    fun `Aus as the choice plays none even with a global Vorwarnung, and Automatisch follows B`() = runTest {
        ControllerBrewStarter(h.controller).startQuickTimer(120, preAlertSeconds = 0)
        assertThat(h.active.preAlertLeadSeconds).isNull()
        assertThat(h.alarms.armed(TimerSlot.QUICK).preAlertAtElapsedMs).isNull()
        h.advanceSeconds(120)
        assertThat(h.alert.ticks).isEqualTo(0)
        h.controller.send(TimerEvent.Poured)

        settings.update { it.copy(preAlertSeconds = 15) }
        ControllerBrewStarter(h.controller).startQuickTimer(120, preAlertSeconds = null)
        assertThat(h.active.preAlertLeadSeconds).isEqualTo(15)
    }

    @Test
    fun `an invalid choice is refused and nothing runs`() = runTest {
        val outcome = ControllerBrewStarter(h.controller).startQuickTimer(120, preAlertSeconds = 35)

        assertThat(outcome).isEqualTo(TransitionOutcome.Rejected(RejectionReason.InvalidQuickTimer))
        assertThat(h.controller.state.value).isEqualTo(TimerUiState.Idle)
    }

    @Test
    fun `a 0_30 quick timer gets at most 10 s, also with B = 30 s (the automatic rule)`() = runTest {
        settings.update { it.copy(preAlertSeconds = 30) }
        ControllerBrewStarter(h.controller).startQuickTimer(30)

        assertThat(h.active.preAlertLeadSeconds).isEqualTo(10)
        h.advanceSeconds(20)
        assertThat(h.alert.ticks).isEqualTo(1)
    }
}
