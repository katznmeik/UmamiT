package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.ForegroundStart
import com.umamit.core.testing.ServiceStarted
import com.umamit.core.testing.ServiceStopped
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The foreground-service contract as the controller drives the service port (04 §4, §6.1,
 * §6.3; D-053, #42): short steps and alerts run with the service, a long step without it,
 * and the alert notification the service shows is full screen only when its alert starts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForegroundServiceContractTest {
    private val h = ControllerHarness()

    @Before
    fun setUp() = runTest { h.teaWithStock() }

    @After
    fun tearDown() = h.close()

    @Test
    fun `a ten-minute step runs without the service and its alert brings it back full screen`() = runTest {
        h.controller.startSession(brewStart(infusion(600)))
        assertThat(h.service.running).isTrue()

        h.controller.send(TimerEvent.Start)

        assertThat(h.active.longStep).isTrue()
        assertThat(h.service.running).isFalse()
        assertThat(h.log.calls.last { it is ServiceStarted || it == ServiceStopped }).isEqualTo(ServiceStopped)

        h.advanceSeconds(600)

        assertThat(h.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(h.service.running).isTrue()
        assertThat(h.service.foreground?.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(h.service.fullScreen).isTrue()
    }

    @Test
    fun `a short step keeps the service from the start through the alert`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)

        assertThat(h.service.running).isTrue()
        assertThat(h.service.fullScreen).isFalse()

        h.advanceSeconds(60)

        assertThat(h.log.calls).doesNotContain(ServiceStopped)
        assertThat(h.service.foreground?.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(h.service.fullScreen).isTrue()
    }

    @Test
    fun `the alert notification is full screen only in the transition that starts the alert`() = runTest {
        h.controller.startSession(brewStart(infusion(60), infusion(60)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(60)
        // The deadline alarm after the wake-up, the end of the alert and Abgegossen start nothing full screen.
        h.controller.onAlarm(TimerAlarmKind.DEADLINE)
        h.controller.alertCompleted(checkNotNull(h.alert.playing))
        h.controller.send(TimerEvent.Poured)
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(60)

        assertThat(h.service.starts).containsExactly(
            ForegroundStart(TimerPhase.READY, fullScreen = false),
            ForegroundStart(TimerPhase.RUNNING, fullScreen = false),
            // The pre-alert tick ensures the service too (D-054).
            ForegroundStart(TimerPhase.RUNNING, fullScreen = false),
            ForegroundStart(TimerPhase.ALERTING, fullScreen = true),
            ForegroundStart(TimerPhase.RUNNING, fullScreen = false),
            ForegroundStart(TimerPhase.RUNNING, fullScreen = false),
            ForegroundStart(TimerPhase.ALERTING, fullScreen = true),
        ).inOrder()
    }
}
