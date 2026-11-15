package com.umamit.app

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import com.umamit.app.timer.TimerRequest
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Leaving the timer while the phone is locked (SCR-05, D-117 build note „Lock screen", A-17/A-18): the timer is
 * reported as gone in the turn of the close and stays drawn, the pop follows at `ON_STOP` with no transition, a
 * back (also a swipe) takes that path with no preview of the shell, and a new alert cancels it at once (rule 8).
 * Unlocked, the close is the `exitFade` pop as ever. The keyguard is a fake.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerOverKeyguardTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val running =
        ActiveBrew(title = "Hisui", step = "Aufguss 1/3", remainingSeconds = 30, liquorColor = Color.Green)
    private val brew = MutableStateFlow<ActiveBrew?>(running)
    private val alertKey = MutableStateFlow<String?>(null)
    private val timerRequest = MutableStateFlow(TimerRequest(0L))
    private val visible = mutableListOf<Boolean>()
    private var lockedNow = true
    private val keyguard = KeyguardLock { lockedNow }
    private var timerTaps = 0

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
                    journalSources = testJournalSources(),
                    alertKey = alertKey,
                    onTimerVisibleChange = { visible += it },
                    keyguard = keyguard,
                    timerRequest = timerRequest,
                    timerScreen = { onClose, _, _, shown ->
                        Column {
                            Text("TIMER")
                            Text("slot:${shown.slot}")
                            Button(onClick = onClose) { Text("Sitzung beenden") }
                            Button(onClick = { timerTaps++ }) { Text("Pause") }
                            Button(onClick = { shown.onShowOther(TimerSlot.BREW_2) }) { Text("Anderer") }
                        }
                    },
                    settings = testSettingsScreens(),
                    startScreen = { Text("START") },
                )
            }
        }
    }

    private fun openTimer() {
        compose.onNodeWithText("Hisui", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertThat(visible.last()).isTrue()
    }

    /** Five frames (≈ 80 ms): an instant pop has settled, the 200 ms `exitFade` has not. */
    private fun advanceFrames() = repeat(5) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    /** One second, far beyond every route transition. */
    private fun advanceSecond() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun assertShellNotComposed() {
        compose.onNodeWithText("START").assertDoesNotExist()
        compose.onNodeWithText("Hisui", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun stopAndStart() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    /** Opens the timer and closes it over the keyguard: the close waits for the stop. */
    private fun openAndClose() {
        show()
        openTimer()
        compose.onNodeWithText("Sitzung beenden").performClick()
        compose.waitForIdle()
        assertThat(keyguard.closePending.value).isTrue()
    }

    @Test
    fun `a close over the keyguard clears the flags in its turn and keeps the timer drawn until the stop`() {
        show()
        openTimer()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Sitzung beenden").performClick()
        // In the tap's own turn, before any frame.
        assertThat(visible.last()).isFalse()
        advanceSecond()

        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertShellNotComposed()
        assertThat(visible.last()).isFalse()
    }

    @Test
    fun `the timer left over the keyguard is popped at the stop with no transition`() {
        show()
        openTimer()
        compose.onNodeWithText("Sitzung beenden").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        stopAndStart()
        advanceFrames()

        compose.onNodeWithText("TIMER").assertDoesNotExist()
        compose.onNodeWithText("START").assertIsDisplayed()
        assertThat(visible.last()).isFalse()
    }

    @Test
    fun `the timer leaving over the keyguard takes no tap`() {
        show()
        openTimer()
        compose.onNodeWithText("Sitzung beenden").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(LEAVING_COVER_TAG).assertExists()
        compose.onNodeWithText("Pause").performClick()

        assertThat(timerTaps).isEqualTo(0)
    }

    @Test
    fun `an alert before the stop cancels the close and reports the timer again at once (rule 8)`() {
        show()
        openTimer()
        compose.onNodeWithText("Sitzung beenden").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        alert("A")
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()

        assertThat(visible.last()).isTrue()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        // The cover goes in the next frame; the timer underneath already shows the alert.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithTag(LEAVING_COVER_TAG).assertDoesNotExist()
        // No close waits any more: the stop leaves the alerting timer where it is.
        compose.mainClock.autoAdvance = true
        stopAndStart()
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertThat(visible.last()).isTrue()
    }

    @Test
    fun `back over the keyguard takes the locked path`() {
        show()
        openTimer()
        compose.mainClock.autoAdvance = false

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertThat(visible.last()).isFalse()
        advanceSecond()

        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertShellNotComposed()
    }

    @Test
    fun `a back swipe over the keyguard previews nothing of the shell`() {
        show()
        openTimer()
        // Back over the keyguard: the app was stopped and started again.
        stopAndStart()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val dispatcher = compose.activity.onBackPressedDispatcher

        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(200f, 400f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        advanceFrames()

        assertShellNotComposed()
        assertThat(visible.last()).isTrue()
        compose.runOnUiThread { dispatcher.onBackPressed() }
        advanceSecond()
        assertShellNotComposed()
        assertThat(visible.last()).isFalse()
    }

    @Test
    fun `unlocked, a close pops with the exit fade as ever`() {
        lockedNow = false
        show()
        openTimer()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Sitzung beenden").performClick()
        advanceFrames()

        // The fade still runs over the shell.
        compose.onNodeWithText("TIMER").assertExists()
        compose.onNodeWithText("START").assertExists()
        compose.onNodeWithTag(LEAVING_COVER_TAG).assertDoesNotExist()
        advanceSecond()
        compose.onNodeWithText("TIMER").assertDoesNotExist()
        assertThat(visible.last()).isFalse()
    }

    @Test
    fun `during an alert a close over the keyguard pops at once as ever (rule 8)`() {
        alert("A")
        show()
        openTimer()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Sitzung beenden").performClick()
        advanceFrames()

        compose.onNodeWithText("TIMER").assertDoesNotExist()
        compose.onNodeWithTag(LEAVING_COVER_TAG).assertDoesNotExist()
    }

    @Test
    fun `unlocked while the close waits, the timer leaves with the exit fade`() {
        openAndClose()
        compose.mainClock.autoAdvance = false

        lockedNow = false
        compose.runOnUiThread { keyguard.read() }
        advanceFrames()

        // The fade runs over the shell, and the timer no longer takes the locked path's cover.
        compose.onNodeWithText("TIMER").assertExists()
        compose.onNodeWithText("START").assertExists()
        compose.onNodeWithTag(LEAVING_COVER_TAG).assertDoesNotExist()
        advanceSecond()
        compose.onNodeWithText("TIMER").assertDoesNotExist()
        compose.onNodeWithText("START").assertIsDisplayed()
    }

    @Test
    fun `an alert that starts just before the stop keeps the timer on top (rule 8, B1)`() {
        openAndClose()
        compose.mainClock.autoAdvance = false

        // No frame between the alert and the stop: the root has not recomposed for it.
        alert("A")
        stopAndStart()

        assertThat(keyguard.closePending.value).isFalse()
        assertThat(visible.last()).isTrue()
        advanceFrames()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        compose.onNodeWithTag(LEAVING_COVER_TAG).assertDoesNotExist()
        assertThat(visible.last()).isTrue()
    }

    @Test
    fun `a notification's request while the close waits cancels it (N1)`() {
        openAndClose()
        compose.mainClock.autoAdvance = false

        timerRequest.value = TimerRequest(1L)
        advanceFrames()

        assertThat(keyguard.closePending.value).isFalse()
        compose.onNodeWithTag(LEAVING_COVER_TAG).assertDoesNotExist()
        assertThat(visible.last()).isTrue()
        stopAndStart()
        advanceFrames()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertThat(visible.last()).isTrue()
    }

    @Test
    fun `a notification's request just before the stop keeps the timer on top (N1)`() {
        openAndClose()
        compose.mainClock.autoAdvance = false

        timerRequest.value = TimerRequest(1L)
        stopAndStart()

        assertThat(keyguard.closePending.value).isFalse()
        assertThat(visible.last()).isTrue()
        advanceFrames()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `over the keyguard, the second timer's route closes as ever onto the first (N2)`() {
        show()
        openTimer()
        compose.onNodeWithText("Anderer").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("slot:BREW_2").assertIsDisplayed()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Sitzung beenden").performClick()
        assertThat(keyguard.closePending.value).isFalse()
        advanceFrames()
        compose.onNodeWithTag(LEAVING_COVER_TAG).assertDoesNotExist()
        advanceSecond()

        compose.onNodeWithText("slot:BREW_2").assertDoesNotExist()
        compose.onNodeWithText("slot:null").assertIsDisplayed()
        assertShellNotComposed()
        assertThat(visible.last()).isTrue()
    }

    @Test
    fun `a close that reads the phone unlocked while one waits pops once, as unlocked (N3)`() {
        openAndClose()
        compose.mainClock.autoAdvance = false

        lockedNow = false
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.onBackPressed()
            // In the back's own turn: no wait is left for a second pop.
            assertThat(keyguard.closePending.value).isFalse()
        }
        advanceFrames()

        compose.onNodeWithText("TIMER").assertExists()
        compose.onNodeWithText("START").assertExists()
        advanceSecond()
        compose.onNodeWithText("TIMER").assertDoesNotExist()
        compose.onNodeWithText("START").assertIsDisplayed()
    }

    @Test
    fun `unlocked with no word of it, the close re-reads the phone after a second (N5)`() {
        openAndClose()
        compose.mainClock.autoAdvance = false

        lockedNow = false
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        compose.onNodeWithTag(LEAVING_COVER_TAG).assertExists()

        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertDoesNotExist()
        compose.onNodeWithText("START").assertIsDisplayed()
    }

    @Test
    fun `the activity counts a waiting close as visible once an alert rings (rule 8)`() {
        assertThat(timerVisibleForFlags(reported = false, closePending = true, alerting = true)).isTrue()
        assertThat(timerVisibleForFlags(reported = false, closePending = true, alerting = false)).isFalse()
        assertThat(timerVisibleForFlags(reported = false, closePending = false, alerting = true)).isFalse()
        assertThat(timerVisibleForFlags(reported = true, closePending = false, alerting = false)).isTrue()
    }
}
