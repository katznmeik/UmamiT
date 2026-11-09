package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.PlannedStep
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
 * Two brew sessions survive process death and a reboot (04 §7.2, §7.3; D-108, rule 8): the slots are restored in
 * the order `BREW`, `BREW_2`, `QUICK`, each in its own slot; a slot recovers held while another slot's alert rings,
 * also one that rang before the process died (the alert that rang first keeps the sound); then the arbitration
 * settles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TwoBrewsRecoveryTest {
    private val before = ControllerHarness()

    @Before
    fun setUp() = runTest {
        before.teaWithStock()
        before.teaWithStock(ControllerHarness.TEA_2_ID, ControllerHarness.TEA_2_NAME)
    }

    @After
    fun tearDown() = before.close()

    private fun ControllerHarness.active(slot: TimerSlot) = controller.state(slot).value as TimerUiState.Active

    private fun secondTea(vararg steps: PlannedStep) =
        brewStart(*steps, teaId = ControllerHarness.TEA_2_ID, teaName = ControllerHarness.TEA_2_NAME)

    @Test
    fun `two brews killed while running both come back in their slots and alert at their deadlines`() = runTest {
        before.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        before.controller.send(TimerSlot.BREW, TimerEvent.Start)
        before.controller.startSession(secondTea(infusion(120, preAlert = 0)))
        before.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        val first = SessionKind.Brew(checkNotNull(before.active(TimerSlot.BREW).sessionId))
        val second = SessionKind.Brew(checkNotNull(before.active(TimerSlot.BREW_2).sessionId))
        before.advanceSeconds(20)

        val after = before.processRestarted()
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.active(TimerSlot.BREW).kind).isEqualTo(first)
        assertThat(after.active(TimerSlot.BREW_2).kind).isEqualTo(second)
        assertThat(after.active(TimerSlot.BREW_2).teaName).isEqualTo(ControllerHarness.TEA_2_NAME)
        assertThat(after.alarms.armed(TimerSlot.BREW_2).deadlineElapsedMs)
            .isEqualTo(after.active(TimerSlot.BREW_2).deadlineElapsedMs)
        assertThat(after.service.foreground?.slot).isEqualTo(TimerSlot.BREW)
        assertThat(after.notifier.other?.slot).isEqualTo(TimerSlot.BREW_2)
        assertThat(after.sessions.inProgressSessionIds()).containsExactly(first.sessionId, second.sessionId)

        after.advanceSeconds(40)
        assertThat(after.alert.playing?.kind).isEqualTo(first)
        after.controller.send(TimerSlot.BREW, TimerEvent.Poured)
        after.advanceSeconds(60)
        assertThat(after.alert.playing?.kind).isEqualTo(second)
    }

    @Test
    fun `a ringing brew and a held one killed - the ringing one is replayed, the held one waits for it`() = runTest {
        before.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        before.controller.send(TimerSlot.BREW, TimerEvent.Start)
        before.advanceSeconds(10)
        before.controller.startSession(secondTea(infusion(40, preAlert = 0)))
        before.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        before.advanceSeconds(55)
        val first = SessionKind.Brew(checkNotNull(before.active(TimerSlot.BREW).sessionId))
        val second = SessionKind.Brew(checkNotNull(before.active(TimerSlot.BREW_2).sessionId))
        assertThat(before.active(TimerSlot.BREW).alertHeld).isTrue()
        assertThat(before.active(TimerSlot.BREW_2).ringing).isTrue()

        val after = before.processRestarted()
        after.clock.advanceBy(5_000)
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.alert.started.map { it.kind }).containsExactly(second)
        assertThat(after.active(TimerSlot.BREW_2).ringing).isTrue()
        assertThat(after.active(TimerSlot.BREW).alertHeld).isTrue()

        after.controller.send(TimerSlot.BREW_2, TimerEvent.Poured)

        assertThat(after.alert.started.map { it.kind }).containsExactly(second, first).inOrder()
        assertThat(after.active(TimerSlot.BREW).ringing).isTrue()
    }

    @Test
    fun `two brews past their deadlines after a death - the one whose deadline came first rings`() = runTest {
        before.controller.startSession(brewStart(infusion(62, preAlert = 0)))
        before.controller.send(TimerSlot.BREW, TimerEvent.Start)
        before.controller.startSession(secondTea(infusion(60, preAlert = 0)))
        before.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        val first = SessionKind.Brew(checkNotNull(before.active(TimerSlot.BREW).sessionId))
        val second = SessionKind.Brew(checkNotNull(before.active(TimerSlot.BREW_2).sessionId))
        before.advanceSeconds(10)

        val after = before.processRestarted()
        after.clock.advanceBy(60_000)
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.alert.started.map { it.kind }).containsExactly(second)
        assertThat(after.active(TimerSlot.BREW_2).ringing).isTrue()
        assertThat(after.active(TimerSlot.BREW).alertHeld).isTrue()

        after.controller.send(TimerSlot.BREW_2, TimerEvent.Poured)

        assertThat(after.alert.started.map { it.kind }).containsExactly(second, first).inOrder()
    }

    @Test
    fun `a recovered alert whose sound had ended holds nothing - the other brew past its deadline rings`() = runTest {
        // D-109: the first brew alerts, its sound ended before the kill; the second's deadline passes while dead.
        before.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        before.controller.send(TimerSlot.BREW, TimerEvent.Start)
        before.controller.startSession(secondTea(infusion(90, preAlert = 0)))
        before.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        before.advanceSeconds(60)
        before.advanceSeconds(15)
        before.controller.alertCompleted(checkNotNull(before.alert.playing))
        val second = SessionKind.Brew(checkNotNull(before.active(TimerSlot.BREW_2).sessionId))

        val after = before.processRestarted()
        after.clock.advanceBy(30_000)
        after.controller.recover(RecoveryTrigger.APP_START)

        // The first is not replayed (it played), so it holds nothing.
        assertThat(after.alert.started.map { it.kind }).containsExactly(second)
        assertThat(after.active(TimerSlot.BREW_2).ringing).isTrue()
        assertThat(after.active(TimerSlot.BREW).phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.active(TimerSlot.BREW).alertHeld).isFalse()
        assertThat(after.service.foreground?.slot).isEqualTo(TimerSlot.BREW_2)
    }

    @Test
    fun `a brew whose deadline passed while dead never takes the sound from a brew already ringing`() = runTest {
        // The second brew rings; the first's deadline comes only after the process died.
        before.controller.startSession(brewStart(infusion(120, preAlert = 0)))
        before.controller.send(TimerSlot.BREW, TimerEvent.Start)
        before.controller.startSession(secondTea(infusion(60, preAlert = 0)))
        before.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        before.advanceSeconds(60)
        val first = SessionKind.Brew(checkNotNull(before.active(TimerSlot.BREW).sessionId))
        val second = SessionKind.Brew(checkNotNull(before.active(TimerSlot.BREW_2).sessionId))
        assertThat(before.active(TimerSlot.BREW_2).ringing).isTrue()
        assertThat(before.active(TimerSlot.BREW).phase).isEqualTo(TimerPhase.RUNNING)

        val after = before.processRestarted()
        after.clock.advanceBy(90_000)
        after.controller.recover(RecoveryTrigger.APP_START)

        // The slot order recovers the first brew first; the second, which rang first, keeps the sound.
        assertThat(after.alert.started.map { it.kind }).containsExactly(second)
        assertThat(after.active(TimerSlot.BREW_2).ringing).isTrue()
        assertThat(after.active(TimerSlot.BREW).alertHeld).isTrue()
        assertThat((after.store.stored(TimerSlot.BREW) as TimerState.Alerting).held).isTrue()
        assertThat(after.alarms.armed(TimerSlot.BREW).overtimeTimeoutAtElapsedMs).isNull()

        after.controller.send(TimerSlot.BREW_2, TimerEvent.Poured)

        assertThat(after.alert.started.map { it.kind }).containsExactly(second, first).inOrder()
        assertThat(after.active(TimerSlot.BREW).ringing).isTrue()
        // Its overtime since its own deadline, 30 s before the restart.
        assertThat(after.active(TimerSlot.BREW).display(after.clock.elapsed).overtimeSeconds).isEqualTo(30)
    }

    @Test
    fun `reboot with two brews - one notice names both, and each alerts at its deadline`() = runTest {
        before.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        before.controller.send(TimerSlot.BREW, TimerEvent.Start)
        before.controller.startSession(secondTea(infusion(120, preAlert = 0)))
        before.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        before.advanceSeconds(10)

        before.clock.reboot(offMs = 20_000, upMs = 15_000)
        val after = before.processRestarted()
        after.controller.recover(RecoveryTrigger.BOOT_COMPLETED)

        assertThat(after.notifier.resumeNotices.map { notice -> notice.map { it.slot } })
            .containsExactly(listOf(TimerSlot.BREW, TimerSlot.BREW_2))
        after.advanceSeconds(15)
        assertThat(after.active(TimerSlot.BREW).ringing).isTrue()
        after.controller.send(TimerSlot.BREW, TimerEvent.Poured)
        after.advanceSeconds(60)
        assertThat(after.active(TimerSlot.BREW_2).ringing).isTrue()
        assertThat(after.alert.started).hasSize(2)
    }

    @Test
    fun `a brew in the second slot and a quick timer killed both come back in their slots`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.startSession(secondTea(infusion(90, preAlert = 0)))
        before.controller.send(TimerSlot.BREW, TimerEvent.Discard)
        before.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        before.controller.startQuickTimer(60, preAlertSeconds = 0, run = true)
        val brew = SessionKind.Brew(checkNotNull(before.active(TimerSlot.BREW_2).sessionId))
        before.advanceSeconds(30)

        val after = before.processRestarted()
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.controller.state(TimerSlot.BREW).value).isEqualTo(TimerUiState.Idle)
        assertThat(after.active(TimerSlot.BREW_2).kind).isEqualTo(brew)
        assertThat(after.active(TimerSlot.QUICK).phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(after.store.storedPhase(TimerSlot.BREW)).isNull()
        assertThat(after.store.storedPhase(TimerSlot.BREW_2)).isEqualTo(TimerPhase.RUNNING)

        after.advanceSeconds(30)
        assertThat(after.alert.playing?.kind).isEqualTo(SessionKind.Quick)
        after.controller.send(TimerSlot.QUICK, TimerEvent.Poured)
        after.advanceSeconds(30)
        assertThat(after.alert.playing?.kind).isEqualTo(brew)
    }
}
