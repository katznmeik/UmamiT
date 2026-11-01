package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The BrewSessionController (04 §2, §4, §7.1; spec #40 seam 2): every brew and
 * quick timer goes through it; it runs the real engine, writes the journal rows
 * and the active brew through the real repositories, and drives the ports.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrewSessionControllerTest {
    private val h = ControllerHarness()

    @After
    fun tearDown() = h.close()

    @Test
    fun `starting a session creates its journal row, saves the timer and waits for Start`() = runTest {
        h.teaWithStock()

        val outcome = h.controller.startSession(brewStart(infusion(60), infusion(30)))

        assertThat(outcome).isEqualTo(TransitionOutcome.Accepted)
        val ui = h.active
        assertThat(ui.phase).isEqualTo(TimerPhase.READY)
        assertThat(ui.stepIndex).isEqualTo(0)
        assertThat(ui.teaName).isEqualTo(ControllerHarness.TEA_NAME)
        val session = h.sessions.getSession(h.sessionId)!!
        assertThat(session.status).isEqualTo(SessionStatus.IN_PROGRESS)
        assertThat(session.snapshot.teaName).isEqualTo(ControllerHarness.TEA_NAME)
        assertThat(session.steps.map { it.plannedSeconds }).containsExactly(60, 30).inOrder()
        assertThat(h.store.storedPhase()).isEqualTo(TimerPhase.READY)
        assertThat(h.service.running).isTrue()
        assertThat(h.notifier.shown?.phase).isEqualTo(TimerPhase.READY)
    }

    /**
     * Rinse 10 s, three infusions of 60, 30 and 45 s; the third is never
     * started. The rinse is poured 3 s late, the 30-s infusion 2 s late.
     */
    @Test
    fun `a full session runs on its own wake-up and ends in the journal with cost and stock`() = runTest {
        h.teaWithStock()
        h.controller.startSession(brewStart(rinse(10), infusion(60), infusion(30), infusion(45)))
        val id = h.sessionId

        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(10)
        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
        // The alert notification names the tea („Jetzt abgießen!" · Hisui · Aufguss 1, 04 §8.2).
        assertThat(h.notifier.shown?.teaName).isEqualTo(ControllerHarness.TEA_NAME)
        h.advanceSeconds(3)
        h.controller.send(TimerEvent.Poured)

        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(49)
        assertThat(h.alert.ticks).isEqualTo(0)
        h.advanceSeconds(1)
        assertThat(h.alert.ticks).isEqualTo(1)
        assertThat(h.active.preAlertPlayed).isTrue()
        h.advanceSeconds(10)
        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
        h.controller.send(TimerEvent.Poured)

        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(32)
        h.controller.send(TimerEvent.Poured)
        assertThat(h.active.phase).isEqualTo(TimerPhase.READY)
        assertThat(h.active.stepIndex).isEqualTo(3)

        h.controller.send(TimerEvent.FinishSession)

        assertThat(h.ui).isEqualTo(TimerUiState.Idle)
        val session = h.sessions.getSession(id)!!
        assertThat(session.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(session.autoFinished).isFalse()
        assertThat(session.plannedStepCount).isEqualTo(4)
        assertThat(session.costCents).isEqualTo(34) // 4 g × 8,50 € / 100 g
        // The never-started fourth row is kept without an actual time (Q-19, 05 §3.7).
        assertThat(session.steps.map { Triple(it.position, it.actualSeconds, it.overtimeSeconds) })
            .containsExactly(Triple(0, 13, 3), Triple(1, 60, 0), Triple(2, 32, 2), Triple(3, null, null))
            .inOrder()
        assertThat(session.endedAtMs).isEqualTo(h.clock.wall)
        assertThat(h.stock.observeStock(ControllerHarness.TEA_ID).first().remainingGrams).isEqualTo(96.0)
        assertThat(h.teas.getTea(ControllerHarness.TEA_ID)!!.openedDate).isEqualTo(LocalDate.of(2026, 10, 22))
        assertThat(h.store.storedPhase()).isNull()
        assertThat(h.alert.ticks).isEqualTo(2)
        assertThat(h.alert.started.map { it.stepIndex }).containsExactly(0, 1, 2).inOrder()
        assertThat(h.savedSheet.shown).containsExactly(id)
        assertThat(h.service.running).isFalse()
        assertThat(h.alarms.armed(TimerSlot.BREW)).isEqualTo(DueTimes.NONE)
        assertThat(h.wakeUp.dueAtElapsedMs).isNull()
    }
}
