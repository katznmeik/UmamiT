package com.umamit.core.common

import java.util.Locale

/**
 * The two languages the app shows (GEN-13, D-136 Q6 and item 15) and the locale its numbers and dates are formatted
 * in: German („4,0 g", „21. Okt. 2026") or English („4.0 g", „Oct 21, 2026"). English when the current locale is
 * English, German for every other language.
 *
 * The current locale is the JVM's default, which Android sets from the app's configuration: the per-app language
 * (*Sprache*, SET-35, or the phone's per-app setting, both through the platform `LocaleManager`) or, under *System*,
 * the phone's language. It is read at every call ([current]), never kept: a language change re-creates the activity,
 * and every text formatted after it follows the new language.
 *
 * Only for what the app shows. Stored data (Room, the settings, the backup and the recipe file) never goes through
 * it, and the fixed tea vocabulary and the shop's texts stay German in both languages (GEN-13 AC2).
 */
enum class AppLanguage(
    /** The locale numbers and dates are formatted in. */
    val locale: Locale,
) {
    GERMAN(Locale.GERMANY),
    ENGLISH(Locale.US),
    ;

    companion object {
        /** The language the app shows now. */
        fun current(): AppLanguage = of(Locale.getDefault())

        /** English for an English [locale] (any region), German for any other. */
        fun of(locale: Locale): AppLanguage = if (locale.language == ENGLISH_LANGUAGE) ENGLISH else GERMAN

        private const val ENGLISH_LANGUAGE = "en"
    }
}
