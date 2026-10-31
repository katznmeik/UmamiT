package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowCompat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerUiStep
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.StepStatus
import com.umamit.feature.brew.quick.QuickTimerActions
import com.umamit.feature.brew.quick.QuickTimerScreen
import com.umamit.feature.brew.quick.QuickTimerUiState
import com.umamit.feature.brew.timer.TimerScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The status-bar icons and the gesture handle have one owner, the root (D-069): light on the
 * dark timer and quick timer, dark on the alert, the theme's own on everything else. The real
 * SCR-07 and SCR-05 overlap during the route crossfade, and SCR-07 leaves last; the bars must
 * still follow the screen on top, and the shell must get its dark icons back in the light theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SystemBarIconsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val brew = MutableStateFlow<ActiveBrew?>(null)
    private val alertKey = MutableStateFlow<String?>(null)
    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<ActiveBrew?> = brew
        override val progress: () -> Float = { 0f }
    }
    private var timerState by mutableStateOf(quickRunning(TimerPhase.RUNNING))

    private fun quickRunning(phase: TimerPhase) = TimerUiState.Active(
        kind = SessionKind.Quick,
        teaName = null,
        phase = phase,
        stepIndex = 0,
        steps = listOf(TimerUiStep(StepKind.INFUSION, 90, null, StepStatus.STARTED)),
        deadlineElapsedMs = 90_000,
        deadlineWallMs = 90_000,
        pausedRemainingMs = null,
        preAlertLeadSeconds = null,
        preAlertAtElapsedMs = null,
        preAlertPlayed = false,
        longStep = false,
        kettleTempC = null,
        demoted = false,
        finishWouldDiscard = false,
        preAlertBaseSeconds = 10,
    )

    private val controller get() = compose.activity.window.let { WindowCompat.getInsetsController(it, it.decorView) }

    private fun assertDarkIcons(expected: Boolean, where: String) {
        compose.waitForIdle()
        assertWithMessage("$where: status bar").that(controller.isAppearanceLightStatusBars).isEqualTo(expected)
        assertWithMessage("$where: gesture handle").that(controller.isAppearanceLightNavigationBars).isEqualTo(expected)
    }

    private var closeTimer: () -> Unit = {}

    private fun show() {
        // What enableEdgeToEdge leaves in the light theme: dark icons.
        compose.runOnUiThread {
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = false) {
                UmamitRoot(
                    activeBrewSource = source,
                    journalSources = testJournalSources(),
                    alertKey = alertKey,
                    settings = testSettingsScreens(),
                    startScreen = { tab -> Button(onClick = tab.navigation.openQuickTimer) { Text("Schnell") } },
                    quickTimerScreen = { onBack, onOpenTimer ->
                        QuickTimerScreen(
                            state = QuickTimerUiState(),
                            actions = QuickTimerActions(back = onBack, start = onOpenTimer),
                        )
                    },
                    timerScreen = { onClose, _, _, _ ->
                        closeTimer = onClose
                        TimerScreen(
                            state = timerState,
                            nowElapsedMs = { 0L },
                            onEvent = {},
                            onBack = onClose,
                            onAdjustBrew = {},
                            onOpenTimerSettings = {},
                        )
                    },
                )
            }
        }
    }

    @Test
    fun `quick timer, timer and back - the bars follow the screen on top (S1)`() {
        show()
        assertDarkIcons(true, "Start")

        compose.onNodeWithText("Schnell").performClick()
        assertDarkIcons(false, "quick timer")

        // The timer replaces SCR-07 with a crossfade; SCR-07 leaves last.
        compose.onNodeWithText("Start").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        assertDarkIcons(false, "running quick timer")

        timerState = quickRunning(TimerPhase.ALERTING)
        alertKey.value = "quick:0:90000"
        assertDarkIcons(true, "alert")

        timerState = quickRunning(TimerPhase.RUNNING)
        alertKey.value = null
        assertDarkIcons(false, "after the alert")

        compose.runOnIdle { closeTimer() }
        compose.mainClock.advanceTimeBy(1_000)
        assertDarkIcons(true, "Start again")
    }

    @Test
    fun `the dark theme gets light icons on the shell`() {
        compose.setContent {
            UmamiTheme(darkTheme = true, reduceMotion = false) {
                UmamitRoot(
                    activeBrewSource = source,
                    journalSources = testJournalSources(),
                    alertKey = alertKey,
                    settings = testSettingsScreens(),
                    startScreen = { Text("START") },
                )
            }
        }
        assertDarkIcons(false, "Start")
    }
}
