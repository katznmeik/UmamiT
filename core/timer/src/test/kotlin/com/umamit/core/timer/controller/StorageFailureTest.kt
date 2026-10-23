package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.AlertStarted
import com.umamit.core.testing.NotificationShown
import com.umamit.core.timer.engine.TimerEffect
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rule 8 and 04 §4, §7.1 (D-054): a failed transaction is logged and `Persist`
 * alone is retried once; the side effects run from the in-memory transition
 * either way, so a storage error never keeps the alert from playing. A port
 * that throws is logged and skipped; the effects after it still run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StorageFailureTest {
    private val h = ControllerHarness()

    @Before
    fun setUp() = runTest { h.teaWithStock() }

    @After
    fun tearDown() = h.close()

    @Test
    fun `a persist that fails twice still plays the alert, and the next event saves the timer again`() = runTest {
        h.controller.startSession(brewStart(infusion(60), infusion(30)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(50)
        h.store.failNext(2)

        h.advanceSeconds(10)

        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(h.alert.started).hasSize(1)
        assertThat(h.log.calls).contains(NotificationShown(TimerPhase.ALERTING, fullScreen = true))
        assertThat(h.store.storedPhase()).isEqualTo(TimerPhase.RUNNING)
        assertThat(h.diagnostics.entriesOf<TimerDiagnostic.PersistFailed>().map { it.retry })
            .containsExactly(false, true)
            .inOrder()

        h.controller.send(TimerEvent.Poured)

        assertThat(h.store.storedPhase()).isEqualTo(TimerPhase.READY)
        assertThat(h.sessions.getSession(h.sessionId)!!.steps[0].actualSeconds).isEqualTo(60)
    }

    @Test
    fun `a transaction that fails once is retried with Persist alone, which still records the step`() = runTest {
        h.controller.startSession(brewStart(infusion(10), infusion(20)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(14)
        h.store.failNext(1)

        h.controller.send(TimerEvent.Poured)

        // The transaction with RecordStep rolled back; the retried store write brings the rows to the plan.
        assertThat(h.store.storedPhase()).isEqualTo(TimerPhase.READY)
        val recorded = h.sessions.getSession(h.sessionId)!!.steps[0]
        assertThat(recorded.actualSeconds).isEqualTo(14)
        assertThat(recorded.overtimeSeconds).isEqualTo(4)
        assertThat(h.diagnostics.entriesOf<TimerDiagnostic.PersistFailed>().map { it.retry }).containsExactly(false)
    }

    @Test
    fun `a service that throws does not keep the alert from starting`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(50)
        h.service.failing = true

        h.advanceSeconds(10)

        assertThat(h.log.calls.filterIsInstance<AlertStarted>()).hasSize(1)
        assertThat(h.notifier.shown?.alerting).isTrue()
        assertThat(h.diagnostics.entriesOf<TimerDiagnostic.EffectFailed>().map { it.effect })
            .containsExactly(TimerEffect.StartForeground)
    }
}
