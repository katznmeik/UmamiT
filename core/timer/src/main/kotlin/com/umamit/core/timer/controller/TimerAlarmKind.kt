package com.umamit.core.timer.controller

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
 * so rescheduling replaces the alarm (D-054). [exact] alarms use
 * `setExactAndAllowWhileIdle` with a wake-up; the midnight alarm is an inexact
 * `set(RTC)` without one ([wakeUp] false). [event] is the time-based event the
 * alarm stands for; the receiver hands the kind to
 * [BrewSessionController.onAlarm], which sends every event due at that moment.
 */
enum class TimerAlarmKind(
    val requestCode: Int,
    val clock: AlarmClock,
    val exact: Boolean,
    val wakeUp: Boolean,
    val event: TimerEvent,
) {
    PRE_ALERT(requestCode = 101, AlarmClock.ELAPSED, exact = true, wakeUp = true, TimerEvent.PreAlertReached),
    DEADLINE(requestCode = 102, AlarmClock.ELAPSED, exact = true, wakeUp = true, TimerEvent.DeadlineReached),
    OVERTIME_TIMEOUT(requestCode = 103, AlarmClock.ELAPSED, exact = true, wakeUp = true, TimerEvent.OvertimeTimeout),
    IDLE_DEMOTE(requestCode = 104, AlarmClock.WALL, exact = true, wakeUp = true, TimerEvent.IdleDemote),
    QUICK_CANCEL(requestCode = 105, AlarmClock.WALL, exact = true, wakeUp = true, TimerEvent.QuickTimerTimeout),
    MIDNIGHT(requestCode = 106, AlarmClock.WALL, exact = false, wakeUp = false, TimerEvent.MidnightTimeout),
    ;

    /** This kind's time in [due] (on [clock]); null when it is not due in that state (cancel it). */
    fun timeIn(due: DueTimes): Long? = when (this) {
        PRE_ALERT -> due.preAlertAtElapsedMs
        DEADLINE -> due.deadlineElapsedMs
        OVERTIME_TIMEOUT -> due.overtimeTimeoutAtElapsedMs
        IDLE_DEMOTE -> due.idleDemoteAtWallMs
        QUICK_CANCEL -> due.quickCancelAtWallMs
        MIDNIGHT -> due.midnightAtWallMs
    }

    companion object {
        /** The kind whose alarm stands for the time-based [event]; null for any other event. */
        fun of(event: TimerEvent): TimerAlarmKind? = entries.firstOrNull { it.event == event }
    }
}
