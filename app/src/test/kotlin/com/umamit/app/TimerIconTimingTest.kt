package com.umamit.app

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowCompat
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.designsystem.theme.UmamiAppTheme
import com.umamit.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The status-bar icons on the timer's own transitions (D-117 build note A-16, amends D-069's timing): a change waits
 * for the fade's midpoint, 150 ms into `fade` on the way in and 100 ms into `exitFade` on the way out, and an alert
 * flips them at once (rule 8). From v0.8.0 the timer is in the app's theme (SET-05), so its own transitions change
 * nothing. A night switch in place (A-08) keeps the alert surface and its icons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerIconTimingTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val alertKey = MutableStateFlow<String?>(null)
    private var systemDark by mutableStateOf(false)
    private var closeTimer: () -> Unit = {}

    private val controller get() = compose.activity.window.let { WindowCompat.getInsetsController(it, it.decorView) }

    private fun assertDarkIcons(expected: Boolean, where: String) {
        compose.waitForIdle()
        assertWithMessage("$where: status bar").that(controller.isAppearanceLightStatusBars).isEqualTo(expected)
        assertWithMessage("$where: gesture handle").that(controller.isAppearanceLightNavigationBars).isEqualTo(expected)
    }

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiAppTheme(ThemeMode.SYSTEM, systemDark = systemDark, reduceMotion = false) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(),
                    alertKey = alertKey,
                    settings = testSettingsScreens(),
                    startScreen = { tab ->
                        Column {
                            Button(onClick = tab.navigation.openTimer) { Text("Zum Timer") }
                            Button(onClick = tab.navigation.openQuickTimer) { Text("Schnell") }
                        }
                    },
                    quickTimerScreen = { _, openTimer -> Button(onClick = openTimer) { Text("QUICK-START") } },
                    timerScreen = { onClose, _, _, _ ->
                        closeTimer = onClose
                        Text("TIMER")
                    },
                )
            }
        }
        compose.mainClock.advanceTimeBy(FRAME_MS * 3)
    }

    /**
     * The timer follows the app's theme (SET-05, D-128 Q20), so into and out of it the icons are those of the theme
     * all along: there is no flip to time in either theme (the midpoint timing of A-16 stays for any other change).
     */
    @Test
    fun `into and out of the light timer the icons stay dark`() = iconsStayThroughTheTimer(dark = false)

    @Test
    fun `into and out of the dark timer the icons stay light`() = iconsStayThroughTheTimer(dark = true)

    private fun iconsStayThroughTheTimer(dark: Boolean) {
        systemDark = dark
        show()
        val icons = !dark
        assertDarkIcons(icons, "Start, dark theme $dark")

        compose.onNodeWithText("Zum Timer").performClick()
        compose.mainClock.advanceTimeBy(130)
        assertDarkIcons(icons, "130 ms into the timer's fade, dark theme $dark")
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertDarkIcons(icons, "the timer, dark theme $dark")

        compose.runOnIdle { closeTimer() }
        compose.mainClock.advanceTimeBy(80)
        assertDarkIcons(icons, "80 ms into the exit fade, dark theme $dark")
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertDarkIcons(icons, "back on Start, dark theme $dark")
    }

    @Test
    fun `from the quick timer into the timer the icons never change (SCR-07 to SCR-05)`() {
        show()
        compose.onNodeWithText("Schnell").performClick()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertDarkIcons(true, "the light quick timer")

        compose.onNodeWithText("QUICK-START").performClick()
        compose.mainClock.advanceTimeBy(FRAME_MS * 3)
        assertDarkIcons(true, "the light timer's fade, early")
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertDarkIcons(true, "the light timer")
    }

    @Test
    fun `an alert flips the icons at once (rule 8)`() {
        systemDark = true
        show()
        assertDarkIcons(false, "Start, dark theme")

        // On the way into the timer: an alert does not wait for the fade's midpoint.
        compose.onNodeWithText("Zum Timer").performClick()
        compose.mainClock.advanceTimeBy(FRAME_MS * 2)
        alertKey.value = "brew:0:60000"
        compose.mainClock.advanceTimeBy(FRAME_MS)
        assertDarkIcons(true, "the alert's first frame, mid-fade")

        // On the running timer: the light alert surface takes dark icons in its first frame.
        alertKey.value = null
        compose.mainClock.advanceTimeBy(FRAME_MS)
        assertDarkIcons(false, "the timer after the alert")
        alertKey.value = "brew:1:120000"
        compose.mainClock.advanceTimeBy(FRAME_MS)
        assertDarkIcons(true, "the next alert's first frame")
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `a night switch in place during an alert keeps the alert surface and its icons (A-08)`() {
        show()
        compose.onNodeWithText("Zum Timer").performClick()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        alertKey.value = "brew:0:60000"
        compose.mainClock.advanceTimeBy(FRAME_MS * 2)
        assertDarkIcons(true, "the alert")

        systemDark = true
        compose.mainClock.advanceTimeBy(FRAME_MS * 2)
        assertDarkIcons(true, "the alert after the night switch")
        compose.onNodeWithText("TIMER").assertIsDisplayed()

        systemDark = false
        compose.mainClock.advanceTimeBy(FRAME_MS * 2)
        assertDarkIcons(true, "the alert after the switch back")
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun `the activity handles a night switch itself, with no re-creation (A-08)`() {
        val info = compose.activity.packageManager.getActivityInfo(
            ComponentName(compose.activity, MainActivity::class.java),
            0,
        )
        assertThat(info.configChanges and ActivityInfo.CONFIG_UI_MODE).isEqualTo(ActivityInfo.CONFIG_UI_MODE)
    }

    private companion object {
        const val FRAME_MS = 16L
        const val SETTLE_MS = 500L
    }
}
