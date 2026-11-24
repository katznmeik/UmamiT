package com.umamit.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/** GEN-10 AC3: English for any English locale, German for every other language; read at each call. */
class AppLanguageTest {
    @Test
    fun `English for any English locale`() {
        assertThat(AppLanguage.of(Locale.US)).isEqualTo(AppLanguage.ENGLISH)
        assertThat(AppLanguage.of(Locale.UK)).isEqualTo(AppLanguage.ENGLISH)
        assertThat(AppLanguage.of(Locale.forLanguageTag("en"))).isEqualTo(AppLanguage.ENGLISH)
    }

    @Test
    fun `German for German and for every other language`() {
        assertThat(AppLanguage.of(Locale.GERMANY)).isEqualTo(AppLanguage.GERMAN)
        assertThat(AppLanguage.of(Locale.forLanguageTag("de-AT"))).isEqualTo(AppLanguage.GERMAN)
        assertThat(AppLanguage.of(Locale.FRANCE)).isEqualTo(AppLanguage.GERMAN)
        assertThat(AppLanguage.of(Locale.JAPAN)).isEqualTo(AppLanguage.GERMAN)
    }

    @Test
    fun `the current language follows the default locale at each call`() {
        assertThat(AppLanguage.current()).isEqualTo(AppLanguage.GERMAN)
        inLocale(Locale.US) { assertThat(AppLanguage.current()).isEqualTo(AppLanguage.ENGLISH) }
        assertThat(AppLanguage.current()).isEqualTo(AppLanguage.GERMAN)
    }

    @Test
    fun `the formatting locales are Germany and the US`() {
        assertThat(AppLanguage.GERMAN.locale).isEqualTo(Locale.GERMANY)
        assertThat(AppLanguage.ENGLISH.locale).isEqualTo(Locale.US)
    }
}

/** Runs [block] with [locale] as the default locale, then restores the one before. */
internal fun inLocale(locale: Locale, block: () -> Unit) {
    val before = Locale.getDefault()
    Locale.setDefault(locale)
    try {
        block()
    } finally {
        Locale.setDefault(before)
    }
}
