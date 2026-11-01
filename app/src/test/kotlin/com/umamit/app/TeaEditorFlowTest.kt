package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeAddWithoutCatalog
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.feature.collection.detail.TeaDetailHost
import com.umamit.feature.collection.editor.ResourceSorteNames
import com.umamit.feature.collection.editor.SortePickerTags
import com.umamit.feature.collection.editor.TeaEditorRoute
import com.umamit.feature.collection.editor.TeaEditorTags
import com.umamit.feature.collection.editor.TeaEditorViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * *Ohne Katalog* in the Sammlung tab (TEA-01, SCR-11, SCR-10): *Meine Tees*' FAB choice opens *Sorte wählen*, a
 * Sorte the editor of a new tea, *Speichern* the new tea's detail, whose back returns to the list; the detail's
 * pencil opens the editor of that tea, and ✕ returns to the detail. The real root and shell navigation and the real
 * editor on fakes; stand-ins for the list and the detail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaEditorFlowTest {
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
                        myTeas = { tab -> MyTeas(tab) },
                        tea = { host -> Detail(host) },
                        teaEditor = { host -> Editor(host) },
                    ),
                )
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun MyTeas(tab: MyTeasTab) {
        Column(tab.modifier) {
            Text(LIST)
            TextButton(onClick = tab.addWithoutCatalog) { Text(ADD) }
            TextButton(onClick = { tab.openTea(SEEDED) }) { Text(SEEDED) }
        }
    }

    /** A stand-in for the tea detail (SCR-09): the tea's name and its pencil. */
    @Composable
    private fun Detail(host: TeaDetailHost) {
        Column {
            Text(DETAIL)
            TextButton(onClick = host.onEditTea) { Text(EDIT) }
            TextButton(onClick = host.onBack) { Text(BACK) }
        }
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

    private fun openCollection() {
        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `Ohne Katalog adds a tea of the chosen Sorte and opens its detail`() {
        show()
        openCollection()
        compose.onNodeWithText(ADD).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sorte wählen").assertIsDisplayed()

        compose.onNodeWithTag(SortePickerTags.SEARCH).performTextReplacement("oolong")
        compose.onNodeWithTag(SortePickerTags.style("oolong_generic")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Neuer Tee").assertIsDisplayed()
        compose.onNodeWithTag(TeaEditorTags.NAME).performTextInput("Milky Oolong")
        compose.onNodeWithTag(TeaEditorTags.SAVE).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(DETAIL).assertIsDisplayed()
        val tea = runBlocking { collection.teas.getTea("manual-1") }!!
        assertThat(tea.name).isEqualTo("Milky Oolong")
        assertThat(tea.styleKey).isEqualTo("oolong_generic")
        // Neither the editor nor the picker is left below the detail.
        compose.onNodeWithText(BACK).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(LIST).assertIsDisplayed()
    }

    @Test
    fun `the detail's pencil opens the editor, and its close returns to the detail`() {
        runBlocking { collection.teas.saveTea(Tea(SEEDED, "Asahi", TeaCategory.GREEN, TeaForm.LOOSE_LEAF)) }
        show()
        openCollection()
        compose.onNodeWithText(SEEDED).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(EDIT).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tee bearbeiten").assertIsDisplayed()

        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(DETAIL).assertIsDisplayed()

        compose.onNodeWithText(EDIT).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TeaEditorTags.NAME).performTextReplacement("Asahi Gyokuro")
        compose.onNodeWithTag(TeaEditorTags.SAVE).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(DETAIL).assertIsDisplayed()
        assertThat(runBlocking { collection.teas.getTea(SEEDED) }!!.name).isEqualTo("Asahi Gyokuro")
    }

    private companion object {
        const val LIST = "Liste"
        const val ADD = "Ohne Katalog hinzufügen"
        const val SEEDED = "seeded"
        const val DETAIL = "Detail"
        const val EDIT = "Bearbeiten"
        const val BACK = "Zurück zur Liste"
    }
}
