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
    }

    @Test
    fun `Beenden und neu starten without an active session just starts`() = runTest {
        harness.teaWithStock()

        assertThat(starter.finishActiveAndStart(brewStart(infusion(30)))).isEqualTo(TransitionOutcome.Accepted)
        assertThat(harness.active.phase).isEqualTo(TimerPhase.READY)
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
