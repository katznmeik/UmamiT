package com.umamit.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** When a held − / + repeats (SCR-31, PRF-04, #141): after 400 ms, every 150 ms, then faster. */
class HoldRepeatTest {

    @Test
    fun `the first repeat comes after 400 ms, then every 150 ms until 1,5 s`() {
        assertThat(HoldRepeat.timesMs().take(9).toList())
            .containsExactly(400L, 550L, 700L, 850L, 1_000L, 1_150L, 1_300L, 1_450L, 1_600L)
            .inOrder()
    }

    @Test
    fun `from 1,5 s it repeats every 60 ms, from 2 s every 25 ms`() {
        assertThat(HoldRepeat.timesMs().drop(8).take(10).toList())
            .containsExactly(1_600L, 1_660L, 1_720L, 1_780L, 1_840L, 1_900L, 1_960L, 2_020L, 2_045L, 2_070L)
            .inOrder()
    }

    @Test
    fun `60 repeats, 5_00 to 10_00 in 5 s steps, take about 3 s`() {
        assertThat(HoldRepeat.timesMs().takeWhile { it <= 3_120L }.count()).isEqualTo(60)
    }
}
