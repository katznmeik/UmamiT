package com.umamit.feature.brew.timer

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.reliability.ReliabilityProblem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reliability banner on the timer (TMR-31, SCR-05): the general message for a missing check, „Alarmlautstärke
 * ist aus" when the phone's alarm volume is 0 (D-098), each with *Beheben*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerReliabilityBannerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var fixes = 0

    private fun show(problem: ReliabilityProblem?) = compose.setContent {
        UmamiTheme(reduceMotion = true) {
            TimerScreen(
                state = TimerStates.ready(),
                nowElapsedMs = { TimerStates.NOW },
                onEvent = {},
                onBack = {},
                onAdjustBrew = {},
                onOpenTimerSettings = {},
                reliabilityProblem = problem,
                onFixReliability = { fixes++ },
            )
        }
    }

    @Test
    fun `the alarm volume at 0 shows Alarmlautstaerke ist aus, and Beheben opens its setting`() {
        show(ReliabilityProblem.AlarmVolumeOff)

        compose.onNodeWithText("Alarmlautstärke ist aus").assertIsDisplayed()
        compose.onNodeWithText("Der Timer ist eventuell unzuverlässig").assertDoesNotExist()
        compose.onNodeWithText("Beheben").performClick()

        assertThat(fixes).isEqualTo(1)
    }

    @Test
    fun `another missing check keeps the general message`() {
        show(ReliabilityProblem.Unreliable)

        compose.onNodeWithText("Der Timer ist eventuell unzuverlässig").assertIsDisplayed()
        compose.onNodeWithText("Alarmlautstärke ist aus").assertDoesNotExist()
    }

    @Test
    fun `no problem, no banner`() {
        show(null)

        compose.onNodeWithText("Beheben").assertDoesNotExist()
    }
}
