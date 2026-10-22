package com.umamit.feature.brew.picker

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

/** The tea picker sheet (SCR-03): a pick, the quick timer footer, the search hint, and alerts first (rule 8). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaPickerSheetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val hisui = PickerTea("h", "Bio Sencha Hisui", "Sencha", null)
    private val tanabata = PickerTea("t", "Bio Sencha Tanabata", "Sencha", null)
    private val picked = mutableListOf<String>()
    private var quick = 0
    private var dismissed = 0

    private fun show(state: TeaPickerUiState) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaPickerSheet(
                    state = state,
                    onQueryChange = {},
                    onPickTea = { picked += it },
                    onQuickTimer = { quick++ },
                    onDismissRequest = { dismissed++ },
                )
            }
        }
    }

    @Test
    fun `a tap on a tea picks it, the footer starts the quick timer`() {
        show(TeaPickerUiState(recent = listOf(tanabata), all = listOf(hisui, tanabata), loading = false))

        compose.onNodeWithText("ZULETZT").assertIsDisplayed()
        compose.onNodeWithText("Bio Sencha Hisui").performClick()
        compose.onNodeWithText("Ohne Tee (Schnelltimer)").performScrollTo().performClick()

        assertThat(picked).containsExactly("h")
        assertThat(quick).isEqualTo(1)
    }

    @Test
    fun `a pick calls back in the tap's frame, so no alert can come between the pick and the setup (rule 8)`() {
        var alertKey by mutableStateOf<String?>(null)
        var open by mutableStateOf(true)
        compose.setContent {
            UmamiTheme(reduceMotion = false) {
                AlertGate(open = open, openedIn = null, alertKey = alertKey, onDismissRequest = { open = false }) {
                    TeaPickerSheet(
                        state = TeaPickerUiState(all = listOf(hisui), loading = false),
                        onQueryChange = {},
                        onPickTea = {
                            picked += it
                            open = false
                        },
                        onQuickTimer = {},
                        onDismissRequest = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Bio Sencha Hisui").performClick()
        assertThat(picked).containsExactly("h")

        // An alert in the next frames changes nothing: the pick already happened, once.
        alertKey = "s1:0:1"
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        assertThat(picked).containsExactly("h")
        compose.onNodeWithText("Tee wählen").assertDoesNotExist()
    }

    @Test
    fun `a search without hits says so`() {
        show(TeaPickerUiState(all = listOf(hisui), query = "Matcha", results = emptyList(), loading = false))

        compose.onNodeWithText("Kein Tee zu „Matcha“ gefunden.").assertIsDisplayed()
    }

    @Test
    fun `an alert that starts while the picker is open removes it at once and closes it (rule 8)`() {
        var alertKey by mutableStateOf<String?>(null)
        var open by mutableStateOf(true)
        compose.setContent {
            UmamiTheme(reduceMotion = false) {
                AlertGate(open = open, openedIn = null, alertKey = alertKey, onDismissRequest = { open = false }) {
                    TeaPickerSheet(
                        state = TeaPickerUiState(all = listOf(hisui), loading = false),
                        onQueryChange = {},
                        onPickTea = {},
                        onQuickTimer = {},
                        onDismissRequest = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Tee wählen").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        alertKey = "s1:0:1"
        // The frame that recomposes, and the frame that drops the sheet's window (as for the Schritte-Sheet).
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

        compose.onNodeWithText("Tee wählen").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertThat(open).isFalse()
    }

    @Test
    fun `a picker opened during an alert stays for that alert`() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                AlertGate(open = true, openedIn = "s1:0:1", alertKey = "s1:0:1", onDismissRequest = {}) {
                    TeaPickerSheet(
                        state = TeaPickerUiState(all = listOf(hisui), loading = false),
                        onQueryChange = {},
                        onPickTea = {},
                        onQuickTimer = {},
                        onDismissRequest = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Tee wählen").assertIsDisplayed()
    }
}
