package com.umamit.feature.collection.detail

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/** „Korrektur bearbeiten"'s signed difference (INV-03, SCR-29, D-136 item 11). */
class CorrectionFormTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun `a signed difference is read with plus, hyphen or minus sign, a bare one is more`() {
        assertThat(CorrectionForm.parse("+5")).isEqualTo(5.0)
        assertThat(CorrectionForm.parse("5")).isEqualTo(5.0)
        assertThat(CorrectionForm.parse("-3")).isEqualTo(-3.0)
        assertThat(CorrectionForm.parse("−3")).isEqualTo(-3.0)
        assertThat(CorrectionForm.parse(" −2,5 g ")).isEqualTo(-2.5)
    }

    @Test
    fun `0, blank and anything else is no difference`() {
        listOf("0", "+0", "−0", "", " ", "+", "--3", "+-3", "abc", "1e3").forEach {
            assertThat(CorrectionForm.parse(it)).isNull()
        }
    }

    @Test
    fun `the field starts on the difference with its sign`() {
        assertThat(CorrectionForm.format(-15.0)).isEqualTo("−15")
        assertThat(CorrectionForm.format(5.0)).isEqualTo("+5")
        assertThat(CorrectionForm.format(37.5)).isEqualTo("+37,5")
    }

    @Test
    fun `a day after today is refused`() {
        assertThat(CorrectionForm("+5", today).delta(today)).isEqualTo(5.0)
        assertThat(CorrectionForm("+5", today.plusDays(1)).delta(today)).isNull()
        assertThat(CorrectionForm("0", today).deltaError).isNotNull()
    }
}
