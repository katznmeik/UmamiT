package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A process killed while its alert plays (04 §7.2; #42 review O4/R2-3, #46, D-063): ALERTING
 * keeps only the overtime alarm 10 min out, whose recovery acknowledges silently. While the
 * alert plays, an exact watchdog alarm comes every 30 s, so a new process replays the alert
 * that never finished (at least once, D-047).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertWatchdogTest {
    private val before = ControllerHarness()

    @Before
    fun setUp() = runTest { before.teaWithStock() }

    @After
    fun tearDown() = before.close()

    @Test
    fun `while the alert plays, the watchdog comes every 30 s, and its end cancels it`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(60)
        assertThat(before.alarms.watchdogAtElapsedMs).isEqualTo(before.clock.elapsed + WATCHDOG_MS)

        before.clock.advanceBy(WATCHDOG_MS)
        before.controller.onAlarm(TimerAlarmKind.ALERT_WATCHDOG)

        assertThat(before.alarms.watchdogAtElapsedMs).isEqualTo(before.clock.elapsed + WATCHDOG_MS)
        assertThat(before.alert.started).hasSize(1)
        val received = before.diagnostics.entriesOf<TimerDiagnostic.AlarmReceived>().last()
        assertThat(received.scheduled?.lateMs(received.at)).isEqualTo(0L)

        before.controller.alertCompleted(checkNotNull(before.alert.playing))

        assertThat(before.alarms.watchdogAtElapsedMs).isNull()
        assertThat(before.active.phase).isEqualTo(TimerPhase.ALERTING)
    }

    @Test
    fun `Abgegossen stops the watchdog with the alert`() = runTest {
        before.controller.startSession(brewStart(infusion(60), infusion(30)))
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(60)

        before.controller.send(TimerEvent.Poured)

        assertThat(before.alarms.watchdogAtElapsedMs).isNull()
    }

    @Test
    fun `a process killed during the alert replays it when the watchdog starts a new one`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(65)
        val alert = checkNotNull(before.alert.playing)

        val after = before.processRestarted()
        after.clock.advanceBy(25_000)
        after.controller.onAlarm(TimerAlarmKind.ALERT_WATCHDOG)

        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started).containsExactly(alert)
        assertThat(after.service.foreground?.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.service.fullScreen).isTrue()
        // The replayed alert is watched again.
        assertThat(after.alarms.watchdogAtElapsedMs).isEqualTo(after.clock.elapsed + WATCHDOG_MS)
    }

    @Test
    fun `a watchdog whose new process cannot read the timer comes again and still replays the alert`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(65)

        val after = before.processRestarted()
        after.store.failNextReads(3)
        after.clock.advanceBy(25_000)
        after.controller.onAlarm(TimerAlarmKind.ALERT_WATCHDOG)

        // Nothing is known to play in this process yet: the watchdog is set again, not cancelled.
        assertThat(after.alarms.retries.map { it.kind }).containsExactly(TimerAlarmKind.ALERT_WATCHDOG)
        assertThat(after.alarms.watchdogCancelled).isFalse()
        after.clock.advanceBy(5_000)
        after.controller.onAlarm(TimerAlarmKind.ALERT_WATCHDOG)
        assertThat(after.alert.started).hasSize(1)
    }

    @Test
    fun `a new process with nothing playing cancels a watchdog left by the old one`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(60)
        before.controller.alertCompleted(checkNotNull(before.alert.playing))

        val after = before.processRestarted()
        after.controller.onAlarm(TimerAlarmKind.ALERT_WATCHDOG)

        // The alert ran its cycle (alertPlayed): nothing is replayed, and no watchdog stays set.
        assertThat(after.alert.started).isEmpty()
        assertThat(after.alarms.watchdogCancelled).isTrue()
    }

    private companion object {
        const val WATCHDOG_MS = 30_000L
    }
}
