package com.umamit.core.timer.controller

import com.umamit.core.common.ClockReading
import com.umamit.core.model.AlertLength
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.alert.AlertConditions
import com.umamit.core.timer.alert.AlertCue
import com.umamit.core.timer.alert.AlertOutputs
import com.umamit.core.timer.alert.CueStart
import com.umamit.core.timer.diagnostics.PermissionStates
import com.umamit.core.timer.engine.TimerEffect
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome

/** What recovery did with the persisted state: the row of 04 §7.2 that applied (06 §5.6). */
enum class RecoveryDecision {
    /** Restored as it was: alarms, notification, and the service where the state needs it. */
    RESTORED,

    /** The deadline passed less than 10 min ago, or the alert had not run its cycle: replayed. */
    ALERT_REPLAYED,

    /** A quick timer's alert waits for the brew's (04 §7.2 two slots, §9.3; D-098): held, it rings after it. */
    ALERT_HELD,

    /** The deadline passed 10 min or more ago: poured on time, silently (`overtimeAutoAck`). */
    OVERTIME_ACKNOWLEDGED,

    /** Waiting for 30 min: demoted, without the service. */
    DEMOTED,

    /** The midnight timeout or the quick timer's cancel ended the session silently. */
    SESSION_ENDED,
}

/** Where an event came from, for the diagnostics log (06 §5.6). */
enum class EventSource {
    /** A tap in the app (timer screen, active brew bar, Start). */
    APP,

    /** A notification or Live Update action (TMR-41). */
    NOTIFICATION,

    /** An exact or inexact alarm of 04 §6.2. */
    ALARM,

    /** The controller's in-process wake-up. */
    WAKE_UP,

    /** The AlertPlayer reporting the end of its cycle. */
    ALERT_PLAYER,

    /** The controller's first turn in a process: `recover` from the persisted state (04 §7.2). */
    RECOVERY,

    /** The controller's alert arbitration between its two slots: a quick alert held or released (04 §9.3). */
    ARBITRATION,
}

/**
 * When a time-based event of [slot]'s timer was due: [atMs] on the clock of
 * [kind]. With the reading of the moment it was handled, the log shows
 * scheduled vs actual fire time (06 §5.6, TMR-30); a quick timer's line names
 * its slot, so the accuracy run tells the two timers apart (D-098, #137).
 *
 * [heldMs] is set only for the sound of a quick alert released after it was held behind the brew's (04 §9.3): it
 * was due at its release ([atMs]), [heldMs] after its deadline, so the log shows the player's own delay and the
 * hold apart, never a hold as lateness.
 */
data class ScheduledTime(
    val kind: TimerAlarmKind,
    val atMs: Long,
    val slot: TimerSlot = TimerSlot.BREW,
    val heldMs: Long? = null,
) {
    /** How late [now] is after the scheduled time, in ms, on this kind's clock. */
    fun lateMs(now: ClockReading): Long = when (kind.clock) {
        AlarmClock.ELAPSED -> now.elapsedMs - atMs
        AlarmClock.WALL -> now.wallMs - atMs
    }
}

/**
 * One entry of the timer's diagnostics log (06 §5.6), written by the
 * controller. It holds plain values only; tea names may appear through the
 * events, notes never do. [at] is the clock reading of the event lock turn.
 */
sealed interface TimerDiagnostic {
    val at: ClockReading

    /**
     * The engine handled [event] from [source] with [outcome], moving from
     * phase [from] to [to] (null: idle). [scheduled] is set for a time-based
     * event: when it was due.
     */
    data class EventHandled(
        override val at: ClockReading,
        val event: TimerEvent,
        val source: EventSource,
        val outcome: TransitionOutcome,
        val from: TimerPhase?,
        val to: TimerPhase?,
        val scheduled: ScheduledTime?,
    ) : TimerDiagnostic

    /**
     * The alarm of [kind] in [slot] arrived; [scheduled] is its time in the slot's current due set, null when
     * nothing is due.
     */
    data class AlarmReceived(
        override val at: ClockReading,
        val kind: TimerAlarmKind,
        val scheduled: ScheduledTime?,
        val slot: TimerSlot = TimerSlot.BREW,
    ) : TimerDiagnostic

    /**
     * Writing a transition failed (04 §7.1): the transaction of its data
     * effects and `Persist` ([retry] false), or the retry of `Persist` alone
     * ([retry] true). The previous `active_brew` row stays until a later
     * persist succeeds; the side effects ran anyway.
     */
    data class PersistFailed(override val at: ClockReading, val retry: Boolean, val error: Throwable) : TimerDiagnostic

    /** A port threw while executing [effect]; the following effects still ran. */
    data class EffectFailed(override val at: ClockReading, val effect: TimerEffect, val error: Throwable) :
        TimerDiagnostic

    /** The alert player reported the end of [alert], which is no longer the playing alert; ignored. */
    data class StaleAlertCompletion(override val at: ClockReading, val alert: StepAlert) : TimerDiagnostic

    /**
     * The persisted `active_brew` row is not a state `restore` accepts ([error]); it was
     * cleared instead of running a wrong timer (04 §4, §7.2).
     */
    data class SnapshotUnreadable(override val at: ClockReading, val error: IllegalArgumentException) :
        TimerDiagnostic

