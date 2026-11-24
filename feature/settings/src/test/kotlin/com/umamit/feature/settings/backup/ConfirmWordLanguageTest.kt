package com.umamit.feature.settings.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.AppLanguage
import com.umamit.feature.settings.R
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * GEN-13: the word to type follows the language of the resources the screen shows, like the hint that names it
 * (BAK-02 AC3, SET-33), never the JVM's locale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConfirmWordLanguageTest {
    private val defaultLocale = Locale.getDefault()
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    @Config(qualifiers = "de")
    fun germanKeepsTheGermanWords() {
        assertThat(ConfirmWords.of(resources)).isEqualTo(ConfirmWords("ERSETZEN", "LÖSCHEN"))
        assertThat(ConfirmWords.of(resources)).isEqualTo(ConfirmWords.GERMAN)
        assertThat(resources.getString(R.string.backup_confirm_hint, ConfirmWords.GERMAN.replace))
            .isEqualTo("Tippe ERSETZEN zur Bestätigung")
        assertThat(resources.getString(R.string.backup_wipe_confirm_hint, ConfirmWords.GERMAN.wipe))
            .isEqualTo("Tippe LÖSCHEN zur Bestätigung")
    }

    @Test
    @Config(qualifiers = "en")
    fun englishNeedsTheEnglishWords() {
        assertThat(ConfirmWords.of(resources)).isEqualTo(ConfirmWords("REPLACE", "DELETE"))
    }

    @Test
    @Config(qualifiers = "en")
    fun englishResourcesWinOverAFrenchJvmLocale() {
        // *System* with the phone list [Français, English]: values-en is shown, the JVM's locale is French.
        Locale.setDefault(Locale.FRENCH)
        assertThat(AppLanguage.current()).isEqualTo(AppLanguage.GERMAN)

        val words = ConfirmWords.of(resources)
        assertThat(
            resources.getString(R.string.backup_confirm_hint, words.replace),
        ).isEqualTo("Type REPLACE to confirm")
        val state = DataBackupUiState(confirmWords = words, confirmText = "REPLACE")
        assertThat(state.canReplace).isTrue()
        assertThat(state.copy(confirmText = "ERSETZEN").canReplace).isFalse()
        val wipe = DataBackupUiState(dialog = BackupDialog.Wipe(safetyCopy = false), confirmWords = words)
        assertThat(wipe.copy(confirmText = "DELETE").canWipe).isTrue()
        assertThat(wipe.copy(confirmText = "LÖSCHEN").canWipe).isFalse()
    }

    @Test
    fun theTestsRunInGerman() {
        assertThat(DataBackupUiState(confirmText = "ERSETZEN").canReplace).isTrue()
    }
}
