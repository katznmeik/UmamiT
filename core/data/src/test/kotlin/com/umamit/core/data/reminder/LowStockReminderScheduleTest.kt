package com.umamit.core.data.reminder

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * When the daily low-stock reminder runs (INV-11, D-098; ticket #130): once a day at the set time, at most
 * once per local date, a run late by the job's inexactness still posts and a run far too early (a clock or
 * DST change) does not.
 */
class LowStockReminderScheduleTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val sixPm = 18 * 60
    private val today = LocalDate.of(2026, 10, 30)

    private fun at(hour: Int, minute: Int, date: LocalDate = today): Long =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(berlin).toInstant().toEpochMilli()

    private fun delay(nowMs: Long, lastDate: LocalDate? = null, minuteOfDay: Int = sixPm) =
        LowStockReminderSchedule.delayMs(nowMs, berlin, minuteOfDay, lastDate)

    @Test
    fun beforeTheSetTimeTheNextRunIsTodayAtThatTime() {
        assertThat(delay(at(10, 0))).isEqualTo(TimeUnit.HOURS.toMillis(8))
        assertThat(delay(at(17, 59))).isEqualTo(TimeUnit.MINUTES.toMillis(1))
    }

    @Test
    fun afterTodaysRunTheNextIsTomorrowAtTheSetTime() {
        assertThat(delay(at(18, 4), lastDate = today)).isEqualTo(TimeUnit.MINUTES.toMillis(24 * 60 - 4))
        // Also when the set time is still ahead today: the day had its reminder.
        assertThat(delay(at(10, 0), lastDate = today)).isEqualTo(TimeUnit.HOURS.toMillis(32))
    }

    @Test
    fun aDayWhoseTimeHasPassedWithoutARunRunsNow() {
        // The job was late (the phone was off) and the app started first: today's reminder still comes.
        assertThat(delay(at(18, 10), lastDate = today.minusDays(1))).isEqualTo(0L)
        assertThat(delay(at(23, 30))).isEqualTo(0L)
    }

    @Test
    fun aChangedTimeMovesTheNextRun() {
        assertThat(delay(at(10, 0), minuteOfDay = 7 * 60 + 30, lastDate = today.minusDays(1))).isEqualTo(0L)
        assertThat(delay(at(6, 0), minuteOfDay = 7 * 60 + 30)).isEqualTo(TimeUnit.MINUTES.toMillis(90))
        assertThat(delay(at(10, 0), minuteOfDay = 21 * 60)).isEqualTo(TimeUnit.HOURS.toMillis(11))
    }

    @Test
    fun theNextDayAcrossTheEndOfSummerTimeIsTheSetTimeOnTheClock() {
        // 2026-10-25 has 25 hours in Berlin: 18:00 to 18:00 is 25 h.
        val saturday = LocalDate.of(2026, 10, 24)
        assertThat(delay(at(18, 0, saturday), lastDate = saturday)).isEqualTo(TimeUnit.HOURS.toMillis(25))
    }

    @Test
    fun aRunIsDueFromHalfAnHourBeforeTheSetTimeOncePerDate() {
        fun due(nowMs: Long, lastDate: LocalDate? = null) =
            LowStockReminderSchedule.isDue(nowMs, berlin, sixPm, lastDate)

        assertThat(due(at(17, 29))).isFalse()
        assertThat(due(at(17, 30))).isTrue()
        assertThat(due(at(18, 0))).isTrue()
        assertThat(due(at(18, 14), lastDate = today.minusDays(1))).isTrue()
        assertThat(due(at(23, 59))).isTrue()
        assertThat(due(at(18, 0), lastDate = today)).isFalse()
    }
}
