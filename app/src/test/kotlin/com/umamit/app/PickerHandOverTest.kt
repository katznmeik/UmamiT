package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SeedData
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeCollectionList
import com.umamit.core.testing.FakeLowStockRepository
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.feature.brew.picker.TEA_PICKER_TAG
import com.umamit.feature.brew.picker.TeaPickerViewModel
import com.umamit.feature.brew.setup.BrewSetupViewModel
import com.umamit.feature.home.HomeViewModel
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
 * The picker's hand-over to the setup (03 SCR-03, D-129, #331): after a tea row's tap, Start is never the only
 * thing on screen. In every frame until the setup's enter has finished (the shell has left the composition), the
 * sheet or its frozen stand-in still covers Start, and the setup opens in the frames right after the tap. The real
 * root, shell and NavHost wiring with the real Start tab and setup on fakes, with the calm (not reduced) motion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class PickerHandOverTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)
    private val starter = FakeBrewStarter()
    private val catalog = FakeCatalogRepository(initial = CatalogOverview(emptyList(), lastRefreshAtMillis = 1L))
    private val alertKey = MutableStateFlow<String?>(null)

    @Before
    fun seed() = runBlocking {
        SeedData.teas.forEach { collection.teas.saveTea(it) }
        SeedData.vessels.forEach { collection.vessels.saveVessel(it) }
        SeedData.recipes.forEach { collection.recipes.saveRecipe(it) }
    }

    private fun show() {
        val photos = PhotoFiles(folder.newFolder("photos"))
        compose.setContent {
            UmamiTheme(reduceMotion = false) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(sessions, collection.teas),
                    alertKey = alertKey,
                    startScreen = { tab -> Start(tab, photos) },
                    setupScreen = { host -> Setup(host.onBack, host.onOpenTimer) },
                )
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Start(tab: StartTab, photos: PhotoFiles) {
        StartTabContent(
            tab = tab,
            homeViewModel = viewModel {
                HomeViewModel(
                    collection.teas,
                    sessions.sessions,
                    starter,
                    FakeClock(elapsed = 0L),
                    photos,
                    FakeTimerReliability(),
                    FakeLowStockRepository(),
                )
            },
            pickerViewModel = viewModel {
                TeaPickerViewModel(
                    collection.teas,
                    sessions.sessions,
                    FakeTeaArchive(sessions),
                    photos,
                    FakeCollectionList(sessions),
                )
            },
        )
    }

    @Composable
    private fun Setup(onBack: () -> Unit, onOpenTimer: (TimerSlot?) -> Unit) {
        BrewSetupScreenRoute(
            onBack = onBack,
            onOpenTimer = onOpenTimer,
            onOpenCatalog = {},
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

    private fun exists(matcher: SemanticsMatcher): Boolean =
        compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Start is composed: the shell has not left yet, so it can still show. */
    private fun startComposed(): Boolean = exists(hasText(START_BREW))

    /** The sheet in its own window, or its frozen stand-in in the activity's window. */
    private fun sheetOrStandIn(): Boolean = exists(hasTestTag(TEA_PICKER_TAG)) || exists(hasTestTag(STAND_IN_TAG))

    private fun openPickerAndScrollTo(tea: String) {
        compose.onNodeWithText(START_BREW).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TEA_PICKER_TAG).assertIsDisplayed()
        compose.onNode(hasText(tea) and hasAnyAncestor(hasTestTag(TEA_PICKER_TAG))).performScrollTo()
        compose.waitForIdle()
    }

    @Test
    fun `a tea row's tap never shows bare Start before the setup has faded in (SCR-03, D-129)`() {
        show()
        openPickerAndScrollTo(TEA)
        compose.mainClock.autoAdvance = false

        compose.onNode(hasText(TEA) and hasAnyAncestor(hasTestTag(TEA_PICKER_TAG))).performClick()
        var frames = 0
        var setupSeenAt = -1
        while (startComposed()) {
            assertWithMessage("frame $frames after the tap: the sheet or its stand-in covers Start")
                .that(sheetOrStandIn())
                .isTrue()
            compose.mainClock.advanceTimeByFrame()
            frames++
            if (setupSeenAt < 0 && exists(hasText(SETUP_TITLE))) setupSeenAt = frames
            assertWithMessage("the setup's enter ends").that(frames).isLessThan(MAX_FRAMES)
        }
        // Navigation is never delayed (rule 8): the setup is composed in the frames right after the tap.
        assertWithMessage("frame the setup was first composed").that(setupSeenAt).isIn(1..2)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(SETUP_TITLE).assertIsDisplayed()
        compose.onNodeWithTag(TEA_PICKER_TAG).assertDoesNotExist()
        compose.onNodeWithTag(STAND_IN_TAG).assertDoesNotExist()
    }

    private companion object {
        const val START_BREW = "Aufguss starten"
        const val SETUP_TITLE = "Neue Session"
        const val TEA = "Bio Sencha Hisui"

        /** The frozen frame of the sheet in the activity's window (D-129). */
        const val STAND_IN_TAG = "sheet_stand_in"

        /** Far more than the 300 ms fade at 16 ms per frame. */
        const val MAX_FRAMES = 120
    }
}
