package com.umamit.core.model

/**
 * Phase of the active brew (05 §5), stored in `active_brew.phase`. One value
 * per non-idle state of the timer state machine (04 §3.1); no active brew
 * means idle.
 */
enum class TimerPhase {
    /** Waiting for *Start* (TMR-08). */
    READY,

    /** Counting down. */
    RUNNING,

    /** Countdown frozen. */
    PAUSED,

    /** Deadline reached; the alert plays until *Abgegossen*. */
    ALERTING,

    /** All planned steps done; waiting for *Sitzung beenden* or *Weiterer Aufguss*. */
    FINISHED,
}
