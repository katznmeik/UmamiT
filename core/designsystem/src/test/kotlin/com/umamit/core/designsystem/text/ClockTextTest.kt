package com.umamit.core.designsystem.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClockTextTest {

    @Test
    fun `durations under an hour read m colon ss`() {
        assertThat(clockText(0)).isEqualTo("0:00")
        assertThat(clockText(20)).isEqualTo("0:20")
        assertThat(clockText(60)).isEqualTo("1:00")
        assertThat(clockText(599)).isEqualTo("9:59")
        assertThat(clockText(3_599)).isEqualTo("59:59")
    }

    @Test
    fun `durations from one hour read h colon mm colon ss`() {
        assertThat(clockText(3_600)).isEqualTo("1:00:00")
        assertThat(clockText(14_430)).isEqualTo("4:00:30")
    }

    @Test
    fun `rating uses a German decimal comma`() {
        assertThat(ratingText(4.5f)).isEqualTo("4,5")
        assertThat(ratingText(4f)).isEqualTo("4,0")
    }
}
