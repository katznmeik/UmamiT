package com.umamit.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TasteProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The note field of the shared [InfusionTasteEditor] (JRN-05, D-114) against a host that passes only the stored note,
 * as the timer's sheet does: a field re-created before a save lands never writes older text over a newer stored note
 * (T3 m5), an echo of its own older save is not taken, and a draft restored after process death is handed over again
 * (T4 nit 7).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class InfusionTasteEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var stored by mutableStateOf<String?>("Alt")
    private var shown by mutableStateOf(true)
    private val saves = mutableListOf<String>()

    @Composable
    private fun Editor() {
        if (shown) {
            InfusionTasteEditor(
                taste = TasteProfile.EMPTY,
                note = stored,
                onTasteChange = { _, _ -> },
                onNoteSave = { saves += it },
                onReset = {},
            )
        }
    }

    private fun field() = compose.onNodeWithTag(INFUSION_NOTE_TAG)

    private fun type(text: String) {
        field().performTextClearance()
        field().performTextInput(text)
        compose.waitForIdle()
    }

    @Test
    fun `a field re-created from the older note follows the newer one once it is stored`() {
        compose.setContent { UmamiTheme(reduceMotion = true) { Editor() } }
        type("Neu")
        // Folded: the save is handed over, the store has not shown it yet.
        compose.runOnUiThread { shown = false }
        compose.waitForIdle()
        assertThat(saves).containsExactly("Neu")

        // Unfolded before the save lands: the field starts from the older note …
        compose.runOnUiThread { shown = true }
        compose.waitForIdle()
        field().assertTextEquals("Alt")
        // … and follows the newer one when the store shows it, with nothing handed over again.
        compose.runOnUiThread { stored = "Neu" }
        compose.waitForIdle()
        field().assertTextEquals("Neu")
        assertThat(saves).containsExactly("Neu")
    }

    @Test
    fun `an echo of an older save of the field is not taken while a newer one is on its way`() {
        compose.setContent { UmamiTheme(reduceMotion = true) { Editor() } }
        type("Eins")
        compose.mainClock.advanceTimeBy(INFUSION_NOTE_DEBOUNCE_MS + 100)
        type("Zwei")
        compose.mainClock.advanceTimeBy(INFUSION_NOTE_DEBOUNCE_MS + 100)
        compose.waitForIdle()
        assertThat(saves).containsExactly("Eins", "Zwei").inOrder()

        compose.runOnUiThread { stored = "Eins" }
        compose.waitForIdle()
        field().assertTextEquals("Zwei")

        compose.runOnUiThread { stored = "Zwei" }
        compose.waitForIdle()
        field().assertTextEquals("Zwei")
        assertThat(saves).containsExactly("Eins", "Zwei").inOrder()
    }

    @Test
    fun `a draft restored after the process died is handed over again`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { UmamiTheme(reduceMotion = true) { Editor() } }
        compose.mainClock.autoAdvance = false
        field().performTextClearance()
        field().performTextInput("Weich")
        compose.mainClock.advanceTimeByFrame()
        assertThat(saves).isEmpty()

        // The process dies before the save lands (the leaving field's save may be lost with it); the restored field
        // holds the draft, the store the older note.
        restoration.emulateSavedInstanceStateRestore()
        val before = saves.size
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(INFUSION_NOTE_DEBOUNCE_MS + 100)
        compose.waitForIdle()

        field().assertTextEquals("Weich")
        // The restored field hands it over again, 1 s later.
        assertThat(saves.drop(before)).containsExactly("Weich")
    }
}
