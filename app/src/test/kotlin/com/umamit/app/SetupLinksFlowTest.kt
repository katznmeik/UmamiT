package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import com.umamit.feature.collection.detail.TEA_DETAIL_BREW_TAG
import com.umamit.feature.collection.detail.TEA_DETAIL_TAG
import com.umamit.feature.collection.detail.TeaDetailHost
import com.umamit.feature.collection.detail.TeaDetailRoute
import com.umamit.feature.collection.detail.TeaDetailViewModel
import com.umamit.feature.teaware.VesselEditorTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
 * The setup's links of v0.4.0 as routes of the root (SCR-04, D-100; seam 3): ⋮ *Tee ansehen* opens the tea's
 * detail (SCR-09) above the setup and back returns to it unchanged; *Neues Gefäß …* opens the vessel editor
 * (SCR-14) and *Speichern* returns with the new vessel selected and scaled (WARE-02, WARE-03). The real root,
 * the real setup, detail and editor view models on fakes; an invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SetupLinksFlowTest {

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
    }

    private fun show() {
        val photos = PhotoFiles(folder.newFolder("photos"))
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(sessions, collection.teas),
                    alertKey = alertKey,
                    startScreen = { tab ->
                        TextButton(onClick = { tab.navigation.openSetup(ID) }) { Text(BREW) }
                    },
                    collectionScreens = CollectionStackScreens(tea = { host -> Detail(host, photos) }),
                    setupScreen = { host -> Setup(host) },
                    settings = testSettingsScreens(collection),
                    timerScreen = { _, _, _, _ -> Text(TIMER) },
                )
            }
        }
        compose.waitForIdle()
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

    /** The real setup route with every link of its host. */
    @Composable
    private fun Setup(host: SetupHost) {
        BrewSetupScreenRoute(
            onBack = host.onBack,
            onOpenTimer = host.onOpenTimer,
            onOpenCatalog = host.onOpenCatalog,
            onOpenTea = host.onOpenTea,
            onNewVessel = host.onNewVessel,
            newVesselId = host.newVesselId,
            onConsumeNewVessel = host.onConsumeNewVessel,
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

    private fun openSetup() {
        compose.onNodeWithText(BREW).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Brühen starten").assertExists()
    }

    private fun openTeaFromSetup() {
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNode(hasText("Tee ansehen") and hasAnyAncestor(isPopup())).performClick()
        compose.waitForIdle()
    }

    private fun moreWater() {
        compose.onNodeWithContentDescription("Mehr Wasser").performClick()
        compose.waitForIdle()
    }

    private fun assertAmounts(water: String, leaf: String) {
        compose.onNode(hasText("Wasser") and hasText(water)).assertExists()
        compose.onNode(hasText("Blätter") and hasText(leaf)).assertExists()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `Tee ansehen opens the tea's detail above the setup, back returns to the setup unchanged (SCR-04, D-100)`() {
        show()
        openSetup()
        moreWater()
        assertAmounts("255 ml", "5,1 g")

        openTeaFromSetup()
        compose.onNodeWithTag(TEA_DETAIL_TAG).assertIsDisplayed()
        compose.onNodeWithText("Brühen starten").assertDoesNotExist()

        back()

        compose.onNodeWithTag(TEA_DETAIL_TAG).assertDoesNotExist()
        compose.onNodeWithText("Brühen starten").assertExists()
        assertAmounts("255 ml", "5,1 g")
        compose.onNodeWithContentDescription("Gefäß: Kyusu 250 ml. Ändern").assertExists()
    }

    @Test
    fun `Bruehen on that detail opens a new setup in place of the old one (D-100)`() {
        show()
        openSetup()
        moreWater()
        openTeaFromSetup()

        compose.onNodeWithTag(TEA_DETAIL_BREW_TAG).performClick()
        compose.waitForIdle()

        // The default recipe's values, not the changed draft.
        assertAmounts("250 ml", "5,0 g")
        back()
        compose.onNodeWithText(BREW).assertIsDisplayed()
    }

    @Test
    fun `an alert replaces the detail opened from the setup with the timer (rule 8)`() {
        show()
        openSetup()
        openTeaFromSetup()

        alertKey.value = "brew-1"
        compose.waitForIdle()

        compose.onNodeWithTag(TEA_DETAIL_TAG).assertDoesNotExist()
        compose.onNodeWithText(TIMER).assertIsDisplayed()
    }

    @Test
    fun `Neues Gefaess opens the vessel editor and returns with the new vessel selected and scaled (WARE-02)`() {
        show()
        openSetup()
        compose.onNodeWithContentDescription("Gefäß: Kyusu 250 ml. Ändern").performClick()
        compose.onNode(hasText("Neues Gefäß …") and hasAnyAncestor(isPopup())).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Neues Gefäß").assertIsDisplayed()

        compose.onNodeWithTag(VesselEditorTags.NAME).performTextInput("Gaiwan")
        compose.onNodeWithTag(VesselEditorTags.CAPACITY).performTextInput("120")
        compose.onNodeWithTag(VesselEditorTags.SAVE).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Brühen starten").assertExists()
        compose.onNodeWithContentDescription("Gefäß: Gaiwan. Ändern").assertExists()
        // 5,0 g / 250 ml, locked: 2,4 g in 120 ml (WARE-03).
        assertAmounts("120 ml", "2,4 g")
        // Taken once: back from the setup and in again starts from the recipe.
        back()
        openSetup()
        compose.onNodeWithContentDescription("Gefäß: Kyusu 250 ml. Ändern").assertExists()
    }

    @Test
    fun `leaving the vessel editor without saving returns to the setup unchanged (WARE-02)`() {
        show()
        openSetup()
        moreWater()
        compose.onNodeWithContentDescription("Gefäß: Kyusu 250 ml. Ändern").performClick()
        compose.onNode(hasText("Neues Gefäß …") and hasAnyAncestor(isPopup())).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Gefäß: Kyusu 250 ml. Ändern").assertExists()
        assertAmounts("255 ml", "5,1 g")
        assertThat(runBlocking { collection.vessels.observeVessels().first() }.map { it.name })
            .containsExactly("Kyusu 250 ml")
    }

    @Test
    fun `Archivieren on that detail closes the detail and the setup (D-100)`() {
        show()
        openSetup()
        openTeaFromSetup()

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Archivieren").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TEA_DETAIL_TAG).assertDoesNotExist()
        compose.onNodeWithText("Brühen starten").assertDoesNotExist()
        compose.onNodeWithText(BREW).assertIsDisplayed()
        assertThat(runBlocking { collection.teas.getTea(ID)!!.isArchived }).isTrue()
    }

    private companion object {
        const val ID = "morgennebel"
        const val BREW = "Morgennebel brühen"
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
