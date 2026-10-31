package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The recovery table of 04 §7.2 at the controller (#46; spec #40 seam 2): what a new process
 * finds when the app starts, after a reboot (`BOOT_COMPLETED`, E2) and after an app update
 * (`MY_PACKAGE_REPLACED`, 04 §7.5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecoveryTableTest {
    private val before = ControllerHarness()

    @Before
    fun setUp() = runTest { before.teaWithStock() }

    @After
    fun tearDown() = before.close()

    @Test
    fun `process death mid-step - the app start restores the step, its alarms and the service`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(20)
        val armed = before.alarms.armed(TimerSlot.BREW)

        val after = before.processRestarted()
        after.clock.advanceBy(5_000)
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.active.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(after.active.teaName).isEqualTo(ControllerHarness.TEA_NAME)
        assertThat(after.alarms.armed(TimerSlot.BREW)).isEqualTo(armed)
        assertThat(after.service.foreground?.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(after.recovered()).isEqualTo(
            Decision(
                TimerPhase.RUNNING,
                TimerPhase.RUNNING,
                rebooted = false,
                RecoveryDecision.RESTORED,
                service = true,
            ),
        )
        // The process's own wake-up alerts at the deadline.
        after.advanceSeconds(35)
        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started).hasSize(1)
    }

    @Test
    fun `same process - a recovery request changes nothing and is logged`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        before.log.clear()
        val earlier = before.diagnostics.entries.size

        before.controller.recover(RecoveryTrigger.BOOT_COMPLETED)

        assertThat(before.log.calls).isEmpty()
        assertThat(before.active.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(before.diagnostics.entries.drop(earlier)).containsExactly(
            TimerDiagnostic.RecoveryRequested(
                before.clock.reading(),
                RecoveryTrigger.BOOT_COMPLETED,
                alreadyRecovered = true,
            ),
        )
    }

    @Test
    fun `a recovery request in a new process is logged before the recovery it starts`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))

        val after = before.processRestarted()
        after.controller.recover(RecoveryTrigger.PACKAGE_REPLACED)

        val requested = after.diagnostics.entries.first()
        assertThat(requested).isEqualTo(
            TimerDiagnostic.RecoveryRequested(
                after.clock.reading(),
                RecoveryTrigger.PACKAGE_REPLACED,
                alreadyRecovered = false,
            ),
        )
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.EventHandled>().single().event)
            .isEqualTo(TimerEvent.Recover)
    }

    @Test
    fun `deadline passed less than 10 min ago - the alert is replayed full screen`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        val deadlineWall = before.clock.wall + 60_000

        val after = before.processRestarted()
        after.clock.advanceBy(60_000 + 3 * MINUTE_MS)
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started.single().deadlineWallMs).isEqualTo(deadlineWall)
        assertThat(after.service.foreground?.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.service.fullScreen).isTrue()
        assertThat(
            after.alarms.armed(TimerSlot.BREW).overtimeTimeoutAtElapsedMs,
        ).isEqualTo(after.clock.elapsed + 7 * MINUTE_MS)
        assertThat(after.recovered()).isEqualTo(
            Decision(TimerPhase.RUNNING, TimerPhase.ALERTING, rebooted = false, RecoveryDecision.ALERT_REPLAYED),
        )
    }

    @Test
    fun `deadline passed 10 min or more ago - poured on time silently, the next step waits`() = runTest {
        before.controller.startSession(brewStart(infusion(60), infusion(30)))
        val id = before.sessionId
        before.controller.send(TimerEvent.Start)

        val after = before.processRestarted()
        after.clock.advanceBy(60_000 + 11 * MINUTE_MS)
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.active.phase).isEqualTo(TimerPhase.READY)
        assertThat(after.active.stepIndex).isEqualTo(1)
        assertThat(after.alert.started).isEmpty()
        assertThat(after.savedSheet.shown).isEmpty()
        val step = after.sessions.getSession(id)!!.steps.first()
        assertThat(step.overtimeAutoAck).isTrue()
        assertThat(step.actualSeconds).isEqualTo(60)
        assertThat(step.overtimeSeconds).isEqualTo(0)
        assertThat(after.recovered()).isEqualTo(
            Decision(TimerPhase.RUNNING, TimerPhase.READY, rebooted = false, RecoveryDecision.OVERTIME_ACKNOWLEDGED),
        )
    }

    @Test
    fun `reboot during a step - BOOT_COMPLETED rebases it on the new clock and alerts at its deadline`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(10)

        // Off for 20 s, unlocked 15 s after the boot: 15 s of the step are left.
        before.clock.reboot(offMs = 20_000, upMs = 15_000)
        val after = before.processRestarted()
        after.controller.recover(RecoveryTrigger.BOOT_COMPLETED)

        assertThat(after.active.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(after.active.deadlineElapsedMs).isEqualTo(30_000L)
        assertThat(after.alarms.armed(TimerSlot.BREW).deadlineElapsedMs).isEqualTo(30_000L)
        assertThat(after.service.foreground?.phase).isEqualTo(TimerPhase.RUNNING)
        // „Dein …-Timer läuft weiter" (04 §7.3).
        assertThat(after.notifier.timersResumed.map { it.phase }).containsExactly(TimerPhase.RUNNING)
        assertThat(after.recovered()).isEqualTo(
            Decision(TimerPhase.RUNNING, TimerPhase.RUNNING, rebooted = true, RecoveryDecision.RESTORED),
        )
        after.advanceSeconds(15)
        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started).hasSize(1)
    }

    @Test
    fun `reboot with a brew step and a quick timer - one notice names both (04 §7_3)`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerSlot.BREW, TimerEvent.Start)
        before.controller.startQuickTimer(120, preAlertSeconds = 0, run = true)
        before.advanceSeconds(10)

        before.clock.reboot(offMs = 20_000, upMs = 15_000)
        val after = before.processRestarted()
        after.controller.recover(RecoveryTrigger.BOOT_COMPLETED)

        assertThat(after.controller.state(TimerSlot.BREW).value).isInstanceOf(TimerUiState.Active::class.java)
        assertThat(after.controller.state(TimerSlot.QUICK).value).isInstanceOf(TimerUiState.Active::class.java)
        // One notification 3, never the quick timer's replacing the brew's.
        assertThat(after.notifier.resumeNotices.map { notice -> notice.map { it.slot } })
            .containsExactly(listOf(TimerSlot.BREW, TimerSlot.QUICK))
    }

    @Test
    fun `reboot past the deadline - the alert comes after the first unlock, with overtime (E2)`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)

        // The deadline passed while the phone was off; unlocked 2 min after it.
        before.clock.reboot(offMs = 2 * MINUTE_MS, upMs = MINUTE_MS)
        val after = before.processRestarted()
        after.controller.recover(RecoveryTrigger.BOOT_COMPLETED)

        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.active.display(after.clock.elapsed).overtimeSeconds).isEqualTo(120L)
        assertThat(after.alert.started).hasSize(1)
        assertThat(after.service.fullScreen).isTrue()
        assertThat(
            after.alarms.armed(TimerSlot.BREW).overtimeTimeoutAtElapsedMs,
        ).isEqualTo(after.clock.elapsed + 8 * MINUTE_MS)
        // The alert is the only notice: nothing competes with it (D-044).
        assertThat(after.notifier.timersResumed).isEmpty()
    }

    @Test
    fun `app update while paused - MY_PACKAGE_REPLACED brings the service and its notification back`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(20)
        before.controller.send(TimerEvent.Pause)

        val after = before.processRestarted()
        after.clock.advanceBy(MINUTE_MS)
        after.controller.recover(RecoveryTrigger.PACKAGE_REPLACED)

        assertThat(after.active.phase).isEqualTo(TimerPhase.PAUSED)
        assertThat(after.active.pausedRemainingMs).isEqualTo(40_000L)
        assertThat(after.service.foreground?.phase).isEqualTo(TimerPhase.PAUSED)
        assertThat(after.alarms.armed(TimerSlot.BREW).idleDemoteAtWallMs).isNotNull()
        // No reboot: no „läuft weiter" notice.
        assertThat(after.notifier.timersResumed).isEmpty()
    }

    @Test
    fun `waiting 30 min while dead - restored quiet, without the service`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))

        val after = before.processRestarted()
        after.clock.advanceBy(31 * MINUTE_MS)
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.active.phase).isEqualTo(TimerPhase.READY)
        assertThat(after.active.demoted).isTrue()
        assertThat(after.service.starts).isEmpty()
        assertThat(after.notifier.shown?.demoted).isTrue()
        assertThat(after.recovered()).isEqualTo(
            Decision(TimerPhase.READY, TimerPhase.READY, rebooted = false, RecoveryDecision.DEMOTED, service = false),
        )
    }

    @Test
    fun `a long step - restored without the service, its deadline alarm kept`() = runTest {
        before.controller.startSession(brewStart(infusion(60 * 60)))
        before.controller.send(TimerEvent.Start)
        val deadline = before.alarms.armed(TimerSlot.BREW).deadlineElapsedMs

        val after = before.processRestarted()
        after.clock.advanceBy(5 * MINUTE_MS)
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.active.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(after.active.longStep).isTrue()
        assertThat(after.service.starts).isEmpty()
        assertThat(after.alarms.armed(TimerSlot.BREW).deadlineElapsedMs).isEqualTo(deadline)
    }

    @Test
    fun `midnight passed while dead - saved silently with Sitzung gespeichert, no saved sheet`() = runTest {
        before.controller.startSession(brewStart(infusion(60), infusion(30)))
        val id = before.sessionId
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(60)
        before.controller.send(TimerEvent.Poured)

        val after = before.processRestarted()
        after.clock.advanceBy(16 * HOUR_MS)
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.ui).isEqualTo(TimerUiState.Idle)
        assertThat(after.sessions.getSession(id)!!.autoFinished).isTrue()
        assertThat(after.notifier.sessionsSaved).containsExactly(id)
        assertThat(after.savedSheet.shown).isEmpty()
        assertThat(after.alert.started).isEmpty()
        assertThat(after.store.storedPhase()).isNull()
        assertThat(after.recovered()).isEqualTo(
            Decision(TimerPhase.READY, null, rebooted = false, RecoveryDecision.SESSION_ENDED, service = false),
        )
    }

    @Test
    fun `a quick timer waiting 30 min while dead - ended silently`() = runTest {
        before.controller.startQuickTimer(60)

        val after = before.processRestarted()
        after.clock.advanceBy(31 * MINUTE_MS)
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.ui).isEqualTo(TimerUiState.Idle)
        assertThat(after.service.starts).isEmpty()
        assertThat(after.store.storedPhase()).isNull()
    }

    @Test
    fun `device restore - an in-progress session with a completed infusion is saved, nothing resumes`() = runTest {
        before.controller.startSession(brewStart(infusion(60), infusion(30)))
        val id = before.sessionId
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(60)
        before.controller.send(TimerEvent.Poured)

        val after = before.restoredOnAnotherPhone()
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.ui).isEqualTo(TimerUiState.Idle)
        assertThat(after.service.starts).isEmpty()
        val session = after.sessions.getSession(id)!!
        assertThat(session.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(session.autoFinished).isTrue()
        assertThat(session.steps.map { it.actualSeconds }).containsExactly(60)
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.OrphanSessionEnded>().map { it.saved })
            .containsExactly(true)
    }

    @Test
    fun `device restore - an in-progress session without a completed infusion is discarded`() = runTest {
        before.controller.startSession(brewStart(rinse(10), infusion(60)))
        val id = before.sessionId
        before.controller.send(TimerEvent.Start)
        before.advanceSeconds(10)
        before.controller.send(TimerEvent.Poured)

        val after = before.restoredOnAnotherPhone()
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.ui).isEqualTo(TimerUiState.Idle)
        assertThat(after.sessions.getSession(id)).isNull()
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.OrphanSessionEnded>().map { it.saved })
            .containsExactly(false)
    }

    @Test
    fun `device restore - a session whose only infusion was skipped is discarded`() = runTest {
        before.controller.startSession(brewStart(infusion(60), infusion(30)))
        val id = before.sessionId
        // Skipped steps record 0 s, not nothing (04 §5.1); still no infusion was brewed.
        before.controller.send(TimerEvent.SkipStep)

        val after = before.restoredOnAnotherPhone()
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.sessions.getSession(id)).isNull()
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.OrphanSessionEnded>().map { it.saved })
            .containsExactly(false)
    }

    @Test
    fun `the active session is never taken for a restored one`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        val id = before.sessionId

        val after = before.processRestarted()
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.active.sessionId).isEqualTo(id)
        assertThat(after.sessions.getSession(id)!!.status).isEqualTo(SessionStatus.IN_PROGRESS)
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.OrphanSessionEnded>()).isEmpty()
    }

    @Test
    fun `an unreadable snapshot - cleared at the app start instead of running a wrong timer`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        val id = before.sessionId
        before.controller.send(TimerEvent.Start)

        val after = before.processRestarted()
        after.store.readAs = { snapshot -> snapshot?.copy(activeBrew = snapshot.activeBrew.copy(pausedTotalMs = -1)) }
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.ui).isEqualTo(TimerUiState.Idle)
        assertThat(after.store.storedPhase()).isNull()
        assertThat(after.diagnostics.entriesOf<TimerDiagnostic.SnapshotUnreadable>()).hasSize(1)
        // Its session row does not stay in progress forever: nothing was completed, so it goes.
        assertThat(after.sessions.getSession(id)).isNull()
    }

    /** What a [TimerDiagnostic.Recovered] entry says, without its clock reading. */
    private data class Decision(
        val from: TimerPhase,
        val to: TimerPhase?,
        val rebooted: Boolean,
        val decision: RecoveryDecision,
        val service: Boolean = true,
    )

    private fun ControllerHarness.recovered(): Decision =
        diagnostics.entriesOf<TimerDiagnostic.Recovered>().single().let {
            Decision(it.from, it.to, it.rebooted, it.decision, it.service)
        }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS
    }
}
