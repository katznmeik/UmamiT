package com.umamit.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.reliability.ReliabilityStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** *Hilfe* in the settings (SCR-25, SET-36): *Einführung*, and *Anleitungen* below it (ONB-08). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class HelpSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private var tours = 0
    private var howTos = 0

    @Test
    fun `Anleitungen sits below Einfuehrung with its subtitle, and opens the list`() {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                SettingsScreen(
                    settings = UserSettings(),
                    status = ReliabilityStatus.AllOk,
                    alerting = false,
                    onBack = {},
                    actions = SettingsActions(openTour = { tours++ }, openHowTos = { howTos++ }),
                )
            }
        }

        compose.onNodeWithTag(SettingsTags.TOUR).performScrollTo().assertIsDisplayed()
        val row = compose.onNodeWithTag(SettingsTags.HOWTOS).performScrollTo()
        row.assertIsDisplayed()
        row.assertTextContains("Anleitungen")
        row.assertTextContains("Schritt für Schritt: acht Themen")
        row.performClick()

        assertThat(howTos).isEqualTo(1)
        assertThat(tours).isEqualTo(0)
    }
}
