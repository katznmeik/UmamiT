package com.umamit.feature.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** SET-35 *Sprache* (GEN-13, D-136 Q6): the phone's language, or German or English for the app alone. */
enum class LanguageChoice {
    SYSTEM,
    GERMAN,
    ENGLISH,
}

/**
 * Reads and sets the app's own language through Android's per-app language (`LocaleManager`, min SDK 35; D-136
 * item 15). Android stores it, so there is no settings key and no backup carries it; the phone's per-app language
 * settings show and change the same value. *System* is the empty list: the app then follows the phone (GEN-13 AC3).
 *
 * A change re-creates the activity in the new language (`locale` is not in `configChanges`): the screen, its saved
 * state and a running timer, which lives in the timer service, stay.
 */
class AppLocales @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager: LocaleManager? get() = context.getSystemService(LocaleManager::class.java)

    /** The language set for the app now; *System* when none is. */
    fun current(): LanguageChoice {
        val locales = manager?.applicationLocales ?: return LanguageChoice.SYSTEM
        return when (if (locales.isEmpty) null else locales[0].language) {
            GERMAN_TAG -> LanguageChoice.GERMAN
            ENGLISH_TAG -> LanguageChoice.ENGLISH
            else -> LanguageChoice.SYSTEM
        }
    }

    /** Sets [choice] for the app; Android re-creates the activity when the language changes. */
    fun apply(choice: LanguageChoice) {
        manager?.applicationLocales = when (choice) {
            LanguageChoice.SYSTEM -> LocaleList.getEmptyLocaleList()
            LanguageChoice.GERMAN -> LocaleList.forLanguageTags(GERMAN_TAG)
            LanguageChoice.ENGLISH -> LocaleList.forLanguageTags(ENGLISH_TAG)
        }
    }

    private companion object {
        const val GERMAN_TAG = "de"
        const val ENGLISH_TAG = "en"
    }
}
