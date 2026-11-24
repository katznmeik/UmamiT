package com.umamit.testlocale

import java.util.Locale

/**
 * Runs [block] with [locale] as the default locale, then restores the one before: the English side of a plain JVM
 * test of a formatter (GEN-10). A Robolectric test names its language in its qualifiers instead
 * ([GermanConfigConfigurer]).
 */
fun <T> inLocale(locale: Locale, block: () -> T): T {
    val before = Locale.getDefault()
    Locale.setDefault(locale)
    return try {
        block()
    } finally {
        Locale.setDefault(before)
    }
}
