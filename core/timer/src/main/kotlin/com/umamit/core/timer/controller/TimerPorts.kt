package com.umamit.core.timer.controller

import com.umamit.core.common.ClockReading
import com.umamit.core.common.MonotonicClock
import com.umamit.core.common.WallClock
import com.umamit.core.common.readClocks
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.engine.SessionKind
import javax.inject.Inject

/*
 * The ports of the BrewSessionController (04 §4): everything the engine's
 * effects reach outside the controller. The controller calls them inside its
 * event lock, in the order of the effects (D-047), so a port call must not
 * block and must not call back into the controller synchronously; an adapter
 * that reports back (the alert player, a receiver) calls the controller's
 * suspend functions from its own coroutine. A port that throws is logged and
 * skipped; the next effect still runs (rule 8: nothing blocks `StartAlert`).
 *
 * The Android adapters come with M1-09 part 2 (#42: alarms, service), M1-10
 * (#44: alert player), M1-11 (#47: notifier), M1-20 (#43: diagnostics) and
 * M1-16 (saved sheet). Until a ticket binds its adapter, the controller cannot
 * be injected, so no build ships a silent stand-in.
 */

/** [TimerEffect.ScheduleAlarms][com.umamit.core.timer.engine.TimerEffect.ScheduleAlarms]: the alarms of 04 §6.2. */
interface TimerAlarmPort {
    /**
     * Reconciles the alarms with [due], the full due set of the new state, per
     * [TimerAlarmKind] and its fixed request code: a kind whose time is present
     * is set (replacing the earlier alarm of that kind), a kind whose time is
     * absent is cancelled. [DueTimes.NONE] cancels all.
     */
    fun reconcile(due: DueTimes)
}

/** `StartForeground` / `StopForeground`: the `specialUse` foreground service (04 §6.3, D-053). */
interface TimerServicePort {
    /**
     * Ensures the service runs (idempotent), with the notification of [state]
     * as its foreground notification: the alert (ID 2) while alerting, else
     * notification 1 (D-044). A start from the background may be refused; the
     * adapter logs it and the regular notification stays until the next
     * interaction or alarm (04 §6.3).
     */
    fun startForeground(state: TimerUiState.Active)

    /** Stops the service if it runs and removes notifications 1 and 2, also when posted without it (04 §8). */
    fun stopForeground()
}

/** `UpdateNotification` and `NotifySessionSaved` (04 §8.2, §8.3). */
interface TimerNotifierPort {
    /**
     * Posts the notification of [state]. While alerting: the alert (ID 2), with
     * its full-screen intent only when [fullScreen] (the alert starts in the
     * same transition), and notification 1 is cancelled. Otherwise
     * notification 1: the Live Update while the service runs, a regular silent
     * ongoing notification while the session is demoted or a long step runs.
     */
    fun update(state: TimerUiState.Active, fullScreen: Boolean)

    /** „Sitzung gespeichert" (ID 3, channel `sessions`) for session [sessionId] (TMR-14). */
    fun notifySessionSaved(sessionId: String)
}

/**
 * The alert of one alerting step: its session, its step and its deadline on
 * the wall clock. `StartAlert` plays it at most once per deadline; the player
 * reports the end of its cycle with [BrewSessionController.alertCompleted] and
 * this value, so a late report for an earlier alert changes nothing.
 */
data class StepAlert(val kind: SessionKind, val stepIndex: Int, val deadlineWallMs: Long)

/** `PlayPreAlert`, `StartAlert`, `StopAlert`: the AlertPlayer that follows the ringer mode (04 §9). */
interface TimerAlertPort {
    /** The pre-alert tick (TMR-23); the engine plays it at most once per step. */
    fun playPreAlert()

    /**
     * Starts [alert] (SET-14). When its cycle has run, the player calls
     * [BrewSessionController.alertCompleted] with [alert] (at least once per
     * alert, D-047). The controller never starts the same alert twice.
     */
    fun startAlert(alert: StepAlert)

    /** Stops the playing alert; idempotent. */
    fun stopAlert()
}

/** `ShowSavedSheet`: the „Gespeichert im Journal" sheet (SCR-06) for a finished session. */
interface SavedSheetPort {
    fun showSavedSheet(sessionId: String)
}

/** The diagnostics log (06 §5.6, M1-20). [record] is called inside the event lock: it must not block. */
interface TimerDiagnosticsPort {
    fun record(entry: TimerDiagnostic)
}

/**
 * The controller's in-process wake-up (04 §4, §6.1): the timer it re-arms from
 * the due set after every event, so a due time-based event is handled while
 * the process lives; the exact alarms are the second, independent path.
 */
interface TimerWakeUpPort {
    /** Calls [onDue] once after [delayMs], replacing an armed wake-up. */
    fun arm(delayMs: Long, onDue: suspend () -> Unit)

    /** Disarms the wake-up; idempotent. */
    fun cancel()
}

/** The ports the controller drives, grouped for injection. */
data class TimerPorts @Inject constructor(
    val alarms: TimerAlarmPort,
    val service: TimerServicePort,
    val notifier: TimerNotifierPort,
    val alert: TimerAlertPort,
    val diagnostics: TimerDiagnosticsPort,
    val savedSheet: SavedSheetPort,
    val wakeUp: TimerWakeUpPort,
)

/** The monotonic clock with the boot count and the wall clock with the zone (04 §5). */
class TimerClocks @Inject constructor(private val monotonic: MonotonicClock, private val wall: WallClock) {
    /** Both clocks at one moment: the `now` of one reduce (04 §4). */
    fun read(): ClockReading = readClocks(monotonic, wall)
}
