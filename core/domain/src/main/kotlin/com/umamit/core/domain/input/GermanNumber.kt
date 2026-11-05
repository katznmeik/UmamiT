package com.umamit.core.domain.input

import java.math.BigDecimal

/**
 * Reads German number input for the add sheet and the tea editor with one rule (D-090, F14):
 * trimmed, a decimal comma (a decimal point is also read), optional "." thousands groups of
 * exactly three digits ("1.234,50", "1.234" = 1234), and an optional unit sign („€" for amounts,
 * „g" for grams). A "." followed by three digits is a group, so "12.345" is 12 345; anything else
 * malformed ("1.23.4", "-1", "abc", "1e3") is rejected with null.
 */
object GermanNumber {
    private const val MAX_GRAMS = 10_000.0
    private const val CENT_DIGITS = 2
    private val AMOUNT = Regex("""\d{1,6}(?:[.,]\d{1,2})?""")
    private val GRAMS = Regex("""\d{1,5}(?:[.,]\d)?""")
    private val GROUPED_AMOUNT = Regex("""\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?""")
    private val GROUPED_GRAMS = Regex("""\d{1,2}(?:\.\d{3})+(?:,\d)?""")

    /** „12,40", „12.4", „12", „1.234,50", „€ 12,90" → cents; null when blank or no amount. */
    fun parseCents(text: String): Long? {
        val bare = ungrouped(text.replace("€", "").trim(), GROUPED_AMOUNT)
        if (!AMOUNT.matches(bare)) return null
        return BigDecimal(bare.replace(',', '.')).movePointRight(CENT_DIGITS).toLong()
    }

    /** „250", „37,5", „1.000", „250 g" → grams in (0, 10 000]; null otherwise. */
    fun parseGrams(text: String): Double? {
        val bare = ungrouped(text.replace("g", "").trim(), GROUPED_GRAMS)
        if (!GRAMS.matches(bare)) return null
        return bare.replace(',', '.').toDouble().takeIf { it > 0.0 && it <= MAX_GRAMS }
    }

    /** [text] without its thousands separators when [grouped] matches it; else as it is. */
    private fun ungrouped(text: String, grouped: Regex): String =
        if (grouped.matches(text)) text.replace(".", "") else text
}
