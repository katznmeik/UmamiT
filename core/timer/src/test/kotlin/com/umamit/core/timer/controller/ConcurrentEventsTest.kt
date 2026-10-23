package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.AlertStopped
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Events from the app and a notification race (E5, 04 §2.4): the event lock
 * serialises them, so a duplicate meets the state its twin left and is
 * ignored; a step is never skipped. One active timer at a time (TMR-21).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConcurrentEventsTest {
    private val h = ControllerHarness()

    @Before
    fun setUp() = runTest { h.teaWithStock() }

    @After
    fun tearDown() = h.close()

    @Test
    fun `Abgegossen in the app and in the notification at once moves on by one step`() = runTest {
        h.controller.startSession(brewStart(infusion(10), infusion(20), infusion(30)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(10)
        val gate = h.store.hold()

        // The app's tap is being saved when the notification's arrives.
        val app = async { h.controller.send(TimerEvent.Poured, EventSource.APP) }
        gate.reached.await()
        val notification = async { h.controller.send(TimerEvent.Poured, EventSource.NOTIFICATION) }
        runCurrent()
        gate.open.complete(Unit)

        assertThat(listOf(app.await(), notification.await()))
            .containsExactly(TransitionOutcome.Accepted, TransitionOutcome.Ignored)
            .inOrder()
        assertThat(h.active.phase).isEqualTo(TimerPhase.READY)
        assertThat(h.active.stepIndex).isEqualTo(1)
        val steps = h.sessions.getSession(h.sessionId)!!.steps
        assertThat(steps.map { it.actualSeconds }).containsExactly(10, null, null).inOrder()
        assertThat(h.store.storedPhase()).isEqualTo(TimerPhase.READY)
        assertThat(h.log.calls.count { it == AlertStopped }).isEqualTo(1)
    }

    @Test
    fun `an event that arrives while another is being saved waits for it and reads the clock after it`() = runTest {
        h.controller.startSession(brewStart(infusion(10), infusion(20)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(10)
        val gate = h.store.hold()

        val poured = async { h.controller.send(TimerEvent.Poured, EventSource.APP) }
        gate.reached.await()
        val start = async { h.controller.send(TimerEvent.Start, EventSource.NOTIFICATION) }
        runCurrent()
        val handledWhileSaving = h.diagnostics.entriesOf<TimerDiagnostic.EventHandled>().map { it.event }
        h.clock.advanceBy(2_000) // the save takes a while
        gate.open.complete(Unit)
        poured.await()
        start.await()

        // The notification's Start waited for the lock and read the clock inside it (04 §4, D-054).
        assertThat(handledWhileSaving.last()).isEqualTo(TimerEvent.Poured)
        val started = h.diagnostics.entriesOf<TimerDiagnostic.EventHandled>().last()
        assertThat(started.event).isEqualTo(TimerEvent.Start)
        assertThat(started.at.elapsedMs).isEqualTo(h.clock.elapsed)
        assertThat(h.active.deadlineElapsedMs).isEqualTo(h.clock.elapsed + 20_000)
    }

    @Test
    fun `a tap whose caller is cancelled while it is being saved still completes, so the alert stops`() = runTest {
        h.controller.startSession(brewStart(infusion(10), infusion(20)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(10)
        val gate = h.store.hold()

        // E.g. the screen's scope ends right after *Abgegossen*.
        val tap = launch { h.controller.send(TimerEvent.Poured) }
        gate.reached.await()
        tap.cancel()
        gate.open.complete(Unit)
        tap.join()

        assertThat(h.alert.playing).isNull()
        assertThat(h.active.phase).isEqualTo(TimerPhase.READY)
        assertThat(h.store.storedPhase()).isEqualTo(TimerPhase.READY)
        assertThat(h.sessions.getSession(h.sessionId)!!.steps[0].actualSeconds).isEqualTo(10)
    }

    @Test
    fun `a double tap on Start starts the step once`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(5)
        val calls = h.log.calls.size

        val second = h.controller.send(TimerEvent.Start, EventSource.NOTIFICATION)

        assertThat(second).isEqualTo(TransitionOutcome.Ignored)
        assertThat(h.log.calls).hasSize(calls)
        assertThat(h.active.display(h.clock.elapsed).remainingSeconds).isEqualTo(55)
    }

    @Test
    fun `a second start while a session is active is refused and writes nothing`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        val first = h.sessionId
        val calls = h.log.calls.size

        val again = h.controller.startSession(brewStart(infusion(30)))
        val quick = h.controller.startQuickTimer(durationSeconds = 120)

        val refusal = TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(SessionKind.Brew(first)))
        assertThat(again).isEqualTo(refusal)
        assertThat(quick).isEqualTo(refusal)
        assertThat(h.log.calls).hasSize(calls)
        assertThat(h.sessionId).isEqualTo(first)
        assertThat(sessionRows()).isEqualTo(1)
    }

    @Test
    fun `the quick timer alerts like a session and records nothing`() = runTest {
        h.controller.startQuickTimer(durationSeconds = 60)
        assertThat(h.active.kind).isEqualTo(SessionKind.Quick)
        assertThat(h.active.teaName).isNull()

        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(60)
        h.controller.send(TimerEvent.Poured)

        assertThat(h.alert.ticks).isEqualTo(1)
        assertThat(h.alert.started.map { it.kind }).containsExactly(SessionKind.Quick)
        assertThat(h.ui).isEqualTo(TimerUiState.Idle)
        assertThat(sessionRows()).isEqualTo(0)
        assertThat(h.stock.observeEvents(ControllerHarness.TEA_ID).first().map { it.id }).containsExactly("buy")
        assertThat(h.store.storedPhase()).isNull()
        assertThat(h.savedSheet.shown).isEmpty()
        assertThat(h.service.running).isFalse()
    }

    @Test
    fun `the quick timer uses the global pre-alert`() = runTest {
        h.settings.update { it.copy(preAlertSeconds = 20) }

        h.controller.startQuickTimer(durationSeconds = 60)
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(40)

        assertThat(h.alert.ticks).isEqualTo(1)
        assertThat(h.active.preAlertLeadSeconds).isEqualTo(20)
    }

    private fun sessionRows(): Int =
        h.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM brew_session").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
