package com.umamit.core.data.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * When the daily low-stock reminder runs (INV-11, D-098): once per local date at the set time (SET-29), on
 * the clock of the device's time zone, so a DST change keeps „18:00".
 *
 * A day whose set time has passed without a run (the job was late, the phone was off, the app was started
 * first) runs at once; a run more than [EARLY_MS] before the set time (a clock or DST change) is not due and
 * only waits for it. A day that had its run waits for the next day.
 */
object LowStockReminderSchedule {
    /** How early a run may still post: half an hour before the set time (D-098). */
    val EARLY_MS: Long = TimeUnit.MINUTES.toMillis(30)

    /**
     * The wait from [nowMs] to the next run for the set [minuteOfDay] in [zone], when the last run that was
     * due happened on [lastDate] (null: never); 0 to run at once.
     */
    fun delayMs(nowMs: Long, zone: ZoneId, minuteOfDay: Int, lastDate: LocalDate?): Long {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val today = now.toLocalDate()
        val day = if (lastDate == today) today.plusDays(1) else today
        val next = setTime(day, minuteOfDay, zone)
        return (next.toInstant().toEpochMilli() - nowMs).coerceAtLeast(0L)
    }

    /** Whether a run at [nowMs] posts: its date had no run yet, and the set time is at most [EARLY_MS] ahead. */
    fun isDue(nowMs: Long, zone: ZoneId, minuteOfDay: Int, lastDate: LocalDate?): Boolean {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        if (lastDate == today) return false
        return nowMs >= setTime(today, minuteOfDay, zone).toInstant().toEpochMilli() - EARLY_MS
    }

    /** The local date of [nowMs] in [zone], as [isDue] counts days. */
    fun dateOf(nowMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

    /** [minuteOfDay] on [date]; a time in a DST gap moves forward by the gap. */
    private fun setTime(date: LocalDate, minuteOfDay: Int, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(date, LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR), zone)

    private const val MINUTES_PER_HOUR = 60
}
