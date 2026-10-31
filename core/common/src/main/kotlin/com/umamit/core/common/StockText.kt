package com.umamit.core.common

import java.text.NumberFormat
import java.util.Locale

/**
 * A tea's stock in grams as „… g übrig" shows it (INV-05, INV-10, SCR-04; D-097) and the daily reminder lists it
 * (INV-11): German format to 0,1 g, a whole number without „,0" („14", „12,5", „1.250").
 */
fun stockGramsText(grams: Double): String = NumberFormat.getNumberInstance(Locale.GERMANY).apply {
    maximumFractionDigits = 1
    minimumFractionDigits = 0
}.format(grams)
