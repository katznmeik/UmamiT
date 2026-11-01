package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
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
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.feature.brew.setup.BrewSetupViewModel
import com.umamit.feature.collection.detail.ArchivedTeaSnackbar
import com.umamit.feature.collection.detail.TEA_DETAIL_BREW_TAG
import com.umamit.feature.collection.detail.TEA_DETAIL_TAG
import com.umamit.feature.collection.detail.TeaDetailHost
import com.umamit.feature.collection.detail.TeaDetailRoute
import com.umamit.feature.collection.detail.TeaDetailViewModel
import com.umamit.feature.collection.detail.teaDetailRecipeTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.umamit.feature.brew.setup.BrewSetupRoute as BrewSetupScreenRoute

/**
 * The tea detail (SCR-09) in the Sammlung tab: *Brühen* opens the setup with the default recipe, a recipe's tap
 * with that recipe, back returns to the detail; *Archivieren* closes it and the list below offers *Rückgängig*.
 * The real root and shell navigation, the real view models on fakes; an invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)
    private val archive = FakeTeaArchive(sessions)
    private val catalog = FakeCatalogRepository()
    private val starter = FakeBrewStarter()
    private val alertKey = MutableStateFlow<String?>(null)

    @Before
    fun seed(): Unit = runBlocking {
        collection.teas.saveTea(TEA)
        collection.vessels.saveVessel(Vessel("kyusu", "Kyusu 250 ml", VesselType.KYUSU, 300, 250))
        collection.recipes.saveRecipe(
            Recipe(
                id = "standard",
                teaId = ID,
                name = "Kyusu",
                method = BrewMethod.KYUSU,
                leafGrams = 5.0,
                waterMl = 250,
                baseTempC = 70,
                steps = listOf(RecipeStep(StepKind.INFUSION, 60)),
                vesselId = "kyusu",
                isDefault = true,
            ),
        )
        collection.recipes.saveRecipe(
            Recipe(
                id = "westlich",
                teaId = ID,
                name = "Westlich",
                method = BrewMethod.WESTERN,
                leafGrams = 4.0,
                waterMl = 400,
                baseTempC = 75,
                steps = listOf(RecipeStep(StepKind.INFUSION, 120)),
                position = 1,
            ),
        )
    }

    private fun show() {
        val photos = PhotoFiles(folder.newFolder("photos"))
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(sessions, collection.teas),
                    alertKey = alertKey,
                    collectionScreens = CollectionStackScreens(
                        myTeas = { tab -> MyTeas(tab) },
                        tea = { host -> Detail(host, photos) },
                        archivedSnackbar = { archived, onDone, modifier ->
                            ArchivedTeaSnackbar(
                                archived = archived,
                                onUndo = { runBlocking { archive.restore(it) } },
                                onDone = onDone,
                                modifier = modifier,
                            )
                        },
                    ),
                    setupScreen = { onBack, onOpenTimer, _ -> Setup(onBack, onOpenTimer) },
                    timerScreen = { _, _, _, _ -> Text(TIMER) },
                )
            }
        }
        compose.waitForIdle()
    }

    /** A stand-in for the *Meine Tees* list (SCR-08, #153): one row that opens the tea. */
    @Composable
    private fun MyTeas(tab: MyTeasTab) {
        Box(tab.modifier) {
            TextButton(onClick = { tab.openTea(ID) }) { Text(ROW) }
        }
    }

    @Composable
    private fun Detail(host: TeaDetailHost, photos: PhotoFiles) {
        TeaDetailRoute(
            host = host,
            viewModel = viewModel {
                TeaDetailViewModel(
                    createSavedStateHandle(),
                    collection.teas,
                    collection.recipes,
                    collection.vessels,
                    collection.stock,
                    sessions.journal,
                    settings,
                    catalog,
                    archive,
                    photos,
                )
            },
        )
    }

    /** The setup of the route's tea and recipe (its saved state). */
    @Composable
    private fun Setup(onBack: () -> Unit, onOpenTimer: () -> Unit) {
        BrewSetupScreenRoute(
            onBack = onBack,
            onOpenTimer = onOpenTimer,
            viewModel = viewModel {
                BrewSetupViewModel(
                    createSavedStateHandle(),
                    collection.teas,
                    collection.recipes,
                    collection.vessels,
                    collection.stock,
                    sessions.sessions,
                    settings,
                    starter,
                    catalog,
                )
            },
        )
    }

    private fun openDetail() {
        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ROW).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TEA_DETAIL_TAG).assertIsDisplayed()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun assertSetupRecipe(name: String) {
        compose.onNodeWithText("Brühen starten").assertExists()
        compose.onNode(hasText(name) and isSelectable()).assertIsSelected()
    }

    @Test
    fun `Bruehen opens the setup with the default recipe, back returns to the detail`() {
        show()
        openDetail()

        compose.onNodeWithTag(TEA_DETAIL_BREW_TAG).performClick()
        compose.waitForIdle()

        assertSetupRecipe("Kyusu")
        back()
        compose.onNodeWithTag(TEA_DETAIL_TAG).assertIsDisplayed()
    }

    @Test
    fun `a recipe's tap opens the setup with that recipe`() {
        show()
        openDetail()

        compose.onNodeWithTag(teaDetailRecipeTag("westlich")).performScrollTo().performClick()
        compose.waitForIdle()

        assertSetupRecipe("Westlich")
    }

    @Test
    fun `during an alert Bruehen never opens a setup over it`() {
        show()
        openDetail()
        alertKey.value = "brew-1"
        compose.waitForIdle()

        compose.onNodeWithTag(TEA_DETAIL_BREW_TAG).performClick()
        compose.waitForIdle()

        // The alerting timer instead (rule 8, D-062 S3).
        compose.onNodeWithText("Brühen starten").assertDoesNotExist()
        compose.onNodeWithText(TIMER).assertIsDisplayed()
    }

    @Test
    fun `Archivieren closes the detail, the list below offers Rueckgaengig`() {
        show()
        openDetail()

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Archivieren").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TEA_DETAIL_TAG).assertDoesNotExist()
        compose.onNodeWithText(ROW).assertIsDisplayed()
        assertThat(runBlocking { collection.teas.getTea(ID)!!.isArchived }).isTrue()
        compose.onNodeWithText("Morgennebel archiviert").assertIsDisplayed()

        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitForIdle()

        assertThat(runBlocking { collection.teas.getTea(ID)!!.isArchived }).isFalse()
    }

    @Test
    fun `Loeschen closes the detail`() {
        show()
        openDetail()

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Löschen").performClick()
        compose.onNodeWithText("Löschen").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TEA_DETAIL_TAG).assertDoesNotExist()
        assertThat(archive.deleted).containsExactly(ID)
    }

    private companion object {
        const val ID = "morgennebel"
        const val ROW = "Morgennebel öffnen"
        const val TIMER = "Timer (Attrappe)"
        val TEA = Tea(
            id = ID,
            name = "Morgennebel",
            category = TeaCategory.GREEN,
            form = TeaForm.LOOSE_LEAF,
            styleKey = "sencha",
            teaType = "Sencha",
        )
    }
}
