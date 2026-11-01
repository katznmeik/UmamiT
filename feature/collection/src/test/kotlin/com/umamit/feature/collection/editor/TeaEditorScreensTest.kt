package com.umamit.feature.collection.editor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeAddWithoutCatalog
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tee bearbeiten (SCR-10) with its view model on fakes, and Sorte wählen (SCR-11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaEditorScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val collection = FakeCollection()
    private val adder = FakeAddWithoutCatalog(collection)
    private val saved = mutableListOf<Pair<String, Boolean>>()
    private var closed = 0

    private fun showEditor(teaId: String? = null, styleKey: String? = null) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaEditorRoute(
                    onClose = { closed++ },
                    onSave = { id, isNew -> saved += id to isNew },
                    viewModel = viewModel {
                        TeaEditorViewModel(
                            SavedStateHandle(
                                buildMap {
                                    teaId?.let { put(TeaEditorViewModel.TEA_ID, it) }
                                    styleKey?.let { put(TeaEditorViewModel.STYLE_KEY, it) }
                                },
                            ),
                            collection.teas,
                            adder,
                            ResourceSorteNames(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
                            FakeClock(),
                        )
                    },
                )
            }
        }
    }

    @Test
    fun `a new tea with a package is added and its id handed back`() {
        showEditor(styleKey = "sencha")
        compose.onNodeWithText("Neuer Tee").assertIsDisplayed()
        compose.onNodeWithText("Sencha").assertIsDisplayed()
        compose.onNodeWithTag(TeaEditorTags.NAME).performTextInput("Tanabata")
        compose.onNodeWithTag(TeaEditorTags.PACKAGE).performScrollTo().performTextInput("50")
        compose.onNodeWithTag(TeaEditorTags.PRICE).performScrollTo().performTextInput("12,40")
        compose.onNodeWithText("22. Okt. 2026").assertExists()

        compose.onNodeWithTag(TeaEditorTags.SAVE).performClick()
        compose.waitForIdle()

        assertThat(saved).containsExactly("manual-1" to true)
        val tea = runBlocking { collection.teas.getTea("manual-1") }!!
        assertThat(tea.teaType).isEqualTo("Sencha")
        assertThat(runBlocking { collection.stock.observeEvents("manual-1").first() }.single().deltaGrams)
            .isEqualTo(50.0)
    }

    @Test
    fun `Speichern shows the errors at the fields and stores nothing`() {
        showEditor(styleKey = "sencha")
        compose.onNodeWithTag(TeaEditorTags.HARVEST_YEAR).performScrollTo().performTextInput("2030")
        compose.onNodeWithTag(TeaEditorTags.SAVE).performClick()

        compose.onNodeWithText("Gib dem Tee einen Namen.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Das Jahr liegt in der Zukunft.").performScrollTo().assertIsDisplayed()
        assertThat(saved).isEmpty()
        assertThat(adder.added).isEmpty()
    }

    @Test
    fun `closing with changes asks Aenderungen verwerfen`() {
        showEditor(styleKey = "sencha")
        compose.onNodeWithTag(TeaEditorTags.NAME).performTextInput("Tanabata")
        compose.onNodeWithContentDescription("Schließen").performClick()

        compose.onNodeWithText("Änderungen verwerfen?").assertIsDisplayed()
        compose.onNodeWithText("Weiter bearbeiten").performClick()
        assertThat(closed).isEqualTo(0)

        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.onNodeWithText("Verwerfen").performClick()
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `closing without changes closes at once`() {
        showEditor(styleKey = "sencha")
        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.onAllNodesWithText("Änderungen verwerfen?").assertCountEquals(0)
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `an edit has no package fields and a Sorte change keeps the recipes`() {
        runBlocking {
            adder.add(Tea("x", "Asahi", TeaCategory.GREEN, TeaForm.LOOSE_LEAF, styleKey = "gyokuro"), null)
        }
        val recipes = runBlocking { collection.recipes.observeRecipes("manual-1").first() }
        showEditor(teaId = "manual-1")
        compose.onNodeWithText("Tee bearbeiten").assertIsDisplayed()
        compose.onAllNodesWithTag(TeaEditorTags.PACKAGE).assertCountEquals(0)
        compose.onAllNodesWithTag(TeaEditorTags.PRICE).assertCountEquals(0)

        compose.onNodeWithTag(TeaEditorTags.SORTE).performClick()
        compose.onNodeWithText("Kabusecha").performClick()
        compose.onNodeWithTag(TeaEditorTags.SAVE).performClick()
        compose.waitForIdle()

        assertThat(saved).containsExactly("manual-1" to false)
        val tea = runBlocking { collection.teas.getTea("manual-1") }!!
        assertThat(tea.styleKey).isEqualTo("kabusecha")
        assertThat(tea.teaType).isEqualTo("Kabusecha")
        assertThat(runBlocking { collection.recipes.observeRecipes("manual-1").first() }).isEqualTo(recipes)
    }

    @Test
    fun `Sorte waehlen groups the styles by Kategorie, searches and hands back the tapped one`() {
        val picked = mutableListOf<String>()
        compose.setContent {
            UmamiTheme(reduceMotion = true) { SortePickerScreen(onBack = {}, onPick = { picked += it }) }
        }
        compose.onNodeWithText("Gyokuro").assertIsDisplayed()

        compose.onNodeWithTag(SortePickerTags.SEARCH).performTextReplacement("schwarz")
        compose.onNodeWithTag(SortePickerTags.style("black_generic")).assertIsDisplayed()
        compose.onAllNodesWithTag(SortePickerTags.style("gyokuro")).assertCountEquals(0)
        compose.onNodeWithTag(SortePickerTags.style("black_generic")).performClick()

        compose.onNodeWithTag(SortePickerTags.SEARCH).performTextReplacement("unbekannt")
        compose.onNodeWithText("Andere / unbekannt").performClick()
        assertThat(picked).containsExactly("black_generic", "other").inOrder()

        compose.onNodeWithTag(SortePickerTags.SEARCH).performTextReplacement("xyz")
        compose.onNodeWithText("Keine Sorte passt zu deiner Suche.").assertIsDisplayed()
    }
}
