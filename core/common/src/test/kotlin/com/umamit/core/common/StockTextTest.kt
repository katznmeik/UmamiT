package com.umamit.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/** A tea's grams as the screens and the daily reminder show them (INV-05, INV-10, INV-11; GEN-13): to 0,1 g. */
class StockTextTest {
    @Test
    fun `grams are German to a tenth, a whole number without a decimal`() {
        assertThat(stockGramsText(14.0)).isEqualTo("14")
        assertThat(stockGramsText(12.5)).isEqualTo("12,5")
        assertThat(stockGramsText(3.04)).isEqualTo("3")
        assertThat(stockGramsText(1_250.0)).isEqualTo("1.250")
        assertThat(stockGramsText(0.0)).isEqualTo("0")
    }

    @Test
    fun `grams in English have a decimal point and a thousands comma`() = inLocale(Locale.UK) {
        assertThat(stockGramsText(14.0)).isEqualTo("14")
        assertThat(stockGramsText(12.5)).isEqualTo("12.5")
        assertThat(stockGramsText(1_250.0)).isEqualTo("1,250")
    }

    @Test
    fun `a given language holds whatever the default locale is`() {
        inLocale(Locale.US) {
            assertThat(stockGramsText(12.5, AppLanguage.GERMAN)).isEqualTo("12,5")
            assertThat(stockGramsText(1_250.0, AppLanguage.GERMAN)).isEqualTo("1.250")
        }
        assertThat(stockGramsText(12.5, AppLanguage.ENGLISH)).isEqualTo("12.5")
        assertThat(stockGramsText(1_250.0, AppLanguage.ENGLISH)).isEqualTo("1,250")
    }
}
