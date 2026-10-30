package com.umamit.core.timer.controller

import com.umamit.core.common.ClockReading
import com.umamit.core.common.MonotonicClock
import com.umamit.core.common.WallClock
import com.umamit.core.common.readClocks
import com.umamit.core.model.TimerSlot
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
 * (#44: alert player), M1-20 (#43: diagnostics), M1-11 (#47: notifier,
 * `AndroidTimerNotifier`) and M1-16 (saved sheet). Until a ticket binds its adapter, the controller cannot
 * be injected, so no build ships a silent stand-in.
 */

/**
 * [TimerEffect.ScheduleAlarms][com.umamit.core.timer.engine.TimerEffect.ScheduleAlarms]: the alarms of 04 §6.2,
 * per slot. Each slot has its own request codes ([TimerAlarmKind.requestCodeIn]), so a call for one slot never
 * sets, moves or cancels an alarm of the other (D-098).
 */
interface TimerAlarmPort {
    /**
     * Reconciles the alarms of [slot] with [due], the full due set of its new
     * state, per [TimerAlarmKind] and its fixed request code: a kind whose time
     * is present is set (replacing the earlier alarm of that kind), a kind whose
     * time is absent is cancelled. [DueTimes.NONE] cancels all of the slot's.
     */
    fun reconcile(slot: TimerSlot, due: DueTimes)

    /**
     * Sets the alarm of [kind] in [slot] again at [atMs] on its clock and leaves
     * the other alarms as they are: the turn of that alarm could not read the
     * persisted timer, so the alarm must come again (04 §7.2, D-057).
     */
    fun retry(slot: TimerSlot, kind: TimerAlarmKind, atMs: Long)

    /**
     * Sets the alert watchdog of [slot] ([TimerAlarmKind.ALERT_WATCHDOG], exact, elapsed clock) at
     * [atElapsedMs], replacing the earlier one, or cancels it (null); [reconcile] leaves it alone.
     * It comes while the slot's alert plays, and after the deadline of its running step (D-068), so a
     * process killed meanwhile is started again and its recovery replays the alert (04 §7.2,
     * D-063).
     */
    fun watchAlert(slot: TimerSlot, atElapsedMs: Long?)
}

/** `StartForeground` / `StopForeground`: the `specialUse` foreground service (04 §6.3, D-053). */
interface TimerServicePort {
    /**
     * Ensures the service runs (idempotent), with the notification of [state]
     * as its foreground notification: the alert (ID 2) while alerting, else
     * notification 1 (D-044). The alert carries its full-screen intent only
     * when [fullScreen]: the alert starts in the same transition, as for
     * `UpdateNotification` (D-057). A start from the background may be
     * refused; the adapter logs it and posts the notification without the
     * service until the next interaction or alarm (04 §6.3).
     */
    fun startForeground(state: TimerUiState.Active, fullScreen: Boolean)

    /** Stops the service if it runs and removes notifications 1, 2 and 4, also when posted without it (04 §8). */
    fun stopForeground()

    /**
     * Sends the start Android refused last once more (04 §6.3, §7.2; D-057, D-063), from a turn
     * that may start the service from the background (an exact alarm, a notification action, a
     * tap) and sent no service command itself. The notification is that of [state], the state
     * now, not the refused one, and it does not alert again; the alert keeps its full-screen
     * intent while [fullScreen] (it still plays). A null [state] (the timer needs no service
     * now) drops the refused start. Nothing happens when no start was refused since the last
     * command or stop. A start refused again is logged and waits for the next such turn.
     */
    fun retryRefusedStart(state: TimerUiState.Active?, fullScreen: Boolean)
}

/** `UpdateNotification` and `NotifySessionSaved` (04 §8.2, §8.3). */
interface TimerNotifierPort {
    /**
     * Posts the notification of [state]. While alerting: the alert (ID 2), with
     * its full-screen intent only when [fullScreen] (the alert starts in the
     * same transition), and notification 1 is cancelled. Otherwise
     * notification 1, the Live Update, while the service runs, and the quiet
     * notification (ID 4) while the session is demoted or a long step runs
     * (D-064). While the service runs, the notification changes only through
     * the service (D-057).
     */
    fun update(state: TimerUiState.Active, fullScreen: Boolean)

    /**
     * Notification 5 (channel `timer_other`, importance LOW, silent; 04 §8.2, D-098): the active timer of the
     * other slot while the service shows the first one, with its own actions; a held quick alert as „Fertig,
     * wartet auf den Aufguss" without a full-screen intent. Null removes it. Its LOW importance keeps the alert
     * the app's only notification of importance DEFAULT or higher (D-044). The service also keeps the process
     * awake for it while it runs a short step (04 §6.1 two slots).
     */
    fun showOther(state: TimerUiState.Active?)

    /**
     * „Sitzung gespeichert" (ID 3, channel `sessions`) for session [sessionId]: the midnight
     * timeout (TMR-14) and *Beenden & neu starten* (TMR-21, D-054).
     */
    fun notifySessionSaved(sessionId: String)

    /**
     * „Dein Gyokuro-Timer läuft weiter" (channel `sessions`, 04 §7.3): the session of [state]
     * survived a reboot. Never while alerting: the alert is the notice then (D-044).
     */
    fun notifyTimerResumed(state: TimerUiState.Active)
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
    /**
     * The pre-alert tick (TMR-23); the engine plays it at most once per step. [scheduled] is
     * when it was due, so the log shows how late the tick actually started (06 §5.6, D-068).
     */
    fun playPreAlert(scheduled: ScheduledTime?)

    /**
     * Starts [alert] (SET-14). When its cycle has run, the player calls
     * [BrewSessionController.alertCompleted] with [alert] (at least once per
     * alert, D-047). The controller never starts the same alert twice.
     * [scheduled] is the step's deadline, so the log shows how late the sound actually
     * started (06 §5.6, TMR-30, D-068).
     */
    fun startAlert(alert: StepAlert, scheduled: ScheduledTime?)

    /** Stops the playing alert; idempotent. */
    fun stopAlert()

    /**
     * While [ready] (a timer is active), keeps the sounds of the next tick and alert loaded, so a
     * cue only starts its sound; false releases them (D-092). Called at the end of every turn,
     * after the turn's own cues; idempotent. Like every port call it must not block: the
     * loading happens later on the player's own thread.
     */
    fun keepReady(ready: Boolean)
}

/** `ShowSavedSheet`: the „Gespeichert im Journal" sheet (SCR-06) for a finished session. */
interface SavedSheetPort {
    fun showSavedSheet(sessionId: String)
}

/**
 * The diagnostics log (06 §5.6, M1-20; `LogTimerDiagnostics`). [record] is called inside the
 * event lock and on the main thread: it must not block, and it must not throw.
 */
fun interface TimerDiagnosticsPort {
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
