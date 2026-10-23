package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
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

    @Test
    fun `while a step runs, the deadline guard is set 90 s after its deadline and goes with the step (D-068)`() =
        runTest {
            before.controller.startSession(brewStart(infusion(60), infusion(30)))
            before.controller.send(TimerEvent.Start)
            assertThat(before.alarms.watchdogAtElapsedMs).isEqualTo(before.clock.elapsed + 60_000 + GUARD_MS)

            before.advanceSeconds(20)
            before.controller.send(TimerEvent.Pause)
            assertThat(before.alarms.watchdogAtElapsedMs).isNull()

            before.controller.send(TimerEvent.Resume)
            assertThat(before.alarms.watchdogAtElapsedMs).isEqualTo(before.clock.elapsed + 40_000 + GUARD_MS)

            // The deadline replaces the guard by the alert's cadence, 30 s after the alert started.
            before.advanceSeconds(40)
            assertThat(before.alarms.watchdogAtElapsedMs).isEqualTo(before.clock.elapsed + WATCHDOG_MS)

            before.controller.send(TimerEvent.Poured)
            before.controller.send(TimerEvent.Start)
            assertThat(before.alarms.watchdogAtElapsedMs).isEqualTo(before.clock.elapsed + 30_000 + GUARD_MS)
            before.controller.send(TimerEvent.EndStepNow)
            assertThat(before.alarms.watchdogAtElapsedMs).isNull()
        }

    @Test
    fun `a long step whose deadline alarm was swallowed alerts when only the guard starts a new process`() = runTest {
        before.controller.startSession(brewStart(infusion(3 * 3_600, tempC = 5), method = BrewMethod.COLD_BREW))
        before.controller.send(TimerEvent.Start)
        assertThat(before.active.longStep).isTrue()
        val guard = checkNotNull(before.alarms.watchdogAtElapsedMs)

        // Auto Backup skipped the DEADLINE broadcast and killed the process (D-068); only the guard comes.
        val after = before.processRestarted()
        after.clock.advanceBy(guard - after.clock.elapsed)
        after.controller.onAlarm(TimerAlarmKind.ALERT_WATCHDOG)

        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started).hasSize(1)
        assertThat(after.service.foreground?.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.service.fullScreen).isTrue()
        assertThat(after.alarms.watchdogAtElapsedMs).isEqualTo(after.clock.elapsed + WATCHDOG_MS)

        // The deadline alarm, late after all, finds nothing due: no second alert.
        after.controller.onAlarm(TimerAlarmKind.DEADLINE)
        assertThat(after.alert.started).hasSize(1)
    }

    @Test
    fun `when both the deadline and the guard come, a live process alerts once, in either order`() = runTest {
        before.controller.startSession(brewStart(infusion(600), infusion(600)))
        before.controller.send(TimerEvent.Start)

        before.clock.advanceBy(600_000)
        before.controller.onAlarm(TimerAlarmKind.DEADLINE)
        before.clock.advanceBy(GUARD_MS)
        before.controller.onAlarm(TimerAlarmKind.ALERT_WATCHDOG)
        assertThat(before.alert.started).hasSize(1)

        before.controller.send(TimerEvent.Poured)
        before.controller.send(TimerEvent.Start)
        // The guard first (the deadline alarm late): it handles the deadline, and the alarm finds it handled.
        before.clock.advanceBy(600_000 + GUARD_MS)
        before.controller.onAlarm(TimerAlarmKind.ALERT_WATCHDOG)
        before.controller.onAlarm(TimerAlarmKind.DEADLINE)
        assertThat(before.alert.started).hasSize(2)
        assertThat(before.alert.started.map { it.stepIndex }).containsExactly(0, 1).inOrder()
    }

    private companion object {
        const val WATCHDOG_MS = 30_000L
        const val GUARD_MS = 90_000L
    }
}
