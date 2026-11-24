package com.umamit.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The central German pin of the Robolectric tests (`config/test-locale`, GEN-10, D-136 item 15): with or without a
 * class's qualifiers, the resources and the default locale are German and the qualifiers' size stays; a test that
 * names English gets English.
 */
@RunWith(RobolectricTestRunner::class)
class TestLocalePinTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val resourcesLanguage get() = context.resources.configuration.locales[0].language

    @Test
    fun withoutQualifiers_isGerman() {
        assertThat(Locale.getDefault().language).isEqualTo("de")
        assertThat(resourcesLanguage).isEqualTo("de")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun withReplacingQualifiers_isGermanAndKeepsTheSize() {
        assertThat(Locale.getDefault().language).isEqualTo("de")
        assertThat(resourcesLanguage).isEqualTo("de")
        assertThat(context.resources.configuration.screenWidthDp).isEqualTo(411)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-xhdpi")
    fun namedEnglish_isEnglish() {
        assertThat(Locale.getDefault()).isEqualTo(Locale.US)
        assertThat(resourcesLanguage).isEqualTo("en")
        assertThat(context.resources.configuration.screenWidthDp).isEqualTo(411)
    }

    @Test
    @Config(qualifiers = "+en-rUS")
    fun appendedEnglish_isEnglish() {
        assertThat(Locale.getDefault().language).isEqualTo("en")
        assertThat(resourcesLanguage).isEqualTo("en")
    }
}
