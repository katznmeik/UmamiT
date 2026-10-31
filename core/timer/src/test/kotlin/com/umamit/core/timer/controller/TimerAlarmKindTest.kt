package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.engine.TimerEvent
import org.junit.Test

/** The alarm table of 04 §6.2: one alarm per due-time field, fixed request codes per slot (D-054, D-098). */
class TimerAlarmKindTest {

    @Test
    fun `each kind has the request code, clock and delivery of 04 §6_2`() {
        val table = TimerAlarmKind.entries.map {
            listOf(it.name, it.requestCodeIn(TimerSlot.BREW), it.clock, it.exact, it.wakeUp)
        }

        assertThat(table).containsExactly(
            listOf("PRE_ALERT", 101, AlarmClock.ELAPSED, true, true),
            listOf("DEADLINE", 102, AlarmClock.ELAPSED, true, true),
            listOf("OVERTIME_TIMEOUT", 103, AlarmClock.ELAPSED, true, true),
            listOf("IDLE_DEMOTE", 104, AlarmClock.WALL, true, true),
            listOf("QUICK_CANCEL", 105, AlarmClock.WALL, true, true),
            listOf("MIDNIGHT", 106, AlarmClock.WALL, false, false),
            listOf("ALERT_WATCHDOG", 107, AlarmClock.ELAPSED, true, true),
        ).inOrder()
    }

    @Test
    fun `the quick slot uses 201 to 207 for the same kinds (D-098)`() {
        val codes = TimerAlarmKind.entries.associate { it.name to it.requestCodeIn(TimerSlot.QUICK) }

        assertThat(codes).containsExactly(
            "PRE_ALERT", 201,
            "DEADLINE", 202,
            "OVERTIME_TIMEOUT", 203,
            "IDLE_DEMOTE", 204,
            "QUICK_CANCEL", 205,
            "MIDNIGHT", 206,
            "ALERT_WATCHDOG", 207,
        )
    }

    @Test
    fun `no two alarms of the two slots share a request code`() {
        val codes = TimerSlot.entries.flatMap { slot -> TimerAlarmKind.entries.map { it.requestCodeIn(slot) } }

        assertThat(codes).containsNoDuplicates()
        assertThat(codes).hasSize(TimerSlot.entries.size * TimerAlarmKind.entries.size)
    }

    @Test
    fun `each kind reads its own due-time field and stands for its event`() {
        val due = DueTimes(
            preAlertAtElapsedMs = 1,
            deadlineElapsedMs = 2,
            overtimeTimeoutAtElapsedMs = 3,
            idleDemoteAtWallMs = 4,
            midnightAtWallMs = 6,
            quickCancelAtWallMs = 5,
        )

        assertThat(TimerAlarmKind.DUE.map { it.timeIn(due) }).containsExactly(1L, 2L, 3L, 4L, 5L, 6L).inOrder()
        assertThat(TimerAlarmKind.entries.map { it.timeIn(DueTimes.NONE) }.toSet()).containsExactly(null)
        assertThat(TimerAlarmKind.DUE.map { TimerAlarmKind.of(checkNotNull(it.event)) }).isEqualTo(TimerAlarmKind.DUE)
        assertThat(TimerAlarmKind.of(TimerEvent.Poured)).isNull()
        // The alert watchdog stands for no event and no due time (D-063).
        assertThat(TimerAlarmKind.ALERT_WATCHDOG.timeIn(due)).isNull()
        assertThat(TimerAlarmKind.ALERT_WATCHDOG.event).isNull()
        assertThat(TimerAlarmKind.DUE).doesNotContain(TimerAlarmKind.ALERT_WATCHDOG)
    }
}
