package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SeedData
import com.umamit.core.data.SessionStart
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeCollectionList
import com.umamit.core.testing.FakeLowStockRepository
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.feature.brew.picker.TeaPickerViewModel
import com.umamit.feature.home.HomeViewModel
import com.umamit.feature.home.homeRecentTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Start holds still while *Nochmal brühen* opens the timer (SCR-02, D-111): the active brew bar does not grow in
 * under the timer's fade, and on the way back it is already there. The real root, shell and Start tab with its view
 * model on fakes; the bar's source follows the fake controller's starts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class BrewAgainHandOffTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val starter = FakeBrewStarter()
    private val brew = MutableStateFlow<ActiveBrew?>(null)
    private val clock = object : MonotonicClock {
        override fun elapsedMs() = 0L

        override fun bootCount() = 1
    }

    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<ActiveBrew?> = brew
        override val progress: () -> Float = { 0f }
    }

    /** The controller's start publishes the active brew at once, as the real one does. */
    private val publishing = object : BrewStarter by starter {
        override suspend fun startSession(request: BrewStart): TransitionOutcome = starter.startSession(request).also {
            if (it == TransitionOutcome.Accepted) brew.value = HISUI_BAR
        }
    }

    private fun seed() = runBlocking {
        SeedData.teas.forEach { collection.teas.saveTea(it) }
        val snapshot = SessionSnapshot(
            SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", null, "Sencha", "sencha", null, null, "Kyusu",
            BrewMethod.KYUSU, SeedData.KYUSU_VESSEL_ID, "Kyusu", 5.0, 250, 65,
        )
        val steps = listOf(60, 10, 10).map { PlannedStep(StepKind.INFUSION, it, 65) }
        sessions.sessions.startSession(SessionStart("last", snapshot, steps, 10, 1_000, "Europe/Berlin"))
        steps.indices.forEach {
            sessions.sessions.recordStep("last", it, StepRecord(steps[it].plannedSeconds, 1, 0, 0, 1, 2, false, false))
        }
        sessions.sessions.completeSession("last", autoFinished = false)
    }

    @Composable
    private fun Start(tab: StartTab, photos: PhotoFiles) {
        StartTabContent(
            tab = tab,
            homeViewModel = viewModel {
                HomeViewModel(
                    collection.teas,
                    sessions.sessions,
                    publishing,
                    clock,
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

    private fun barShown() =
        compose.onAllNodesWithContentDescription(BAR_TITLE, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the bar does not grow in while the timer opens over Start, and is there on the way back`() {
        seed()
        val photos = PhotoFiles(folder.newFolder("photos"))
        compose.setContent {
            UmamiTheme(reduceMotion = false) {
                UmamitRoot(
                    activeBrewSource = source,
                    journalSources = testJournalSources(sessions),
                    alertKey = MutableStateFlow(null),
                    timerScreen = { _, _, _, _ -> Text(TIMER) },
                    startScreen = { tab -> Start(tab, photos) },
                )
            }
        }
        compose.waitForIdle()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(homeRecentTag(SeedData.HISUI_TEA_ID)).performClick()
        // Every frame of the timer's fade over Start (`fade`, 300 ms) and past it: no bar.
        var timerSeen = false
        repeat(FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            timerSeen = timerSeen || compose.onAllNodesWithText(TIMER).fetchSemanticsNodes().isNotEmpty()
            assertThat(barShown()).isFalse()
        }
        assertThat(timerSeen).isTrue()
        // The timer covers Start: Start has left the screen.
        assertThat(startShown()).isFalse()
        assertThat(brew.value).isEqualTo(HISUI_BAR)
        compose.mainClock.autoAdvance = true

        // Back to Start: in the first frame that shows Start the bar is there at its full height, it does not grow in.
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        var firstHeight: Float? = null
        repeat(FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (firstHeight == null && startShown()) firstHeight = barHeight()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription(BAR_TITLE, substring = true).assertIsDisplayed()
        assertThat(firstHeight).isEqualTo(barHeight())
    }

    private fun startShown() =
        compose.onAllNodesWithTag(homeRecentTag(SeedData.HISUI_TEA_ID)).fetchSemanticsNodes().isNotEmpty()

    private fun barHeight(): Float = compose.onAllNodesWithContentDescription(BAR_TITLE, substring = true)
        .fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.height ?: 0f

    @Test
    fun `a held bar stays away until released, and one shown before stays live`() {
        val held = mutableStateOf(false)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitApp(
                    activeBrewSource = source,
                    journalSources = testJournalSources(),
                    settings = testSettingsScreens(),
                    startScreen = { tab ->
                        DisposableEffect(tab.brewBarHold) {
                            tab.brewBarHold.source = held
                            onDispose { tab.brewBarHold.source = null }
                        }
                        PlaceholderStart(tab)
                    },
                )
            }
        }
        compose.waitForIdle()

        held.value = true
        brew.value = HISUI_BAR
        compose.waitForIdle()
        assertThat(barShown()).isFalse()

        held.value = false
        compose.waitForIdle()
        compose.onNodeWithContentDescription(BAR_TITLE, substring = true).assertIsDisplayed()

        // A bar already shown is not held: it follows the source.
        held.value = true
        brew.value = HISUI_BAR.copy(title = NEXT_TITLE)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(NEXT_TITLE, substring = true).assertIsDisplayed()
    }

    private companion object {
        const val TIMER = "Timer"
        const val BAR_TITLE = "Laufender Tee"
        const val NEXT_TITLE = "Zweiter Tee"

        /** Well past the 300 ms fade at 16 ms a frame. */
        const val FRAMES = 40
        val HISUI_BAR = ActiveBrew(BAR_TITLE, "Aufguss 1/3", 60, Color(0xFF8DA36B))
    }
}
