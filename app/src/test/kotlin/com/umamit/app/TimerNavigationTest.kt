package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The timer route above the shell (SCR-05, D-058) and rule 8: no transition
 * into or out of an alerting timer, and a settings screen opened from the
 * timer is popped when the alert starts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val running =
        ActiveBrew(title = "Hisui", step = "Aufguss 1/3", remainingSeconds = 30, liquorColor = Color.Green)
    private val brew = MutableStateFlow<ActiveBrew?>(running)
    private val visible = mutableListOf<Boolean>()

    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<ActiveBrew?> = brew
        override val progress: () -> Float = { 0.5f }
    }

    private fun show() {
        compose.setContent {
            UmamiTheme(reduceMotion = false) {
                UmamitRoot(
                    activeBrewSource = source,
                    onTimerVisibleChange = { visible += it },
                    timerScreen = { _, openSettings ->
                        Column {
                            Text("TIMER")
                            Button(onClick = openSettings) { Text("Timer-Einstellungen") }
                        }
                    },
                )
            }
        }
    }

    /**
     * Five frames (≈ 80 ms): Navigation settles an instant swap within them, while
     * the calm route fade (300 ms) or exit fade (200 ms) would still be running.
     */
    private fun advanceFrames() = repeat(5) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test
    fun `a tap on the bar opens the timer with a calm fade`() {
        show()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Hisui", useUnmergedTree = true).performClick()
        advanceFrames()

        // The shell is still there under the fading timer.
        compose.onNodeWithText("Hisui", useUnmergedTree = true).assertExists()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertThat(visible.last()).isTrue()
    }

    @Test
    fun `an alerting timer opens without any transition`() {
        brew.value = running.copy(alerting = true, overtimeSeconds = 3)
        show()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Hisui", useUnmergedTree = true).performClick()
        advanceFrames()

        compose.onNodeWithText("Hisui", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `the alert pops Timer-Einstellungen back to the timer at once`() {
        show()
        compose.onNodeWithText("Hisui", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Timer-Einstellungen").performClick()
        compose.onNodeWithText("Einstellungen").assertIsDisplayed()
        compose.onNodeWithText("TIMER").assertDoesNotExist()
        compose.mainClock.autoAdvance = false

        brew.value = running.copy(alerting = true)
        advanceFrames()

        compose.onNodeWithText("Einstellungen").assertDoesNotExist()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `settings opened during the alert stay`() {
        brew.value = running.copy(alerting = true)
        show()
        compose.onNodeWithText("Hisui", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Timer-Einstellungen").performClick()

        compose.onNodeWithText("Einstellungen").assertIsDisplayed()
    }
}
