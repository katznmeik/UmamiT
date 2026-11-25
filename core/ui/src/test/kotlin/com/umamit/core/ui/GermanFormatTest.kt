package com.umamit.core.ui

import com.google.common.truth.Truth.assertThat
import com.umamit.core.domain.input.GermanNumber
import com.umamit.testlocale.inLocale
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * The shared grams, price and date formats (CLAUDE.md rule 4, D-101): German by default, decimal comma, fixed
 * rounding; English when the locale is English (GEN-13).
 */
class GermanFormatTest {

    @Test
    fun `grams with one decimal use the comma`() {
        assertThat(GermanFormat.gramsOneDecimal(4.0)).isEqualTo("4,0")
        assertThat(GermanFormat.gramsOneDecimal(4.56)).isEqualTo("4,6")
        assertThat(GermanFormat.gramsOneDecimal(0.0)).isEqualTo("0,0")
        assertThat(GermanFormat.gramsOneDecimal(1234.5)).isEqualTo("1234,5")
    }

    @Test
    fun `trimmed grams drop trailing zeros and keep two decimals at most`() {
        assertThat(GermanFormat.gramsTrimmed(250.0)).isEqualTo("250")
        assertThat(GermanFormat.gramsTrimmed(37.5)).isEqualTo("37,5")
        assertThat(GermanFormat.gramsTrimmed(37.256)).isEqualTo("37,26")
        assertThat(GermanFormat.gramsTrimmed(1500.0)).isEqualTo("1.500")
    }

    @Test
    fun `euros show two decimals with a thousands dot`() {
        assertThat(GermanFormat.euros(1590)).isEqualTo("15,90")
        assertThat(GermanFormat.euros(5)).isEqualTo("0,05")
        assertThat(GermanFormat.euros(123_450)).isEqualTo("1.234,50")
    }

    @Test
    fun `editable euros have no thousands separator`() {
        assertThat(GermanFormat.editableEuros(123_450)).isEqualTo("1234,50")
        assertThat(GermanFormat.editableEuros(1240)).isEqualTo("12,40")
    }

    @Test
    fun `dates read day, abbreviated month and year`() {
        val day = LocalDate.of(2026, 10, 21)
        assertThat(GermanFormat.day(day)).isEqualTo("21. Okt. 2026")
        assertThat(GermanFormat.dayShort(day)).isEqualTo("21. Okt.")
        assertThat(GermanFormat.dayMonthLong(LocalDate.of(2026, 6, 8))).isEqualTo("8. Juni")
        assertThat(GermanFormat.weekdayDay(day)).isEqualTo("Mittwoch, 21. Oktober")
        assertThat(GermanFormat.weekdayDay(day, withYear = true)).isEqualTo("Mittwoch, 21. Oktober 2026")
        assertThat(GermanFormat.time(LocalTime.of(7, 5))).isEqualTo("07:05")
    }

    @Test
    fun `an instant is formatted in the given zone`() {
        // 2026-10-20T23:30Z is already the 21st in Berlin (UTC+2 in October).
        val millis = 1_792_539_000_000L
        assertThat(GermanFormat.day(millis, ZoneId.of("Europe/Berlin"))).isEqualTo("21. Okt. 2026")
        assertThat(GermanFormat.dayShort(millis, ZoneId.of("Europe/Berlin"))).isEqualTo("21. Okt.")
        assertThat(GermanFormat.dayShort(millis, ZoneId.of("UTC"))).isEqualTo("20. Okt.")
    }

    @Test
    fun `English numbers use the decimal point and the thousands comma`() = inLocale(Locale.US) {
        assertThat(GermanFormat.gramsOneDecimal(4.0)).isEqualTo("4.0")
        assertThat(GermanFormat.gramsOneDecimal(1234.5)).isEqualTo("1234.5")
        assertThat(GermanFormat.gramsTrimmed(37.5)).isEqualTo("37.5")
        assertThat(GermanFormat.gramsTrimmed(1500.0)).isEqualTo("1,500")
        assertThat(GermanFormat.euros(123_450)).isEqualTo("1,234.50")
        assertThat(GermanFormat.editableEuros(123_450)).isEqualTo("1234.50")
    }

    @Test
    fun `English dates put the month first, times stay 24 h`() = inLocale(Locale.UK) {
        val day = LocalDate.of(2026, 10, 21)
        assertThat(GermanFormat.day(day)).isEqualTo("Oct 21, 2026")
        assertThat(GermanFormat.dayShort(day)).isEqualTo("Oct 21")
        assertThat(GermanFormat.dayMonthLong(LocalDate.of(2026, 6, 8))).isEqualTo("June 8")
        assertThat(GermanFormat.weekdayDay(day)).isEqualTo("Wednesday, October 21")
        assertThat(GermanFormat.weekdayDay(day, withYear = true)).isEqualTo("Wednesday, October 21, 2026")
        assertThat(GermanFormat.time(LocalTime.of(14, 5))).isEqualTo("14:05")
        assertThat(GermanFormat.day(1_792_539_000_000L, ZoneId.of("Europe/Berlin"))).isEqualTo("Oct 21, 2026")
    }

    @Test
    fun `the editable values read back in both languages`() {
        listOf(Locale.GERMANY, Locale.US).forEach { locale ->
            inLocale(locale) {
                assertThat(GermanNumber.parseCents(GermanFormat.editableEuros(1240))).isEqualTo(1240)
                assertThat(GermanNumber.parseCents(GermanFormat.editableEuros(123_450))).isEqualTo(123_450)
                assertThat(GermanNumber.parseGrams(GermanFormat.gramsOneDecimal(4.0))).isEqualTo(4.0)
                assertThat(GermanNumber.parseGrams(GermanFormat.gramsOneDecimal(37.5))).isEqualTo(37.5)
            }
        }
    }
}
