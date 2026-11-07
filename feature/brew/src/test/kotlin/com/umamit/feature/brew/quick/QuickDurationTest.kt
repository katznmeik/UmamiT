package com.umamit.feature.brew.quick

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The quick timer's durations (SCR-07, TMR-22): its chips. The picker's steps are tested in `core:ui`. */
class QuickDurationTest {

    @Test
    fun `the chips are 0_30, 1_00, 1_30, 2_00, 3_00, 5_00 and 8_00 (TMR-22)`() {
        assertThat(QuickDuration.CHIPS).containsExactly(30, 60, 90, 120, 180, 300, 480).inOrder()
    }
}
