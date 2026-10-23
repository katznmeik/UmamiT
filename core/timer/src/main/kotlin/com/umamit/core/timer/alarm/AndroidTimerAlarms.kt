package com.umamit.core.timer.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import com.umamit.core.timer.controller.AlarmClock
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerAlarmPort
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import com.umamit.core.timer.engine.DueTimes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The controller's alarm port on [AlarmManager] (04 §6.2, D-057): one alarm per
 * [TimerAlarmKind], each with its fixed request code, delivered to the
 * non-exported [TimerAlarmReceiver].
 *
 * - The exact kinds use `setExactAndAllowWhileIdle`: `ELAPSED_REALTIME_WAKEUP`
 *   for the pre-alert, the deadline and the overtime timeout, so a wall-clock
 *   change never moves them (E1); `RTC_WAKEUP` for idle demote and the
 *   quick-timer cancel, which follow the wall clock.
 * - The midnight timeout is an inexact `set(RTC)` that waits until the phone
 *   wakes; recovery applies it too (04 §7.2).
 *
 * `USE_EXACT_ALARM` is granted at install (D-013). Should exact alarms still be
 * refused, the alarm is set inexact (`setAndAllowWhileIdle`) and the diagnostics
 * log says so; the reliability banner reads `canScheduleExactAlarms()` (TMR-31).
 */
class AndroidTimerAlarms @Inject constructor(
    @ApplicationContext context: Context,
    private val clocks: TimerClocks,
    private val diagnostics: TimerDiagnosticsPort,
) : TimerAlarmPort {
    private val appContext = context.applicationContext
    private val alarmManager = checkNotNull(appContext.getSystemService(AlarmManager::class.java))

    override fun reconcile(due: DueTimes) {
        TimerAlarmKind.DUE.forEach { kind ->
            val at = kind.timeIn(due)
            if (at == null) cancel(kind) else schedule(kind, at)
        }
    }

    override fun retry(kind: TimerAlarmKind, atMs: Long) = schedule(kind, atMs)

    override fun watchAlert(atElapsedMs: Long?) {
        val kind = TimerAlarmKind.ALERT_WATCHDOG
        if (atElapsedMs == null) cancel(kind) else schedule(kind, atElapsedMs)
    }

    /**
     * Sets the alarm of [kind] at [atMs] on its clock, replacing the earlier one
     * (same request code and intent).
     *
     * Lint's MissingPermission check only knows `SCHEDULE_EXACT_ALARM`; the app
     * holds `USE_EXACT_ALARM` (D-013), checks `canScheduleExactAlarms()` and
     * catches the `SecurityException`.
     */
    @SuppressLint("MissingPermission")
    private fun schedule(kind: TimerAlarmKind, atMs: Long) {
        val operation = PendingIntent.getBroadcast(
            appContext,
            kind.requestCode,
            TimerAlarmReceiver.intent(appContext, kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val type = kind.alarmType()
        if (!kind.exact) {
            alarmManager.set(type, atMs, operation)
            return
        }
        if (alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(type, atMs, operation)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm $kind refused; falling back to an inexact alarm", e)
            }
        }
        alarmManager.setAndAllowWhileIdle(type, atMs, operation)
        diagnostics.record(TimerDiagnostic.AlarmNotExact(clocks.read(), kind))
    }

    private fun cancel(kind: TimerAlarmKind) {
        val operation = PendingIntent.getBroadcast(
            appContext,
            kind.requestCode,
            TimerAlarmReceiver.intent(appContext, kind),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private companion object {
        const val TAG = "UmamiTAlarm"
    }
}

/** The [AlarmManager] type of this kind: its clock, with or without waking the phone (04 §6.2). */
private fun TimerAlarmKind.alarmType(): Int = when (clock) {
    AlarmClock.ELAPSED -> if (wakeUp) AlarmManager.ELAPSED_REALTIME_WAKEUP else AlarmManager.ELAPSED_REALTIME
    AlarmClock.WALL -> if (wakeUp) AlarmManager.RTC_WAKEUP else AlarmManager.RTC
}
