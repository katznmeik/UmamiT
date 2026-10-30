package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.AlarmRetried
import com.umamit.core.testing.AlarmsReconciled
import com.umamit.core.testing.AlertWatchdogSet
import com.umamit.core.testing.PortCall
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
 * The controller's two slots (04 §3.3, §6.2; D-098, #125): a brew session runs in the brew slot and
 * the quick timer in the quick slot, each with its own alarms and alert watchdog, and neither ever
 * touches the other slot's. Only one timer is active across both, as before v0.3.0 (TMR-21).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerSlotsTest {
    private val h = ControllerHarness()

    @Before
    fun setUp() = runTest { h.teaWithStock() }

    @After
    fun tearDown() = h.close()

    @Test
    fun `a brew step arms, fires and cancels only the brew slot's alarms`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.log.clear()

        h.controller.send(TimerEvent.Start)
        val deadline = checkNotNull(h.active.deadlineElapsedMs)

        assertThat(h.alarms.armed(TimerSlot.BREW).deadlineElapsedMs).isEqualTo(deadline)
        assertThat(h.alarms.watchdogAtElapsedMs(TimerSlot.BREW)).isEqualTo(deadline + AlertWatchdog.GUARD_MS)

        h.clock.advanceBy(deadline - h.clock.elapsed)
        h.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE)

        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(h.alarms.armed(TimerSlot.BREW).overtimeTimeoutAtElapsedMs).isEqualTo(deadline + 600_000)
        assertThat(h.alarms.watchdogAtElapsedMs(TimerSlot.BREW)).isEqualTo(h.clock.elapsed + AlertWatchdog.INTERVAL_MS)

        h.controller.send(TimerEvent.Poured)
        h.controller.send(TimerEvent.FinishSession)

        assertThat(h.alarms.armed(TimerSlot.BREW)).isEqualTo(DueTimes.NONE)
        assertThat(h.alarms.watchdogCancelled(TimerSlot.BREW)).isTrue()
        assertThat(callsIn(TimerSlot.QUICK)).isEmpty()
    }

    @Test
    fun `a quick timer arms, fires and cancels only the quick slot's alarms`() = runTest {
        h.controller.startQuickTimer(60, preAlertSeconds = 0)
        h.log.clear()

        h.controller.send(TimerEvent.Start)
        val deadline = checkNotNull(h.active.deadlineElapsedMs)

        assertThat(h.alarms.armed(TimerSlot.QUICK)).isEqualTo(DueTimes(deadlineElapsedMs = deadline))
        assertThat(h.alarms.watchdogAtElapsedMs(TimerSlot.QUICK)).isEqualTo(deadline + AlertWatchdog.GUARD_MS)

        h.clock.advanceBy(deadline - h.clock.elapsed)
        h.controller.onAlarm(TimerSlot.QUICK, TimerAlarmKind.DEADLINE)

        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(h.alert.playing?.kind).isEqualTo(SessionKind.Quick)
        assertThat(h.alarms.armed(TimerSlot.QUICK).overtimeTimeoutAtElapsedMs).isEqualTo(deadline + 600_000)
        assertThat(h.alarms.watchdogAtElapsedMs(TimerSlot.QUICK)).isEqualTo(h.clock.elapsed + AlertWatchdog.INTERVAL_MS)

        h.controller.send(TimerEvent.Poured)

        assertThat(h.ui).isEqualTo(TimerUiState.Idle)
        assertThat(h.alarms.armed(TimerSlot.QUICK)).isEqualTo(DueTimes.NONE)
        assertThat(h.alarms.watchdogCancelled(TimerSlot.QUICK)).isTrue()
        assertThat(callsIn(TimerSlot.BREW)).isEmpty()
    }

    @Test
    fun `a quick timer cannot start while a brew session is active, and the brew is untouched`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        val id = h.sessionId
        h.controller.send(TimerEvent.Start)
        val brew = h.active
        h.log.clear()

        val outcome = h.controller.startQuickTimer(90, run = true)

        assertThat(outcome)
            .isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(SessionKind.Brew(id))))
        assertThat(h.active).isEqualTo(brew)
        assertThat(h.store.storedPhase()).isEqualTo(TimerPhase.RUNNING)
        assertThat(h.log.calls).isEmpty()
    }

    @Test
    fun `a brew session cannot start while a quick timer is active, and nothing of it is written`() = runTest {
        h.controller.startQuickTimer(90, run = true)
        val quick = h.active
        h.log.clear()

        val outcome = h.controller.startSession(brewStart(infusion(60)))

        assertThat(outcome).isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(SessionKind.Quick)))
        assertThat(h.active).isEqualTo(quick)
        assertThat(h.sessions.inProgressSessionIds()).isEmpty()
        assertThat(h.log.calls).isEmpty()
        // The refusal is logged as before v0.3.0: the active quick timer refused it.
        val refused = h.diagnostics.entriesOf<TimerDiagnostic.EventHandled>().last()
        assertThat(refused.event).isInstanceOf(TimerEvent.StartSession::class.java)
        assertThat(refused.outcome).isEqualTo(outcome)
        assertThat(refused.from to refused.to).isEqualTo(TimerPhase.RUNNING to TimerPhase.RUNNING)
    }

    @Test
    fun `a second quick timer is still refused by the active one`() = runTest {
        h.controller.startQuickTimer(90, run = true)

        val outcome = h.controller.startQuickTimer(30)

        assertThat(outcome).isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(SessionKind.Quick)))
        assertThat(h.active.steps.single().plannedSeconds).isEqualTo(90)
    }

    @Test
    fun `Beenden und neu starten moves the one active timer from the quick to the brew slot`() = runTest {
        h.controller.startQuickTimer(90, run = true)

        h.controller.finishActiveAndStart(brewStart(infusion(45)))
        h.controller.send(TimerEvent.Start)

        assertThat(h.active.kind).isInstanceOf(SessionKind.Brew::class.java)
        assertThat(h.alarms.armed(TimerSlot.QUICK)).isEqualTo(DueTimes.NONE)
        assertThat(h.alarms.watchdogCancelled(TimerSlot.QUICK)).isTrue()
        assertThat(h.alarms.armed(TimerSlot.BREW).deadlineElapsedMs).isEqualTo(h.active.deadlineElapsedMs)
    }

    /** The alarm port calls of [slot] in the shared log. */
    private fun callsIn(slot: TimerSlot): List<PortCall> = h.log.calls.filter {
        (it is AlarmsReconciled && it.slot == slot) ||
            (it is AlertWatchdogSet && it.slot == slot) ||
            (it is AlarmRetried && it.slot == slot)
    }
}
