package com.umamit.core.model

/**
 * One of the timer controller's two slots (04 §3.3, D-098). Each slot holds one timer with its own engine
 * state, due times, exact alarms, alert watchdog and `active_brew` row (05 §3.12; the column `slot` from
 * umamit.db v4). Until v0.3.0-03 only one timer is active across both slots.
 */
enum class TimerSlot {
    /** A brew session (`startSession`), recorded in the journal; its alarms keep the request codes of v0.2.2. */
    BREW,

    /** The quick timer (`startQuickTimer`, TMR-22); nothing is recorded. */
    QUICK,
}
