package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * *Beenden & neu starten* (TMR-21, D-054; #46): the answer to „Erst die … -Sitzung beenden?"
 * ends the active timer and starts the new session in one controller turn. The finish's
 * „Gespeichert" sheet becomes the notification „Sitzung gespeichert" (TMR-14): the timer
 * screen of the new session is in front.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinishAndRestartTest {
    private val h = ControllerHarness()

    @Before
    fun setUp() = runTest { h.teaWithStock() }

    @After
    fun tearDown() = h.close()

    @Test
    fun `the old session is saved with a notification and the new one waits in READY`() = runTest {
        h.controller.startSession(brewStart(infusion(60), infusion(30)))
        val old = h.sessionId
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(60)
        h.controller.send(TimerEvent.Poured)

        val outcome = h.controller.finishActiveAndStart(brewStart(infusion(45)))

        assertThat(outcome).isEqualTo(TransitionOutcome.Accepted)
        val saved = h.sessions.getSession(old)!!
        assertThat(saved.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(saved.autoFinished).isFalse()
        assertThat(h.notifier.sessionsSaved).containsExactly(old)
        assertThat(h.savedSheet.shown).isEmpty()
        assertThat(h.active.phase).isEqualTo(TimerPhase.READY)
        assertThat(h.active.sessionId).isNotEqualTo(old)
        assertThat(h.active.steps.map { it.plannedSeconds }).containsExactly(45)
        assertThat(h.store.storedPhase()).isEqualTo(TimerPhase.READY)
        assertThat(h.service.foreground?.phase).isEqualTo(TimerPhase.READY)
    }

    @Test
    fun `an alerting session is poured and saved, and its alert stops`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        val old = h.sessionId
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(62)
        assertThat(h.alert.playing).isNotNull()

        h.controller.finishActiveAndStart(brewStart(infusion(45)))

        assertThat(h.alert.playing).isNull()
        assertThat(h.sessions.getSession(old)!!.steps.single().overtimeSeconds).isEqualTo(2)
        assertThat(h.notifier.sessionsSaved).containsExactly(old)
        assertThat(h.active.phase).isEqualTo(TimerPhase.READY)
    }

    @Test
    fun `a session without a completed infusion is discarded without a second question`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        val old = h.sessionId

        h.controller.finishActiveAndStart(brewStart(infusion(45)))

        assertThat(h.sessions.getSession(old)).isNull()
        assertThat(h.notifier.sessionsSaved).isEmpty()
        assertThat(h.active.phase).isEqualTo(TimerPhase.READY)
        assertThat(h.active.sessionId).isNotEqualTo(old)
    }

    @Test
    fun `a quick timer is cancelled for the new session`() = runTest {
        h.controller.startQuickTimer(90)
        h.controller.send(TimerEvent.Start)

        val outcome = h.controller.finishActiveAndStart(brewStart(infusion(45)))

        assertThat(outcome).isEqualTo(TransitionOutcome.Accepted)
        assertThat(h.active.kind).isInstanceOf(SessionKind.Brew::class.java)
        assertThat(h.notifier.sessionsSaved).isEmpty()
    }

    @Test
    fun `nothing gets between the finish and the start`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        val gate = h.store.hold()

        // The finish is being saved when another brew is started from elsewhere.
        val restart = async { h.controller.finishActiveAndStart(brewStart(infusion(45))) }
        gate.reached.await()
        val other = async { h.controller.startSession(brewStart(infusion(30))) }
        runCurrent()
        gate.open.complete(Unit)

        assertThat(restart.await()).isEqualTo(TransitionOutcome.Accepted)
        val brew = h.active.kind
        assertThat(other.await()).isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(brew)))
        assertThat(h.active.steps.single().plannedSeconds).isEqualTo(45)
    }
}
