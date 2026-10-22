package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SeedData
import com.umamit.core.data.SessionStart
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.feature.brew.timer.TimerScreen
import com.umamit.feature.home.HomeActions
import com.umamit.feature.home.HomeRoute
import com.umamit.feature.home.HomeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * *Nochmal brühen* in two taps (BRW-11): the tea on Start opens the timer in
 * READY with the last session's parameters, then *Start* starts the step. Real
 * Start view model and root navigation, fake repositories and controller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class BrewAgainFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val starter = FakeBrewStarter()
    private val events = mutableListOf<TimerEvent>()
    private val clock = object : MonotonicClock {
        override fun elapsedMs() = 0L

        override fun bootCount() = 1
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

    @Test
    fun `a known tea brews again in two taps - the tea, then Start`() {
        seed()
        val viewModel = HomeViewModel(
            collection.teas,
            sessions.sessions,
            starter,
            clock,
            PhotoFiles(folder.newFolder("photos")),
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    alertKey = MutableStateFlow(null),
                    timerScreen = { onClose, _, _ ->
                        val state by starter.state.collectAsStateWithLifecycle()
                        (state as? TimerUiState.Active)?.let { active ->
                            TimerScreen(
                                state = active,
                                nowElapsedMs = { 0L },
                                onEvent = { events += it },
                                onBack = onClose,
                                onAdjustBrew = {},
                                onOpenTimerSettings = {},
                            )
                        }
                    },
                    startScreen = { tab ->
                        HomeRoute(
                            actions = HomeActions(openSettings = tab.openSettings),
                            onOpenTimer = tab.navigation.openTimer,
                            viewModel = viewModel,
                        )
                    },
                )
            }
        }

        // Tap 1: the tea under *Nochmal brühen*.
        compose.onNodeWithContentDescription("Nochmal brühen: Bio Sencha Hisui").performClick()
        compose.waitForIdle()

        val ready = starter.state.value as TimerUiState.Active
        assertThat(ready.phase).isEqualTo(TimerPhase.READY)
        assertThat(ready.steps.map { it.plannedSeconds }).containsExactly(60, 10, 10).inOrder()
        assertThat(starter.starts.single().snapshot.leafGrams).isEqualTo(5.0)
        compose.onNodeWithText("Wasserkocher auf 65 °C").assertIsDisplayed()

        // Tap 2: *Start*.
        compose.onNodeWithText("Start").performClick()

        assertThat(events).containsExactly(TimerEvent.Start)
    }
}
