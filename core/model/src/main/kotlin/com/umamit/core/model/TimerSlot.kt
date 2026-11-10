package com.umamit.core.model

/**
 * A slot of the timer controller (04 §3.3; D-098, D-108): each active timer runs in its own slot, with its own
 * engine state, due times, exact alarms, alert watchdog and `active_brew` row. [BREW] and [BREW_2] hold brew
 * sessions, [QUICK] the quick timer; the controller admits at most two active slots in all. The declaration order
 * is the controller's fixed slot order (BREW, BREW_2, QUICK): of recovery and due events after the brews by deadline
 * (04 §9.3), of the arbitration's ties and of the notifications. Stored by name as the key
 * `active_brew.slot` (umamit.db v4, 05 §3.12), so a name never changes its meaning and is never renamed.
 */
enum class TimerSlot {
    /** A brew session (`is_quick_timer` 0); its alarms keep the request codes of v0.2.2 (101–107). */
    BREW,

    /**
     * A second brew session beside [BREW] (D-108): a brew start takes it while [BREW] holds a session and at most
     * one timer is active. Its alarms use the request codes 301–307.
     */
    BREW_2,

    /** The quick timer (`is_quick_timer` 1); its alarms use the request codes 201–207. */
    QUICK,
    ;

    /** Whether this slot holds brew sessions ([BREW], [BREW_2]) rather than the quick timer. */
    val isBrew: Boolean get() = this != QUICK

    /**
     * The other slot of the v0.3.0 pair of one brew session and the quick timer: [QUICK] for a brew slot, [BREW]
     * for the quick slot. With two brew sessions (D-108) the other active timer is found among the active slots.
     */
    val other: TimerSlot get() = if (isBrew) QUICK else BREW

    /**
     * How this slot's alarm broadcasts, notification actions and diagnostics log lines name it before their own
     * name ([named]): nothing for the brew slot, whose names are those of one timer (v0.2.2), „QUICK." for the quick
     * slot (D-098), „BREW2." for the second brew slot (D-108). Alarms still pending from an earlier version and the
     * accuracy run read these names, so they never change.
     */
    val namePrefix: String get() = when (this) {
        BREW -> ""
        BREW_2 -> "BREW2."
        QUICK -> "QUICK."
    }

    /** [name] as this slot names it: `DEADLINE` for the brew slot, `QUICK.DEADLINE` for the quick slot. */
    fun named(name: String): String = namePrefix + name
}
