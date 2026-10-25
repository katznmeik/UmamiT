package com.umamit.feature.catalog

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** German number and date formats of the catalog (CLAUDE.md rule 4): „1.234,50", „21. Okt.". */
internal object CatalogFormat {
    private val DATE = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)
    private val FULL_DATE = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)
    private const val CENT_DIGITS = 2

    /** [cents] as euros with two decimals and German separators, e.g. „15,90". */
    fun euros(cents: Long): String = NumberFormat.getNumberInstance(Locale.GERMANY).apply {
        minimumFractionDigits = CENT_DIGITS
        maximumFractionDigits = CENT_DIGITS
    }.format(BigDecimal.valueOf(cents, CENT_DIGITS))

    /** [grams] with German separators and no trailing zeros, e.g. „250", „37,5". */
    fun grams(grams: Double): String = NumberFormat.getNumberInstance(Locale.GERMANY).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = CENT_DIGITS
    }.format(grams)

    /** The day of [millis] in [zone] as the catalog header shows it, e.g. „21. Okt." (CAT-07 AC6). */
    fun date(millis: Long, zone: ZoneId): String = DATE.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** The day of [millis] in [zone] with its year, e.g. „21. Okt. 2026" (a guide's date, CAT-09). */
    fun fullDate(millis: Long, zone: ZoneId): String = FULL_DATE.format(Instant.ofEpochMilli(millis).atZone(zone))
}
