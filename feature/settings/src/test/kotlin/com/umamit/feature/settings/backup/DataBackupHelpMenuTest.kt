package com.umamit.feature.settings.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** *Daten & Sicherung*'s new ⋮ with its one item *Hilfe* (SCR-36 „Entry points", D-137 Q10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataBackupHelpMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private var helpOpened = 0

    @Test
    fun `the menu holds only Hilfe, and its tap opens the Anleitung`() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                DataBackupScreen(
                    state = DataBackupUiState(),
                    alerting = false,
                    onBack = {},
                    actions = DataBackupActions(openHelp = { helpOpened++ }),
                )
            }
        }

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hilfe").assertIsDisplayed()
        compose.onNodeWithText("Einstellungen").assertDoesNotExist()
        compose.onNodeWithText("Hilfe").performClick()
        compose.waitForIdle()

        assertThat(helpOpened).isEqualTo(1)
        compose.onNodeWithText("Hilfe").assertDoesNotExist()
    }
}
