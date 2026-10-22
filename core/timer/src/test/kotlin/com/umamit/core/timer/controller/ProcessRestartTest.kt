package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.AlarmsReconciled
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
    fun `the deadline alarm in a new process recovers the step and starts the alert full screen`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(20)
        val id = before.sessionId
        val deadline = checkNotNull(before.active.deadlineElapsedMs)

        val after = before.processRestarted()
        after.clock.advanceBy(40_250)
        after.controller.onAlarm(TimerAlarmKind.DEADLINE)

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
    fun `a quick timer killed during its step alerts after the restart`() = runTest {
        before.controller.startQuickTimer(14)
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(3)

        val after = before.processRestarted()
        after.clock.advanceBy(11_000)
        after.controller.onAlarm(TimerAlarmKind.DEADLINE)

        assertThat(after.active.kind).isEqualTo(SessionKind.Quick)
        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started).hasSize(1)
        assertThat(after.service.fullScreen).isTrue()
    }

    @Test
    fun `an early alarm in a new process restores the running step and re-arms its alarms`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        val armed = before.alarms.armed

        val after = before.processRestarted()
        after.clock.advanceBy(5_000)
        // An alarm of the step arrives before anything is due (a stale or early delivery).
        after.controller.onAlarm(TimerAlarmKind.PRE_ALERT)

        assertThat(after.active.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(after.alarms.armed).isEqualTo(armed)
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
        after.controller.onAlarm(TimerAlarmKind.DEADLINE)
        after.controller.onAlarm(TimerAlarmKind.DEADLINE)
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
        after.controller.onAlarm(TimerAlarmKind.DEADLINE)

        assertThat(after.ui).isEqualTo(TimerUiState.Idle)
        assertThat(after.store.storedPhase()).isNull()
        assertThat(after.log.calls).contains(AlarmsReconciled(DueTimes.NONE))
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.SnapshotUnreadable>()).hasSize(1)
        assertThat(after.service.running).isFalse()
    }

    @Test
    fun `a failed read is logged and recovery is tried again in the next turn`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)

        val after = before.processRestarted()
        after.store.failNextReads(1)
        after.clock.advanceBy(60_000)
        after.controller.onAlarm(TimerAlarmKind.PRE_ALERT)

        assertThat(after.ui).isEqualTo(TimerUiState.Idle)
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.RecoveryFailed>()).hasSize(1)
        assertThat(after.store.storedPhase()).isEqualTo(TimerPhase.RUNNING)

        after.controller.onAlarm(TimerAlarmKind.DEADLINE)

        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started).hasSize(1)
    }

    @Test
    fun `a new process without a persisted timer stays idle`() = runTest {
        val after = before.processRestarted()

        after.controller.onAlarm(TimerAlarmKind.MIDNIGHT)

        assertThat(after.ui).isEqualTo(TimerUiState.Idle)
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.EventHandled>()).isEmpty()
        assertThat(after.service.starts).isEmpty()
    }
}
