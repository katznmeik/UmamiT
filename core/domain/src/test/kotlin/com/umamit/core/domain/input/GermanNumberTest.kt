package com.umamit.core.domain.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The one German number rule of the add sheet and the tea editor (D-090, F14), on synthetic input. */
class GermanNumberTest {
    @Test
    fun `cents read a decimal comma and a decimal point`() {
        assertThat(GermanNumber.parseCents("4")).isEqualTo(400)
        assertThat(GermanNumber.parseCents("4,5")).isEqualTo(450)
        assertThat(GermanNumber.parseCents("12,40")).isEqualTo(1240)
        assertThat(GermanNumber.parseCents("12.4")).isEqualTo(1240)
        assertThat(GermanNumber.parseCents("1.23")).isEqualTo(123)
        assertThat(GermanNumber.parseCents("1234,5")).isEqualTo(123_450)
    }

    @Test
    fun `cents read thousands groups`() {
        assertThat(GermanNumber.parseCents("1.234,50")).isEqualTo(123_450)
        assertThat(GermanNumber.parseCents("1.234")).isEqualTo(123_400)
        assertThat(GermanNumber.parseCents("12.345")).isEqualTo(1_234_500)
    }

    @Test
    fun `cents ignore the euro sign and surrounding space`() {
        assertThat(GermanNumber.parseCents("€ 12,90")).isEqualTo(1290)
        assertThat(GermanNumber.parseCents(" 1.234,50 € ")).isEqualTo(123_450)
    }

    @Test
    fun `cents reject garbage`() {
        for (text in listOf("", "  ", "€", "abc", "-1", "1.23.4", "12.345,6.7", "1,234,5", "12,345", "1e3")) {
            assertThat(GermanNumber.parseCents(text)).isNull()
        }
    }

    @Test
    fun `grams read decimals and thousands groups up to 10000`() {
        assertThat(GermanNumber.parseGrams("4")).isEqualTo(4.0)
        assertThat(GermanNumber.parseGrams("4,5")).isEqualTo(4.5)
        assertThat(GermanNumber.parseGrams("37.5")).isEqualTo(37.5)
        assertThat(GermanNumber.parseGrams("1234,5")).isEqualTo(1234.5)
        assertThat(GermanNumber.parseGrams("1.000")).isEqualTo(1000.0)
        assertThat(GermanNumber.parseGrams("10.000")).isEqualTo(10_000.0)
        assertThat(GermanNumber.parseGrams("250 g")).isEqualTo(250.0)
    }

    @Test
    fun `grams reject garbage zero and too much`() {
        for (text in listOf("", " ", "abc", "-1", "0", "0,0", "10.001", "10000,5", "1,25", "1.2.3", "g")) {
            assertThat(GermanNumber.parseGrams(text)).isNull()
        }
    }
}
