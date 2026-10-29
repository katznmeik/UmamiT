package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.engine.TimerEvent
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
 * The journal rows come from the engine's data effects (05 §3.7, D-052): the
 * effective pre-alert at the start (04 §5), step edits and *Weiterer Aufguss*
 * (TMR-10, TMR-11), and discarding a session (TMR-13).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionDataTest {
    private val h = ControllerHarness()

    @Before
    fun setUp() = runTest { h.teaWithStock() }

    @After
    fun tearDown() = h.close()

    @Test
    fun `without a recipe value the session takes the global pre-alert`() = runTest {
        h.settings.update { it.copy(preAlertSeconds = 20) }

        h.controller.startSession(brewStart(infusion(60), recipePreAlertSeconds = null))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(40)

        assertThat(h.alert.ticks).isEqualTo(1)
        assertThat(h.sessions.getSession(h.sessionId)!!.preAlertSeconds).isEqualTo(20)
    }

    @Test
    fun `the recipe's pre-alert wins over the global one`() = runTest {
        h.settings.update { it.copy(preAlertSeconds = 20) }

        h.controller.startSession(brewStart(infusion(60), recipePreAlertSeconds = 15))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(44)
        assertThat(h.alert.ticks).isEqualTo(0)
        h.advanceSeconds(1)

        assertThat(h.alert.ticks).isEqualTo(1)
        assertThat(h.sessions.getSession(h.sessionId)!!.preAlertSeconds).isEqualTo(15)
    }

    @Test
    fun `the Schritte-Sheet's Aus at the start turns the step's pre-alert off for this session`() = runTest {
        h.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(60)

        assertThat(h.alert.ticks).isEqualTo(0)
        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
        val session = h.sessions.getSession(h.sessionId)!!
        assertThat(session.steps.single().preAlertSeconds).isEqualTo(0)
        // The session keeps B, the global setting frozen at the start (umamit.db v2, D-093).
        assertThat(session.preAlertSeconds).isEqualTo(10)
    }

    @Test
    fun `edited steps and pre-alert replace the pending rows of the running session`() = runTest {
        h.controller.startSession(brewStart(infusion(60), infusion(30)))
        h.controller.send(TimerEvent.Start)

        val outcome = h.controller.send(
            TimerEvent.EditSteps(
                listOf(infusion(60, preAlert = 5), infusion(45, tempC = 70, preAlert = 5), infusion(90, preAlert = 5)),
            ),
        )

        assertThat(outcome).isEqualTo(TransitionOutcome.Accepted)
        val session = h.sessions.getSession(h.sessionId)!!
        assertThat(session.steps.map { it.plannedSeconds to it.tempC })
            .containsExactly(60 to 60, 45 to 70, 90 to 60)
            .inOrder()
        // Each step keeps its choice, the running one's included; the session keeps B (D-093).
        assertThat(session.steps.map { it.preAlertSeconds }).containsExactly(5, 5, 5)
        assertThat(session.preAlertSeconds).isEqualTo(10)
        assertThat(h.active.steps.map { it.plannedSeconds }).containsExactly(60, 45, 90).inOrder()
    }

    @Test
    fun `Weiterer Aufguss after the last step adds a row and waits for Start`() = runTest {
        h.controller.startSession(brewStart(infusion(40)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(40)
        h.controller.send(TimerEvent.Poured)
        assertThat(h.active.phase).isEqualTo(TimerPhase.FINISHED)

        h.controller.send(TimerEvent.AddStep)

        assertThat(h.active.phase).isEqualTo(TimerPhase.READY)
        assertThat(h.active.stepIndex).isEqualTo(1)
        assertThat(
            h.sessions.getSession(h.sessionId)!!.steps.map {
                it.plannedSeconds
            },
        ).containsExactly(40, 60).inOrder()
    }

    @Test
    fun `discarding a running session deletes it and leaves the stock alone`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        val id = h.sessionId
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(30)

        h.controller.send(TimerEvent.Discard)

        assertThat(h.ui).isEqualTo(TimerUiState.Idle)
        assertThat(h.sessions.getSession(id)).isNull()
        assertThat(h.stock.observeStock(ControllerHarness.TEA_ID).first().remainingGrams).isEqualTo(100.0)
        assertThat(h.store.storedPhase()).isNull()
        assertThat(h.savedSheet.shown).isEmpty()
        assertThat(h.service.running).isFalse()
    }

    @Test
    fun `finishing without a completed brew step discards the session`() = runTest {
        h.controller.startSession(brewStart(rinse(10), infusion(60)))
        val id = h.sessionId
        assertThat(h.active.finishWouldDiscard).isTrue()

        h.controller.send(TimerEvent.FinishSession)

        assertThat(h.sessions.getSession(id)).isNull()
        assertThat(h.savedSheet.shown).isEmpty()
    }

    @Test
    fun `events that only the controller sends are refused from outside`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))

        val refused = listOf(TimerEvent.Recover, TimerEvent.AlertCompleted, TimerEvent.DeadlineReached).map { event ->
            runCatching { h.controller.send(event) }.exceptionOrNull()
        }

        assertThat(refused.map { it?.javaClass }).containsExactly(
            IllegalArgumentException::class.java,
            IllegalArgumentException::class.java,
            IllegalArgumentException::class.java,
        )
        assertThat(h.active.phase).isEqualTo(TimerPhase.READY)
    }
}
