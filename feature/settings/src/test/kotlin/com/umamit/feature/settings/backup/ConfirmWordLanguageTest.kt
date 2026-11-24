package com.umamit.feature.settings.backup

import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.AppLanguage
import org.junit.Test

/** GEN-10: the word to type follows the app's language, like the hint that names it (BAK-02 AC3, SET-33). */
class ConfirmWordLanguageTest {
    @Test
    fun germanKeepsTheGermanWords() {
        assertThat(DataBackupUiState.confirmWord(AppLanguage.GERMAN)).isEqualTo("ERSETZEN")
        assertThat(DataBackupUiState.wipeWord(AppLanguage.GERMAN)).isEqualTo("LÖSCHEN")
    }

    @Test
    fun englishNeedsTheEnglishWords() {
        assertThat(DataBackupUiState.confirmWord(AppLanguage.ENGLISH)).isEqualTo("REPLACE")
        assertThat(DataBackupUiState.wipeWord(AppLanguage.ENGLISH)).isEqualTo("DELETE")
    }

    @Test
    fun theTestsRunInGerman() {
        assertThat(DataBackupUiState(confirmText = "ERSETZEN").canReplace).isTrue()
    }
}
