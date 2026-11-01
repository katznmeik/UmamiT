package com.umamit.feature.collection.recipe

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Rezept bearbeiten (SCR-12) with its view model on fakes: what the PO taps, drags and reads. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class RecipeEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val collection = FakeCollection()
    private val saved = mutableListOf<String>()
    private var closed = 0
    private var deleted = 0

    private val recipe = Recipe(
        id = "kyusu",
        teaId = "tea",
        name = "Kyusu",
        method = BrewMethod.KYUSU,
        leafGrams = 5.0,
        waterMl = 250,
        baseTempC = 70,
        steps = listOf(RecipeStep(StepKind.INFUSION, 60), RecipeStep(StepKind.RINSE, 10)),
        isDefault = true,
    )

    @Before
    fun setUp() {
        runBlocking {
            collection.teas.saveTea(Tea("tea", "Morgennebel", TeaCategory.GREEN, TeaForm.LOOSE_LEAF))
            collection.vessels.saveVessel(Vessel("k180", "Kyusu 180 ml", VesselType.KYUSU, 220, usableMl = 180))
            collection.recipes.saveRecipe(recipe)
            collection.recipes.saveRecipe(recipe.copy(id = "kalt", name = "Kalt", isDefault = false, position = 1))
        }
    }

    private fun show(recipeId: String? = "kyusu") {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                RecipeEditorRoute(
                    onClose = { closed++ },
                    onSave = { saved += it },
                    onDelete = { deleted++ },
                    viewModel = viewModel {
                        RecipeEditorViewModel(
                            SavedStateHandle(
                                buildMap {
                                    put(RecipeEditorViewModel.TEA_ID, "tea")
                                    recipeId?.let { put(RecipeEditorViewModel.RECIPE_ID, it) }
                                },
                            ),
                            collection.recipes,
                            collection.teas,
                            collection.vessels,
                            FakeSettingsRepository(),
                        )
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun stored() = runBlocking { collection.recipes.getRecipe("kyusu") }!!

    @Test
    fun `a renamed recipe is stored on Speichern`() {
        show()
        compose.onNodeWithText("Rezept bearbeiten").assertIsDisplayed()
        compose.onNodeWithTag(RecipeEditorTags.NAME).performTextReplacement("Kyusu, morgens")
        compose.onNodeWithTag(RecipeEditorTags.SAVE).performClick()
        compose.waitForIdle()
        assertThat(saved).containsExactly("kyusu")
        assertThat(stored().name).isEqualTo("Kyusu, morgens")
    }

    @Test
    fun `an empty name is refused at the field after Speichern`() {
        show(recipeId = null)
        compose.onNodeWithText("Neues Rezept").assertIsDisplayed()
        compose.onNodeWithText("Gib dem Rezept einen Namen.").assertDoesNotExist()
        compose.onNodeWithTag(RecipeEditorTags.SAVE).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Gib dem Rezept einen Namen.").assertIsDisplayed()
        assertThat(saved).isEmpty()
    }

    @Test
    fun `a vessel sets the water and scales the leaf, and the ratio follows`() {
        show()
        compose.onNodeWithTag(RecipeEditorTags.RATIO).assertTextContains("2,0", substring = true)
        compose.onNodeWithTag(RecipeEditorTags.VESSEL).performClick()
        compose.onNodeWithText("Kyusu 180 ml").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(RecipeEditorTags.WATER).assertTextContains("180", substring = true)
        compose.onNodeWithTag(RecipeEditorTags.LEAF).assertTextContains("3,6", substring = true)

        compose.onNodeWithTag(RecipeEditorTags.LEAF).performTextReplacement("5,4")
        compose.onNodeWithTag(RecipeEditorTags.RATIO).assertTextContains("3,0", substring = true)
    }

    @Test
    fun `a step dragged by its handle below the next one takes its place`() {
        show()
        compose.onNodeWithTag(RecipeStepTags.kind(1)).performScrollTo().assertTextContains("1 · Aufguss")
        compose.onNodeWithTag(RecipeStepTags.handle(1)).performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 40f))
            repeat(STEPS_OF_DRAG) { moveBy(androidx.compose.ui.geometry.Offset(0f, 60f)) }
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag(RecipeStepTags.kind(1)).assertTextContains("1 · Spülen")
        compose.onNodeWithTag(RecipeStepTags.kind(2)).assertTextContains("2 · Aufguss")
        compose.onNodeWithTag(RecipeEditorTags.SAVE).performClick()
        compose.waitForIdle()
        assertThat(stored().steps.map { it.kind }).containsExactly(StepKind.RINSE, StepKind.INFUSION).inOrder()
    }

    @Test
    fun `TalkBack moves a step with the handle's actions`() {
        show()
        val handle = compose.onNodeWithContentDescription("Verschieben, Schritt 2").performScrollTo()
        val actions = handle.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        // The last step can only move up.
        assertThat(actions.map { it.label }).containsExactly("Nach oben verschieben")
        compose.runOnIdle { actions.single().action() }
        compose.waitForIdle()
        compose.onNodeWithTag(RecipeStepTags.kind(1)).assertTextContains("1 · Spülen")
    }

    @Test
    fun `a step's kind is chosen from its menu, and the only brewing step can't rinse`() {
        show()
        compose.onNodeWithTag(RecipeStepTags.kind(1)).performScrollTo().performClick()
        compose.onNodeWithText("Spülen").assertIsNotEnabled()
        compose.onNodeWithText("Aufschlagen").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(RecipeStepTags.kind(1)).assertTextContains("1 · Aufschlagen")
        // The rinse can't be deleted away from the only brewing step; the brewing step can't go.
        compose.onNodeWithContentDescription("Löschen, Schritt 1").assertIsNotEnabled()
    }

    @Test
    fun `each step's Vorwarnung is chosen as in the Schritte-Sheet`() {
        show()
        // 1:00 at the settings' 10 s: Automatisch gives 10 s.
        compose.onNodeWithTag(RecipeStepTags.preAlert(1)).performScrollTo()
            .assertContentDescriptionEquals("Vorwarnung, Schritt 1: Auto · 10 s. Ändern")
            .performClick()
        compose.onNodeWithText("Aus").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(RecipeStepTags.preAlert(1))
            .assertContentDescriptionEquals("Vorwarnung, Schritt 1: Aus. Ändern")
        compose.onNodeWithTag(RecipeEditorTags.SAVE).performClick()
        compose.waitForIdle()
        assertThat(stored().steps.first().preAlertSeconds).isEqualTo(0)
    }

    @Test
    fun `a step is added with the timer's default and Automatisch`() {
        show()
        compose.onNodeWithTag(RecipeStepTags.ADD).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(RecipeStepTags.kind(3)).performScrollTo().assertTextContains("3 · Aufguss")
        compose.onNodeWithTag(RecipeStepTags.preAlert(3)).assertContentDescriptionContains("Auto", substring = true)
    }

    @Test
    fun `closing with changes asks first, and Verwerfen closes without saving`() {
        show()
        compose.onNodeWithContentDescription("Schließen").performClick()
        assertThat(closed).isEqualTo(1)

        compose.onNodeWithTag(RecipeEditorTags.NAME).performTextReplacement("Anders")
        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.onNodeWithText("Änderungen verwerfen?").assertIsDisplayed()
        compose.onNodeWithText("Verwerfen").performClick()
        compose.waitForIdle()
        assertThat(closed).isEqualTo(2)
        assertThat(stored().name).isEqualTo("Kyusu")
    }

    @Test
    fun `Loeschen asks first and deletes the recipe, the default passing on`() {
        show()
        compose.onNodeWithTag(RecipeEditorTags.MORE).performClick()
        compose.onNodeWithText("Rezept löschen").performClick()
        compose.onNodeWithText("Rezept löschen?").assertIsDisplayed()
        compose.onNodeWithText("Löschen").performClick()
        compose.waitForIdle()
        assertThat(deleted).isEqualTo(1)
        assertThat(runBlocking { collection.recipes.getRecipe("kyusu") }).isNull()
        assertThat(runBlocking { collection.recipes.getRecipe("kalt") }!!.isDefault).isTrue()
    }

    @Test
    fun `the last recipe can't be deleted`() {
        runBlocking { collection.recipes.deleteRecipe("kalt") }
        show()
        compose.onNodeWithTag(RecipeEditorTags.MORE).performClick()
        compose.onNodeWithText("Rezept löschen").assertIsNotEnabled()
        compose.onNodeWithText("Ein Tee braucht mindestens ein Rezept.").assertIsDisplayed()
    }

    private companion object {
        const val STEPS_OF_DRAG = 6
    }
}
