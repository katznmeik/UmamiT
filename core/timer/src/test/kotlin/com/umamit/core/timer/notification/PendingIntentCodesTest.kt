package com.umamit.core.timer.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeClock
import com.umamit.core.timer.TimerStateNotifications
import com.umamit.core.timer.alarm.TimerAlarmReceiver
import com.umamit.core.timer.controller.TimerAlarm
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Every pending intent the timer creates (04 §6.2, §8.2; #194): the exact alarms of every slot (101–107, 201–207,
 * 301–307) and every intent of the timer's notifications (content and full-screen 301, 302, 306–309, the Live
 * Update's dismissal 303, the notices 304, 305, the actions from 400). Android takes two pending intents for one
 * when their kind (activity, broadcast), request code and intent (action, component, data, categories) agree, and
 * `FLAG_UPDATE_CURRENT` then gives both the newer extras. The second brew's alarms share the codes 301–307 with the
 * notifications by design: an alarm is a broadcast to the alarm receiver, which no notification intent is. So no two
 * purposes may meet in one pending intent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PendingIntentCodesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val states = NotificationStates(clock)
    private val notifications = TimerStateNotifications(context)

    @Test
    fun `no two purposes of the timer's pending intents meet in one, though the codes 301 to 307 overlap`() {
        val alarms = TimerSlot.entries.flatMap { slot ->
            TimerAlarmKind.entries.map { kind ->
                val alarm = TimerAlarm(slot, kind)
                "alarm ${slot.named(kind.name)}" to PendingIntent.getBroadcast(
                    context,
                    alarm.requestCode,
                    TimerAlarmReceiver.intent(context, slot, kind),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
        }
        val shown = TimerSlot.entries.flatMap { slot ->
            timersIn(slot).flatMap { state ->
                listOf(
                    slot to notifications.build(state, fullScreen = true, clock.reading()),
                    slot to notifications.other(state, clock.reading()),
                    slot to notifications.timerResumed(listOf(state)),
                )
            }
        }.flatMap { (slot, notification) -> purposesOf(notification, slot.name) } +
            purposesOf(
                notifications.timerResumed(listOf(states.running(), states.quick(TimerPhase.RUNNING))),
                timer = "none",
            ) +
            purposesOf(notifications.sessionSaved("Gyokuro"), timer = "the app")

        val alarmCodes = alarms.map { shadowOf(it.second).requestCode }.toSet()
        assertWithMessage("the alarms' and the notifications' codes overlap, as designed")
            .that(alarmCodes intersect shown.map { shadowOf(it.second).requestCode }.toSet())
            .containsAtLeast(301, 302, 303, 305, 306, 307)
        (alarms + shown).groupBy { (_, intent) -> identityOf(intent) }.forEach { (identity, same) ->
            assertWithMessage("purposes in one pending intent ($identity)")
                .that(same.map { it.first }.toSet()).hasSize(1)
        }
    }

    /** What Android compares to tell two pending intents apart: the kind, the request code, the intent's filter. */
    private fun identityOf(intent: PendingIntent): String = with(shadowOf(intent)) {
        val kind = when {
            isActivity -> "activity"
            isBroadcast -> "broadcast"
            isService -> "service"
            else -> "other"
        }
        val intent = savedIntent
        "$kind $requestCode ${intent.action} ${intent.component?.flattenToShortString()} ${intent.data} " +
            "${intent.categories}"
    }

    /**
     * The pending intents of [notification] with what each is for: opening [timer]'s screen, its full-screen alert,
     * the Live Update's dismissal (one Live Update at a time) and each action, by its own name.
     */
    private fun purposesOf(notification: Notification, timer: String): List<Pair<String, PendingIntent>> =
        listOfNotNull(
            notification.contentIntent?.let { "open $timer" to it },
            notification.fullScreenIntent?.let { "full-screen alert of $timer" to it },
            notification.deleteIntent?.let { "Live Update dismissed" to it },
        ) + notification.actions.orEmpty().mapNotNull { action ->
            action.actionIntent?.let { "action ${shadowOf(it).savedIntent.action}" to it }
        }

    /** A timer of every phase with notification actions in [slot]: a brew session's, or the quick timer's. */
    private fun timersIn(slot: TimerSlot): List<TimerUiState.Active> = if (slot.isBrew) {
        listOf(states.ready(), states.running(), states.paused(), states.alerting(), states.finished())
            .map { it.copy(inSlot = slot.takeIf { it == TimerSlot.BREW_2 }) }
    } else {
        listOf(TimerPhase.READY, TimerPhase.RUNNING, TimerPhase.PAUSED, TimerPhase.ALERTING).map(states::quick)
    }
}
