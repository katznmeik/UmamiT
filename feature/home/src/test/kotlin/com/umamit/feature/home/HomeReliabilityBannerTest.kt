package com.umamit.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeLowStockRepository
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
import com.umamit.core.timer.reliability.ReliabilityProblem
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The reliability banner on Start (TMR-31, SCR-02): shown only while a check is missing, *Beheben* deep-links. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class HomeReliabilityBannerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private var fixes = 0

    private fun show(problem: ReliabilityProblem?) = compose.setContent {
        UmamiTheme(reduceMotion = true) {
            HomeScreen(
                state = HomeUiState(loading = false, reliabilityProblem = problem),
                nowElapsedMs = { 0L },
                actions = HomeActions(fixReliability = { fixes++ }),
            )
        }
    }

    @Test
    fun `a missing check shows the banner, and Beheben opens its setting`() {
        show(problem = ReliabilityProblem.Unreliable)

        compose.onNodeWithText("Der Timer ist eventuell unzuverlässig").assertIsDisplayed()
        compose.onNodeWithText("Beheben").performClick()

        assertThat(fixes).isEqualTo(1)
    }

    @Test
    fun `the alarm volume at 0 shows Alarmlautstaerke ist aus with Beheben`() {
        // TMR-31, D-098: the cues play on the alarm stream.
        show(problem = ReliabilityProblem.AlarmVolumeOff)

        compose.onNodeWithText("Alarmlautstärke ist aus").assertIsDisplayed()
        compose.onNodeWithText("Der Timer ist eventuell unzuverlässig").assertDoesNotExist()
        compose.onNodeWithText("Beheben").performClick()

        assertThat(fixes).isEqualTo(1)
    }

    @Test
    fun `Start reads the checks again on resume, back from the system settings`() {
        val reliability = FakeTimerReliability()
        val collection = FakeCollection()
        val viewModel = HomeViewModel(
            collection.teas,
            FakeSessions(collection).sessions,
            FakeBrewStarter(),
            object : MonotonicClock {
                override fun elapsedMs() = 0L

                override fun bootCount() = 1
            },
            PhotoFiles(folder.newFolder("photos")),
            reliability,
            FakeLowStockRepository(),
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                HomeRoute(actions = HomeActions(), onOpenTimer = {}, viewModel = viewModel)
            }
        }
        compose.onNodeWithText("Der Timer ist eventuell unzuverlässig").assertDoesNotExist()

        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        reliability.set(ReliabilityCheck.ExactAlarms, CheckState.Missing)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        compose.onNodeWithText("Der Timer ist eventuell unzuverlässig").assertIsDisplayed()

        // The alarm volume turned down to 0 in the meantime, the exact alarms granted again.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        reliability.set(ReliabilityCheck.ExactAlarms, CheckState.Ok)
        reliability.set(ReliabilityCheck.AlarmVolume, CheckState.Missing)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithText("Alarmlautstärke ist aus").assertIsDisplayed()

        // Raised again: the warning clears on the next resume.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        reliability.set(ReliabilityCheck.AlarmVolume, CheckState.Ok)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithText("Alarmlautstärke ist aus").assertDoesNotExist()
    }

    @Test
    fun `with every check passing there is no banner`() {
        show(problem = null)

        compose.onNodeWithText("Der Timer ist eventuell unzuverlässig").assertDoesNotExist()
        compose.onNodeWithText("Alarmlautstärke ist aus").assertDoesNotExist()
    }
}
