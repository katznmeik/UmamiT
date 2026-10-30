package com.umamit.feature.brew.quick

import com.google.common.truth.Truth.assertThat
import com.umamit.feature.brew.quick.QuickDuration.Field.HOURS
import com.umamit.feature.brew.quick.QuickDuration.Field.MINUTES
import com.umamit.feature.brew.quick.QuickDuration.Field.SECONDS
import org.junit.Test

/** The quick timer's durations (SCR-07, TMR-22): chips, and the picker's bounds and PRF-04 increments. */
class QuickDurationTest {

    @Test
    fun `the chips are 0_30, 1_00, 1_30, 2_00, 3_00, 5_00 and 8_00 (TMR-22)`() {
        assertThat(QuickDuration.CHIPS).containsExactly(30, 60, 90, 120, 180, 300, 480).inOrder()
    }

    @Test
    fun `seconds move in 5 s steps below 10 min`() {
        assertThat(QuickDuration.step(90, SECONDS, up = true)).isEqualTo(95)
        assertThat(QuickDuration.step(90, SECONDS, up = false)).isEqualTo(85)
        assertThat(QuickDuration.step(595, SECONDS, up = true)).isEqualTo(600)
        assertThat(QuickDuration.step(600, SECONDS, up = false)).isEqualTo(595)
    }

    @Test
    fun `from 10 min on only whole minutes, so the seconds cannot move there (PRF-04)`() {
        assertThat(QuickDuration.step(600, SECONDS, up = true)).isNull()
        assertThat(QuickDuration.step(660, SECONDS, up = false)).isNull()
        // 9:55 + 1 min = 10:55, which has no seconds from 10 min on: 10:00.
        assertThat(QuickDuration.step(595, MINUTES, up = true)).isEqualTo(600)
        assertThat(QuickDuration.step(600, MINUTES, up = false)).isEqualTo(540)
    }

    @Test
    fun `an hour more from 0_30 gives whole minutes, 1_00_00`() {
        assertThat(QuickDuration.step(30, HOURS, up = true)).isEqualTo(3_600)
        assertThat(QuickDuration.step(5_400, HOURS, up = false)).isEqualTo(1_800)
    }

    @Test
    fun `the picker stays within 5 s and 24 h`() {
        assertThat(QuickDuration.step(5, SECONDS, up = false)).isNull()
        assertThat(QuickDuration.step(30, MINUTES, up = false)).isNull()
        assertThat(QuickDuration.step(1_800, HOURS, up = false)).isNull()
        assertThat(QuickDuration.step(82_800, HOURS, up = true)).isEqualTo(86_400)
        assertThat(QuickDuration.step(86_400, MINUTES, up = true)).isNull()
        assertThat(QuickDuration.step(84_000, HOURS, up = true)).isNull()
    }

    @Test
    fun `a duration splits into hours, minutes and seconds for the picker`() {
        assertThat(QuickDuration.parts(3_725)).isEqualTo(Triple(1, 2, 5))
        assertThat(QuickDuration.parts(86_400)).isEqualTo(Triple(24, 0, 0))
    }
}
