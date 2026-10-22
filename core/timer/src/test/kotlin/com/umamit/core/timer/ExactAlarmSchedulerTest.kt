package com.umamit.core.timer

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** Alarm scheduling (04 §6.2, §12 Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExactAlarmSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val scheduler = ExactAlarmScheduler(context)

    @Before
    fun setUp() {
        // USE_EXACT_ALARM is granted at install on a device; Robolectric defaults to false.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun schedulesExactAllowWhileIdleAlarmsOnElapsedRealtime() {
        val preAlertExact = scheduler.schedule(TimerAlarm.PRE_ALERT, 50_000L)
        val deadlineExact = scheduler.schedule(TimerAlarm.DEADLINE, 60_000L)

        assertThat(preAlertExact).isTrue()
        assertThat(deadlineExact).isTrue()
        val alarms = shadowOf(alarmManager).scheduledAlarms.sortedBy { it.triggerAtMs }
        assertThat(alarms.map { it.triggerAtMs }).containsExactly(50_000L, 60_000L).inOrder()
        alarms.forEach { alarm ->
            assertThat(alarm.type).isEqualTo(AlarmManager.ELAPSED_REALTIME_WAKEUP)
            assertThat(alarm.isAllowWhileIdle).isTrue()
            assertThat(alarm.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_EXACT)
        }
        assertThat(shadowOf(alarms[0].operation).savedIntent.action).isEqualTo(TimerAlarm.PRE_ALERT.action)
        assertThat(shadowOf(alarms[1].operation).savedIntent.action).isEqualTo(TimerAlarm.DEADLINE.action)
        assertThat(shadowOf(alarms[1].operation).savedIntent.component?.className)
            .isEqualTo(ExactAlarmReceiver::class.java.name)
    }

    @Test
    fun reschedulingReplacesTheAlarmOfTheSameType() {
        scheduler.schedule(TimerAlarm.DEADLINE, 60_000L)
        scheduler.schedule(TimerAlarm.DEADLINE, 75_000L)

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertThat(alarms).hasSize(1)
        assertThat(alarms.single().triggerAtMs).isEqualTo(75_000L)
    }

    @Test
    fun cancelAllRemovesBothAlarms() {
        scheduler.schedule(TimerAlarm.PRE_ALERT, 50_000L)
        scheduler.schedule(TimerAlarm.DEADLINE, 60_000L)

        scheduler.cancelAll()

        assertThat(shadowOf(alarmManager).scheduledAlarms).isEmpty()
    }

    @Test
    fun fallsBackToInexactAlarmWhenExactAlarmsAreNotAllowed() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        val exact = scheduler.schedule(TimerAlarm.DEADLINE, 60_000L)

        assertThat(exact).isFalse()
        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        assertThat(alarm.triggerAtMs).isEqualTo(60_000L)
        assertThat(alarm.windowLengthMs).isNotEqualTo(ShadowAlarmManager.WINDOW_EXACT)
    }
}
