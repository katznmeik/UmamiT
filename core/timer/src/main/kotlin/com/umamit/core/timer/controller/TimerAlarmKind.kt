package com.umamit.core.timer.controller

import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.engine.TimerEvent

/** The clock an alarm time is on: `ELAPSED_REALTIME` or `RTC` (04 §6.2). */
enum class AlarmClock {
    /** Monotonic, since boot (`…ElapsedMs`); a wall-clock change never moves it (E1). */
    ELAPSED,

    /** Wall clock (`…WallMs`); follows a wall-clock or zone change. */
    WALL,
}

/**
 * The alarms of 04 §6.2, one per [DueTimes] field, with a fixed request code
 * per slot so rescheduling replaces the alarm (D-054, D-098), and the alert
 * watchdog (D-063). [exact] alarms use `setExactAndAllowWhileIdle` with a
 * wake-up; the midnight alarm is an inexact `set(RTC)` without one ([wakeUp]
 * false). [event] is the time-based event the alarm stands for, null for the
 * watchdog; the receiver hands the slot and the kind to
 * [BrewSessionController.onAlarm], which sends every event due at that moment.
 * [codeInSlot] is the kind's place in a slot's block of request codes
 * ([requestCodeIn]).
 */
enum class TimerAlarmKind(
    private val codeInSlot: Int,
    val clock: AlarmClock,
    val exact: Boolean,
    val wakeUp: Boolean,
    val event: TimerEvent?,
) {
    PRE_ALERT(codeInSlot = 1, AlarmClock.ELAPSED, exact = true, wakeUp = true, TimerEvent.PreAlertReached),
    DEADLINE(codeInSlot = 2, AlarmClock.ELAPSED, exact = true, wakeUp = true, TimerEvent.DeadlineReached),
    OVERTIME_TIMEOUT(codeInSlot = 3, AlarmClock.ELAPSED, exact = true, wakeUp = true, TimerEvent.OvertimeTimeout),
    IDLE_DEMOTE(codeInSlot = 4, AlarmClock.WALL, exact = true, wakeUp = true, TimerEvent.IdleDemote),
    QUICK_CANCEL(codeInSlot = 5, AlarmClock.WALL, exact = true, wakeUp = true, TimerEvent.QuickTimerTimeout),
    MIDNIGHT(codeInSlot = 6, AlarmClock.WALL, exact = false, wakeUp = false, TimerEvent.MidnightTimeout),

    /**
     * Every 30 s while the alert plays (D-063), and 90 s after the deadline while a step runs
     * (the deadline guard, D-068): no event and no due time of its own. Its only job is to start
     * a new process after a kill, whose recovery replays the alert (04 §7.2).
     */
    ALERT_WATCHDOG(codeInSlot = 7, AlarmClock.ELAPSED, exact = true, wakeUp = true, event = null),
    ;

    /**
     * The request code of this kind's alarm in [slot] (04 §6.2, D-098): 101–107 in the brew slot, the codes
     * of v0.2.2, so a brew running during the update keeps its alarms; 201–207 in the quick slot, so the
     * slots never replace each other's alarms.
     */
    fun requestCodeIn(slot: TimerSlot): Int = when (slot) {
        TimerSlot.BREW -> BREW_CODES
        TimerSlot.QUICK -> QUICK_CODES
    } + codeInSlot

    /**
     * This kind's time in [due] (on [clock]); null when it is not due in that state (cancel it),
     * and always for the watchdog.
     */
    fun timeIn(due: DueTimes): Long? = when (this) {
        PRE_ALERT -> due.preAlertAtElapsedMs
        DEADLINE -> due.deadlineElapsedMs
        OVERTIME_TIMEOUT -> due.overtimeTimeoutAtElapsedMs
        IDLE_DEMOTE -> due.idleDemoteAtWallMs
        QUICK_CANCEL -> due.quickCancelAtWallMs
        MIDNIGHT -> due.midnightAtWallMs
        ALERT_WATCHDOG -> null
    }

    companion object {
        /** The brew slot's block of request codes (101–107), the codes of every timer up to v0.2.2. */
        private const val BREW_CODES = 100

        /** The quick slot's block of request codes (201–207, D-098). */
        private const val QUICK_CODES = 200

        /** The kinds of the [DueTimes] fields, which `ScheduleAlarms` reconciles (04 §6.2). */
        val DUE: List<TimerAlarmKind> = entries.filter { it.event != null }

        /** The kind whose alarm stands for the time-based [event]; null for any other event. */
        fun of(event: TimerEvent): TimerAlarmKind? = entries.firstOrNull { it.event == event }
    }
}

/**
 * The alarm of [kind] in [slot] (04 §6.2, D-098): what an alarm broadcast hands the controller, and which of
 * the alarm manager's alarms it is ([requestCode]).
 */
data class TimerAlarm(val slot: TimerSlot, val kind: TimerAlarmKind) {
    val requestCode: Int get() = kind.requestCodeIn(slot)
}
