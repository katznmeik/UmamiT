package com.umamit.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
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
import com.umamit.core.ui.SHEET_STAND_IN_TAG
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import kotlin.math.abs
import android.graphics.Color as AndroidColor
import com.umamit.feature.brew.setup.BrewSetupRoute as BrewSetupScreenRoute

/**
 * The picker's hand-over to the setup (03 SCR-03, D-129, #331): after a tea row's tap, Start is never the only
 * thing on screen. In every frame until the setup's enter has finished (the shell has left the composition), the
 * sheet or its frozen stand-in still covers Start, and the setup opens in the frames right after the tap. The real
 * root, shell and NavHost wiring with the real Start tab and setup on fakes, with the calm (not reduced) motion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
// Real graphics: the frozen frame is drawn from the sheet's window as on a device.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
                    timerScreen = { _, _, _, _ -> Text("Timer") },
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

        tapTea()
        var frames = 0
        var setupSeenAt = -1
        var standInFrames = 0
        var sheetGoneAt = -1
        while (startComposed()) {
            assertWithMessage("frame $frames after the tap: the sheet or its stand-in covers Start")
                .that(sheetOrStandIn())
                .isTrue()
            if (sheetGoneAt < 0 && !exists(hasTestTag(TEA_PICKER_TAG))) {
                sheetGoneAt = frames
                // Two windows never change in one frame: the stand-in was drawn below the sheet before it went.
                assertWithMessage("frames the stand-in was drawn below the sheet").that(standInFrames).isAtLeast(2)
            }
            if (sheetGoneAt < 0 && exists(hasTestTag(STAND_IN_TAG))) standInFrames++
            compose.mainClock.advanceTimeByFrame()
            frames++
            if (setupSeenAt < 0 && exists(hasText(SETUP_TITLE))) setupSeenAt = frames
            assertWithMessage("the setup's enter ends").that(frames).isLessThan(MAX_FRAMES)
        }
        // Navigation is never delayed (rule 8): the setup is composed in the frames right after the tap.
        assertWithMessage("frame the setup was first composed").that(setupSeenAt).isIn(1..2)
        assertWithMessage("frame the sheet went").that(sheetGoneAt).isAtLeast(0)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(SETUP_TITLE).assertIsDisplayed()
        compose.onNodeWithTag(TEA_PICKER_TAG).assertDoesNotExist()
        compose.onNodeWithTag(STAND_IN_TAG).assertDoesNotExist()

        // Back from the setup returns to Start; the picker is not reopened.
        back()
        compose.onNodeWithText(START_BREW).assertIsDisplayed()
        compose.onNodeWithTag(TEA_PICKER_TAG).assertDoesNotExist()
        compose.onNodeWithTag(STAND_IN_TAG).assertDoesNotExist()
    }

    @Test
    fun `an alert during the hand-over removes the sheet and its stand-in in its first frame (rule 8, D-088)`() {
        show()
        openPickerAndScrollTo(TEA)
        compose.mainClock.autoAdvance = false
        tapTea()
        compose.mainClock.advanceTimeByFrame()
        assertWithMessage("the stand-in is drawn").that(exists(hasTestTag(STAND_IN_TAG))).isTrue()
        assertWithMessage("the sheet is still held").that(exists(hasTestTag(TEA_PICKER_TAG))).isTrue()

        alertKey.value = "s1:0:1"
        compose.mainClock.advanceTimeByFrame()
        assertWithMessage("the sheet in the alert's first frame").that(exists(hasTestTag(TEA_PICKER_TAG))).isFalse()
        assertWithMessage("the stand-in in the alert's first frame").that(exists(hasTestTag(STAND_IN_TAG))).isFalse()

        // Nothing comes back, neither after the alert nor on Start.
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        alertKey.value = null
        compose.waitForIdle()
        compose.onNodeWithTag(TEA_PICKER_TAG).assertDoesNotExist()
        compose.onNodeWithTag(STAND_IN_TAG).assertDoesNotExist()
    }

    @Test
    fun `back during the setup's enter returns to Start without the sheet or its stand-in (SCR-03)`() {
        show()
        openPickerAndScrollTo(TEA)
        compose.mainClock.autoAdvance = false
        tapTea()
        repeat(HALF_FADE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        assertWithMessage("the stand-in mid-fade").that(exists(hasTestTag(STAND_IN_TAG))).isTrue()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertWithMessage("the stand-in once back pops the setup").that(exists(hasTestTag(STAND_IN_TAG))).isFalse()

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(START_BREW).assertIsDisplayed()
        compose.onNodeWithText(SETUP_TITLE).assertDoesNotExist()
        compose.onNodeWithTag(TEA_PICKER_TAG).assertDoesNotExist()
        compose.onNodeWithTag(STAND_IN_TAG).assertDoesNotExist()
    }

    @Test
    fun `the stand-in shows the sheet where it stood (D-129)`() {
        show()
        openPickerAndScrollTo(TEA)
        compose.mainClock.autoAdvance = false
        repeat(SETTLE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val sheet = draw(ShadowDialog.getLatestDialog().window!!.decorView)
        val start = draw(compose.activity.window.decorView)
        tapTea()
        compose.mainClock.advanceTimeByFrame()
        // The activity's window alone, as it shows once the sheet's window has gone: the setup's fade has just begun.
        val standIn = draw(compose.activity.window.decorView)

        assertWithMessage("share of the sheet's pixels Start shows instead (the measure tells them apart)")
            .that(differingShare(sheet, start))
            .isGreaterThan(MAX_DIFFERING_SHARE * 5)
        assertWithMessage("share of the sheet's pixels the stand-in does not show")
            .that(differingShare(sheet, standIn))
            .isAtMost(MAX_DIFFERING_SHARE)
    }

    /** Over the sheet's lower half (its rows and buttons, where Start shows only its background), pixel by pixel. */
    private fun differingShare(sheet: Bitmap, other: Bitmap): Double {
        var differing = 0
        var compared = 0
        for (y in sheet.height / 2 until sheet.height step GRID) {
            for (x in 0 until sheet.width step GRID) {
                compared++
                if (distance(sheet.getPixel(x, y), other.getPixel(x, y)) > PIXEL_TOLERANCE) differing++
            }
        }
        return differing.toDouble() / compared
    }

    private fun draw(view: View): Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    private fun distance(a: Int, b: Int): Int = maxOf(
        abs(AndroidColor.red(a) - AndroidColor.red(b)),
        abs(AndroidColor.green(a) - AndroidColor.green(b)),
        abs(AndroidColor.blue(a) - AndroidColor.blue(b)),
    )

    private fun tapTea() {
        compose.onNode(hasText(TEA) and hasAnyAncestor(hasTestTag(TEA_PICKER_TAG))).performClick()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private companion object {
        const val START_BREW = "Aufguss starten"
        const val SETUP_TITLE = "Neue Session"
        const val TEA = "Bio Sencha Hisui"

        /** The frozen frame of the sheet in the activity's window (D-129). */
        const val STAND_IN_TAG = SHEET_STAND_IN_TAG

        /** Far more than the 300 ms fade at 16 ms per frame. */
        const val MAX_FRAMES = 120

        /** About half of the setup's 300 ms fade. */
        const val HALF_FADE_FRAMES = 9

        /** Frames for the opened sheet to settle before it is drawn. */
        const val SETTLE_FRAMES = 3

        /** Every how many pixels the frames are compared. */
        const val GRID = 8

        /** The largest channel difference still counted as the same pixel. */
        const val PIXEL_TOLERANCE = 12

        /** The share of compared pixels allowed to differ: the pressed row's ripple, edges. */
        const val MAX_DIFFERING_SHARE = 0.01
    }
}
