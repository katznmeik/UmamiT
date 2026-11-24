package com.umamit.feature.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.reliability.ReliabilityStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SET-35 *Sprache* (GEN-10, 03 SCR-25): the row sits below *Design*, its dialog offers *System*, „Deutsch" and
 * „English", and a pick closes the dialog and goes to Android's per-app language ([AppLocales]) at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class LanguageSettingTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val locales = AppLocales(context)
    private var language by mutableStateOf(LanguageChoice.SYSTEM)

    private fun show() {
        language = locales.current()
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                SettingsScreen(
                    settings = UserSettings(),
                    status = ReliabilityStatus.AllOk,
                    alerting = false,
                    onBack = {},
                    actions = SettingsActions(
                        setLanguage = { choice ->
                            locales.apply(choice)
                            language = locales.current()
                        },
                    ),
                    language = language,
                )
            }
        }
    }

    @Test
    fun `Sprache sits below Design and reads System by default`() {
        show()
        val design = compose.onNodeWithTag(SettingsTags.THEME).fetchSemanticsNode().boundsInRoot.top
        val row = compose.onNodeWithTag(SettingsTags.LANGUAGE).fetchSemanticsNode().boundsInRoot.top
        assertThat(design).isLessThan(row)
        compose.onNodeWithTag(SettingsTags.LANGUAGE).assertTextContains("Sprache", substring = true)
        compose.onNodeWithTag(SettingsTags.LANGUAGE).assertTextContains("System", substring = true)
        assertThat(locales.current()).isEqualTo(LanguageChoice.SYSTEM)
    }

    @Test
    fun `a pick goes to Android at once and closes the dialog`() {
        show()
        for ((label, choice) in listOf(
            "English" to LanguageChoice.ENGLISH,
            "Deutsch" to LanguageChoice.GERMAN,
            "System" to LanguageChoice.SYSTEM,
        )) {
            compose.onNodeWithTag(SettingsTags.LANGUAGE).performClick()
            compose.onNode(hasText(label) and isSelectable()).assertIsNotSelected().performClick()
            compose.waitForIdle()
            assertThat(locales.current()).isEqualTo(choice)
            compose.onNode(hasText(label) and isSelectable()).assertDoesNotExist()
            compose.onNodeWithTag(SettingsTags.LANGUAGE).assertTextContains(label, substring = true)
        }
    }
}
