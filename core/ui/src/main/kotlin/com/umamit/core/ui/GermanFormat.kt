package com.umamit.core.ui

import com.umamit.core.common.AppLanguage
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * The one set of grams, price and date formats of the app (CLAUDE.md rule 4, D-101), in the app's language
 * (GEN-10, D-136 item 15; [AppLanguage], read at every call): German keeps its rules exactly, decimal comma and
 * „21. Okt. 2026"; English has a decimal point and „Oct 21, 2026". Times are 24 h in both („07:05"). Display only:
 * nothing stored goes through it. Parsing the typed counterparts stays in `GermanNumber` (`core:domain`), which reads a
 * decimal point too, so the editable values ([editableEuros], [gramsOneDecimal]) parse back in both languages.
 */
object GermanFormat {
    private const val CENT_DIGITS = 2
    private val DAY = DayPattern(german = "d. MMM yyyy", english = "MMM d, yyyy")
    private val DAY_SHORT = DayPattern(german = "d. MMM", english = "MMM d")
    private val DAY_MONTH_LONG = DayPattern(german = "d. MMMM", english = "MMMM d")
    private val WEEKDAY_DAY = DayPattern(german = "EEEE, d. MMMM", english = "EEEE, MMMM d")
    private val WEEKDAY_DAY_YEAR = DayPattern(german = "EEEE, d. MMMM yyyy", english = "EEEE, MMMM d, yyyy")
    private val TIME = DayPattern(german = "HH:mm", english = "HH:mm")

    /** „4,0", „4,6" (English „4.0"): [value] in g to one decimal (leaf, ratio; 05 §7). */
    fun gramsOneDecimal(value: Double): String = "%.1f".format(stringLocale(), value)

    /** [grams] with separators and no trailing zeros, e.g. „250", „37,5", „37,26", „1.500" (English „1,500"). */
    fun gramsTrimmed(grams: Double): String = NumberFormat.getNumberInstance(numberLocale()).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = CENT_DIGITS
    }.format(grams)

    /** [cents] as euros with two decimals and separators, e.g. „15,90", „1.234,50" (English „1,234.50"; no „€"). */
    fun euros(cents: Long): String = NumberFormat.getNumberInstance(numberLocale()).apply {
        minimumFractionDigits = CENT_DIGITS
        maximumFractionDigits = CENT_DIGITS
    }.format(BigDecimal.valueOf(cents, CENT_DIGITS))

    /**
     * [cents] for an editable price field: as [euros] without thousands separators, e.g. „1234,50" (English
     * „1234.50"; D-090), so `GermanNumber` reads it back.
     */
    fun editableEuros(cents: Long): String = NumberFormat.getNumberInstance(numberLocale()).apply {
        minimumFractionDigits = CENT_DIGITS
        maximumFractionDigits = CENT_DIGITS
        isGroupingUsed = false
    }.format(BigDecimal.valueOf(cents, CENT_DIGITS))

    /** „21. Okt. 2026" („Oct 21, 2026") for a date or date-time. */
    fun day(date: TemporalAccessor): String = DAY.format(date)

    /** „21. Okt. 2026" („Oct 21, 2026"): the day of [millis] in [zone]. */
    fun day(millis: Long, zone: ZoneId): String = DAY.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** „21. Okt." („Oct 21") for a date or date-time. */
    fun dayShort(date: TemporalAccessor): String = DAY_SHORT.format(date)

    /** „21. Okt." („Oct 21"): the day of [millis] in [zone]. */
    fun dayShort(millis: Long, zone: ZoneId): String = DAY_SHORT.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** „8. Juni" („June 8") for a date or date-time. */
    fun dayMonthLong(date: TemporalAccessor): String = DAY_MONTH_LONG.format(date)

    /**
     * „Mittwoch, 21. Oktober", with [withYear] „Mittwoch, 21. Oktober 2026" (English „Wednesday, October 21" and
     * „Wednesday, October 21, 2026").
     */
    fun weekdayDay(date: TemporalAccessor, withYear: Boolean = false): String =
        (if (withYear) WEEKDAY_DAY_YEAR else WEEKDAY_DAY).format(date)

    /** „07:05", 24 h in both languages. */
    fun time(time: TemporalAccessor): String = TIME.format(time)

    /**
     * A date pattern per language: German needs its own order and dots („21. Okt."), not only other month names.
     * Both formatters are immutable and built once; the language picks one at every call, so nothing goes stale.
     */
    private class DayPattern(german: String, english: String) {
        private val german = DateTimeFormatter.ofPattern(german, Locale.GERMAN)
        private val english = DateTimeFormatter.ofPattern(english, AppLanguage.ENGLISH.locale)

        fun format(date: TemporalAccessor): String = (if (english()) english else german).format(date)
    }
}

private fun english() = AppLanguage.current() == AppLanguage.ENGLISH

/** The locale of [GermanFormat]'s `%.1f` formats: German as before (`Locale.GERMAN`), or English. */
private fun stringLocale(): Locale = if (english()) AppLanguage.ENGLISH.locale else Locale.GERMAN

/** The locale of [GermanFormat]'s [NumberFormat]s: German as before (`Locale.GERMANY`), or English. */
private fun numberLocale(): Locale = AppLanguage.current().locale
