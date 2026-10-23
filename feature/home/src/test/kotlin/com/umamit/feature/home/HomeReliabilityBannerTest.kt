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
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
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

    private fun show(problem: Boolean) = compose.setContent {
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
        show(problem = true)

        compose.onNodeWithText("Der Timer ist eventuell unzuverlässig").assertIsDisplayed()
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
    }

    @Test
    fun `with every check passing there is no banner`() {
        show(problem = false)

        compose.onNodeWithText("Der Timer ist eventuell unzuverlässig").assertDoesNotExist()
    }
}
