package com.umamit.core.ui

import com.google.common.truth.Truth.assertThat
import com.umamit.testlocale.inLocale
import org.junit.Test
import java.util.Locale

/** The spoken rating and taste values follow the app's language (GEN-13): German by default, English on request. */
class LanguageNumbersTest {

    @Test
    fun `values read with a comma in German`() {
        assertThat(RatingCycle.valueText(3.5)).isEqualTo("3,5")
        assertThat(RatingCycle.valueText(4.0)).isEqualTo("4")
        assertThat(TasteRadarShape.valueText(2.333)).isEqualTo("2,3")
    }

    @Test
    fun `values read with a point in English`() = inLocale(Locale.US) {
        assertThat(RatingCycle.valueText(3.5)).isEqualTo("3.5")
        assertThat(RatingCycle.valueText(4.0)).isEqualTo("4")
        assertThat(TasteRadarShape.valueText(2.333)).isEqualTo("2.3")
    }
}
