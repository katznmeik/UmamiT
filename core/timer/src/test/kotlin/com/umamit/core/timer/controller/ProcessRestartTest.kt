package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.AlarmRetried
import com.umamit.core.testing.AlarmsReconciled
import com.umamit.core.testing.ServiceStopped
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A process killed during a step (E3, 04 §6.2, §7.2; #42): the alarm starts a new process,
 * whose controller first recovers the persisted state, once, and then handles the alarm, so
 * the alert still plays with the service and its full-screen notification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProcessRestartTest {
    private val before = ControllerHarness()

    @Before
    fun setUp() = runTest { before.teaWithStock() }

    @After
    fun tearDown() = before.close()

    @Test
    fun `a new process says it has recovered only after the recovered state is published (D-068)`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))

        val after = before.processRestarted()
        // Idle here means "not read yet": the restored timer route waits (GEN-07).
        assertThat(after.ui).isEqualTo(TimerUiState.Idle)
        assertThat(after.controller.recovered.value).isFalse()

        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.controller.recovered.value).isTrue()
        assertThat(after.active.phase).isEqualTo(TimerPhase.READY)
    }

    @Test
    fun `a new process whose stored timer cannot be read does not keep the screens waiting`() = runTest {
        val after = before.processRestarted()
        after.store.failNextReads(3)

        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.controller.recovered.value).isTrue()
    }

    @Test
    fun `the deadline alarm in a new process recovers the step and starts the alert full screen`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(20)
        val id = before.sessionId
        val deadline = checkNotNull(before.active.deadlineElapsedMs)

        val after = before.processRestarted()
        after.clock.advanceBy(40_250)
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)

        // The log keeps the alarm's scheduled time from the recovered step (06 §5.6, TMR-30).
        val received = after.diagnostics.entriesOf<TimerDiagnostic.AlarmReceived>().single()
        assertThat(received.scheduled).isEqualTo(ScheduledTime(TimerAlarmKind.DEADLINE, deadline))
        assertThat(received.scheduled?.lateMs(received.at)).isEqualTo(250L)

        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.active.teaName).isEqualTo(ControllerHarness.TEA_NAME)
        assertThat(after.alert.started)
            .containsExactly(StepAlert(SessionKind.Brew(id), stepIndex = 0, deadlineWallMs = after.clock.wall - 250))
        assertThat(after.service.foreground?.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.service.fullScreen).isTrue()
        assertThat(after.store.storedPhase()).isEqualTo(TimerPhase.ALERTING)
    }

    @Test
    fun `an alarm whose new process the app start recovered first still logs its scheduled time`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        val deadline = checkNotNull(before.active.deadlineElapsedMs)

        // The alarm started the process; Application.onCreate's recovery took the lock first.
        val after = before.processRestarted()
        after.clock.advanceBy(60_250)
        after.controller.recover(RecoveryTrigger.APP_START)
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)

        val received = after.diagnostics.entriesOf<TimerDiagnostic.AlarmReceived>().single()
        assertThat(received.scheduled).isEqualTo(ScheduledTime(TimerAlarmKind.DEADLINE, deadline))
        assertThat(after.alert.started).hasSize(1)
        // A later alarm of the same kind is due in nothing the process holds now.
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.AlarmReceived>().last().scheduled).isNull()
    }

    @Test
    fun `a quick timer killed during its step alerts after the restart`() = runTest {
        before.controller.startQuickTimer(14)
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(3)

        val after = before.processRestarted()
        after.clock.advanceBy(11_000)
        after.controller.onAlarm(TimerSlot.QUICK, TimerAlarmKind.DEADLINE)

        assertThat(after.active.kind).isEqualTo(SessionKind.Quick)
        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started).hasSize(1)
        assertThat(after.service.fullScreen).isTrue()
    }

    @Test
    fun `an early alarm in a new process restores the running step and re-arms its alarms`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        val armed = before.alarms.armed(TimerSlot.BREW)

        val after = before.processRestarted()
        after.clock.advanceBy(5_000)
        // An alarm of the step arrives before anything is due (a stale or early delivery).
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.PRE_ALERT)

        assertThat(after.active.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(after.alarms.armed(TimerSlot.BREW)).isEqualTo(armed)
        assertThat(after.service.foreground?.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(after.alert.started).isEmpty()
        // The process's own wake-up takes over: the deadline comes without another alarm.
        after.advanceSeconds(55)
        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
    }

    @Test
    fun `the state is recovered only once per process`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)

        val after = before.processRestarted()
        after.clock.advanceBy(60_000)
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)
        after.controller.send(TimerEvent.AddStep)

        val recoveries = after.diagnostics.entriesOf<TimerDiagnostic.EventHandled>()
            .filter { it.event == TimerEvent.Recover }
        assertThat(recoveries).hasSize(1)
        assertThat(recoveries.single().source).isEqualTo(EventSource.RECOVERY)
        assertThat(after.alert.started).hasSize(1)
    }

    @Test
    fun `a start in a new process is refused while the persisted session is still active`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        val id = before.sessionId

        val after = before.processRestarted()
        val outcome = after.controller.startQuickTimer(60)

        assertThat(outcome)
            .isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(SessionKind.Brew(id))))
        assertThat(after.active.sessionId).isEqualTo(id)
    }

    @Test
    fun `an unreadable snapshot is cleared and logged instead of running a wrong timer`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)

        val after = before.processRestarted()
        after.store.readAs = { snapshot ->
            snapshot?.copy(activeBrew = snapshot.activeBrew.copy(pausedTotalMs = -1))
        }
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)

        assertThat(after.ui).isEqualTo(TimerUiState.Idle)
        assertThat(after.store.storedPhase()).isNull()
        assertThat(after.log.calls).contains(AlarmsReconciled(TimerSlot.BREW, DueTimes.NONE))
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.SnapshotUnreadable>()).hasSize(1)
        assertThat(after.service.running).isFalse()
        // A notification the old process posted without the service outlives it: it goes too (#46 review O1).
        assertThat(after.log.calls).contains(ServiceStopped)
    }

    @Test
    fun `a failed read is retried in the same turn, so the deadline alarm still alerts`() = runTest {
        before.controller.startSession(brewStart(infusion(60), recipePreAlertSeconds = 0))
        before.controller.send(TimerEvent.Start)

        val after = before.processRestarted()
        after.store.failNextReads(2)
        after.clock.advanceBy(60_000)
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)

        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.RecoveryFailed>()).hasSize(2)
        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started).hasSize(1)
    }

    @Test
    fun `when no read of the turn succeeds, the only alarm comes again and the alert still fires`() = runTest {
        // No pre-alert: the deadline alarm is the only alarm of the killed step.
        before.controller.startSession(brewStart(infusion(60), recipePreAlertSeconds = 0))
        before.controller.send(TimerEvent.Start)

        val after = before.processRestarted()
        after.store.failNextReads(3)
        after.clock.advanceBy(60_000)
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)

        assertThat(after.ui).isEqualTo(TimerUiState.Idle)
        assertThat(after.store.storedPhase()).isEqualTo(TimerPhase.RUNNING)
        // The alarm is set again a few seconds out, and the process's own wake-up retries too.
        assertThat(after.alarms.retries)
            .containsExactly(AlarmRetried(TimerSlot.BREW, TimerAlarmKind.DEADLINE, after.clock.elapsed + 5_000))
        assertThat(after.wakeUp.dueAtElapsedMs).isNotNull()

        after.advanceSeconds(5)

        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started).hasSize(1)
    }

    @Test
    fun `a stored timer that stays unreadable is retried less and less often, then the retries stop`() = runTest {
        before.controller.startSession(brewStart(infusion(60), recipePreAlertSeconds = 0))
        val id = before.sessionId
        before.controller.send(TimerEvent.Start)

        val after = before.processRestarted()
        after.store.failNextReads(Int.MAX_VALUE)
        after.clock.advanceBy(60_000)
        val firstFailure = after.clock.elapsed
        // Each retried alarm arrives at its time (the in-process wake-up is left out here).
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)
        while (after.alarms.retries.size < MAX_RETRIES) {
            val next = after.alarms.retries.last().atMs
            after.clock.advanceBy(next - after.clock.elapsed)
            val count = after.alarms.retries.size
            after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)
            if (after.alarms.retries.size == count) break
        }

        val times = listOf(firstFailure) + after.alarms.retries.map { it.atMs }
        val gaps = times.zipWithNext { a, b -> b - a }
        assertThat(gaps.first()).isEqualTo(5_000L)
        assertThat(gaps).isInOrder()
        assertThat(gaps.last()).isEqualTo(120_000L)
        assertThat(after.alarms.retries.size).isLessThan(MAX_RETRIES)
        val stopped = after.diagnostics.entriesOf<TimerDiagnostic.RecoveryRetriesStopped>().single()
        assertThat(stopped.failingForMs).isAtLeast(10 * 60_000L)
        assertThat(stopped.at.elapsedMs - firstFailure).isEqualTo(stopped.failingForMs)
        // Only the retries stop (D-063): the row, its alarms and its session stay for a later read.
        assertThat(after.wakeUp.dueAtElapsedMs).isNull()
        assertThat(after.log.calls).doesNotContain(AlarmsReconciled(TimerSlot.BREW, DueTimes.NONE))
        assertThat(after.store.storedPhase()).isEqualTo(TimerPhase.RUNNING)
        assertThat(after.sessions.getSession(id)).isNotNull()
        // A later input reads again, and the step comes back.
        after.store.failNextReads(0)
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.OVERTIME_TIMEOUT)
        assertThat(after.active.sessionId).isEqualTo(id)
    }

    @Test
    fun `the in-process retries back off too and stop`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))

        val after = before.processRestarted()
        after.store.failNextReads(Int.MAX_VALUE)
        after.controller.recover(RecoveryTrigger.APP_START)
        after.advanceSeconds(3_600)

        val failedTurns = after.diagnostics.entriesOf<TimerDiagnostic.RecoveryFailed>().map {
            it.at.elapsedMs
        }.distinct()
        assertThat(failedTurns.size).isLessThan(MAX_RETRIES)
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.RecoveryRetriesStopped>()).hasSize(1)
        assertThat(after.wakeUp.dueAtElapsedMs).isNull()
    }

    @Test
    fun `reads failing for 12 min from the app start never lose a 3-hour cold brew`() = runTest {
        // #46 review S1: the retries stop after 11 min, but the step is still hours from its deadline.
        before.controller.startSession(brewStart(infusion(3 * 3_600, tempC = 20), method = BrewMethod.COLD_BREW))
        val id = before.sessionId
        before.controller.send(TimerEvent.Start)
        val deadline = checkNotNull(before.active.deadlineElapsedMs)

        val after = before.processRestarted()
        after.clock.advanceBy(5 * 60_000)
        after.store.failNextReads(Int.MAX_VALUE)
        after.controller.recover(RecoveryTrigger.APP_START)
        after.advanceSeconds(12 * 60)
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.RecoveryRetriesStopped>()).hasSize(1)

        // The database comes back; the step's own deadline alarm arrives.
        after.store.failNextReads(0)
        after.clock.advanceBy(deadline - after.clock.elapsed)
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)

        assertThat(after.log.calls).doesNotContain(AlarmsReconciled(TimerSlot.BREW, DueTimes.NONE))
        assertThat(after.active.sessionId).isEqualTo(id)
        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started).hasSize(1)
    }

    @Test
    fun `a new process without a persisted timer stays idle`() = runTest {
        val after = before.processRestarted()

        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.MIDNIGHT)

        assertThat(after.ui).isEqualTo(TimerUiState.Idle)
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.EventHandled>()).isEmpty()
        assertThat(after.service.starts).isEmpty()
    }

    private companion object {
        /** More than the backoff ever needs: a retry loop that does not stop fails the test. */
        const val MAX_RETRIES = 30
    }
}
