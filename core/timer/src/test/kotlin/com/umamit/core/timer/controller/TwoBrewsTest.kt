package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerState
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two brew sessions at once (04 §3.3, §9.3; GEN-03, TMR-21, D-108): a second tea's session takes the free brew
 * slot `BREW_2`, each session's events act on itself only, the first alarm rings and a brew deadline due while the
 * other brew rings is held, then rings after *Abgegossen* with its overtime and its own 10 min from ringing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TwoBrewsTest {
    private val h = ControllerHarness()

    @Before
    fun setUp() = runTest {
        h.teaWithStock()
        h.teaWithStock(ControllerHarness.TEA_2_ID, ControllerHarness.TEA_2_NAME, priceCents = 1_200)
    }

    @After
    fun tearDown() = h.close()

    private fun ui(slot: TimerSlot) = h.controller.state(slot).value

    private fun active(slot: TimerSlot) = ui(slot) as TimerUiState.Active

    private fun secondTea(vararg steps: PlannedStep) =
        brewStart(*steps, teaId = ControllerHarness.TEA_2_ID, teaName = ControllerHarness.TEA_2_NAME)

    @Test
    fun `a second tea's session takes the second brew slot, and both wait and steep side by side`() = runTest {
        assertThat(h.controller.startSession(brewStart(infusion(120, preAlert = 0))))
            .isEqualTo(TransitionOutcome.Accepted)
        val first = active(TimerSlot.BREW).sessionId

        assertThat(h.controller.startSession(secondTea(infusion(60, preAlert = 0))))
            .isEqualTo(TransitionOutcome.Accepted)

        val second = active(TimerSlot.BREW_2)
        assertThat(second.slot).isEqualTo(TimerSlot.BREW_2)
        assertThat(second.teaName).isEqualTo(ControllerHarness.TEA_2_NAME)
        assertThat(second.phase).isEqualTo(TimerPhase.READY)
        assertThat(active(TimerSlot.BREW).sessionId).isEqualTo(first)
        assertThat(active(TimerSlot.BREW).phase).isEqualTo(TimerPhase.READY)
        assertThat(h.sessions.inProgressSessionIds()).containsExactly(first, second.sessionId)
        // The new session is in front of the screens.
        assertThat(h.active.slot).isEqualTo(TimerSlot.BREW_2)

        h.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        h.advanceSeconds(10)
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)

        assertThat(active(TimerSlot.BREW).phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(active(TimerSlot.BREW_2).phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(h.store.storedPhase(TimerSlot.BREW)).isEqualTo(TimerPhase.RUNNING)
        assertThat(h.store.storedPhase(TimerSlot.BREW_2)).isEqualTo(TimerPhase.RUNNING)
        assertThat(h.alarms.armed(TimerSlot.BREW_2).deadlineElapsedMs)
            .isEqualTo(active(TimerSlot.BREW_2).deadlineElapsedMs)
        // The Live Update is the timer that ends next; the other is the quiet notification 5.
        assertThat(h.service.foreground?.slot).isEqualTo(TimerSlot.BREW_2)
        assertThat(h.notifier.other?.slot).isEqualTo(TimerSlot.BREW)

        h.controller.send(TimerSlot.BREW, TimerEvent.Pause)

        assertThat(active(TimerSlot.BREW).phase).isEqualTo(TimerPhase.PAUSED)
        assertThat(active(TimerSlot.BREW_2).phase).isEqualTo(TimerPhase.RUNNING)
    }

    @Test
    fun `the first deadline rings, the other brew is held and rings after Abgegossen with its overtime`() = runTest {
        h.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.advanceSeconds(10)
        h.controller.startSession(secondTea(infusion(60, preAlert = 0)))
        h.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        val first = SessionKind.Brew(checkNotNull(active(TimerSlot.BREW).sessionId))
        val second = SessionKind.Brew(checkNotNull(active(TimerSlot.BREW_2).sessionId))

        h.advanceSeconds(50)
        assertThat(h.alert.playing?.kind).isEqualTo(first)

        h.advanceSeconds(10)

        assertThat(active(TimerSlot.BREW).ringing).isTrue()
        assertThat(active(TimerSlot.BREW_2).alertHeld).isTrue()
        assertThat(h.alert.started.map { it.kind }).containsExactly(first)
        assertThat(h.alarms.armed(TimerSlot.BREW_2).overtimeTimeoutAtElapsedMs).isNull()
        assertThat((h.store.stored(TimerSlot.BREW_2) as TimerState.Alerting).held).isTrue()
        assertThat(h.active.slot).isEqualTo(TimerSlot.BREW)
        assertThat(h.service.foreground?.slot).isEqualTo(TimerSlot.BREW)
        assertThat(h.notifier.other?.alertHeld).isTrue()

        // The first alert rings for 8 min before *Abgegossen*.
        h.advanceSeconds(8 * 60L)
        h.controller.send(TimerSlot.BREW, TimerEvent.Poured)

        assertThat(h.alert.playing?.kind).isEqualTo(second)
        assertThat(active(TimerSlot.BREW_2).ringing).isTrue()
        assertThat(active(TimerSlot.BREW_2).display(h.clock.elapsed).overtimeSeconds).isEqualTo(8 * 60L)
        assertThat(h.active.slot).isEqualTo(TimerSlot.BREW_2)
        assertThat(h.service.foreground?.slot).isEqualTo(TimerSlot.BREW_2)
        assertThat(h.service.fullScreen).isTrue()
        // Its silent end counts 10 min from its release, not from its deadline (D-108).
        assertThat(h.alarms.armed(TimerSlot.BREW_2).overtimeTimeoutAtElapsedMs).isEqualTo(h.clock.elapsed + 600_000)

        h.advanceSeconds(9 * 60L)
        assertThat(active(TimerSlot.BREW_2).phase).isEqualTo(TimerPhase.ALERTING)

        h.advanceSeconds(60)
        assertThat(active(TimerSlot.BREW_2).phase).isEqualTo(TimerPhase.FINISHED)
        assertThat(h.alert.started.map { it.kind }).containsExactly(first, second).inOrder()
    }

    @Test
    fun `a held brew rings once the first alert's sound has ended, the first still alerting silently`() = runTest {
        // D-109: the first brew is not poured; its Alarmdauer ends 10 s after its deadline.
        h.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.advanceSeconds(10)
        h.controller.startSession(secondTea(infusion(55, preAlert = 0)))
        h.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        val first = SessionKind.Brew(checkNotNull(active(TimerSlot.BREW).sessionId))
        val second = SessionKind.Brew(checkNotNull(active(TimerSlot.BREW_2).sessionId))
        h.advanceSeconds(50)
        val firstDeadline = checkNotNull(active(TimerSlot.BREW).deadlineElapsedMs)
        h.advanceSeconds(5)
        assertThat(active(TimerSlot.BREW_2).alertHeld).isTrue()
        val firstAlert = checkNotNull(h.alert.playing)
        h.advanceSeconds(5)

        h.controller.alertCompleted(firstAlert)

        assertThat(h.alert.playing?.kind).isEqualTo(second)
        assertThat(active(TimerSlot.BREW_2).ringing).isTrue()
        assertThat(active(TimerSlot.BREW_2).display(h.clock.elapsed).overtimeSeconds).isEqualTo(5)
        assertThat(h.alarms.armed(TimerSlot.BREW_2).overtimeTimeoutAtElapsedMs).isEqualTo(h.clock.elapsed + 600_000)
        // The first stays „Jetzt abgießen!" without sound, its silent end 10 min after its own deadline.
        assertThat(active(TimerSlot.BREW).phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(active(TimerSlot.BREW).alertHeld).isFalse()
        assertThat(h.alarms.armed(TimerSlot.BREW).overtimeTimeoutAtElapsedMs).isEqualTo(firstDeadline + 600_000)
        // The released alert takes the screens and the alert notification; the first is notification 5.
        assertThat(h.active.slot).isEqualTo(TimerSlot.BREW_2)
        assertThat(h.service.foreground?.slot).isEqualTo(TimerSlot.BREW_2)
        assertThat(h.service.fullScreen).isTrue()
        assertThat(h.notifier.other?.slot).isEqualTo(TimerSlot.BREW)
        assertThat(h.notifier.other?.phase).isEqualTo(TimerPhase.ALERTING)

        // *Abgegossen* on the first never cuts the second's sound.
        h.controller.send(TimerSlot.BREW, TimerEvent.Poured)

        assertThat(h.alert.playing?.kind).isEqualTo(second)
        assertThat(h.alert.started.map { it.kind }).containsExactly(first, second).inOrder()
        assertThat(h.service.foreground?.slot).isEqualTo(TimerSlot.BREW_2)
    }

    @Test
    fun `a brew deadline after the other brew's sound has ended rings at once`() = runTest {
        h.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.advanceSeconds(10)
        h.controller.startSession(secondTea(infusion(70, preAlert = 0)))
        h.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        val second = SessionKind.Brew(checkNotNull(active(TimerSlot.BREW_2).sessionId))
        h.advanceSeconds(65)
        h.controller.alertCompleted(checkNotNull(h.alert.playing))

        h.advanceSeconds(15)

        assertThat(h.alert.playing?.kind).isEqualTo(second)
        assertThat(active(TimerSlot.BREW_2).ringing).isTrue()
        // It rang at its deadline, so its silent end is 10 min after it.
        assertThat(h.alarms.armed(TimerSlot.BREW_2).overtimeTimeoutAtElapsedMs)
            .isEqualTo(checkNotNull(active(TimerSlot.BREW_2).deadlineElapsedMs) + 600_000)
        assertThat(h.active.slot).isEqualTo(TimerSlot.BREW_2)
        assertThat(h.service.foreground?.slot).isEqualTo(TimerSlot.BREW_2)
        assertThat(active(TimerSlot.BREW).phase).isEqualTo(TimerPhase.ALERTING)
    }

    @Test
    fun `the second brew goes before a quick timer due at the same instant`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.startSession(secondTea(infusion(60, preAlert = 0)))
        h.controller.send(TimerSlot.BREW, TimerEvent.Discard)
        h.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        h.controller.startQuickTimer(60, preAlertSeconds = 0, run = true)
        val brew = SessionKind.Brew(checkNotNull(active(TimerSlot.BREW_2).sessionId))

        h.advanceSeconds(60)

        assertThat(h.alert.started.map { it.kind }).containsExactly(brew)
        assertThat(active(TimerSlot.BREW_2).ringing).isTrue()
        assertThat(active(TimerSlot.QUICK).alertHeld).isTrue()

        h.controller.send(TimerSlot.BREW_2, TimerEvent.Poured)

        assertThat(h.alert.playing?.kind).isEqualTo(SessionKind.Quick)
    }

    @Test
    fun `a start of a tea with an active session is refused naming that session, in either slot`() = runTest {
        h.controller.startSession(secondTea(infusion(60)))
        h.controller.startSession(brewStart(infusion(60)))
        val inSecondSlot = SessionKind.Brew(checkNotNull(active(TimerSlot.BREW_2).sessionId))
        val calls = h.log.calls.size

        val again = h.controller.startSession(brewStart(infusion(30)))

        assertThat(again).isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(inSecondSlot)))
        assertThat(h.log.calls).hasSize(calls)
        assertThat(h.sessions.inProgressSessionIds()).hasSize(2)
        assertThat(active(TimerSlot.BREW_2).steps.single().plannedSeconds).isEqualTo(60)
    }

    @Test
    fun `a third start is refused naming both timers, and the one chosen ends to make room`() = runTest {
        h.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        h.controller.startSession(secondTea(infusion(60, preAlert = 0), infusion(60, preAlert = 0)))
        val first = checkNotNull(active(TimerSlot.BREW).sessionId)
        val second = checkNotNull(active(TimerSlot.BREW_2).sessionId)
        h.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        h.advanceSeconds(60)
        h.controller.send(TimerSlot.BREW_2, TimerEvent.Poured)
        val calls = h.log.calls.size
        val third = brewStart(infusion(45), teaId = null, teaName = "Ohne Tee")

        val refused = h.controller.startSession(third)
        val quickRefused = h.controller.startQuickTimer(90, run = true)

        val both = TransitionOutcome.Rejected(RejectionReason.TwoTimersActive(listOf(TimerSlot.BREW, TimerSlot.BREW_2)))
        assertThat(refused).isEqualTo(both)
        assertThat(quickRefused).isEqualTo(both)
        assertThat(h.log.calls).hasSize(calls)

        // „Welchen Timer beenden?" → the second tea, with a completed infusion: saved, „Sitzung gespeichert".
        assertThat(h.controller.finishActiveAndStart(third, ending = TimerSlot.BREW_2))
            .isEqualTo(TransitionOutcome.Accepted)

        assertThat(h.sessions.getSession(second)!!.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(h.notifier.sessionsSaved).containsExactly(second)
        assertThat(h.savedSheet.shown).isEmpty()
        assertThat(active(TimerSlot.BREW_2).teaName).isEqualTo("Ohne Tee")
        assertThat(active(TimerSlot.BREW).sessionId).isEqualTo(first)

        // A quick timer in place of the first, which never brewed: discarded silently.
        assertThat(h.controller.startQuickTimer(90, run = true, ending = TimerSlot.BREW))
            .isEqualTo(TransitionOutcome.Accepted)

        assertThat(h.sessions.getSession(first)).isNull()
        assertThat(h.notifier.sessionsSaved).containsExactly(second)
        assertThat(ui(TimerSlot.BREW)).isEqualTo(TimerUiState.Idle)
        assertThat(active(TimerSlot.QUICK).phase).isEqualTo(TimerPhase.RUNNING)
    }

    @Test
    fun `beside a quick timer a brew start of another tea asks too, and ending the quick timer makes room`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.startQuickTimer(90, run = true)

        val refused = h.controller.startSession(secondTea(infusion(60)))

        assertThat(refused)
            .isEqualTo(
                TransitionOutcome.Rejected(RejectionReason.TwoTimersActive(listOf(TimerSlot.BREW, TimerSlot.QUICK))),
            )
        // A second quick timer still asks only about the quick timer (D-098).
        assertThat(h.controller.startQuickTimer(30, run = true))
            .isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(SessionKind.Quick)))

        assertThat(h.controller.finishActiveAndStart(secondTea(infusion(60)), ending = TimerSlot.QUICK))
            .isEqualTo(TransitionOutcome.Accepted)

        assertThat(ui(TimerSlot.QUICK)).isEqualTo(TimerUiState.Idle)
        assertThat(active(TimerSlot.BREW_2).teaName).isEqualTo(ControllerHarness.TEA_2_NAME)
        assertThat(active(TimerSlot.BREW).phase).isEqualTo(TimerPhase.READY)
    }

    @Test
    fun `each session goes quiet after its own 30 minutes and is saved at its own midnight`() = runTest {
        h.controller.startSession(brewStart(infusion(60, preAlert = 0), infusion(30)))
        val first = checkNotNull(active(TimerSlot.BREW).sessionId)
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.advanceSeconds(60)
        h.controller.send(TimerSlot.BREW, TimerEvent.Poured)
        h.advanceSeconds(20 * 60L)
        h.controller.startSession(secondTea(infusion(60), infusion(30)))
        val second = checkNotNull(active(TimerSlot.BREW_2).sessionId)

        h.advanceSeconds(10 * 60L)

        assertThat(active(TimerSlot.BREW).demoted).isTrue()
        assertThat(active(TimerSlot.BREW_2).demoted).isFalse()

        h.advanceSeconds(20 * 60L)
        assertThat(active(TimerSlot.BREW_2).demoted).isTrue()

        // The first idles since 10:01 and ends at 2026-10-23 00:00; the second, brewed again at 19:00, a day later.
        h.advanceSeconds((19 * 60 - 10 * 60 - 51) * 60L)
        h.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        h.advanceSeconds(60)
        h.controller.send(TimerSlot.BREW_2, TimerEvent.Poured)
        h.advanceSeconds(5 * 3_600L)

        assertThat(ui(TimerSlot.BREW)).isEqualTo(TimerUiState.Idle)
        assertThat(h.sessions.getSession(first)!!.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(h.sessions.getSession(first)!!.autoFinished).isTrue()
        assertThat(active(TimerSlot.BREW_2).sessionId).isEqualTo(second)
        assertThat(h.sessions.getSession(second)!!.status).isEqualTo(SessionStatus.IN_PROGRESS)
    }

    @Test
    fun `two sessions give two journal entries, each with its own stock deduction and cost`() = runTest {
        h.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        h.controller.startSession(secondTea(infusion(30, preAlert = 0)))
        val first = checkNotNull(active(TimerSlot.BREW).sessionId)
        val second = checkNotNull(active(TimerSlot.BREW_2).sessionId)
        h.controller.send(TimerSlot.BREW, TimerEvent.Start)
        h.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        h.advanceSeconds(30)
        h.controller.send(TimerSlot.BREW_2, TimerEvent.Poured)
        h.advanceSeconds(30)
        h.controller.send(TimerSlot.BREW, TimerEvent.Poured)

        h.controller.send(TimerSlot.BREW_2, TimerEvent.FinishSession)
        h.controller.send(TimerSlot.BREW, TimerEvent.FinishSession)

        assertThat(h.sessions.getSession(first)!!.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(h.sessions.getSession(second)!!.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(h.sessions.getSession(first)!!.costCents).isEqualTo(34) // 4 g × 8,50 € / 100 g
        assertThat(h.sessions.getSession(second)!!.costCents).isEqualTo(48) // 4 g × 12,00 € / 100 g
        assertThat(h.stock.observeStock(ControllerHarness.TEA_ID).first().remainingGrams).isEqualTo(96.0)
        assertThat(h.stock.observeStock(ControllerHarness.TEA_2_ID).first().remainingGrams).isEqualTo(96.0)
        assertThat(h.savedSheet.shown).containsExactly(second, first).inOrder()
        assertThat(h.service.running).isFalse()
    }
}
