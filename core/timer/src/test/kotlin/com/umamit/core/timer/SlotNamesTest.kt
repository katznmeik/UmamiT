package com.umamit.core.timer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.alarm.TimerAlarmReceiver
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.notification.TimerActionReceiver
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The names each slot gives its alarm broadcasts and notification actions (04 §6.2, §8.2; D-098), written out:
 * alarms still pending from v0.2.2 or an earlier v0.3.0 build, and the actions of notifications already posted, are
 * matched by these names, so `TimerSlot.named` must keep them byte for byte. The log lines are pinned by
 * `ControllerDiagnosticsLogTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SlotNamesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the alarm broadcasts keep their names`() {
        fun actions(slot: TimerSlot) =
            TimerAlarmKind.entries.map { TimerAlarmReceiver.intent(context, slot, it).action }

        assertThat(actions(TimerSlot.BREW)).containsExactly(
            "com.umamit.core.timer.action.TIMER_ALARM.PRE_ALERT",
            "com.umamit.core.timer.action.TIMER_ALARM.DEADLINE",
            "com.umamit.core.timer.action.TIMER_ALARM.OVERTIME_TIMEOUT",
            "com.umamit.core.timer.action.TIMER_ALARM.IDLE_DEMOTE",
            "com.umamit.core.timer.action.TIMER_ALARM.QUICK_CANCEL",
            "com.umamit.core.timer.action.TIMER_ALARM.MIDNIGHT",
            "com.umamit.core.timer.action.TIMER_ALARM.ALERT_WATCHDOG",
        ).inOrder()
        assertThat(actions(TimerSlot.QUICK)).containsExactly(
            "com.umamit.core.timer.action.TIMER_ALARM.QUICK.PRE_ALERT",
            "com.umamit.core.timer.action.TIMER_ALARM.QUICK.DEADLINE",
            "com.umamit.core.timer.action.TIMER_ALARM.QUICK.OVERTIME_TIMEOUT",
            "com.umamit.core.timer.action.TIMER_ALARM.QUICK.IDLE_DEMOTE",
            "com.umamit.core.timer.action.TIMER_ALARM.QUICK.QUICK_CANCEL",
            "com.umamit.core.timer.action.TIMER_ALARM.QUICK.MIDNIGHT",
            "com.umamit.core.timer.action.TIMER_ALARM.QUICK.ALERT_WATCHDOG",
        ).inOrder()
    }

    @Test
    fun `the notification actions keep their names and read back to their slot and event`() {
        val events = listOf(
            TimerEvent.Start,
            TimerEvent.Pause,
            TimerEvent.Resume,
            TimerEvent.Poured,
            TimerEvent.FinishSession,
            TimerEvent.Cancel,
        )
        fun intents(slot: TimerSlot) = events.map { TimerActionReceiver.intent(context, it, slot) }

        assertThat(intents(TimerSlot.BREW).map { it.action }).containsExactly(
            "com.umamit.core.timer.action.NOTIFICATION.START",
            "com.umamit.core.timer.action.NOTIFICATION.PAUSE",
            "com.umamit.core.timer.action.NOTIFICATION.RESUME",
            "com.umamit.core.timer.action.NOTIFICATION.POURED",
            "com.umamit.core.timer.action.NOTIFICATION.FINISH_SESSION",
            "com.umamit.core.timer.action.NOTIFICATION.CANCEL",
        ).inOrder()
        assertThat(intents(TimerSlot.QUICK).map { it.action }).containsExactly(
            "com.umamit.core.timer.action.NOTIFICATION.QUICK.START",
            "com.umamit.core.timer.action.NOTIFICATION.QUICK.PAUSE",
            "com.umamit.core.timer.action.NOTIFICATION.QUICK.RESUME",
            "com.umamit.core.timer.action.NOTIFICATION.QUICK.POURED",
            "com.umamit.core.timer.action.NOTIFICATION.QUICK.FINISH_SESSION",
            "com.umamit.core.timer.action.NOTIFICATION.QUICK.CANCEL",
        ).inOrder()
        TimerSlot.entries.forEach { slot ->
            assertThat(intents(slot).map(TimerActionReceiver::slotOf)).containsExactlyElementsIn(events.map { slot })
            assertThat(intents(slot).map(TimerActionReceiver::eventOf)).containsExactlyElementsIn(events).inOrder()
        }
    }
}
