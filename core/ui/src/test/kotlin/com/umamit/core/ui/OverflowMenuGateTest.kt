package com.umamit.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.component.OverflowItem
import com.umamit.core.designsystem.component.OverflowSettingsAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tabs' ⋮ menu (Journal, Sammlung, Statistik) gives way to an alert (03 „Overlays give way", D-058, #330): wired
 * to the shared gate as the three hosts wire it, an alert's first frame removes the open menu without Material's exit
 * animation, and it does not come back after the alert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class OverflowMenuGateTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var alertKey by mutableStateOf<String?>(null)
    private var settingsOpened = 0
    private lateinit var gate: AlertGate

    private fun showMenu() {
        compose.setContent {
            gate = rememberAlertGate(alertKey)
            OverflowSettingsAction(
                onOpenSettings = { settingsOpened++ },
                shown = gate.shown,
                keepsPopup = gate.keepsPopup,
                onOpen = gate::open,
                onClose = gate::close,
                items = listOf(OverflowItem("Katalog aktualisieren") {}),
            )
        }
    }

    private fun openMenu() {
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Einstellungen").assertIsDisplayed()
    }

    private fun advanceFrames(count: Int) {
        repeat(count) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    @Test
    fun `an alert closes the open menu in its first frame without an exit animation and it does not reopen`() {
        showMenu()
        openMenu()

        compose.mainClock.autoAdvance = false
        alertKey = "s1:0:1"
        // The frame that recomposes (the test clock delivers the change one frame late), and the frame that drops
        // the popup's window; Material's exit animation would keep it for far longer.
        advanceFrames(2)
        compose.onNodeWithText("Einstellungen").assertDoesNotExist()
        compose.onNodeWithText("Katalog aktualisieren").assertDoesNotExist()

        compose.mainClock.autoAdvance = true
        alertKey = null
        compose.waitForIdle()
        compose.onNodeWithText("Einstellungen").assertDoesNotExist()
        assertThat(gate.isOpen).isFalse()
    }

    @Test
    fun `a menu opened during an alert stays for that alert and closes at the next`() {
        alertKey = "s1:0:1"
        showMenu()
        openMenu()

        alertKey = null
        compose.waitForIdle()
        compose.onNodeWithText("Einstellungen").assertIsDisplayed()

        alertKey = "s1:1:2"
        compose.waitForIdle()
        compose.onNodeWithText("Einstellungen").assertDoesNotExist()
        assertThat(gate.isOpen).isFalse()
    }

    @Test
    fun `without an alert an entry closes the menu and opens the settings`() {
        showMenu()
        openMenu()

        compose.onNodeWithText("Einstellungen").performClick()
        compose.waitForIdle()

        assertThat(settingsOpened).isEqualTo(1)
        assertThat(gate.isOpen).isFalse()
        compose.onNodeWithText("Einstellungen").assertDoesNotExist()
    }
}
