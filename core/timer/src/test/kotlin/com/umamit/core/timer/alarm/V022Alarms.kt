package com.umamit.core.timer.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.umamit.core.timer.controller.TimerAlarmKind

/**
 * The alarms v0.2.2 left set for a timer, brew or quick, as the update to v0.3.0 finds them (04 §6.2, §7.5):
 * the request codes 101–107 and the broadcasts „…TIMER_ALARM.<KIND>" of that version, written out here
 * rather than taken from the code under test.
 */
object V022Alarms {
    private val REQUEST_CODES = mapOf(
        TimerAlarmKind.PRE_ALERT to 101,
        TimerAlarmKind.DEADLINE to 102,
        TimerAlarmKind.OVERTIME_TIMEOUT to 103,
        TimerAlarmKind.IDLE_DEMOTE to 104,
        TimerAlarmKind.QUICK_CANCEL to 105,
        TimerAlarmKind.MIDNIGHT to 106,
        TimerAlarmKind.ALERT_WATCHDOG to 107,
    )

    /** The request code v0.2.2 gave the alarm of [kind]. */
    fun requestCodeOf(kind: TimerAlarmKind): Int = REQUEST_CODES.getValue(kind)

    /** Sets the exact elapsed alarm of [kind] at [atElapsedMs] exactly as v0.2.2 set it. */
    fun set(context: Context, kind: TimerAlarmKind, atElapsedMs: Long) {
        val intent = Intent(context, TimerAlarmReceiver::class.java)
            .setAction("com.umamit.core.timer.action.TIMER_ALARM.${kind.name}")
        val operation = PendingIntent.getBroadcast(
            context,
            requestCodeOf(kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        checkNotNull(context.getSystemService(AlarmManager::class.java))
            .setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsedMs, operation)
    }
}
