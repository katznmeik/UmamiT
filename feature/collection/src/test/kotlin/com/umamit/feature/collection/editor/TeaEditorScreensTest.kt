package com.umamit.feature.collection.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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
import java.time.LocalDate

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
    private var alertKey by mutableStateOf<String?>(null)

    private fun showEditor(teaId: String? = null, styleKey: String? = null) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaEditorRoute(
                    onClose = { closed++ },
                    onSave = { id, isNew -> saved += id to isNew },
                    alertKey = alertKey,
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
    fun `the opening date is picked, shown in German and cleared again (INV-06)`() {
        runBlocking {
            collection.teas.saveTea(
                Tea(id = "t1", name = "Morgennebel", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF),
            )
        }
        showEditor(teaId = "t1")
        compose.onNodeWithTag(TeaEditorTags.OPENED_DATE).performScrollTo().assertTextContains("Nicht gesetzt")
        compose.onNodeWithTag(TeaEditorTags.OPENED_DATE + CLEAR_SUFFIX).assertDoesNotExist()

        compose.onNodeWithTag(TeaEditorTags.OPENED_DATE).performClick()
        compose.onNodeWithText("OK").performClick() // the picker starts at today
        compose.onNodeWithTag(TeaEditorTags.OPENED_DATE).performScrollTo().assertTextContains("22. Okt. 2026")

        compose.onNodeWithTag(TeaEditorTags.SAVE).performClick()
        compose.waitForIdle()
        assertThat(runBlocking { collection.teas.getTea("t1") }!!.openedDate).isEqualTo(LocalDate.of(2026, 10, 22))
    }

    @Test
    fun `Entfernen clears a stored opening and best-before date and the tea is unopened again`() {
        runBlocking {
            collection.teas.saveTea(
                Tea(
                    id = "t1",
                    name = "Morgennebel",
                    category = TeaCategory.GREEN,
                    form = TeaForm.LOOSE_LEAF,
                    openedDate = LocalDate.of(2026, 10, 8),
                    bestBefore = LocalDate.of(2027, 3, 1),
                ),
            )
        }
        showEditor(teaId = "t1")
        compose.onNodeWithTag(TeaEditorTags.OPENED_DATE).performScrollTo().assertTextContains("8. Okt. 2026")
        compose.onNodeWithTag(TeaEditorTags.BEST_BEFORE).performScrollTo().assertTextContains("1. März 2027")

        compose.onNodeWithTag(TeaEditorTags.OPENED_DATE + CLEAR_SUFFIX).performClick()
        compose.onNodeWithTag(TeaEditorTags.BEST_BEFORE + CLEAR_SUFFIX).performClick()
        compose.onNodeWithTag(TeaEditorTags.SAVE).performClick()
        compose.waitForIdle()

        val tea = runBlocking { collection.teas.getTea("t1") }!!
        assertThat(tea.openedDate).isNull()
        assertThat(tea.bestBefore).isNull()
    }

    @Test
    fun `a stored opening date in the future is refused at the field`() {
        runBlocking {
            collection.teas.saveTea(
                Tea(
                    id = "t1",
                    name = "Morgennebel",
                    category = TeaCategory.GREEN,
                    form = TeaForm.LOOSE_LEAF,
                    openedDate = LocalDate.of(2026, 12, 1),
                ),
            )
        }
        showEditor(teaId = "t1")
        compose.onNodeWithTag(TeaEditorTags.SAVE).performClick()

        compose.onNodeWithText("Das Öffnungsdatum liegt in der Zukunft.").performScrollTo().assertIsDisplayed()
        assertThat(saved).isEmpty()
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
    fun `an alert removes the discard question in its first frame and it does not come back (rule 8)`() {
        showEditor(styleKey = "sencha")
        compose.onNodeWithTag(TeaEditorTags.NAME).performTextInput("Tanabata")
        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.onNodeWithText("Änderungen verwerfen?").assertIsDisplayed()

        alertAndEnd()

        compose.onAllNodesWithText("Änderungen verwerfen?").assertCountEquals(0)
        assertThat(closed).isEqualTo(0)
    }

    @Test
    fun `an alert removes the date picker and it does not come back (rule 8)`() {
        showEditor(styleKey = "sencha")
        compose.onNodeWithTag(TeaEditorTags.PURCHASE_DATE).performScrollTo().performClick()
        compose.onNodeWithText("OK").assertIsDisplayed()

        alertAndEnd()

        compose.onAllNodesWithText("OK").assertCountEquals(0)
    }

    @Test
    fun `an alert removes the Sorte menu and it does not come back (rule 8)`() {
        showEditor(styleKey = "sencha")
        compose.onNodeWithText("Sencha").performClick()
        compose.onNodeWithText("Gyokuro").assertExists()

        alertAndEnd()

        compose.onAllNodesWithText("Gyokuro").assertCountEquals(0)
    }

    @Test
    fun `a menu opened during an alert stays for that alert`() {
        alertKey = "s1:0:1"
        showEditor(styleKey = "sencha")

        compose.onNodeWithText("Sencha").performClick()

        compose.onNodeWithText("Gyokuro").assertExists()
    }

    /** An alert starts: what was open is gone after the frame that recomposes and the one that drops its window. */
    private fun alertAndEnd() {
        compose.mainClock.autoAdvance = false
        alertKey = "s1:0:1"
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onAllNodesWithText("Änderungen verwerfen?").assertCountEquals(0)
        compose.onAllNodesWithText("OK").assertCountEquals(0)
        compose.onAllNodesWithText("Gyokuro").assertCountEquals(0)
        compose.mainClock.autoAdvance = true
        alertKey = null
        compose.waitForIdle()
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