    /**
     * Reading the persisted state at process start failed ([error]); the controller stays
     * idle for this turn and tries again in the next one (04 §7.2, D-057).
     */
    data class RecoveryFailed(override val at: ClockReading, val error: Throwable) : TimerDiagnostic

    /**
     * The persisted state could not be read for [failingForMs] (04 §7.2, D-063): this process
     * stops its retries; the stored timer, its alarms and its session stay, and a later input
     * reads again. Logged once per process.
     */
    data class RecoveryRetriesStopped(override val at: ClockReading, val failingForMs: Long) : TimerDiagnostic

    /**
     * The alarm of [kind] could not be exact (`canScheduleExactAlarms()` false or a
     * `SecurityException`) and was set inexact, allowed while idle; the reliability banner
     * reads the same check (04 §6.2, TMR-31).
     */
    data class AlarmNotExact(override val at: ClockReading, val kind: TimerAlarmKind) : TimerDiagnostic

    /**
     * Android refused to start the foreground service, or to make it show notification
     * [notificationId] in the foreground ([error], 04 §6.3); the notification was posted
     * without the service until the next interaction or alarm. [again]: the refused start
     * sent once more was refused too (D-063).
     */
    data class ServiceStartRefused(
        override val at: ClockReading,
        val notificationId: Int,
        val error: Exception,
        val again: Boolean = false,
    ) : TimerDiagnostic

    /** The foreground service runs in the foreground with notification [notificationId] (04 §6.3). */
    data class ServiceInForeground(override val at: ClockReading, val notificationId: Int) : TimerDiagnostic

    /**
     * The service took over notification [notificationId], which a killed process had left
     * flagged as a foreground notification that the app cannot cancel, so its foreground switch
     * removes it (D-084).
     */
    data class ServiceTookOverStale(override val at: ClockReading, val notificationId: Int) : TimerDiagnostic

    /** The foreground service ended (stopped by the port or by itself). */
    data class ServiceStopped(override val at: ClockReading) : TimerDiagnostic

    /**
     * The alert player started [cue] with the [conditions] it read (ringer mode, call, Do Not
     * Disturb) and the [outputs] the ringer-mode matrix allowed then (04 §9.1, 06 §5.6), playing
     * [tone] (the file key; for the pre-alert its sound's, SET-26) for [length] (SET-14). [scheduled] is when
     * a step's tick or alert was due (null for *Alarm testen*): with [at], the moment the sound
     * started, it is the delay *Diagnose* and the accuracy run measure (TMR-30, D-068). [start]
     * splits a step's cue into the time it was queued, waited for the settings and started its
     * outputs, and says whether its sound was loaded before (D-092; null for *Alarm testen*).
     * [volumePercent] is the *Lautstärke* its sound played at on the alarm stream (SET-27, D-098);
     * [alarmVolumeOff]: the phone's alarm volume was 0, so the sound was inaudible (TMR-31, E18).
     */
    data class AlertPlayed(
        override val at: ClockReading,
        val cue: AlertCue,
        val conditions: AlertConditions,
        val outputs: AlertOutputs,
        val tone: String,
        val length: AlertLength,
        val scheduled: ScheduledTime? = null,
        val start: CueStart? = null,
        val volumePercent: Int = 100,
        val alarmVolumeOff: Boolean = false,
    ) : TimerDiagnostic

    /**
     * [trigger] asked the controller to recover the persisted timer (04 §7.2): the app start,
     * `BOOT_COMPLETED` or `MY_PACKAGE_REPLACED` (06 §5.6). [alreadyRecovered]: this process
     * had recovered before, so nothing changed.
     */
    data class RecoveryRequested(
        override val at: ClockReading,
        val trigger: RecoveryTrigger,
        val alreadyRecovered: Boolean,
    ) : TimerDiagnostic

    /**
     * Recovery restored the persisted [from] state and applied the row of 04 §7.2 that
     * [decision] names, leaving [to] (null: idle). [rebooted]: the phone booted since it was
     * persisted, so its elapsed values were moved to the new clock (E2). [service]: the state
     * runs with the foreground service, whose start recovery attempted.
     */
    data class Recovered(
        override val at: ClockReading,
        val from: TimerPhase,
        val to: TimerPhase?,
        val rebooted: Boolean,
        val decision: RecoveryDecision,
        val service: Boolean,
    ) : TimerDiagnostic

    /**
     * Recovery found a session in progress without its `active_brew` row (a device restore, E11,
     * or a stored timer that was cleared as unreadable) and ended it (04 §7.2): [saved] when it
     * had a completed infusion, else it was discarded.
     */
    data class OrphanSessionEnded(override val at: ClockReading, val saved: Boolean) : TimerDiagnostic

    /** Ending the sessions left in progress failed ([error]); the next process start tries again. */
    data class OrphanSessionsFailed(override val at: ClockReading, val error: Throwable) : TimerDiagnostic

    /** The process started with these permission [states] (06 §5.6, TMR-31). */
    data class PermissionsAtStart(override val at: ClockReading, val states: PermissionStates) : TimerDiagnostic
}
