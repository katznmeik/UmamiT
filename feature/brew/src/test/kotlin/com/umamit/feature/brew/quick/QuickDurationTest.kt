package com.umamit.feature.brew.quick

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The quick timer's durations (SCR-07, TMR-22): its chips. The picker's steps are tested in `core:ui`. */
class QuickDurationTest {

    @Test
    fun `the chips are 1_00, 2_00, 3_00, 5_00, 8_00, 10_00 and 15_00 (TMR-22, D-114)`() {
        assertThat(QuickDuration.CHIPS).containsExactly(60, 120, 180, 300, 480, 600, 900).inOrder()
    }

    @Test
    fun `the screen opens at 1_00, the first chip (D-114)`() {
        assertThat(QuickDuration.DEFAULT_SECONDS).isEqualTo(60)
        assertThat(QuickDuration.CHIPS.first()).isEqualTo(QuickDuration.DEFAULT_SECONDS)
    }
}
