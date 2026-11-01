package com.umamit.core.ui

import com.google.common.truth.Truth.assertThat
import com.umamit.core.ui.DurationSteps.Field.HOURS
import com.umamit.core.ui.DurationSteps.Field.MINUTES
import com.umamit.core.ui.DurationSteps.Field.SECONDS
import org.junit.Test

/** „Dauer wählen" (SCR-07, SCR-31): the picker's bounds and PRF-04 increments. */
class DurationStepsTest {

    @Test
    fun `seconds move in 5 s steps below 10 min`() {
        assertThat(DurationSteps.step(90, SECONDS, up = true)).isEqualTo(95)
        assertThat(DurationSteps.step(90, SECONDS, up = false)).isEqualTo(85)
        assertThat(DurationSteps.step(595, SECONDS, up = true)).isEqualTo(600)
        assertThat(DurationSteps.step(600, SECONDS, up = false)).isEqualTo(595)
    }

    @Test
    fun `from 10 min on only whole minutes, so the seconds cannot move there (PRF-04)`() {
        assertThat(DurationSteps.step(600, SECONDS, up = true)).isNull()
        assertThat(DurationSteps.step(660, SECONDS, up = false)).isNull()
        // 9:55 + 1 min = 10:55, which has no seconds from 10 min on: 10:00.
        assertThat(DurationSteps.step(595, MINUTES, up = true)).isEqualTo(600)
        assertThat(DurationSteps.step(600, MINUTES, up = false)).isEqualTo(540)
    }

    @Test
    fun `an hour more from 0_30 gives whole minutes, 1_00_00`() {
        assertThat(DurationSteps.step(30, HOURS, up = true)).isEqualTo(3_600)
        assertThat(DurationSteps.step(5_400, HOURS, up = false)).isEqualTo(1_800)
    }

    @Test
    fun `the picker stays within 5 s and 24 h`() {
        assertThat(DurationSteps.step(5, SECONDS, up = false)).isNull()
        assertThat(DurationSteps.step(30, MINUTES, up = false)).isNull()
        assertThat(DurationSteps.step(1_800, HOURS, up = false)).isNull()
        assertThat(DurationSteps.step(82_800, HOURS, up = true)).isEqualTo(86_400)
        assertThat(DurationSteps.step(86_400, MINUTES, up = true)).isNull()
        assertThat(DurationSteps.step(84_000, HOURS, up = true)).isNull()
    }

    @Test
    fun `a duration splits into hours, minutes and seconds for the picker`() {
        assertThat(DurationSteps.parts(3_725)).isEqualTo(Triple(1, 2, 5))
        assertThat(DurationSteps.parts(86_400)).isEqualTo(Triple(24, 0, 0))
    }

    @Test
    fun `the steppers move 5 s below 10 min and whole minutes from there, within 5 s - 24 h (PRF-04)`() {
        assertThat(DurationSteps.stepped(60, up = true)).isEqualTo(65)
        assertThat(DurationSteps.stepped(60, up = false)).isEqualTo(55)
        assertThat(DurationSteps.stepped(62, up = true)).isEqualTo(65)
        assertThat(DurationSteps.stepped(62, up = false)).isEqualTo(60)
        assertThat(DurationSteps.stepped(595, up = true)).isEqualTo(600)
        assertThat(DurationSteps.stepped(600, up = true)).isEqualTo(660)
        assertThat(DurationSteps.stepped(660, up = false)).isEqualTo(600)
        assertThat(DurationSteps.stepped(600, up = false)).isEqualTo(595)
        assertThat(DurationSteps.stepped(5, up = false)).isEqualTo(5)
        assertThat(DurationSteps.stepped(86_400, up = true)).isEqualTo(86_400)
    }

    @Test
    fun `a set duration is bounded and rounded down to the increment`() {
        assertThat(DurationSteps.rounded(0)).isEqualTo(5)
        assertThat(DurationSteps.rounded(92)).isEqualTo(90)
        assertThat(DurationSteps.rounded(659)).isEqualTo(600)
        assertThat(DurationSteps.rounded(100_000)).isEqualTo(86_400)
    }
}
