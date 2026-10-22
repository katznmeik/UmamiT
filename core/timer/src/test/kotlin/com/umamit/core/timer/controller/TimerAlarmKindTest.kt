package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.engine.TimerEvent
import org.junit.Test

/** The alarm table of 04 §6.2: one alarm per due-time field, fixed request codes (D-054). */
class TimerAlarmKindTest {

    @Test
    fun `each kind has the request code, clock and delivery of 04 §6_2`() {
        val table = TimerAlarmKind.entries.map { listOf(it.name, it.requestCode, it.clock, it.exact, it.wakeUp) }

        assertThat(table).containsExactly(
            listOf("PRE_ALERT", 101, AlarmClock.ELAPSED, true, true),
            listOf("DEADLINE", 102, AlarmClock.ELAPSED, true, true),
            listOf("OVERTIME_TIMEOUT", 103, AlarmClock.ELAPSED, true, true),
            listOf("IDLE_DEMOTE", 104, AlarmClock.WALL, true, true),
            listOf("QUICK_CANCEL", 105, AlarmClock.WALL, true, true),
            listOf("MIDNIGHT", 106, AlarmClock.WALL, false, false),
        ).inOrder()
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

        assertThat(TimerAlarmKind.entries.map { it.timeIn(due) }).containsExactly(1L, 2L, 3L, 4L, 5L, 6L).inOrder()
        assertThat(TimerAlarmKind.entries.map { it.timeIn(DueTimes.NONE) }.toSet()).containsExactly(null)
        assertThat(TimerAlarmKind.entries.map { TimerAlarmKind.of(it.event) }).isEqualTo(TimerAlarmKind.entries)
        assertThat(TimerAlarmKind.of(TimerEvent.Poured)).isNull()
    }
}
