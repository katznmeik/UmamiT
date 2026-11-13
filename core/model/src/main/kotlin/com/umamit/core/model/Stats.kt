package com.umamit.core.model

/** The time range selector of INS-07: rolling up to today, one for every tab. */
enum class StatsRange {
    /** „30 T": today and the 29 days before, per ISO week. */
    DAYS_30,

    /** „90 T": today and the 89 days before, per ISO week. */
    DAYS_90,

    /** „12 M": the current month and the 11 before, per month (12 bars, the current month last). */
    MONTHS_12,

    /** „Gesamt": everything up to today, per month from the first finished session's month. */
    ALL,
}

/** The tab of the Statistik screen (INS-07, SCR-18), remembered across app starts (05 §6 `statsTab`). */
enum class StatsTab {
    /** *Überblick*, the default. */
    OVERVIEW,

    /** *Bewertungen*. */
    RATINGS,

    /** *Verbrauch*. */
    CONSUMPTION,
}
