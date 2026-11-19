package com.umamit.feature.collection.receive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.recipe.RecipeRefusal
import com.umamit.core.data.catalog.TeaLinkOutcome
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TeaCategory
import com.umamit.feature.collection.R
import com.umamit.feature.collection.receive.ReceivedRecipes.kyusu
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SCR-32's controls (03 SCR-32, PRF-08): the radio group, *Anderen Tee wählen …*, the shop link and the refusal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ReceiveRecipeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val chosen = mutableListOf<TeaChoice>()
    private val picked = mutableListOf<String>()
    private var takeOvers = 0
    private var shopLinks = 0
    private var closes = 0

    private val sencha = TeaOption(id = "s", name = "Sencha Morgen", category = TeaCategory.GREEN, isArchived = true)
    private val full = TeaOption(id = "f", name = "Volle Dose", category = TeaCategory.GREEN, recipeCount = 10)

    private val sheets = mutableListOf<PackageSheetRequest>()
    private var closedSheets = 0

    private fun show(state: ReceiveRecipeUiState, alertKey: () -> String? = { null }) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                ReceiveRecipeScreen(
                    state = state,
                    actions = ReceiveRecipeActions(
                        close = { closes++ },
                        choose = { chosen += it },
                        pick = { picked += it },
                        loadShopLink = { shopLinks++ },
                        takeOver = { takeOvers++ },
                        linkMessage = { R.string.receive_recipe_full },
                        closePackageSheet = { closedSheets++ },
                    ),
                    alertKey = alertKey(),
                    photo = { null },
                    packageSheet = { sheets += it },
                )
            }
        }
    }

    @Test
    fun rezeptUebernehmenWaitsForATeaAndNeuenTeeAnlegenChoosesIt() {
        show(ReceiveRecipeUiState.Ready(recipe = kyusu, teas = listOf(sencha)))

        compose.onNodeWithTag(ReceiveRecipeTags.TAKE_OVER).assertIsNotEnabled()
        compose.onNodeWithText("Diesen Tee hast du noch nicht.").assertExists()
        compose.onNodeWithTag(ReceiveRecipeTags.NEW_TEA).performClick()

        assertThat(chosen).containsExactly(TeaChoice.NewTea)
    }

    @Test
    fun theMatchIsSelectedAndRezeptUebernehmenRuns() {
        val match = sencha.copy(isArchived = false)
        show(
            ReceiveRecipeUiState.Ready(
                recipe = kyusu,
                match = match,
                matchKind = MatchKind.NAME,
                choice = TeaChoice.Existing("s"),
                teas = listOf(match),
            ),
        )

        compose.onNodeWithTag(ReceiveRecipeTags.tea("s")).assertIsSelected()
        compose.onNodeWithText("gleicher Name").assertExists()
        compose.onNodeWithTag(ReceiveRecipeTags.TAKE_OVER).assertIsEnabled().performClick()

        assertThat(takeOvers).isEqualTo(1)
    }

    @Test
    fun theSheetListsArchivedTeasAndGreysOutAFullOne() {
        show(ReceiveRecipeUiState.Ready(recipe = kyusu, teas = listOf(sencha, full)))

        compose.onNodeWithTag(ReceiveRecipeTags.OTHER_TEA).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Höchstens 10 Rezepte").assertExists()
        compose.onNodeWithTag(ReceiveRecipeTags.sheetTea("f")).performClick()
        compose.onNodeWithTag(ReceiveRecipeTags.sheetTea("s")).performClick()
        compose.waitForIdle()

        assertThat(picked).containsExactly("s")
    }

    @Test
    fun theShopLinkShowsOnlyWhenOfferedAndItsFailure() {
        show(
            ReceiveRecipeUiState.Ready(
                recipe = kyusu,
                offersShopLink = true,
                linkFailure = TeaLinkOutcome.Offline,
            ),
        )

        compose.onNodeWithText("Lädt eine Seite von tee-kontor-kiel.de").assertExists()
        compose.onNodeWithTag(ReceiveRecipeTags.SHOP_LINK).performClick()

        assertThat(shopLinks).isEqualTo(1)
        // The message the catalog hands in (here a stand-in).
        compose.onNodeWithText("Höchstens 10 Rezepte").assertExists()
    }

    @Test
    fun aRefusedFileShowsItsMessageAndSchliessenOnly() {
        show(ReceiveRecipeUiState.Refused(RecipeRefusal.DAMAGED))

        compose.onNodeWithText("Die Rezeptdatei ist beschädigt.").assertExists()
        compose.onNodeWithTag(ReceiveRecipeTags.TAKE_OVER).assertDoesNotExist()
        compose.onNodeWithTag(ReceiveRecipeTags.CLOSE_REFUSED).performClick()

        assertThat(closes).isEqualTo(1)
    }

    @Test
    fun neuenTeeAnlegenIsHiddenWhenTheFileKeysTeaIsInTheCollection() {
        val match = sencha.copy(catalogKey = "XY0170")
        show(
            ReceiveRecipeUiState.Ready(
                recipe = kyusu,
                match = match,
                matchKind = MatchKind.CATALOG,
                choice = TeaChoice.Existing("s"),
                teas = listOf(match),
                catalogKey = "XY0170",
                offersNewTea = false,
            ),
        )

        compose.onNodeWithTag(ReceiveRecipeTags.NEW_TEA).assertDoesNotExist()
        compose.onNodeWithTag(ReceiveRecipeTags.SHOP_LINK).assertDoesNotExist()
        compose.onNodeWithTag(ReceiveRecipeTags.tea("s")).assertIsSelected()
    }

    @Test
    fun thePackageSheetShowsThroughTheHostAndAnotherAlertRemovesIt() {
        var alert by mutableStateOf<String?>(null)
        show(
            ReceiveRecipeUiState.Ready(
                recipe = kyusu,
                choice = TeaChoice.NewTea,
                catalogKey = "XY0170",
                packageSheet = PackageSheet("XY0170"),
            ),
            alertKey = { alert },
        )
        compose.waitForIdle()
        assertThat(sheets.last().catalogKey).isEqualTo("XY0170")
        assertThat(closedSheets).isEqualTo(0)

        val shown = sheets.size
        alert = "slot-1"
        compose.waitForIdle()

        // Rule 8: the sheet is no longer composed and the view model is told to close it.
        assertThat(closedSheets).isEqualTo(1)
        assertThat(sheets.size).isEqualTo(shown)
    }
}
