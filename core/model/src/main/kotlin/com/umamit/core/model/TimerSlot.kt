package com.umamit.core.model

/**
 * A slot of the timer controller (04 §3.3, D-098): from v0.3.0 at most one active brew session and one active
 * quick timer run at the same time, each in its own slot. Stored by name as the key `active_brew.slot` (umamit.db
 * v4, 05 §3.12), so a name never changes its meaning and is never renamed.
 */
enum class TimerSlot {
    /** A brew session (`is_quick_timer` 0). */
    BREW,

    /** The quick timer (`is_quick_timer` 1). */
    QUICK,
}
