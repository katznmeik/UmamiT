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
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.feature.collection.detail.TeaDetailHost
import com.umamit.feature.collection.recipe.RecipeEditorRoute
import com.umamit.feature.collection.recipe.RecipeEditorTags
import com.umamit.feature.collection.recipe.RecipeEditorViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tea detail's REZEPTE in the Sammlung tab (SCR-09 → SCR-12, PRF-01): a recipe's pencil opens its editor, ✕ and
 * *Speichern* return to the detail; *Rezept hinzufügen* opens a new recipe. The real root and shell navigation and the
 * real editor on fakes; stand-ins for the list and the detail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class RecipeEditorFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)

    private fun show() {
        runBlocking {
            collection.teas.saveTea(Tea(TEA, "Asahi", TeaCategory.GREEN, TeaForm.LOOSE_LEAF))
            collection.recipes.saveRecipe(
                Recipe(
                    id = RECIPE,
                    teaId = TEA,
                    name = "Kyusu",
                    method = BrewMethod.KYUSU,
                    leafGrams = 5.0,
                    waterMl = 250,
                    baseTempC = 70,
                    steps = listOf(RecipeStep(StepKind.INFUSION, 60)),
                    isDefault = true,
                ),
            )
        }
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(sessions, collection.teas),
                    alertKey = MutableStateFlow(null),
                    collectionScreens = CollectionStackScreens(
                        myTeas = { tab -> MyTeas(tab) },
                        tea = { host -> Detail(host) },
                        recipeEditor = { host -> Editor(host) },
                    ),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(TEA).performClick()
        compose.waitForIdle()
    }

    @Composable
    private fun MyTeas(tab: MyTeasTab) {
        Column(tab.modifier) { TextButton(onClick = { tab.openTea(TEA) }) { Text(TEA) } }
    }

    /** A stand-in for the tea detail (SCR-09): a recipe's pencil and *Rezept hinzufügen*. */
    @Composable
    private fun Detail(host: TeaDetailHost) {
        Column {
            Text(DETAIL)
            TextButton(onClick = { host.onEditRecipe(RECIPE) }) { Text(EDIT) }
            TextButton(onClick = host.onAddRecipe) { Text(ADD) }
        }
    }

    @Composable
    private fun Editor(host: RecipeEditorHost) {
        RecipeEditorRoute(
            onClose = host.onClose,
            onSave = { host.onDone() },
            onDelete = host.onDone,
            viewModel = viewModel {
                RecipeEditorViewModel(
                    createSavedStateHandle(),
                    collection.recipes,
                    collection.teas,
                    collection.vessels,
                    settings,
                )
            },
        )
    }

    @Test
    fun `a recipe's pencil opens its editor, and closing and Speichern return to the detail`() {
        show()
        compose.onNodeWithText(EDIT).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Rezept bearbeiten").assertIsDisplayed()
        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(DETAIL).assertIsDisplayed()

        compose.onNodeWithText(EDIT).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(RecipeEditorTags.NAME).performTextReplacement("Kyusu, morgens")
        compose.onNodeWithTag(RecipeEditorTags.SAVE).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(DETAIL).assertIsDisplayed()
        assertThat(runBlocking { collection.recipes.getRecipe(RECIPE) }!!.name).isEqualTo("Kyusu, morgens")
    }

    @Test
    fun `Rezept hinzufuegen opens a new recipe, stored on Speichern`() {
        show()
        compose.onNodeWithText(ADD).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Neues Rezept").assertIsDisplayed()
        compose.onNodeWithTag(RecipeEditorTags.NAME).performTextInput("Kaltaufguss")
        compose.onNodeWithTag(RecipeEditorTags.SAVE).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(DETAIL).assertIsDisplayed()
        val names = runBlocking { collection.recipes.observeRecipes(TEA).first() }.map { it.name }
        assertThat(names).containsExactly("Kyusu", "Kaltaufguss").inOrder()
    }

    private companion object {
        const val TEA = "asahi"
        const val RECIPE = "kyusu"
        const val DETAIL = "Detail"
        const val EDIT = "Rezept bearbeiten öffnen"
        const val ADD = "Rezept hinzufügen"
    }
}
