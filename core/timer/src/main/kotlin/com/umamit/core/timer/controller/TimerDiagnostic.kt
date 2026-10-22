package com.umamit.core.timer.controller

import com.umamit.core.common.ClockReading
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.engine.TimerEffect
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome

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
}

/**
 * When a time-based event was due: [atMs] on the clock of [kind]. With the
 * reading of the moment it was handled, the log shows scheduled vs actual
 * fire time (06 §5.6, TMR-30).
 */
data class ScheduledTime(val kind: TimerAlarmKind, val atMs: Long) {
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

    /** The alarm of [kind] arrived; [scheduled] is its time in the current due set, null when nothing is due. */
    data class AlarmReceived(override val at: ClockReading, val kind: TimerAlarmKind, val scheduled: ScheduledTime?) :
        TimerDiagnostic

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
}
