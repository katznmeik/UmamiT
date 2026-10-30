package com.umamit.core.timer.controller

import android.util.Log
import com.umamit.core.common.ClockReading
import com.umamit.core.model.TimerSlot

/**
 * The alert watchdog alarm ([TimerAlarmKind.ALERT_WATCHDOG], 04 §6.2, §7.2; D-063, D-068). Its
 * only job is to start a new process after a kill, whose recovery then replays the alert that
 * had not run its cycle (at least once, D-047). It has two cadences:
 *
 * - **While the alert plays** it comes every [INTERVAL_MS]: a process killed during ALERTING
 *   keeps only the overtime alarm, whose recovery would acknowledge the step silently (D-063).
 * - **While a step runs** it is the deadline guard, [GUARD_MS] after the deadline (D-068): a
 *   long step has the deadline alarm as its only alarm, and Android skips that broadcast while
 *   Auto Backup runs the app's full backup, then kills the process. The guard starts a new
 *   process, whose recovery finds the deadline passed less than 10 min ago and alerts.
 *
 * In a live process it finds nothing due: the deadline turn has already replaced the guard by
 * the alert cadence, and `StartAlert` never restarts the playing alert. Otherwise it is
 * cancelled. Each [slot] has its own (107, 207; D-098). Used inside the controller's event lock.
 */
internal class AlertWatchdog(private val alarms: TimerAlarmPort, private val slot: TimerSlot) {
    /** When the watchdog alarm is set (elapsed); null when it is not. */
    private var atMs: Long? = null

    /** [atMs] is the alert's cadence, not the deadline guard. */
    private var forAlert = false

    /** Whether [atMs] is known: a new process does not know what the old one left set. */
    private var known = false

    /** When the watchdog is due as this process set it, for the log's scheduled vs actual time. */
    val scheduled: ScheduledTime? get() = atMs?.let { ScheduledTime(TimerAlarmKind.ALERT_WATCHDOG, it) }

    /**
     * At the end of a turn whose process knows its state: while an alert [playing], the alarm is
     * set [INTERVAL_MS] after it was last due, the first time [INTERVAL_MS] after the alert
     * started (not at a guard the deadline left); else at [guardAtMs], the deadline guard of the
     * running step (null: no step runs); otherwise it is cancelled, also one a killed process
     * left set.
     */
    fun follow(playing: Boolean, guardAtMs: Long?, now: ClockReading) {
        val armed = atMs
        val wanted = when {
            playing && forAlert && armed != null && armed > now.elapsedMs -> armed
            playing -> now.elapsedMs + INTERVAL_MS
            else -> guardAtMs
        }
        if (known && wanted == armed && forAlert == playing) return
        atMs = wanted
        forAlert = playing
        known = true
        attempt { alarms.watchAlert(slot, wanted) }?.let { Log.e(TAG, "$slot alert watchdog not set to $wanted", it) }
    }

    companion object {
        /** How often the watchdog comes while the alert plays. */
        const val INTERVAL_MS = 30_000L

        /**
         * How long after a running step's deadline the guard comes (D-068): late enough that a
         * live process has handled the deadline and an Auto Backup that swallowed it has ended
         * and killed the process, early enough that the alert comes at most this late.
         */
        const val GUARD_MS = 90_000L
        private const val TAG = "UmamiTTimer"
    }
}
