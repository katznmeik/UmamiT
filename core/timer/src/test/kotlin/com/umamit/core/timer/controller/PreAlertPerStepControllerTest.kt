package com.umamit.core.timer.controller

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.TimerStateNotifications
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The pre-alert per step from v0.2.1 through the controller (TMR-22, TMR-23, D-093 point 3): each step's own lead
 * reaches the timer screen's state, the Live Update's progress point, the alarms and the tick. The global setting
 * is the default 10 s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreAlertPerStepControllerTest {
    private val h = ControllerHarness()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = h.close()

    /** The ProgressStyle points of the Live Update for the last state the notifier got, in tenths of a second. */
    private fun livePoints(): List<Int> {
        val notification = TimerStateNotifications(context).build(h.notifier.shown!!, false, h.clock.reading())
        val style = Notification.Builder.recoverBuilder(context, notification).style as Notification.ProgressStyle
        return style.progressPoints.map { it.position }
    }

    @Test
    fun `a 20 s second infusion warns 5 s ahead on the screen, in the Live Update and by its tick`() = runTest {
        h.teaWithStock()
        h.controller.startSession(brewStart(infusion(60), infusion(20)))
        h.controller.send(TimerEvent.Start)
        assertThat(h.active.preAlertLeadSeconds).isEqualTo(10)
        // Segments of 60 and 20 s in tenths of a second: the point 10 s before the end of the first.
        assertThat(livePoints()).containsExactly(500)
        h.advanceSeconds(60)
        assertThat(h.alert.ticks).isEqualTo(1)
        h.controller.send(TimerEvent.Poured)

        assertThat(h.active.phase).isEqualTo(TimerPhase.READY)
        assertThat(h.active.preAlertLeadSeconds).isEqualTo(5)
        assertThat(livePoints()).containsExactly(600 + 150)

        h.controller.send(TimerEvent.Start)
        val deadline = h.active.deadlineElapsedMs!!
        assertThat(h.active.preAlertAtElapsedMs).isEqualTo(deadline - 5_000)
        assertThat(h.alarms.armed.preAlertAtElapsedMs).isEqualTo(deadline - 5_000)
        h.advanceSeconds(14)
        assertThat(h.alert.ticks).isEqualTo(1)
        h.advanceSeconds(1)
        assertThat(h.alert.ticks).isEqualTo(2)
        assertThat(h.notifier.shown?.preAlertPlayed).isTrue()
    }

    @Test
    fun `a pre-alert changed mid-step moves the running step's tick, alarm and point`() = runTest {
        h.teaWithStock()
        h.controller.startSession(brewStart(infusion(20)))
        h.controller.send(TimerEvent.Start)
        val deadline = h.active.deadlineElapsedMs!!
        assertThat(h.alarms.armed.preAlertAtElapsedMs).isEqualTo(deadline - 5_000)
        assertThat(livePoints()).containsExactly(150)
        h.advanceSeconds(2)

        h.controller.send(TimerEvent.EditSteps(listOf(infusion(20)), PreAlertChoice.Seconds(10)))

        assertThat(h.active.preAlertLeadSeconds).isEqualTo(10)
        assertThat(h.alarms.armed.preAlertAtElapsedMs).isEqualTo(deadline - 10_000)
        assertThat(livePoints()).containsExactly(100)
        h.advanceSeconds(7)
        assertThat(h.alert.ticks).isEqualTo(0)
        h.advanceSeconds(1)
        assertThat(h.alert.ticks).isEqualTo(1)
    }

    @Test
    fun `the quick timer takes the automatic rule with the global setting`() = runTest {
        h.controller.startQuickTimer(durationSeconds = 20, run = true)

        val deadline = h.active.deadlineElapsedMs!!
        assertThat(h.active.preAlertLeadSeconds).isEqualTo(5)
        assertThat(h.alarms.armed.preAlertAtElapsedMs).isEqualTo(deadline - 5_000)
        h.advanceSeconds(15)
        assertThat(h.alert.ticks).isEqualTo(1)
    }
}
