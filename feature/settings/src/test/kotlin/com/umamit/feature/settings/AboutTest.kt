package com.umamit.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Über (SCR-27): the version and the source note, *Einrichtung erneut zeigen* (ONB-04) and *Diagnose*. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class AboutTest {
    @get:Rule
    val compose = createComposeRule()

    private var reruns = 0
    private var diagnostics = 0

    private fun show() = compose.setContent {
        UmamiTheme(darkTheme = false, reduceMotion = true) {
            AboutScreen(
                versionName = "0.1.0-rc.1",
                onBack = {},
                onRerunSetup = { reruns++ },
                onOpenDiagnostics = { diagnostics++ },
            )
        }
    }

    @Test
    fun `the version and the Tee-Kontor source note are shown`() {
        show()

        compose.onNodeWithText("Version 0.1.0-rc.1").assertIsDisplayed()
        compose.onNodeWithText("Tee-Kontor Kiel", substring = true).assertIsDisplayed()
    }

    @Test
    fun `Einrichtung erneut zeigen and Diagnose lead on`() {
        show()

        compose.onNodeWithText("Einrichtung erneut zeigen").performScrollTo().performClick()
        compose.onNodeWithText("Diagnose").performScrollTo().performClick()

        assertThat(reruns).isEqualTo(1)
        assertThat(diagnostics).isEqualTo(1)
    }
}
