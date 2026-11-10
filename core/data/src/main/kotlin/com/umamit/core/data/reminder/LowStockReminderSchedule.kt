package com.umamit.core.data.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * When the low-stock reminder runs (INV-11, D-098; interval from v0.5.0, D-108 point 7): at the set time
 * (SET-29) on the first local date at least N days (SET-28: 1, 2, 3 or 7) after the date of the last reminder
 * that was **posted**, on the clock of the device's time zone, so a DST change keeps „18:00".
 *
 * A due date whose set time has passed without a run (the job was late, the phone was off, the app was started
 * first) runs at once; a run more than [EARLY_MS] before the set time (a clock or DST change) is not due and
 * only waits for it. A due day that had its run but posted nothing (no tea due) keeps the date, so the next
 * try is the next day's set time.
 */
object LowStockReminderSchedule {
    /** How early a run may still post: half an hour before the set time (D-098). */
    val EARLY_MS: Long = TimeUnit.MINUTES.toMillis(30)

    /**
     * The wait from [nowMs] to the next run for the set [minuteOfDay] in [zone], every [everyDays] days from
     * [lastPosted], the date of the last posted reminder (null: never); 0 to run at once. [todayHandled]: today
     * had its run already (it posted nothing, or failed), so the next is tomorrow at the earliest.
     */
    fun delayMs(
        nowMs: Long,
        zone: ZoneId,
        minuteOfDay: Int,
        everyDays: Int,
        lastPosted: LocalDate?,
        todayHandled: Boolean = false,
    ): Long {
        val today = dateOf(nowMs, zone)
        val earliest = if (todayHandled) today.plusDays(1) else today
        val day = maxOf(earliest, firstDueDate(everyDays, lastPosted) ?: earliest)
        val next = setTime(day, minuteOfDay, zone)
        return (next.toInstant().toEpochMilli() - nowMs).coerceAtLeast(0L)
    }

    /**
     * Whether a run at [nowMs] posts: its date is at least [everyDays] after [lastPosted] (or nothing was
     * posted yet), and the set time is at most [EARLY_MS] ahead.
     */
    fun isDue(nowMs: Long, zone: ZoneId, minuteOfDay: Int, everyDays: Int, lastPosted: LocalDate?): Boolean {
        val today = dateOf(nowMs, zone)
        val first = firstDueDate(everyDays, lastPosted)
        if (first != null && today < first) return false
        return nowMs >= setTime(today, minuteOfDay, zone).toInstant().toEpochMilli() - EARLY_MS
    }

    /** The first date a reminder may post after one on [lastPosted]; never the same date. */
    private fun firstDueDate(everyDays: Int, lastPosted: LocalDate?): LocalDate? =
        lastPosted?.plusDays(everyDays.coerceAtLeast(1).toLong())

    /** The local date of [nowMs] in [zone], as [isDue] counts days. */
    fun dateOf(nowMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

    /** [minuteOfDay] on [date]; a time in a DST gap moves forward by the gap. */
    private fun setTime(date: LocalDate, minuteOfDay: Int, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(date, LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR), zone)

    private const val MINUTES_PER_HOUR = 60
}
