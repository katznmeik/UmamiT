package com.umamit.feature.journal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
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

/** The Journal's ⋮ *Hilfe* (SCR-36 „Entry points"): just above *Einstellungen*; its tap opens the Anleitung. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class JournalHelpMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private var helpOpened = 0

    @Test
    fun `the menu holds Hilfe above Einstellungen, and its tap opens the Anleitung`() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                JournalScreen(
                    list = JournalList.EMPTY,
                    onOpenSettings = {},
                    onOpenTimer = {},
                    onOpenHelp = { helpOpened++ },
                )
            }
        }

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.waitForIdle()
        val help = compose.onNodeWithText("Hilfe")
        help.assertIsDisplayed()
        assertThat(help.getBoundsInRoot().top)
            .isLessThan(compose.onNodeWithText("Einstellungen").getBoundsInRoot().top)
        help.performClick()
        compose.waitForIdle()

        assertThat(helpOpened).isEqualTo(1)
        compose.onNodeWithText("Hilfe").assertDoesNotExist()
    }
}
