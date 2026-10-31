package com.umamit.app

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
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import com.umamit.app.timer.TimerRequest
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.brew.picker.TeaPickerSheet
import com.umamit.feature.brew.picker.TeaPickerUiState
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
    private val timerRequest = MutableStateFlow(TimerRequest(0L))
    private val visible = mutableListOf<Boolean>()
    private val handled = mutableListOf<Long>()

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
                    timerRequest = timerRequest,
                    onConsumeTimerRequest = { handled += it },
                    onTimerVisibleChange = { visible += it },
                    timerScreen = { _, openSettings, adjust, _ ->
                        Column {
                            Text("TIMER")
                            Button(onClick = openSettings) { Text("Timer-Einstellungen") }
                            Button(onClick = { adjust("s1") }) { Text("Brühen anpassen") }
                        }
                    },
                    settings = testSettingsScreens(),
                    startScreen = { tab ->
                        Column {
                            Button(onClick = { tab.navigation.openSetup("t") }) { Text("Tee brühen") }
                            Button(onClick = tab.navigation.openQuickTimer) { Text("Schnell") }
                        }
                        // The real picker sheet with its footer, opened in the alert playing at show().
                        if (pickerShown) {
                            TeaPickerSheet(
                                state = TeaPickerUiState(loading = false),
                                onQueryChange = {},
                                onPickTea = tab.navigation.openSetup,
                                onQuickTimer = tab.navigation.openQuickTimer,
                                onDismissRequest = {},
                            )
                        }
                    },
                    setupScreen = { onBack, _, openCatalog ->
                        Column {
                            Text("SETUP")
                            Button(onClick = { openCatalog("TG0557") }) { Text("Im Katalog ansehen") }
                            Button(onClick = onBack) { Text("Setup verlassen") }
                        }
                    },
                    catalogScreens = CatalogStackScreens(
                        tea = { host ->
                            Column {
                                Text("KATALOG-TEE")
                                Button(onClick = host.onBack) { Text("Katalog verlassen") }
                            }
                        },
                    ),
                    quickTimerScreen = { _, openTimer ->
                        Button(onClick = openTimer) { Text("QUICK-START") }
                    },
                )
            }
        }
    }

    private var pickerShown = false

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
        compose.onNodeWithText("Über UmamiT").performScrollTo().performClick()
        compose.onNodeWithText("Diagnose").performScrollTo().performClick()
        compose.onNodeWithText("DIAGNOSE-LOG").assertIsDisplayed()
        compose.mainClock.autoAdvance = false

        alert("A")
        advanceFrames()

        compose.onNodeWithText("DIAGNOSE-LOG").assertDoesNotExist()
        assertTimerOnTop()
    }

    private fun openSetupFromStart() {
        compose.onNodeWithText("Tee brühen").performClick()
        compose.onNodeWithText("SETUP").assertIsDisplayed()
    }

    @Test
    fun `the alert replaces the setup with the timer at once (rule 8)`() {
        show()
        openSetupFromStart()
        compose.mainClock.autoAdvance = false

        alert("A")
        advanceFrames()

        compose.onNodeWithText("SETUP").assertDoesNotExist()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `the setup's Im Katalog ansehen opens the catalog page above it, and back returns to the setup (D-098)`() {
        show()
        openSetupFromStart()

        compose.onNodeWithText("Im Katalog ansehen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("KATALOG-TEE").assertIsDisplayed()
        compose.onNodeWithText("SETUP").assertDoesNotExist()

        compose.onNodeWithText("Katalog verlassen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("SETUP").assertIsDisplayed()
        compose.onNodeWithText("KATALOG-TEE").assertDoesNotExist()
    }

    @Test
    fun `the alert replaces the catalog page opened from the setup with the timer at once (rule 8, D-098)`() {
        show()
        openSetupFromStart()
        compose.onNodeWithText("Im Katalog ansehen").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        alert("A")
        advanceFrames()

        compose.onNodeWithText("KATALOG-TEE").assertDoesNotExist()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `Im Katalog ansehen during an alert opens the timer, not the catalog page (rule 8, D-098)`() {
        alert("A")
        show()
        bar().performClick()
        // A setup opened in this alert stays for it (navigateAbove), so its menu is reachable.
        compose.onNodeWithText("Brühen anpassen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("SETUP").assertIsDisplayed()

        compose.onNodeWithText("Im Katalog ansehen").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("KATALOG-TEE").assertDoesNotExist()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `a tea tap on Start during an alert opens the timer, not the setup (rule 8)`() {
        // Like a refused start during ALERTING (D-062 S3): the alerting timer is where to go now.
        alert("A")
        show()

        compose.onNodeWithText("Tee brühen").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("SETUP").assertDoesNotExist()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `a route opened during an alert stays after a resume during the same alert`() {
        // The route remembers the alert it was opened in (navigateAbove); only a new alert moves it.
        alert("A")
        show()
        openSettingsFromTimer()

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        compose.onNodeWithText("Einstellungen").assertIsDisplayed()
        compose.onNodeWithText("TIMER").assertDoesNotExist()
    }

    @Test
    fun `back from the timer that replaced the setup returns to the shell`() {
        show()
        openSetupFromStart()
        alert("A")
        compose.waitForIdle()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithText("Tee brühen").assertIsDisplayed()
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

    @Test
    fun `the alert's full-screen intent opens the timer, which counts as visible from the first frame`() {
        // MainActivity got the full-screen intent before its content was set (#47, D-058).
        alert("A")
        timerRequest.value = TimerRequest(1L)
        compose.mainClock.autoAdvance = false
        show()

        // The shell is never reported as what shows over the lock screen, and never drawn: the timer's dark
        // background covers it until the timer is on top.
        assertThat(visible).doesNotContain(false)
        compose.onNodeWithTag(SHELL_COVER_TAG).assertIsDisplayed()
        advanceFrames()
        bar().assertDoesNotExist()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        compose.onNodeWithTag(SHELL_COVER_TAG).assertDoesNotExist()
        assertThat(visible).doesNotContain(false)
        // The activity forgets the request only now, so a recreation before it reopens the timer.
        assertThat(handled).containsExactly(1L)
    }

    @Test
    fun `a notification opened while Timer-Einstellungen are on top goes back to the timer`() {
        show()
        openSettingsFromTimer()

        timerRequest.value = TimerRequest(2L)
        compose.waitForIdle()

        assertTimerOnTop()
        // Back from the timer still returns to the shell, not to the settings.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        bar().assertIsDisplayed()
    }

    @Test
    fun `a request already handled does not open the timer again after the timer was closed`() {
        timerRequest.value = TimerRequest(3L)
        show()
        compose.onNodeWithText("TIMER").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        bar().assertIsDisplayed()
        compose.onNodeWithText("TIMER").assertDoesNotExist()
    }

    // --- Quick timer (SCR-07, M1-14) ---------------------------------------------------------

    private fun openQuickTimer() {
        compose.onNodeWithText("Schnell").performClick()
        compose.onNodeWithText("QUICK-START").assertIsDisplayed()
    }

    @Test
    fun `Schnelltimer opens the quick timer, and its Start replaces it with the timer`() {
        show()
        openQuickTimer()

        compose.onNodeWithText("QUICK-START").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertIsDisplayed()

        // Back from the running quick timer returns to Start, not to the chips.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Schnell").assertIsDisplayed()
    }

    @Test
    fun `Schnelltimer on Start during an alert opens the timer, not the quick timer (rule 8)`() {
        alert("A")
        show()

        compose.onNodeWithText("Schnell").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("QUICK-START").assertDoesNotExist()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `the picker's Ohne Tee during an alert opens the timer, not the quick timer (rule 8)`() {
        alert("A")
        pickerShown = true
        show()

        compose.onNodeWithText("Ohne Tee (Schnelltimer)").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("QUICK-START").assertDoesNotExist()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `the alert replaces the quick timer with the timer at once (rule 8)`() {
        show()
        openQuickTimer()
        compose.mainClock.autoAdvance = false

        alert("A")
        advanceFrames()

        compose.onNodeWithText("QUICK-START").assertDoesNotExist()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }
}
