package com.umamit.core.timer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * The two exact alarms of a step (04 §6.2). Request codes are fixed per type,
 * so scheduling again replaces the earlier alarm.
 */
enum class TimerAlarm(val requestCode: Int, val action: String) {
    PRE_ALERT(requestCode = 101, action = "com.umamit.core.timer.action.PRE_ALERT"),
    DEADLINE(requestCode = 102, action = "com.umamit.core.timer.action.DEADLINE"),
    ;

    companion object {
        fun fromAction(action: String?): TimerAlarm? = entries.firstOrNull { it.action == action }
    }
}

/**
 * Schedules [TimerAlarm]s with `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, …)`
 * (04 §6.2), delivered to the non-exported [ExactAlarmReceiver].
 *
 * `USE_EXACT_ALARM` is granted at install, so [canScheduleExactAlarms] should
 * always be true. If it is not, the alarm falls back to an inexact
 * `setAndAllowWhileIdle` and [schedule] returns false, so the caller can warn
 * (reliability banner, TMR-31).
 */
class ExactAlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = checkNotNull(appContext.getSystemService(AlarmManager::class.java))

    fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    /**
     * Schedules [alarm] at [triggerAtElapsedMs] (`SystemClock.elapsedRealtime()`
     * base). [fillExtras] adds the data the receiver needs. Returns true when
     * the alarm is exact.
     *
     * Lint's MissingPermission check only knows `SCHEDULE_EXACT_ALARM`; the app
     * holds `USE_EXACT_ALARM` (D-013), checks [canScheduleExactAlarms] and
     * catches the `SecurityException`.
     */
    @SuppressLint("MissingPermission")
    fun schedule(alarm: TimerAlarm, triggerAtElapsedMs: Long, fillExtras: (Intent) -> Unit = {}): Boolean {
        val operation = PendingIntent.getBroadcast(
            appContext,
            alarm.requestCode,
            alarmIntent(alarm).also(fillExtras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsedMs,
                    operation,
                )
                return true
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm $alarm refused; falling back to an inexact alarm", e)
            }
        } else {
            Log.w(TAG, "canScheduleExactAlarms() is false; $alarm falls back to an inexact alarm")
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsedMs, operation)
        return false
    }

    fun cancel(alarm: TimerAlarm) {
        val operation = PendingIntent.getBroadcast(
            appContext,
            alarm.requestCode,
            alarmIntent(alarm),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    fun cancelAll() {
        TimerAlarm.entries.forEach(::cancel)
    }

    private fun alarmIntent(alarm: TimerAlarm) =
        Intent(appContext, ExactAlarmReceiver::class.java).setAction(alarm.action)

    private companion object {
        const val TAG = "UmamiTAlarm"
    }
}
