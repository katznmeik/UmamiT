package com.umamit.app

import android.content.pm.ActivityInfo
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The root's report for the portrait timer screens (D-136 Q5, build note „Portrait timer"; SCR-05, SCR-07,
 * NFR-COMP-02): a timer screen on top ([isTimerScreen]: the timer or the quick timer before *Start*) or a
 * notification's request for the timer still pending reports true; the shell, its tabs and every route above the
 * timer report false. A close over the keyguard keeps it until the timer is popped at `ON_STOP`, so the timer never
 * turns while the keyguard comes back. The keyguard is a fake.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerScreenOnTopTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val running =
        ActiveBrew(title = "Hisui", step = "Aufguss 1/3", remainingSeconds = 30, liquorColor = Color.Green)
    private val brew = MutableStateFlow<ActiveBrew?>(running)
    private val alertKey = MutableStateFlow<String?>(null)
    private val timerRequest = MutableStateFlow(TimerRequest(0L))
    private val onTop = mutableListOf<Boolean>()
    private var lockedNow = false
    private val keyguard = KeyguardLock { lockedNow }

    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<ActiveBrew?> = brew
        override val progress: () -> Float = { 0.5f }
    }

    private fun show() {
        compose.setContent {
            UmamiTheme(reduceMotion = false) {
                UmamitRoot(
                    activeBrewSource = source,
                    journalSources = testJournalSources(),
                    alertKey = alertKey,
                    timerRequest = timerRequest,
                    onTimerScreenOnTopChange = { onTop += it },
                    keyguard = keyguard,
                    timerScreen = { onClose, openSettings, _, _ ->
                        Column {
                            Text("TIMER")
                            Button(onClick = openSettings) { Text("Timer-Einstellungen") }
                            Button(onClick = onClose) { Text("Session beenden") }
                        }
                    },
                    settings = testSettingsScreens(),
                    startScreen = { tab ->
                        Column {
                            Text("START")
                            Button(onClick = { tab.navigation.openSetup("t") }) { Text("Tee brühen") }
                            Button(onClick = tab.navigation.openQuickTimer) { Text("Schnell") }
                        }
                    },
                    setupScreen = { Text("SETUP") },
                    quickTimerScreen = { _, openTimer ->
                        Button(onClick = openTimer) { Text("QUICK-START") }
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun bar() = compose.onNodeWithText("Hisui", useUnmergedTree = true)

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun openTimer() {
        bar().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `the shell is no timer screen`() {
        show()

        compose.onNodeWithText("START").assertIsDisplayed()
        assertThat(onTop).doesNotContain(true)
    }

    @Test
    fun `the timer on top reports true, and false again once it is closed`() {
        show()
        openTimer()
        assertThat(onTop.last()).isTrue()

        compose.onNodeWithText("Session beenden").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("START").assertIsDisplayed()
        assertThat(onTop.last()).isFalse()
    }

    @Test
    fun `the quick timer before Start is a timer screen, and its Start keeps it so with no false between`() {
        show()
        compose.onNodeWithText("Schnell").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("QUICK-START").assertIsDisplayed()
        assertThat(onTop.last()).isTrue()
        val opened = onTop.size

        compose.onNodeWithText("QUICK-START").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertIsDisplayed()

        assertThat(onTop.drop(opened)).doesNotContain(false)
        back()
        assertThat(onTop.last()).isFalse()
    }

    @Test
    fun `a route above the timer rotates, and back on the timer it is portrait again`() {
        show()
        openTimer()

        compose.onNodeWithText("Timer-Einstellungen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertDoesNotExist()
        assertThat(onTop.last()).isFalse()

        back()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertThat(onTop.last()).isTrue()
    }

    @Test
    fun `the Journal tab is no timer screen`() {
        show()
        openTimer()
        back()

        compose.onNodeWithTag(tabTag(Tab.Journal)).performClick()
        compose.waitForIdle()

        assertThat(onTop.last()).isFalse()
    }

    @Test
    fun `a request pending at the first frame reports true before the timer is there, and never false after`() {
        timerRequest.value = TimerRequest(1L)
        show()

        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertThat(onTop.first()).isTrue()
        assertThat(onTop).doesNotContain(false)
    }

    @Test
    fun `a request from the shell reports true and keeps it while the timer comes up`() {
        show()
        val before = onTop.size

        timerRequest.value = TimerRequest(1L)
        compose.waitForIdle()

        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertThat(onTop.drop(before)).isNotEmpty()
        assertThat(onTop.drop(before)).doesNotContain(false)
    }

    @Test
    fun `an alert bringing the timer up over the setup reports true (rule 8)`() {
        show()
        compose.onNodeWithText("Tee brühen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("SETUP").assertIsDisplayed()
        assertThat(onTop.last()).isFalse()

        alertKey.value = "A"
        brew.value = running.copy(alerting = true)
        compose.waitForIdle()

        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertThat(onTop.last()).isTrue()
    }

    @Test
    fun `a close over the keyguard keeps the timer portrait until it is popped at the stop`() {
        lockedNow = true
        show()
        openTimer()

        compose.onNodeWithText("Session beenden").performClick()
        compose.waitForIdle()

        // Reported gone for the lock-screen flags, but still drawn and still upright.
        assertThat(keyguard.closePending.value).isTrue()
        assertThat(onTop.last()).isTrue()

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        compose.onNodeWithText("TIMER").assertDoesNotExist()
        assertThat(onTop.last()).isFalse()
    }

    @Test
    fun `the activity asks for portrait on a timer screen and for the system's choice elsewhere`() {
        assertThat(screenOrientationFor(timerScreenOnTop = true)).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        assertThat(screenOrientationFor(timerScreenOnTop = false))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }
}
