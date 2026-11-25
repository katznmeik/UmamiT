package com.umamit.feature.stats

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.MonthSpan
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** Statistik's month span labels in the app's language (GEN-13): German by default, English when named. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PeriodLabelLanguageTest {
    private val resources: Resources get() = ApplicationProvider.getApplicationContext<Context>().resources
    private val sameYear = MonthSpan(YearMonth.of(2026, 3), YearMonth.of(2026, 10))
    private val twoYears = MonthSpan(YearMonth.of(2025, 12), YearMonth.of(2026, 8))

    @Test
    fun `German months`() {
        assertThat(sameYear.label(resources)).isEqualTo("März–Okt.")
        assertThat(twoYears.label(resources)).isEqualTo("Dez. 25–Aug. 26")
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `English months`() {
        assertThat(sameYear.label(resources)).isEqualTo("Mar–Oct")
        assertThat(twoYears.label(resources)).isEqualTo("Dec 25–Aug 26")
    }
}
