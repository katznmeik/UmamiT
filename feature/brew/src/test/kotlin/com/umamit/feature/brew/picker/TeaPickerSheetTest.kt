package com.umamit.feature.brew.picker

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
    private var catalog = 0
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
                    onCatalog = { catalog++ },
                )
            }
        }
    }

    @Test
    fun `Aus dem Katalog leads to the catalog (D-073)`() {
        show(TeaPickerUiState(all = listOf(hisui), loading = false))

        compose.onNodeWithText("Aus dem Katalog").performScrollTo().assertIsDisplayed().performClick()

        assertThat(catalog).isEqualTo(1)
        assertThat(picked).isEmpty()
        assertThat(quick).isEqualTo(0)
    }

    @Test
    fun `an empty collection offers the catalog too`() {
        show(TeaPickerUiState(loading = false))

        compose.onNodeWithText("Aus dem Katalog").performScrollTo().assertIsDisplayed()
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

    private val removedTeas = mutableListOf<String>()
    private var undone = 0
    private var snackbarGone = 0

    private fun showRemovable(state: TeaPickerUiState) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaPickerSheet(
                    state = state,
                    onQueryChange = {},
                    onPickTea = { picked += it },
                    onQuickTimer = {},
                    onDismissRequest = {},
                    onRemoveTea = { removedTeas += it },
                    onUndoRemove = { undone++ },
                    onRemovedDismiss = { snackbarGone++ },
                )
            }
        }
    }

    @Test
    fun `a long press offers Aus Sammlung entfernen, which removes the tea without a dialog`() {
        showRemovable(TeaPickerUiState(all = listOf(hisui, tanabata), loading = false))

        compose.onNodeWithText("Bio Sencha Hisui").performTouchInput { longClick() }
        compose.onNodeWithText("Aus Sammlung entfernen").assertIsDisplayed().performClick()

        assertThat(removedTeas).containsExactly("h")
        assertThat(picked).isEmpty()
        compose.onNodeWithText("Aus Sammlung entfernen").assertDoesNotExist()
    }

    @Test
    fun `the tea of the active session is greyed out with Erst die Sitzung beenden (E15)`() {
        showRemovable(TeaPickerUiState(all = listOf(hisui, tanabata.copy(removable = false)), loading = false))

        compose.onNodeWithText("Bio Sencha Tanabata").performTouchInput { longClick() }

        compose.onNodeWithText("Erst die Sitzung beenden", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Aus Sammlung entfernen")).assertIsNotEnabled().performClick()
        assertThat(removedTeas).isEmpty()
    }

    @Test
    fun `TalkBack offers Aus Sammlung entfernen as a custom action on the row`() {
        showRemovable(TeaPickerUiState(all = listOf(hisui, tanabata.copy(removable = false)), loading = false))

        val row = compose.onNode(hasText("Bio Sencha Hisui") and hasClickAction()).fetchSemanticsNode()
        compose.runOnIdle {
            row.config[SemanticsActions.CustomActions].single { it.label == "Aus Sammlung entfernen" }.action()
        }
        // Not for the tea of the active session.
        val blocked = compose.onNode(hasText("Bio Sencha Tanabata") and hasClickAction()).fetchSemanticsNode()

        assertThat(removedTeas).containsExactly("h")
        assertThat(blocked.config.getOrElse(SemanticsActions.CustomActions) { emptyList() }).isEmpty()
    }

    @Test
    fun `the snackbar names the removed tea and Rueckgaengig undoes it`() {
        showRemovable(TeaPickerUiState(all = listOf(tanabata), loading = false, removed = hisui))

        compose.onNodeWithText("Bio Sencha Hisui aus der Sammlung entfernt").assertIsDisplayed()
        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitForIdle()

        assertThat(undone).isEqualTo(1)
        assertThat(snackbarGone).isEqualTo(0)
    }

    @Test
    fun `the snackbar stays about 8 s, then goes and the tea stays removed (D-093 Q21)`() {
        showRemovable(TeaPickerUiState(all = listOf(tanabata), loading = false, removed = hisui))
        compose.onNodeWithText("Bio Sencha Hisui aus der Sammlung entfernt").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(7_500)
        compose.onNodeWithText("Bio Sencha Hisui aus der Sammlung entfernt").assertIsDisplayed()
        assertThat(snackbarGone).isEqualTo(0)

        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertThat(snackbarGone).isEqualTo(1)
        assertThat(undone).isEqualTo(0)
    }
}
