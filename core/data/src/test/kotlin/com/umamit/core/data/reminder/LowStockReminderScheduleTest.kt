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

    private fun delay(
        nowMs: Long,
        lastDate: LocalDate? = null,
        minuteOfDay: Int = sixPm,
        everyDays: Int = 1,
        todayHandled: Boolean = false,
    ) = LowStockReminderSchedule.delayMs(nowMs, berlin, minuteOfDay, everyDays, lastDate, todayHandled)

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
            LowStockReminderSchedule.isDue(nowMs, berlin, sixPm, everyDays = 1, lastPosted = lastDate)

        assertThat(due(at(17, 29))).isFalse()
        assertThat(due(at(17, 30))).isTrue()
        assertThat(due(at(18, 0))).isTrue()
        assertThat(due(at(18, 14), lastDate = today.minusDays(1))).isTrue()
        assertThat(due(at(23, 59))).isTrue()
        assertThat(due(at(18, 0), lastDate = today)).isFalse()
    }

    @Test
    fun theIntervalCountsFromTheLastPostedDate() {
        // Posted on 2026-10-30 at 18:00 (v0.5.0, D-108 point 7): N = 1, 2, 3 and 7 days later at 18:00.
        val posted = at(18, 0)
        for (days in listOf(1, 2, 3, 7)) {
            assertThat(
                delay(posted, lastDate = today, everyDays = days),
            ).isEqualTo(TimeUnit.DAYS.toMillis(days.toLong()))
        }
        // Wöchentlich, asked on the Monday after: the Friday a week after the post.
        assertThat(delay(at(9, 0, LocalDate.of(2026, 11, 2)), lastDate = today, everyDays = 7))
            .isEqualTo(TimeUnit.DAYS.toMillis(4) + TimeUnit.HOURS.toMillis(9))
    }

    @Test
    fun aRunIsDueOnTheFirstDateAtLeastNDaysAfterTheLastPost() {
        fun due(date: LocalDate, everyDays: Int) =
            LowStockReminderSchedule.isDue(at(18, 0, date), berlin, sixPm, everyDays, lastPosted = today)

        assertThat(due(today.plusDays(1), everyDays = 2)).isFalse()
        assertThat(due(today.plusDays(2), everyDays = 2)).isTrue()
        assertThat(due(today.plusDays(2), everyDays = 3)).isFalse()
        assertThat(due(today.plusDays(3), everyDays = 3)).isTrue()
        assertThat(due(today.plusDays(6), everyDays = 7)).isFalse()
        assertThat(due(today.plusDays(7), everyDays = 7)).isTrue()
        // A day that posted nothing kept the date: the day after is still due.
        assertThat(due(today.plusDays(8), everyDays = 7)).isTrue()
    }

    @Test
    fun aDueDayWithoutATeaWaitsForTheNextDayNotTheNextInterval() {
        // Wöchentlich, due today, no tea: the date stays, so tomorrow at 18:00 is the next try.
        val dueDay = today.plusDays(7)
        assertThat(delay(at(18, 0, dueDay), lastDate = today, everyDays = 7, todayHandled = true))
            .isEqualTo(TimeUnit.DAYS.toMillis(1))
        // Before the set time of a handled day too.
        assertThat(delay(at(10, 0, dueDay), lastDate = today, everyDays = 7, todayHandled = true))
            .isEqualTo(TimeUnit.HOURS.toMillis(32))
    }

    @Test
    fun aLateRunOnADueDayRunsNow() {
        assertThat(delay(at(20, 0, today.plusDays(3)), lastDate = today, everyDays = 2)).isEqualTo(0L)
        assertThat(delay(at(18, 20, today.plusDays(7)), lastDate = today, everyDays = 7)).isEqualTo(0L)
    }

    @Test
    fun anIntervalAcrossTheEndOfSummerTimeKeepsTheSetTimeOnTheClock() {
        // Posted Friday 2026-10-23 at 18:00, every 2 days: Sunday 18:00, 49 h later (25-hour day).
        val friday = LocalDate.of(2026, 10, 23)
        assertThat(delay(at(18, 0, friday), lastDate = friday, everyDays = 2)).isEqualTo(TimeUnit.HOURS.toMillis(49))
        // Wöchentlich: 7 × 24 h + 1 h.
        assertThat(delay(at(18, 0, friday), lastDate = friday, everyDays = 7))
            .isEqualTo(TimeUnit.DAYS.toMillis(7) + TimeUnit.HOURS.toMillis(1))
    }
}
