package com.umamit.core.timer.controller

import android.util.Log
import com.umamit.core.common.ClockReading

/**
 * The alert watchdog (04 §6.2, §7.2; D-063). A process killed during ALERTING keeps only the
 * overtime alarm, whose recovery would acknowledge the step silently. So while the alert plays,
 * an exact alarm ([TimerAlarmKind.ALERT_WATCHDOG]) comes every [INTERVAL_MS]: a process it
 * starts after a kill recovers the state and replays the alert that had not run its cycle
 * (at least once, D-047). In a live process it finds nothing due and is set again. Used inside
 * the controller's event lock.
 */
internal class AlertWatchdog(private val alarms: TimerAlarmPort) {
    /** When the watchdog alarm is set (elapsed); null when it is not. */
    private var atMs: Long? = null

    /** Whether [atMs] is known: a new process does not know what the old one left set. */
    private var known = false

    /** When the watchdog is due as this process set it, for the log's scheduled vs actual time. */
    val scheduled: ScheduledTime? get() = atMs?.let { ScheduledTime(TimerAlarmKind.ALERT_WATCHDOG, it) }

    /**
     * At the end of a turn whose process knows its state: while an alert [playing], the alarm is
     * set [INTERVAL_MS] after it was last due; otherwise it is cancelled, also one a killed
     * process left set.
     */
    fun follow(playing: Boolean, now: ClockReading) {
        val armed = atMs
        val wanted = when {
            !playing -> null
            armed != null && armed > now.elapsedMs -> armed
            else -> now.elapsedMs + INTERVAL_MS
        }
        if (known && wanted == armed) return
        atMs = wanted
        known = true
        attempt { alarms.watchAlert(wanted) }?.let { Log.e(TAG, "alert watchdog not set to $wanted", it) }
    }

    companion object {
        /** How often the watchdog comes while the alert plays. */
        const val INTERVAL_MS = 30_000L
        private const val TAG = "UmamiTTimer"
    }
}
