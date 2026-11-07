package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeAddWithoutCatalog
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.feature.collection.editor.ResourceSorteNames
import com.umamit.feature.collection.editor.SortePickerTags
import com.umamit.feature.collection.editor.TeaEditorRoute
import com.umamit.feature.collection.editor.TeaEditorTags
import com.umamit.feature.collection.editor.TeaEditorViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The keyboard as an inset (03 §2.5, D-102), dispatched to the Compose view as the system does: *Meine Tees* ends
 * right above the keyboard (no empty band the navigation bar's height), and the tea editor's fields stay reachable
 * above it (no second padding). The real root with the real editor on fakes; Robolectric has no keyboard of its own.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class KeyboardInsetsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)
    private val adder = FakeAddWithoutCatalog(collection)

    private fun show() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(sessions, collection.teas),
                    alertKey = MutableStateFlow(null),
                    collectionScreens = CollectionStackScreens(
                        // A stand-in for the list that fills its place: its bottom is the shell's content edge.
                        myTeas = { tab -> Box(tab.modifier.fillMaxSize().testTag(LIST)) },
                        teaEditor = { host -> Editor(host) },
                    ),
                )
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Editor(host: TeaEditorHost) {
        TeaEditorRoute(
            onClose = host.onClose,
            onSave = host.onSave,
            viewModel = viewModel {
                TeaEditorViewModel(
                    createSavedStateHandle(),
                    collection.teas,
                    adder,
                    ResourceSorteNames(compose.activity),
                    FakeClock(),
                )
            },
        )
    }

    /** The Compose view's height in px: the window, as the test activity has no system bars. */
    private fun composeViewHeightPx(): Int =
        compose.activity.findViewById<android.view.View>(android.R.id.content).height

    /** Reports a keyboard of [heightPx] to the Compose view, as the window does when one opens. */
    private fun showKeyboard(heightPx: Int) {
        val content = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, heightPx))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), insets) }
        compose.waitForIdle()
    }

    private fun openCollection() {
        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `Meine Tees ends right above the keyboard`() {
        show()
        openCollection()
        val keyboardPx = composeViewHeightPx() / 2
        val keyboardTop = with(compose.density) { (composeViewHeightPx() - keyboardPx).toDp() }

        showKeyboard(keyboardPx)

        val listBottom = compose.onNodeWithTag(LIST).getBoundsInRoot().bottom
        assertThat(listBottom.value).isWithin(2f).of(keyboardTop.value)
    }

    @Test
    fun `the tea editor's fields stay above the keyboard`() {
        show()
        openCollection()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Tee ohne Katalog hinzufügen").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SortePickerTags.SEARCH).performTextReplacement("oolong")
        compose.onNodeWithTag(SortePickerTags.style("oolong_generic")).performClick()
        compose.waitForIdle()
        val keyboardPx = composeViewHeightPx() / 2
        val keyboardTop = with(compose.density) { (composeViewHeightPx() - keyboardPx).toDp() }

        showKeyboard(keyboardPx)

        compose.onNodeWithText("Neuer Tee").assertIsDisplayed()
        compose.onNodeWithTag(TeaEditorTags.NAME).assertIsDisplayed()
        // The notes are the last field: they scroll into the room above the keyboard, never behind it.
        compose.onNodeWithTag(TeaEditorTags.NOTES).performScrollTo().assertIsDisplayed()
        val notesBottom = compose.onNodeWithTag(TeaEditorTags.NOTES).getBoundsInRoot().bottom
        assertThat(notesBottom).isAtMost(keyboardTop + 1.dp)
    }

    private companion object {
        const val LIST = "keyboard_test_list"
    }
}
