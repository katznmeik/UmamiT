package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.AlertStarted
import com.umamit.core.testing.AlertStopped
import com.umamit.core.testing.ServiceStopped
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A brew session and a quick timer at the same time (04 §3.3, §9.3; TMR-21, TMR-22, D-098; PO Q2, R2-Q2, S1):
 * each timer's events act on itself only, one alert player serves both with the brew's alert first, a quick
 * alert due meanwhile is held and rings after *Abgegossen* with its overtime, and its silent end counts from
 * when it starts ringing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TwoTimersTest {
    private val h = ControllerHarness()

    @Before
    fun setUp() = runTest { h.teaWithStock() }

    @After
    fun tearDown() = h.close()

    private fun ui(slot: TimerSlot) = h.controller.state(slot).value

    private fun active(slot: TimerSlot) = ui(slot) as TimerUiState.Active

    @Test
    fun `a brew with a quick timer alongside, each alerts on time and the service runs until both end`() = runTest {
        h.controller.startSession(brewStart(infusion(120, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.controller.startQuickTimer(30, preAlertSeconds = 0, run = true)
        val brewDeadline = checkNotNull(active(TimerSlot.BREW).deadlineElapsedMs)

        h.advanceSeconds(30)

        assertThat(h.alert.playing?.kind).isEqualTo(SessionKind.Quick)
        assertThat(active(TimerSlot.QUICK).ringing).isTrue()
        assertThat(active(TimerSlot.BREW).phase).isEqualTo(TimerPhase.RUNNING)
        // The ringing quick timer's alert is the service's notification; the brew shows as notification 5.
        assertThat(h.service.foreground?.slot).isEqualTo(TimerSlot.QUICK)
        assertThat(h.service.fullScreen).isTrue()
        assertThat(h.notifier.other?.slot).isEqualTo(TimerSlot.BREW)

        h.controller.send(TimerSlot.QUICK, TimerEvent.Poured)

        assertThat(ui(TimerSlot.QUICK)).isEqualTo(TimerUiState.Idle)
        assertThat(h.service.running).isTrue()
        assertThat(h.service.foreground?.slot).isEqualTo(TimerSlot.BREW)
        assertThat(h.notifier.other).isNull()
        assertThat(h.log.calls).doesNotContain(ServiceStopped)

        h.advanceSeconds(90)

        assertThat(active(TimerSlot.BREW).ringing).isTrue()
        assertThat(h.alert.playing?.kind).isInstanceOf(SessionKind.Brew::class.java)
        assertThat(h.alert.scheduled.last()?.atMs).isEqualTo(brewDeadline)
        h.controller.send(TimerSlot.BREW, TimerEvent.Poured)
        h.controller.send(TimerSlot.BREW, TimerEvent.FinishSession)
        assertThat(h.service.running).isFalse()
    }

    @Test
    fun `Sitzung beenden and verwerfen leave the quick timer running, Abbrechen leaves the brew running`() = runTest {
        h.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        h.controller.startQuickTimer(300, preAlertSeconds = 0, run = true)
        val quick = active(TimerSlot.QUICK)

        h.controller.send(TimerSlot.BREW, TimerEvent.Discard)

        assertThat(ui(TimerSlot.BREW)).isEqualTo(TimerUiState.Idle)
        assertThat(ui(TimerSlot.QUICK)).isEqualTo(quick)
        assertThat(h.service.running).isTrue()
        assertThat(h.service.foreground?.slot).isEqualTo(TimerSlot.QUICK)
        assertThat(h.notifier.other).isNull()

        h.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.advanceSeconds(60)
        h.controller.send(TimerSlot.BREW, TimerEvent.Poured)
        h.controller.send(TimerSlot.BREW, TimerEvent.FinishSession)
        assertThat(active(TimerSlot.QUICK).phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(h.savedSheet.shown).hasSize(1)

        h.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        val brew = active(TimerSlot.BREW)
        h.controller.send(TimerSlot.QUICK, TimerEvent.Cancel)

        assertThat(ui(TimerSlot.QUICK)).isEqualTo(TimerUiState.Idle)
        assertThat(ui(TimerSlot.BREW)).isEqualTo(brew)
        assertThat(h.service.foreground?.slot).isEqualTo(TimerSlot.BREW)
        assertThat(h.service.running).isTrue()
    }

    @Test
    fun `both due within 1 s, the brew alerts, the quick alert is held and rings after Abgegossen`() = runTest {
        h.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.clock.advanceBy(400)
        h.controller.startQuickTimer(60, preAlertSeconds = 0, run = true)
        // Both deadlines have passed when the brew's alarm arrives, 0.4 s apart.
        h.clock.advanceBy(60_000)

        h.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)

        assertThat(
            h.alert.started.map {
                it.kind
            },
        ).containsExactly(SessionKind.Brew(active(TimerSlot.BREW).sessionId!!))
        assertThat(active(TimerSlot.BREW).ringing).isTrue()
        assertThat(active(TimerSlot.QUICK).alertHeld).isTrue()
        assertThat(h.alarms.armed(TimerSlot.QUICK).overtimeTimeoutAtElapsedMs).isNull()
        assertThat((h.store.stored(TimerSlot.QUICK) as TimerState.Alerting).held).isTrue()
        // The screens and the alert notification are the brew's; the held quick alert waits quietly.
        assertThat(h.active.slot).isEqualTo(TimerSlot.BREW)
        assertThat(h.service.foreground?.slot).isEqualTo(TimerSlot.BREW)
        assertThat(h.notifier.other?.alertHeld).isTrue()

        h.clock.advanceBy(5_000)
        h.controller.send(TimerSlot.BREW, TimerEvent.Poured)

        assertThat(h.alert.playing?.kind).isEqualTo(SessionKind.Quick)
        assertThat(active(TimerSlot.QUICK).ringing).isTrue()
        assertThat(h.active.slot).isEqualTo(TimerSlot.QUICK)
        assertThat(h.service.foreground?.slot).isEqualTo(TimerSlot.QUICK)
        assertThat(h.service.fullScreen).isTrue()
        assertThat(active(TimerSlot.QUICK).display(h.clock.elapsed).overtimeSeconds).isEqualTo(5)
        assertThat(h.notifier.other?.slot).isEqualTo(TimerSlot.BREW)
        assertThat(h.notifier.other?.phase).isEqualTo(TimerPhase.FINISHED)
    }

    @Test
    fun `a brew deadline while the quick alert rings takes the player at once and holds the quick alert`() = runTest {
        h.controller.startSession(brewStart(infusion(40, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.controller.startQuickTimer(30, preAlertSeconds = 0, run = true)
        h.advanceSeconds(30)
        assertThat(h.alert.playing?.kind).isEqualTo(SessionKind.Quick)
        h.log.clear()

        h.advanceSeconds(10)

        val brewAlert = checkNotNull(h.alert.playing)
        assertThat(brewAlert.kind).isInstanceOf(SessionKind.Brew::class.java)
        assertThat(active(TimerSlot.QUICK).alertHeld).isTrue()
        // The quick alert's stop never reaches the player after the brew's alert took it over.
        val calls = h.log.calls
        assertThat(calls.filterIsInstance<AlertStarted>().map { it.alert }).containsExactly(brewAlert)
        assertThat(calls.indexOf(AlertStopped).let { it < 0 || it < calls.indexOf(AlertStarted(brewAlert)) }).isTrue()
        assertThat(h.alert.playing).isEqualTo(brewAlert)
        // The preempted quick alert is never reported played, so its watchdog goes and recovery would replay it.
        assertThat(h.alarms.watchdogCancelled(TimerSlot.QUICK)).isTrue()
    }

    @Test
    fun `the quick alert held for 12 min rings with +12 00 and ends silently only 10 min after it began`() = runTest {
        // S1: the quick timer rang for 3 min, the brew took over for 9 min, then Abgegossen.
        h.controller.startSession(brewStart(infusion(4 * 60, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.controller.startQuickTimer(60, preAlertSeconds = 0, run = true)
        h.advanceSeconds(60)
        assertThat(active(TimerSlot.QUICK).ringing).isTrue()
        h.advanceSeconds(3 * 60)
        assertThat(active(TimerSlot.QUICK).alertHeld).isTrue()

        h.advanceSeconds(9 * 60)
        // The quick timer's own deadline was 12 min ago: held, it did not end silently 10 min after it.
        assertThat(active(TimerSlot.QUICK).alertHeld).isTrue()
        h.controller.send(TimerSlot.BREW, TimerEvent.Poured)

        val quick = active(TimerSlot.QUICK)
        assertThat(quick.ringing).isTrue()
        assertThat(quick.display(h.clock.elapsed).overtimeSeconds).isEqualTo(12 * 60)
        assertThat(h.alert.playing?.kind).isEqualTo(SessionKind.Quick)
        assertThat(h.alarms.armed(TimerSlot.QUICK).overtimeTimeoutAtElapsedMs).isEqualTo(h.clock.elapsed + 600_000)

        h.advanceSeconds(10 * 60 - 1)
        assertThat(active(TimerSlot.QUICK).ringing).isTrue()
        h.advanceSeconds(1)
        assertThat(ui(TimerSlot.QUICK)).isEqualTo(TimerUiState.Idle)
        assertThat(active(TimerSlot.BREW).phase).isEqualTo(TimerPhase.FINISHED)
    }

    @Test
    fun `a released alert is due at its release for the player's log, with the time it was held (06 §5_6)`() = runTest {
        h.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.controller.startQuickTimer(60, preAlertSeconds = 0, run = true)
        h.advanceSeconds(60)
        assertThat(active(TimerSlot.QUICK).alertHeld).isTrue()
        val quickDeadline = checkNotNull(active(TimerSlot.QUICK).deadlineElapsedMs)
        // Held 9 min, within the brew alert's 10 min.
        h.advanceSeconds(9 * 60)

        h.controller.send(TimerSlot.BREW, TimerEvent.Poured)

        assertThat(h.alert.playing?.kind).isEqualTo(SessionKind.Quick)
        val brewDue = h.alert.scheduled.first()
        val released = checkNotNull(h.alert.scheduled.last())
        // The brew rang at its deadline: due then, never held.
        assertThat(brewDue?.heldMs).isNull()
        assertThat(released.slot).isEqualTo(TimerSlot.QUICK)
        assertThat(released.kind).isEqualTo(TimerAlarmKind.DEADLINE)
        assertThat(released.atMs).isEqualTo(h.clock.elapsed)
        assertThat(released.heldMs).isEqualTo(h.clock.elapsed - quickDeadline)
    }

    @Test
    fun `a process death with both timers and a held alert replays the brew alert first, then the quick one`() =
        runTest {
            h.controller.startSession(brewStart(infusion(60, preAlert = 0)))
            h.controller.send(TimerSlot.BREW, TimerEvent.Start)
            h.controller.startQuickTimer(45, preAlertSeconds = 0, run = true)
            h.advanceSeconds(60)
            assertThat(active(TimerSlot.QUICK).alertHeld).isTrue()

            // Killed during the brew's alert; a new process starts 20 s later.
            val after = h.processRestarted()
            after.clock.advanceBy(20_000)
            after.controller.recover(RecoveryTrigger.APP_START)

            val brew = after.controller.state(TimerSlot.BREW).value as TimerUiState.Active
            val quick = after.controller.state(TimerSlot.QUICK).value as TimerUiState.Active
            assertThat(brew.ringing).isTrue()
            assertThat(quick.alertHeld).isTrue()
            assertThat(after.alert.started.map { it.kind }).containsExactly(brew.kind)
            assertThat(after.service.foreground?.slot).isEqualTo(TimerSlot.BREW)
            assertThat(after.notifier.other?.alertHeld).isTrue()
            assertThat(
                after.diagnostics.entriesOf<TimerDiagnostic.Recovered>().map { it.decision },
            ).containsExactly(RecoveryDecision.ALERT_REPLAYED, RecoveryDecision.ALERT_HELD).inOrder()

            after.controller.send(TimerSlot.BREW, TimerEvent.Poured)

            assertThat(after.alert.playing?.kind).isEqualTo(SessionKind.Quick)
            val ringing = after.controller.state(TimerSlot.QUICK).value as TimerUiState.Active
            assertThat(ringing.display(after.clock.elapsed).overtimeSeconds).isEqualTo(35)
        }

    @Test
    fun `a process death during both steps holds a quick timer past its deadline behind the brew's alert`() = runTest {
        h.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.controller.startQuickTimer(30, preAlertSeconds = 0, run = true)
        h.advanceSeconds(20)

        // Killed before either deadline; the brew's deadline alarm starts a new process 45 s later.
        val after = h.processRestarted()
        after.clock.advanceBy(45_000)
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)

        assertThat((after.controller.state(TimerSlot.BREW).value as TimerUiState.Active).ringing).isTrue()
        assertThat((after.controller.state(TimerSlot.QUICK).value as TimerUiState.Active).alertHeld).isTrue()
        assertThat(after.alert.started.map { it.kind }.single()).isInstanceOf(SessionKind.Brew::class.java)
    }

    @Test
    fun `a process death while the quick alert rings beside a running brew replays the quick alert`() = runTest {
        h.controller.startSession(brewStart(infusion(120, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.controller.startQuickTimer(30, preAlertSeconds = 0, run = true)
        h.advanceSeconds(30)

        val after = h.processRestarted()
        after.clock.advanceBy(10_000)
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.alert.started.map { it.kind }).containsExactly(SessionKind.Quick)
        assertThat((after.controller.state(TimerSlot.BREW).value as TimerUiState.Active).phase)
            .isEqualTo(TimerPhase.RUNNING)
        assertThat(after.alarms.armed(TimerSlot.BREW).deadlineElapsedMs).isNotNull()
    }

    @Test
    fun `a pre-alert during the other slot's alert waits for it to end, then plays with at least 1 s left`() = runTest {
        h.controller.startSession(brewStart(infusion(60, preAlert = 10)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.controller.startQuickTimer(45, preAlertSeconds = 0, run = true)
        h.advanceSeconds(45)
        val quickAlert = checkNotNull(h.alert.playing)

        // The brew's pre-alert is due at 0:10 left, while the quick alert still sounds: it waits.
        h.advanceSeconds(7)
        assertThat(h.alert.ticks).isEqualTo(0)
        assertThat(active(TimerSlot.BREW).preAlertPlayed).isTrue()

        // The quick alert's sound ends 8 s before the brew's deadline: the pre-alert plays now.
        h.controller.alertCompleted(quickAlert)

        assertThat(h.alert.ticks).isEqualTo(1)
        assertThat(active(TimerSlot.QUICK).ringing).isTrue()
    }

    @Test
    fun `a pre-alert whose wait ends less than 1 s before its deadline is consumed silently`() = runTest {
        h.controller.startSession(brewStart(infusion(60, preAlert = 10)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.controller.startQuickTimer(45, preAlertSeconds = 0, run = true)
        h.advanceSeconds(45)
        h.advanceSeconds(14)
        h.clock.advanceBy(500)

        // *Fertig* on the quick alert 0.5 s before the brew's deadline.
        h.controller.send(TimerSlot.QUICK, TimerEvent.Poured)
        h.advanceSeconds(1)

        assertThat(h.alert.ticks).isEqualTo(0)
        assertThat(active(TimerSlot.BREW).ringing).isTrue()
    }

    @Test
    fun `a pre-alert during the other slot's pre-alert waits until it has rung out`() = runTest {
        h.controller.startSession(brewStart(infusion(60, preAlert = 10)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.clock.advanceBy(1_000)
        h.controller.startQuickTimer(60, preAlertSeconds = 10, run = true)

        h.advanceSeconds(50)
        assertThat(h.alert.ticks).isEqualTo(1)
        // The quick timer's pre-alert is due 1 s later, while the brew's still rings (at most 3.45 s).
        h.advanceSeconds(2)
        assertThat(h.alert.ticks).isEqualTo(1)

        h.advanceSeconds(2)

        assertThat(h.alert.ticks).isEqualTo(2)
    }

    @Test
    fun `an alert cuts the other slot's ringing pre-alert`() = runTest {
        h.controller.startSession(brewStart(infusion(31, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.controller.startQuickTimer(60, preAlertSeconds = 30, run = true)

        h.advanceSeconds(30)
        assertThat(h.alert.ticks).isEqualTo(1)
        h.advanceSeconds(1)

        assertThat(h.alert.playing?.kind).isInstanceOf(SessionKind.Brew::class.java)
    }
}
