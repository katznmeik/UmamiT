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
import androidx.lifecycle.Lifecycle
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
 * into or out of an alerting timer, and *Timer-Einstellungen* is popped by
 * every alert it was not opened in, also one that started in the background.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val running =
        ActiveBrew(title = "Hisui", step = "Aufguss 1/3", remainingSeconds = 30, liquorColor = Color.Green)
    private val brew = MutableStateFlow<ActiveBrew?>(running)
    private val alertKey = MutableStateFlow<String?>(null)
    private val visible = mutableListOf<Boolean>()

    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<ActiveBrew?> = brew
        override val progress: () -> Float = { 0.5f }
    }

    private fun alert(key: String?) {
        alertKey.value = key
        brew.value = running.copy(alerting = key != null)
    }

    private fun show() {
        compose.setContent {
            UmamiTheme(reduceMotion = false) {
                UmamitRoot(
                    activeBrewSource = source,
                    alertKey = alertKey,
                    onTimerVisibleChange = { visible += it },
                    timerScreen = { _, openSettings ->
                        Column {
                            Text("TIMER")
                            Button(onClick = openSettings) { Text("Timer-Einstellungen") }
                        }
                    },
                    diagnosticsScreen = { Text("DIAGNOSE-LOG") },
                )
            }
        }
    }

    private fun bar() = compose.onNodeWithText("Hisui", useUnmergedTree = true)

    private fun openSettingsFromTimer() {
        bar().performClick()
        compose.onNodeWithText("Timer-Einstellungen").performClick()
        compose.onNodeWithText("Einstellungen").assertIsDisplayed()
        compose.onNodeWithText("TIMER").assertDoesNotExist()
    }

    /**
     * Five frames (≈ 80 ms): Navigation settles an instant swap within them, while
     * the calm route fade (300 ms) or exit fade (200 ms) would still be running.
     */
    private fun advanceFrames() = repeat(5) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun assertTimerOnTop() {
        compose.onNodeWithText("Einstellungen").assertDoesNotExist()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `a tap on the bar opens the timer with a calm fade`() {
        show()
        compose.mainClock.autoAdvance = false

        bar().performClick()
        advanceFrames()

        // The shell is still there under the fading timer.
        bar().assertExists()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertThat(visible.last()).isTrue()
    }

    @Test
    fun `an alerting timer opens without any transition`() {
        alert("A")
        show()
        compose.mainClock.autoAdvance = false

        bar().performClick()
        advanceFrames()

        bar().assertDoesNotExist()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `the alert pops Timer-Einstellungen back to the timer at once`() {
        show()
        openSettingsFromTimer()
        compose.mainClock.autoAdvance = false

        alert("A")
        advanceFrames()

        assertTimerOnTop()
    }

    @Test
    fun `the alert pops Diagnose opened from Timer-Einstellungen back to the timer at once`() {
        show()
        openSettingsFromTimer()
        compose.onNodeWithText("Diagnose").performClick()
        compose.onNodeWithText("DIAGNOSE-LOG").assertIsDisplayed()
        compose.mainClock.autoAdvance = false

        alert("A")
        advanceFrames()

        compose.onNodeWithText("DIAGNOSE-LOG").assertDoesNotExist()
        assertTimerOnTop()
    }

    @Test
    fun `settings opened during an alert stay for that alert, and the next alert pops them`() {
        alert("A")
        show()
        openSettingsFromTimer()

        // *Abgegossen* and *Start* from the notification, settings still on top.
        alert(null)
        compose.waitForIdle()
        compose.onNodeWithText("Einstellungen").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        alert("B")
        advanceFrames()

        assertTimerOnTop()
    }

    @Test
    fun `an alert that started in the background pops the settings on resume`() {
        show()
        openSettingsFromTimer()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        alert("A")
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        assertTimerOnTop()
    }

    @Test
    fun `a new alert in the background pops settings opened during the previous one`() {
        alert("A")
        show()
        openSettingsFromTimer()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        // A ends and B starts while the app is in the background: no edge is ever composed.
        alert(null)
        alert("B")
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        assertTimerOnTop()
    }
}
