package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.BrewSession
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.SessionStepRow
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The start seam of Start and the setup (SCR-02, SCR-04): *Brühen starten*,
 * *Nochmal brühen* (BRW-11) and *Beenden & neu starten* (TMR-21) on the real
 * controller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrewStarterTest {
    private val harness = ControllerHarness()
    private val starter = ControllerBrewStarter(harness.controller)

    @After
    fun tearDown() = harness.close()

    @Test
    fun `a start waits in READY, and a second start is refused (TMR-01, TMR-21)`() = runTest {
        harness.teaWithStock()

        assertThat(starter.startSession(brewStart(infusion(60)))).isEqualTo(TransitionOutcome.Accepted)
        val first = harness.sessionId

        assertThat(harness.active.phase).isEqualTo(TimerPhase.READY)
        assertThat(starter.startSession(brewStart(infusion(30))))
            .isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(SessionKind.Brew(first))))
    }

    @Test
    fun `Beenden und neu starten saves the active session and starts the new one in READY`() = runTest {
        harness.teaWithStock()
        starter.startSession(brewStart(infusion(60)))
        val first = harness.sessionId
        harness.controller.send(TimerEvent.Start)
        harness.advanceSeconds(60)
        harness.controller.send(TimerEvent.Poured)

        val outcome = starter.finishActiveAndStart(brewStart(infusion(30), infusion(20)))

        assertThat(outcome).isEqualTo(TransitionOutcome.Accepted)
        assertThat(harness.sessions.getSession(first)!!.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(harness.active.phase).isEqualTo(TimerPhase.READY)
        assertThat(harness.active.steps.map { it.plannedSeconds }).containsExactly(30, 20).inOrder()
        // One controller turn (D-054, #46): the new timer is in front, so „Sitzung gespeichert" instead of the sheet.
        assertThat(harness.notifier.sessionsSaved).containsExactly(first)
        assertThat(harness.savedSheet.shown).isEmpty()
    }

    @Test
    fun `Beenden und neu starten without an active session just starts`() = runTest {
        harness.teaWithStock()

        assertThat(starter.finishActiveAndStart(brewStart(infusion(30)))).isEqualTo(TransitionOutcome.Accepted)
        assertThat(harness.active.phase).isEqualTo(TimerPhase.READY)
    }

    @Test
    fun `the quick timer runs at once and a full run records nothing (TMR-22, 04 sec 10)`() = runTest {
        harness.teaWithStock()

        assertThat(starter.startQuickTimer(90)).isEqualTo(TransitionOutcome.Accepted)
        assertThat(harness.active.kind).isEqualTo(SessionKind.Quick)
        assertThat(harness.active.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(harness.active.currentStep?.plannedSeconds).isEqualTo(90)
        harness.advanceSeconds(90)
        assertThat(harness.active.phase).isEqualTo(TimerPhase.ALERTING)
        harness.controller.send(TimerEvent.Poured) // *Fertig*

        assertThat(harness.ui).isEqualTo(TimerUiState.Idle)
        assertThat(sessionRows()).isEqualTo(0)
        assertThat(stepRows()).isEqualTo(0)
        assertThat(harness.stock.observeEvents(ControllerHarness.TEA_ID).first().map { it.id }).containsExactly("buy")
        assertThat(harness.savedSheet.shown).isEmpty()
    }

    @Test
    fun `Abbrechen ends a running quick timer and records nothing (04 sec 10)`() = runTest {
        harness.teaWithStock()
        starter.startQuickTimer(60)
        harness.advanceSeconds(20)

        assertThat(harness.controller.send(TimerEvent.Cancel)).isEqualTo(TransitionOutcome.Accepted)

        assertThat(harness.ui).isEqualTo(TimerUiState.Idle)
        assertThat(sessionRows()).isEqualTo(0)
        assertThat(stepRows()).isEqualTo(0)
        assertThat(harness.stock.observeEvents(ControllerHarness.TEA_ID).first().map { it.id }).containsExactly("buy")
        assertThat(harness.savedSheet.shown).isEmpty()
        assertThat(harness.alert.started).isEmpty()
    }

    @Test
    fun `Start is one turn, nothing lands between READY and running, and a cancelled caller still runs it`() = runTest {
        val gate = harness.store.hold()

        // The screen closes while the quick timer's READY is being saved.
        val first = launch { starter.startQuickTimer(60) }
        gate.reached.await()
        val second = async { harness.controller.startQuickTimer(30) }
        runCurrent()
        first.cancel()
        gate.open.complete(Unit)
        second.await()

        // No READY quick timer is left behind, and no other input landed between READY and RUNNING.
        assertThat(harness.active.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(harness.active.currentStep?.plannedSeconds).isEqualTo(60)
        val events = harness.diagnostics.entriesOf<TimerDiagnostic.EventHandled>().map { it.event }
        assertThat(events.take(2).map { it::class }).containsExactly(
            TimerEvent.StartQuickTimer::class,
            TimerEvent.Start::class,
        ).inOrder()
    }

    @Test
    fun `a quick timer outside 5 s to 24 h is refused, and so is one while a session is active`() = runTest {
        harness.teaWithStock()
        assertThat(starter.startQuickTimer(4)).isEqualTo(TransitionOutcome.Rejected(RejectionReason.InvalidQuickTimer))
        assertThat(starter.startQuickTimer(86_401))
            .isEqualTo(TransitionOutcome.Rejected(RejectionReason.InvalidQuickTimer))

        starter.startSession(brewStart(infusion(60)))
        val first = harness.sessionId

        assertThat(starter.startQuickTimer(60))
            .isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(SessionKind.Brew(first))))
        assertThat(harness.active.kind).isEqualTo(SessionKind.Brew(first))
    }

    @Test
    fun `Beenden und neu starten for the quick timer saves the session and runs the quick timer (TMR-21)`() = runTest {
        harness.teaWithStock()
        starter.startSession(brewStart(infusion(60)))
        val first = harness.sessionId
        harness.controller.send(TimerEvent.Start)
        harness.advanceSeconds(60)
        harness.controller.send(TimerEvent.Poured)

        assertThat(starter.finishActiveAndStartQuickTimer(120)).isEqualTo(TransitionOutcome.Accepted)

        assertThat(harness.sessions.getSession(first)!!.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(harness.active.kind).isEqualTo(SessionKind.Quick)
        assertThat(harness.active.phase).isEqualTo(TimerPhase.RUNNING)
    }

    @Test
    fun `an ice brew carries its method to the timer, also after a restart (BRW-10)`() = runTest {
        harness.teaWithStock()
        starter.startSession(brewStart(PlannedStep(StepKind.INFUSION, 7_200, 5), method = BrewMethod.ICE_BREW))

        assertThat(harness.active.iceBrew).isTrue()
        val after = harness.processRestarted()
        after.controller.onAlarm(TimerAlarmKind.IDLE_DEMOTE)
        assertThat(after.active.iceBrew).isTrue()
    }

    @Test
    fun `an ice brew stays one through every recovery of 04 sec 7 (app start, reboot, update)`() = runTest {
        harness.teaWithStock()
        starter.startSession(brewStart(PlannedStep(StepKind.INFUSION, 7_200, 5), method = BrewMethod.ICE_BREW))
        harness.controller.send(TimerEvent.Start)

        val appStart = harness.processRestarted()
        appStart.controller.recover(RecoveryTrigger.APP_START)
        assertThat(appStart.active.iceBrew).isTrue()

        appStart.clock.reboot(offMs = 20_000, upMs = 15_000)
        val rebooted = appStart.processRestarted()
        rebooted.controller.recover(RecoveryTrigger.BOOT_COMPLETED)
        assertThat(rebooted.active.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(rebooted.active.iceBrew).isTrue()

        val updated = rebooted.processRestarted()
        updated.controller.recover(RecoveryTrigger.PACKAGE_REPLACED)
        assertThat(updated.active.iceBrew).isTrue()
    }

    @Test
    fun `Beenden und neu starten into the quick timer is one turn (TMR-21, D-054)`() = runTest {
        harness.teaWithStock()
        starter.startSession(brewStart(infusion(60)))
        harness.controller.send(TimerEvent.Start)
        harness.advanceSeconds(60)
        harness.controller.send(TimerEvent.Poured)
        val before = harness.diagnostics.entriesOf<TimerDiagnostic.EventHandled>().size
        val gate = harness.store.hold()

        // A notification's tap arrives while the finish is being saved.
        val restart = async { starter.finishActiveAndStartQuickTimer(90) }
        gate.reached.await()
        val tap = async { harness.controller.send(TimerEvent.Pause, EventSource.NOTIFICATION) }
        runCurrent()
        gate.open.complete(Unit)
        assertThat(restart.await()).isEqualTo(TransitionOutcome.Accepted)
        tap.await()

        // Finish, quick start and its step's start ran back to back; the tap came after them.
        val events = harness.diagnostics.entriesOf<TimerDiagnostic.EventHandled>().drop(before).map { it.event }
        assertThat(events.map { it::class }).containsExactly(
            TimerEvent.FinishSession::class,
            TimerEvent.StartQuickTimer::class,
            TimerEvent.Start::class,
            TimerEvent.Pause::class,
        ).inOrder()
        // The finish's „Gespeichert" sheet became the notification: the quick timer is in front (D-054).
        assertThat(harness.notifier.sessionsSaved).hasSize(1)
        assertThat(harness.savedSheet.shown).isEmpty()
    }

    @Test
    fun `a cold brew and the quick timer are no ice brew`() = runTest {
        harness.teaWithStock()
        starter.startSession(brewStart(PlannedStep(StepKind.INFUSION, 7_200, 5), method = BrewMethod.COLD_BREW))
        assertThat(harness.active.iceBrew).isFalse()
        starter.finishActiveAndStartQuickTimer(60)
        assertThat(harness.active.iceBrew).isFalse()
    }

    private fun sessionRows(): Int = count("brew_session")

    private fun stepRows(): Int = count("session_step")

    private fun count(table: String): Int =
        harness.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    @Test
    fun `brewing again copies the last session's snapshot, planned steps and pre-alert (BRW-11)`() {
        val last = BrewSession(
            id = "s1",
            status = SessionStatus.COMPLETED,
            snapshot = brewStart(infusion(1)).snapshot.copy(method = BrewMethod.KYUSU, leafGrams = 5.0, waterMl = 250),
            preAlertSeconds = 15,
            plannedStepCount = 3,
            costCents = null,
            startedAtMs = 0,
            endedAtMs = 1,
            zoneId = "Europe/Berlin",
            autoFinished = false,
            steps = listOf(
                row(position = 1, StepKind.INFUSION, plannedSeconds = 10, tempC = 70),
                row(position = 0, StepKind.INFUSION, plannedSeconds = 45, tempC = 65),
            ),
        )

        val start = brewAgainStart(last)

        assertThat(start.snapshot).isEqualTo(last.snapshot)
        assertThat(start.steps).containsExactly(
            PlannedStep(StepKind.INFUSION, 45, 65),
            PlannedStep(StepKind.INFUSION, 10, 70),
        ).inOrder()
        assertThat(start.recipePreAlertSeconds).isEqualTo(15)
        assertThat(start.preAlert).isEqualTo(PreAlertChoice.Inherit)
    }

    private fun row(position: Int, kind: StepKind, plannedSeconds: Int, tempC: Int?) = SessionStepRow(
        position = position,
        kind = kind,
        plannedSeconds = plannedSeconds,
        tempC = tempC,
        actualSeconds = plannedSeconds,
        pausedSeconds = 0,
        overtimeSeconds = 0,
        startedAtWallMs = 0,
        endedAtWallMs = 1,
        skipped = false,
        overtimeAutoAck = false,
    )
}
