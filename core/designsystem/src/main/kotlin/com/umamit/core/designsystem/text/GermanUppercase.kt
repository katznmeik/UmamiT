package com.umamit.core.designsystem.text

import java.util.Locale

private const val SHARP_S = 'ß'
private const val CAPITAL_SHARP_S = 'ẞ'

/**
 * Uppercases German UI text for overlines (03 §3.2): umlauts and macrons stay,
 * and `ß` becomes the capital `ẞ` rather than the `SS` that [String.uppercase]
 * produces, so „Große Blätter" reads „GROẞE BLÄTTER".
 */
// Stays German in both app languages (GEN-10, D-136 item 15): the German locale changes nothing in English text and
// keeps the ẞ rule for the German tea vocabulary that overlines show in both.
fun String.germanUppercase(): String = replace(SHARP_S, CAPITAL_SHARP_S).uppercase(Locale.GERMAN)
