package com.umamit.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared alert gate (rule 8, D-058): a menu, dialog or sheet remembers the alert it was opened in; another alert
 * removes it in that frame, without an exit animation, and closes it for good.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class AlertGateTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var alertKey by mutableStateOf<String?>(null)
    private lateinit var gate: AlertGate

    private fun showDialogGate() {
        compose.setContent {
            gate = rememberAlertGate(alertKey)
            Column {
                TextButton(onClick = gate::open) { Text("Open") }
                AlertGated(gate) { Text("Overlay") }
            }
        }
    }

    private fun showMenuGate() {
        compose.setContent {
            gate = rememberAlertGate(alertKey)
            Column {
                TextButton(onClick = gate::open) { Text("Open") }
                AlertGatedDropdownMenu(gate) {
                    DropdownMenuItem(text = { Text("Item") }, onClick = gate::close)
                }
            }
        }
    }

    private fun advanceFrames(count: Int) {
        repeat(count) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    @Test
    fun `an alert removes an overlay opened before it in that frame and it stays closed after the alert`() {
        showDialogGate()
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithText("Overlay").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        alertKey = "s1:0:1"
        // The frame that recomposes (the test clock delivers the change one frame late).
        advanceFrames(2)
        compose.onNodeWithText("Overlay").assertDoesNotExist()

        compose.mainClock.autoAdvance = true
        alertKey = null
        compose.waitForIdle()
        compose.onNodeWithText("Overlay").assertDoesNotExist()
        assertThat(gate.isOpen).isFalse()
    }

    @Test
    fun `an overlay opened during an alert stays for that alert and after it, until the next alert`() {
        alertKey = "s1:0:1"
        showDialogGate()
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithText("Overlay").assertIsDisplayed()

        alertKey = null
        compose.waitForIdle()
        compose.onNodeWithText("Overlay").assertIsDisplayed()

        alertKey = "s1:1:2"
        compose.waitForIdle()
        compose.onNodeWithText("Overlay").assertDoesNotExist()
        assertThat(gate.isOpen).isFalse()
    }

    @Test
    fun `an alert removes an open menu without its exit animation and it does not reopen after the alert`() {
        showMenuGate()
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithText("Item").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        alertKey = "s1:0:1"
        // The frame that recomposes, and the frame that drops the popup's window.
        advanceFrames(2)
        compose.onNodeWithText("Item").assertDoesNotExist()

        compose.mainClock.autoAdvance = true
        alertKey = null
        compose.waitForIdle()
        compose.onNodeWithText("Item").assertDoesNotExist()
        assertThat(gate.isOpen).isFalse()
    }

    @Test
    fun `a menu opened during an alert stays for that alert`() {
        alertKey = "s1:0:1"
        showMenuGate()
        compose.onNodeWithText("Open").performClick()

        compose.onNodeWithText("Item").assertIsDisplayed()
    }

    @Test
    fun `an open overlay and the alert it was opened in survive a configuration change (GEN-07)`() {
        alertKey = "s1:0:1"
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            gate = rememberAlertGate(alertKey)
            Column {
                TextButton(onClick = gate::open) { Text("Open") }
                AlertGated(gate) { Text("Overlay") }
            }
        }
        compose.onNodeWithText("Open").performClick()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Overlay").assertIsDisplayed()
        alertKey = "s1:1:2"
        compose.waitForIdle()
        compose.onNodeWithText("Overlay").assertDoesNotExist()
    }
}
