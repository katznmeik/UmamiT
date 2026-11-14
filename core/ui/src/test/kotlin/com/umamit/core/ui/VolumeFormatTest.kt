package com.umamit.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The shared German volume format (INV-08, D-115 Amendment 2). */
class VolumeFormatTest {

    @Test
    fun `a volume below a litre is rounded to 10 ml, never 0 for a non-zero volume (INV-08, D-115 Amendment 2)`() {
        assertThat(VolumeFormat.volume(0)).isEqualTo("0 ml")
        assertThat(VolumeFormat.volume(1)).isEqualTo("10 ml")
        assertThat(VolumeFormat.volume(5)).isEqualTo("10 ml")
        assertThat(VolumeFormat.volume(746)).isEqualTo("750 ml")
        assertThat(VolumeFormat.volume(360)).isEqualTo("360 ml")
    }

    @Test
    fun `from a litre the volume reads litres with one decimal half up, decided on the rounded value`() {
        assertThat(VolumeFormat.volume(995)).isEqualTo("1,0 l")
        assertThat(VolumeFormat.volume(994)).isEqualTo("990 ml")
        assertThat(VolumeFormat.volume(1_000)).isEqualTo("1,0 l")
        assertThat(VolumeFormat.volume(1_050)).isEqualTo("1,1 l")
        assertThat(VolumeFormat.volume(12_449)).isEqualTo("12,4 l")
        assertThat(VolumeFormat.volume(12_450)).isEqualTo("12,5 l")
        assertThat(VolumeFormat.volume(2_000)).isEqualTo("2,0 l")
    }
}
