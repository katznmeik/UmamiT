package com.umamit.core.model

/**
 * A slot of the timer controller (04 §3.3, D-098): from v0.3.0 at most one active brew session and one active
 * quick timer run at the same time, each in its own slot, with its own engine state, due times, exact alarms,
 * alert watchdog and `active_brew` row. Until v0.3.0-03 only one timer is active across both slots. Stored by
 * name as the key `active_brew.slot` (umamit.db v4, 05 §3.12), so a name never changes its meaning and is never
 * renamed.
 */
enum class TimerSlot {
    /** A brew session (`is_quick_timer` 0); its alarms keep the request codes of v0.2.2 (101–107). */
    BREW,

    /** The quick timer (`is_quick_timer` 1); its alarms use the request codes 201–207. */
    QUICK,
    ;

    /** The other slot. */
    val other: TimerSlot get() = if (this == BREW) QUICK else BREW

    /**
     * How this slot's alarm broadcasts, notification actions and diagnostics log lines name it before their own
     * name ([named]): nothing for the brew slot, whose names are those of one timer (v0.2.2), „QUICK." for the quick
     * slot (D-098). Alarms still pending from an earlier version and the accuracy run read these names, so they never
     * change.
     */
    val namePrefix: String get() = if (this == BREW) "" else "QUICK."

    /** [name] as this slot names it: `DEADLINE` for the brew slot, `QUICK.DEADLINE` for the quick slot. */
    fun named(name: String): String = namePrefix + name
}
