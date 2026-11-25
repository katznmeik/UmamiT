package com.umamit.feature.stats

import com.google.common.truth.Truth.assertThat
import com.umamit.feature.stats.charts.ChartNumbers
import com.umamit.testlocale.inLocale
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

/** Statistik's axis numbers, ratings and months in the app's language (GEN-13): German by default, or English. */
class StatsLanguageFormatTest {
    private val october = YearMonth.of(2026, 10)

    @Test
    fun `German axis numbers, ratings and months`() {
        assertThat(ChartNumbers.axis(1200.0)).isEqualTo("1.200")
        assertThat(ChartNumbers.axis(2.5)).isEqualTo("2,5")
        assertThat(ChartNumbers.rating(4.2)).isEqualTo("4,2")
        assertThat(october.fullLabel()).isEqualTo("Okt. 2026")
    }

    @Test
    fun `English axis numbers, ratings and months`() = inLocale(Locale.US) {
        assertThat(ChartNumbers.axis(1200.0)).isEqualTo("1,200")
        assertThat(ChartNumbers.axis(2.5)).isEqualTo("2.5")
        assertThat(ChartNumbers.rating(4.2)).isEqualTo("4.2")
        assertThat(october.fullLabel()).isEqualTo("Oct 2026")
    }
}
