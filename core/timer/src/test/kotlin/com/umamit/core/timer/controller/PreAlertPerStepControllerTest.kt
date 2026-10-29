package com.umamit.core.timer.controller

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.TimerStateNotifications
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerState
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

        h.controller.send(TimerEvent.EditSteps(listOf(infusion(20, preAlert = 10))))

        assertThat(h.active.preAlertLeadSeconds).isEqualTo(10)
        assertThat(h.alarms.armed.preAlertAtElapsedMs).isEqualTo(deadline - 10_000)
        assertThat(livePoints()).containsExactly(100)
        h.advanceSeconds(7)
        assertThat(h.alert.ticks).isEqualTo(0)
        h.advanceSeconds(1)
        assertThat(h.alert.ticks).isEqualTo(1)
    }

    @Test
    fun `a new process restores each step's own lead, a running step's lead changed mid-step included`() = runTest {
        h.teaWithStock()
        // B = 10 s: 20 s explicit, then Automatisch (5 s for 20 s), then Aus (umamit.db v2, #90).
        h.controller.startSession(brewStart(infusion(60, preAlert = 20), infusion(20), infusion(30, preAlert = 0)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(5)
        // The running step's only editable field: its pre-alert (SCR-31, D-093). Its row follows with the Persist.
        h.controller.send(
            TimerEvent.EditSteps(listOf(infusion(60, preAlert = 15), infusion(20), infusion(30, preAlert = 0))),
        )
        val deadline = h.active.deadlineElapsedMs!!

        val after = h.processRestarted()
        after.controller.recover(RecoveryTrigger.APP_START)

        assertThat(after.active.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(after.active.preAlertLeadSeconds).isEqualTo(15)
        assertThat(after.alarms.armed.preAlertAtElapsedMs).isEqualTo(deadline - 15_000)
        after.advanceSeconds(39)
        assertThat(after.alert.ticks).isEqualTo(0)
        after.advanceSeconds(1)
        assertThat(after.alert.ticks).isEqualTo(1)
        after.advanceSeconds(15)
        after.controller.send(TimerEvent.Poured)
        assertThat(after.active.preAlertLeadSeconds).isEqualTo(5)

        val again = after.processRestarted()
        again.controller.recover(RecoveryTrigger.APP_START)
        assertThat(again.active.preAlertLeadSeconds).isEqualTo(5)
        again.controller.send(TimerEvent.SkipStep)
        assertThat(again.active.preAlertLeadSeconds).isNull()
        assertThat(again.store.stored().let { (it as TimerState.Active).session.steps.map { s -> s.preAlertSeconds } })
            .containsExactly(15, null, 0).inOrder()
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
