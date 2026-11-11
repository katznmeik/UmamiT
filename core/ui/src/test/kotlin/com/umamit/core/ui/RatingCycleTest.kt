package com.umamit.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The tap cycle and the TalkBack steps of `RatingStars` (03 §3.8, JRN-02, D-112). */
class RatingCycleTest {
    @Test
    fun `a tap on an unrated or other star sets that many whole stars`() {
        assertThat(RatingCycle.afterTap(null, 3)).isEqualTo(3.0)
        assertThat(RatingCycle.afterTap(2.0, 4)).isEqualTo(4.0)
        assertThat(RatingCycle.afterTap(4.5, 2)).isEqualTo(2.0)
    }

    @Test
    fun `a second tap on the same star takes half off, a third clears`() {
        assertThat(RatingCycle.afterTap(3.0, 3)).isEqualTo(2.5)
        assertThat(RatingCycle.afterTap(2.5, 3)).isNull()
    }

    @Test
    fun `the cycle works on the first and the last star`() {
        assertThat(RatingCycle.afterTap(1.0, 1)).isEqualTo(0.5)
        assertThat(RatingCycle.afterTap(0.5, 1)).isNull()
        assertThat(RatingCycle.afterTap(5.0, 5)).isEqualTo(4.5)
        assertThat(RatingCycle.afterTap(4.5, 5)).isNull()
    }

    @Test
    fun `a tap on another star than the half star sets that whole star`() {
        assertThat(RatingCycle.afterTap(2.5, 4)).isEqualTo(4.0)
        assertThat(RatingCycle.afterTap(2.5, 2)).isEqualTo(2.0)
    }

    @Test
    fun `a progress target snaps to the nearest half step and zero clears`() {
        assertThat(RatingCycle.snap(3.3f)).isEqualTo(3.5)
        assertThat(RatingCycle.snap(0.1f)).isNull()
        assertThat(RatingCycle.snap(9f)).isEqualTo(5.0)
    }

    @Test
    fun `the description reads whole and half stars with a German comma`() {
        assertThat(RatingCycle.valueText(3.5)).isEqualTo("3,5")
        assertThat(RatingCycle.valueText(4.0)).isEqualTo("4")
    }
}
