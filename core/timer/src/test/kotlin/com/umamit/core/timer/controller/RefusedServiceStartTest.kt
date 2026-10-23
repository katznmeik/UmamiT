package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.ForegroundStart
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A foreground start Android refused (04 §6.3, §7.2; D-057; #42 review R2-2, #46): the next
 * turn that may start the service from the background (an exact alarm, a notification action,
 * a tap) sends it again, inside the controller's turn and with the notification of the state
 * of that moment, never the refused one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RefusedServiceStartTest {
    private val h = ControllerHarness()

    @Before
    fun setUp() = runTest { h.teaWithStock() }

    @After
    fun tearDown() = h.close()

    @Test
    fun `an alarm sends a refused start again with the state of now, not the refused alert`() = runTest {
        h.controller.startSession(brewStart(infusion(60), infusion(30)))
        h.controller.send(TimerEvent.Start)
        h.service.stopForeground() // the service died meanwhile; the step goes on
        h.service.refusing = true
        // The in-process wake-up handles the deadline in the background: the alert's start is refused.
        h.advanceSeconds(60)
        assertThat(h.service.startRefused).isTrue()
        // *Abgegossen* only updates the notification (both states run with the service).
        h.controller.send(TimerEvent.Poured, EventSource.NOTIFICATION)
        h.service.refusing = false

        // The deadline alarm right after: nothing is due, but it may start the service.
        h.controller.onAlarm(TimerAlarmKind.DEADLINE)

        assertThat(h.service.retried.last()).isEqualTo(ForegroundStart(TimerPhase.READY, fullScreen = false))
        assertThat(h.service.foreground?.phase).isEqualTo(TimerPhase.READY)
    }

    @Test
    fun `an alarm sends a refused alert again full screen while it plays`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)
        h.service.stopForeground()
        h.service.refusing = true
        h.advanceSeconds(60)
        h.service.refusing = false

        h.controller.onAlarm(TimerAlarmKind.DEADLINE)

        assertThat(h.service.retried).containsExactly(ForegroundStart(TimerPhase.ALERTING, fullScreen = true))
        assertThat(h.service.foreground?.phase).isEqualTo(TimerPhase.ALERTING)
    }

    @Test
    fun `a tap brings back the service that recovery could not start`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)
        val after = h.processRestarted()
        after.service.refusing = true
        after.controller.recover(RecoveryTrigger.APP_START)
        assertThat(after.service.running).isFalse()
        after.service.refusing = false

        // *Pause* only updates the notification.
        after.controller.send(TimerEvent.Pause)

        assertThat(after.service.retried).containsExactly(ForegroundStart(TimerPhase.PAUSED, fullScreen = false))
        assertThat(after.service.running).isTrue()
    }

    @Test
    fun `a start refused in a turn is not sent again in the same turn`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)
        val after = h.processRestarted()
        after.service.refusing = true
        after.clock.advanceBy(60_000)

        after.controller.onAlarm(TimerAlarmKind.DEADLINE)

        assertThat(after.service.starts).containsExactly(ForegroundStart(TimerPhase.ALERTING, fullScreen = true))
        assertThat(after.service.retried).isEmpty()
        assertThat(after.service.startRefused).isTrue()
    }

    @Test
    fun `the alert player's report never sends a refused start again, it may not start the service`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)
        h.service.stopForeground()
        h.service.refusing = true
        h.advanceSeconds(60)
        h.service.refusing = false

        h.controller.alertCompleted(checkNotNull(h.alert.playing))

        assertThat(h.service.retried).isEmpty()
        assertThat(h.service.startRefused).isTrue()
    }
}
