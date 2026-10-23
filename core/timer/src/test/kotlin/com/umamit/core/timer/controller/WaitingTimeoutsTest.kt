package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.NotificationShown
import com.umamit.core.testing.ServiceStarted
import com.umamit.core.testing.ServiceStopped
import com.umamit.core.testing.SessionSavedNotified
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Time rules the controller carries out on its own (TMR-14, TMR-18, D-053):
 * a waiting session goes quiet after 30 min and is saved at midnight with
 * „Sitzung gespeichert"; a long step runs without the service, which the
 * deadline brings back for the alert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WaitingTimeoutsTest {
    private val h = ControllerHarness()

    @Before
    fun setUp() = runTest { h.teaWithStock() }

    @After
    fun tearDown() = h.close()

    @Test
    fun `a session left waiting goes quiet after 30 minutes and is saved at midnight with a notification`() = runTest {
        h.controller.startSession(brewStart(infusion(60), infusion(30)))
        val id = h.sessionId
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(60)
        h.controller.send(TimerEvent.Poured)
        h.log.clear()

        h.advanceSeconds(30 * 60)

        assertThat(h.active.demoted).isTrue()
        assertThat(h.log.calls.drop(2))
            .containsExactly(ServiceStopped, NotificationShown(TimerPhase.READY, fullScreen = false))
            .inOrder()
        h.log.clear()

        // Idle since 10:01; the first midnight at least 6 h later is 2026-10-23 00:00.
        h.advanceSeconds(14 * 3_600L)

        assertThat(h.ui).isEqualTo(TimerUiState.Idle)
        val session = h.sessions.getSession(id)!!
        assertThat(session.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(session.autoFinished).isTrue()
        assertThat(session.plannedStepCount).isEqualTo(2)
        assertThat(h.log.calls.drop(2)).containsExactly(ServiceStopped, SessionSavedNotified(id)).inOrder()
        assertThat(h.savedSheet.shown).isEmpty()
    }

    @Test
    fun `a cold brew step runs without the service, and its deadline brings it back for the alert`() = runTest {
        h.controller.startSession(brewStart(infusion(300, tempC = 20), method = BrewMethod.COLD_BREW))
        h.log.clear()

        h.controller.send(TimerEvent.Start)

        assertThat(h.service.running).isFalse()
        assertThat(h.active.longStep).isTrue()
        assertThat(h.active.preAlertLeadSeconds).isNull()
        assertThat(h.log.calls).contains(ServiceStopped)
        h.log.clear()

        h.advanceSeconds(300)

        assertThat(h.alert.ticks).isEqualTo(0)
        assertThat(h.log.calls.drop(2).first()).isEqualTo(ServiceStarted(TimerPhase.ALERTING))
        assertThat(h.alert.playing).isNotNull()
    }

    @Test
    fun `a user event on a quiet session brings the service back`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.advanceSeconds(30 * 60)
        assertThat(h.service.running).isFalse()

        h.controller.send(TimerEvent.Start)

        assertThat(h.active.demoted).isFalse()
        assertThat(h.service.running).isTrue()
    }
}
