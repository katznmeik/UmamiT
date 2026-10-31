package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Belt and braces (04 §2.5, §6): the in-process wake-up and the exact alarms
 * both deliver the time-based events; either alone is enough, and together
 * they give one alert per deadline (D-048). The alert player reports the end
 * of its cycle (at-least-once delivery, D-047).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertDeliveryTest {
    private val h = ControllerHarness()

    @Before
    fun setUp() = runTest { h.teaWithStock() }

    @After
    fun tearDown() = h.close()

    @Test
    fun `the sounds stay loaded from the start of a session to its end, so its first tick finds them`() = runTest {
        h.controller.startSession(brewStart(infusion(60), infusion(60)))
        assertThat(h.alert.soundsReady).isTrue()

        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(50)
        assertThat(h.alert.ticks).isEqualTo(1)
        h.advanceSeconds(10)
        h.controller.send(TimerEvent.Poured)
        assertThat(h.active.phase).isEqualTo(TimerPhase.READY)
        assertThat(h.alert.soundsReady).isTrue()

        h.controller.send(TimerEvent.FinishSession)
        assertThat(h.alert.soundsReady).isFalse()
        assertThat(h.alert.readyChanges).containsExactly(true, false).inOrder()
    }

    @Test
    fun `a quick timer keeps them loaded until it ends`() = runTest {
        h.controller.startQuickTimer(60, run = true)
        assertThat(h.alert.soundsReady).isTrue()

        h.controller.send(TimerEvent.Cancel)

        assertThat(h.alert.soundsReady).isFalse()
    }

    @Test
    fun `a new process loads them again for the timer it recovers`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)

        val restarted = h.processRestarted()
        restarted.controller.recover(RecoveryTrigger.APP_START)

        assertThat(restarted.alert.soundsReady).isTrue()
    }

    @Test
    fun `the deadline alarm after the wake-up starts no second alert`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(60)

        h.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)

        assertThat(h.alert.started).hasSize(1)
        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
    }

    @Test
    fun `the alarms alone deliver the pre-alert and the deadline when the process's wake-up is held up`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)

        // Deep sleep: the in-process timer does not run, the exact alarms wake the phone.
        h.clock.advanceBy(50_000)
        h.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.PRE_ALERT)
        h.clock.advanceBy(10_000)
        h.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)
        h.wakeUp.advanceBy(0) // the process's own timer catches up: nothing more is due

        assertThat(h.alert.ticks).isEqualTo(1)
        assertThat(h.alert.started).hasSize(1)
        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
    }

    @Test
    fun `an alarm sends every event due at that moment, the deadline first`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)

        // The pre-alert alarm is delivered only after the deadline: no tick at or after it (D-053).
        h.clock.advanceBy(61_000)
        h.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.PRE_ALERT)

        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(h.alert.started).hasSize(1)
        assertThat(h.alert.ticks).isEqualTo(0)
    }

    @Test
    fun `the end of the alert is saved, so recovery does not play it again`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(60)

        h.controller.alertCompleted(h.alert.playing!!)

        assertThat(h.store.read(TimerSlot.BREW)!!.activeBrew.alertPlayed).isTrue()
        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
    }

    @Test
    fun `a late end report of an earlier step's alert changes nothing`() = runTest {
        h.controller.startSession(brewStart(infusion(10), infusion(20)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(10)
        val first = h.alert.playing!!
        h.controller.send(TimerEvent.Poured)
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(20)

        h.controller.alertCompleted(first)

        assertThat(h.store.read(TimerSlot.BREW)!!.activeBrew.alertPlayed).isFalse()
        assertThat(h.diagnostics.entriesOf<TimerDiagnostic.StaleAlertCompletion>().map { it.alert })
            .containsExactly(first)
        assertThat(h.alert.playing).isEqualTo(StepAlert(SessionKind.Brew(h.sessionId), 1, h.clock.wall))
    }

    @Test
    fun `an alarm is logged with its scheduled time and the moment it arrived`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)
        val deadline = h.clock.elapsed + 60_000
        h.clock.advanceBy(60_120)

        h.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)

        val received = h.diagnostics.entriesOf<TimerDiagnostic.AlarmReceived>().single()
        assertThat(received.kind).isEqualTo(TimerAlarmKind.DEADLINE)
        assertThat(received.scheduled).isEqualTo(ScheduledTime(TimerAlarmKind.DEADLINE, deadline))
        assertThat(received.scheduled!!.lateMs(received.at)).isEqualTo(120)
        val handled = h.diagnostics.entriesOf<TimerDiagnostic.EventHandled>()
            .single { it.event == TimerEvent.DeadlineReached }
        assertThat(handled.source).isEqualTo(EventSource.ALARM)
        assertThat(handled.from).isEqualTo(TimerPhase.RUNNING)
        assertThat(handled.to).isEqualTo(TimerPhase.ALERTING)
        assertThat(handled.scheduled).isEqualTo(ScheduledTime(TimerAlarmKind.DEADLINE, deadline))
    }
}
