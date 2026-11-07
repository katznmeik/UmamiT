package com.umamit.core.ui

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * The one set of German grams, price and date formats of the app (CLAUDE.md rule 4, D-101): decimal comma,
 * „21. Okt. 2026". Pure and locale-fixed, so screens and tests agree whatever the device language. Parsing
 * the typed counterparts stays in `GermanNumber` (`core:domain`).
 */
object GermanFormat {
    private const val CENT_DIGITS = 2
    private val DAY = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)
    private val DAY_SHORT = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)
    private val DAY_MONTH_LONG = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)
    private val WEEKDAY_DAY = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
    private val WEEKDAY_DAY_YEAR = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

    /** „4,0", „4,6": [value] in g to one decimal, German comma (leaf, ratio; 05 §7). */
    fun gramsOneDecimal(value: Double): String = "%.1f".format(Locale.GERMAN, value)

    /** [grams] with German separators and no trailing zeros, e.g. „250", „37,5", „37,26". */
    fun gramsTrimmed(grams: Double): String = NumberFormat.getNumberInstance(Locale.GERMANY).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = CENT_DIGITS
    }.format(grams)

    /** [cents] as euros with two decimals and German separators, e.g. „15,90", „1.234,50" (no „€"). */
    fun euros(cents: Long): String = NumberFormat.getNumberInstance(Locale.GERMANY).apply {
        minimumFractionDigits = CENT_DIGITS
        maximumFractionDigits = CENT_DIGITS
    }.format(BigDecimal.valueOf(cents, CENT_DIGITS))

    /** [cents] for an editable price field: as [euros] without thousands separators, e.g. „1234,50" (D-090). */
    fun editableEuros(cents: Long): String = NumberFormat.getNumberInstance(Locale.GERMANY).apply {
        minimumFractionDigits = CENT_DIGITS
        maximumFractionDigits = CENT_DIGITS
        isGroupingUsed = false
    }.format(BigDecimal.valueOf(cents, CENT_DIGITS))

    /** „21. Okt. 2026" for a date or date-time. */
    fun day(date: TemporalAccessor): String = DAY.format(date)

    /** „21. Okt. 2026": the day of [millis] in [zone]. */
    fun day(millis: Long, zone: ZoneId): String = DAY.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** „21. Okt." for a date or date-time. */
    fun dayShort(date: TemporalAccessor): String = DAY_SHORT.format(date)

    /** „21. Okt.": the day of [millis] in [zone]. */
    fun dayShort(millis: Long, zone: ZoneId): String = DAY_SHORT.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** „8. Juni" for a date or date-time. */
    fun dayMonthLong(date: TemporalAccessor): String = DAY_MONTH_LONG.format(date)

    /** „Mittwoch, 21. Oktober", with [withYear] „Mittwoch, 21. Oktober 2026". */
    fun weekdayDay(date: TemporalAccessor, withYear: Boolean = false): String =
        (if (withYear) WEEKDAY_DAY_YEAR else WEEKDAY_DAY).format(date)

    /** „07:05". */
    fun time(time: TemporalAccessor): String = TIME.format(time)
}
