package com.umamit.core.common

import java.text.NumberFormat

/**
 * A tea's stock in grams as „… g übrig" shows it (INV-05, INV-10, SCR-04; D-097) and the daily reminder lists it
 * (INV-11): to 0,1 g, a whole number without „,0", in the app's language (GEN-13): „14", „12,5", „1.250" in German,
 * „12.5", „1,250" in English. Display only.
 */
fun stockGramsText(grams: Double): String = stockGramsText(grams, AppLanguage.current())

/**
 * [stockGramsText] in [language], for a text built without an activity (a worker's notification), where
 * [AppLanguage.current] is the phone's language rather than the app's (GEN-13 AC1; D-136, „A process without an
 * activity").
 */
fun stockGramsText(grams: Double, language: AppLanguage): String = NumberFormat.getNumberInstance(language.locale)
    .apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }.format(grams)
