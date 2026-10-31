package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Both timers in view (D-098 Q3): a tap on either half of the active brew bar opens that timer; the row on a timer
 * switches to the other one and back returns; the row of the switched-to timer goes back too, so two timers never
 * stack more than two routes. The quick timer's *Start* opens its own timer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TwoTimersNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val brew = MutableStateFlow<ActiveBrew?>(
        ActiveBrew(title = "Hisui", step = "Aufguss 1/3", remainingSeconds = 30, liquorColor = Color.Green),
    )
    private val quick = MutableStateFlow<ActiveBrew?>(
        ActiveBrew(title = "Schnelltimer", step = null, remainingSeconds = 134, liquorColor = Color.Gray),
    )

    private val timerRequest = MutableStateFlow(TimerRequest(0L))

    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<ActiveBrew?> = brew
        override val progress: () -> Float = { 0.5f }
        override val alongside: StateFlow<ActiveBrew?> = quick
    }

    private fun show() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = source,
                    journalSources = testJournalSources(),
                    alertKey = MutableStateFlow(null),
                    timerRequest = timerRequest,
                    timerScreen = { onClose, _, _, shown ->
                        // Opened for no timer, it shows the one in front: here the brew, as the real route reports it.
                        val slot = shown.slot ?: TimerSlot.BREW
                        LaunchedEffect(slot) { shown.onPin(slot) }
                        val other = slot.other
                        Column {
                            Text("TIMER $slot")
                            Button(onClick = { shown.onShowOther(other) }) { Text("ROW $other") }
                            Button(onClick = onClose) { Text("CLOSE") }
                        }
                    },
                    settings = testSettingsScreens(),
                    startScreen = { tab ->
                        Column {
                            Button(onClick = tab.navigation.openQuickTimer) { Text("Schnell") }
                            // Start's card *Aufguss fortsetzen*, as StartTabContent wires it (SCR-02, D-098).
                            Button(onClick = { tab.navigation.openTimerOf(TimerSlot.BREW) }) { Text("Fortsetzen") }
                        }
                    },
                    quickTimerScreen = { _, openTimer -> Button(onClick = openTimer) { Text("QUICK-START") } },
                )
            }
        }
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun brewHalf() = compose.onNodeWithContentDescription(
        "Laufender Aufguss: Hisui, Aufguss 1/3, noch 30 Sekunden. Zum Timer",
    )

    private fun quickHalf() =
        compose.onNodeWithContentDescription("Laufender Timer: Schnelltimer, noch 2 Minuten 14 Sekunden. Zum Timer")

    @Test
    fun `each half of the bar opens its own timer`() {
        show()

        brewHalf().performClick()
        compose.onNodeWithText("TIMER BREW").assertIsDisplayed()
        pressBack()

        quickHalf().performClick()
        compose.onNodeWithText("TIMER QUICK").assertIsDisplayed()
    }

    @Test
    fun `the row switches to the other timer and back returns, never stacking more than two`() {
        show()
        brewHalf().performClick()

        compose.onNodeWithText("ROW QUICK").performClick()
        compose.onNodeWithText("TIMER QUICK").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("TIMER BREW").assertIsDisplayed()

        // The switched-to timer's row goes back to the timer below it.
        compose.onNodeWithText("ROW QUICK").performClick()
        compose.onNodeWithText("ROW BREW").performClick()
        compose.onNodeWithText("TIMER BREW").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("TIMER BREW").assertDoesNotExist()
        brewHalf().assertIsDisplayed()
    }

    @Test
    fun `the switched-to timer closes to the timer below it when it ends`() {
        show()
        brewHalf().performClick()
        compose.onNodeWithText("ROW QUICK").performClick()

        compose.onNodeWithText("CLOSE").performClick()

        compose.onNodeWithText("TIMER BREW").assertIsDisplayed()
    }

    /** A tap on [slot]'s timer notification (04 §8.2: each carries its slot); null: one that names none. */
    private fun notificationTap(slot: TimerSlot?) {
        timerRequest.value = TimerRequest(timerRequest.value.token + 1, slot)
        compose.waitForIdle()
    }

    @Test
    fun `a notification opens its own timer from the shell, and the other's above it`() {
        show()

        notificationTap(TimerSlot.QUICK)
        compose.onNodeWithText("TIMER QUICK").assertIsDisplayed()

        // The brew's notification while the Schnelltimer is shown: the brew above it, as its row would open it.
        notificationTap(TimerSlot.BREW)
        compose.onNodeWithText("TIMER BREW").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("TIMER QUICK").assertIsDisplayed()
    }

    @Test
    fun `with the brew shown, the Schnelltimer's notification opens it, and the brew's goes back`() {
        show()
        brewHalf().performClick()

        // The brew was paused from its row, so the Live Update is the Schnelltimer's: its tap opens the Schnelltimer.
        notificationTap(TimerSlot.QUICK)
        compose.onNodeWithText("TIMER QUICK").assertIsDisplayed()

        // Notification 5, the brew's, returns to the brew below; never more than two timer routes.
        notificationTap(TimerSlot.BREW)
        compose.onNodeWithText("TIMER BREW").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("TIMER BREW").assertDoesNotExist()
        brewHalf().assertIsDisplayed()
    }

    @Test
    fun `a timer opened for the one in front gives way to the other timer's notification`() {
        show()
        notificationTap(null)
        compose.onNodeWithText("TIMER BREW").assertIsDisplayed()

        notificationTap(TimerSlot.QUICK)
        compose.onNodeWithText("TIMER QUICK").assertIsDisplayed()

        // Its own notification keeps it: no second route for the same timer.
        notificationTap(TimerSlot.QUICK)
        compose.onNodeWithText("TIMER QUICK").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("TIMER BREW").assertIsDisplayed()
    }

    @Test
    fun `Start's card opens the brew it shows, not the timer in front (SCR-02)`() {
        show()

        compose.onNodeWithText("Fortsetzen").performClick()

        compose.onNodeWithText("TIMER BREW").assertIsDisplayed()
    }

    @Test
    fun `the quick timer's Start opens the quick timer`() {
        show()
        compose.onNodeWithText("Schnell").performClick()

        compose.onNodeWithText("QUICK-START").performClick()

        compose.onNodeWithText("TIMER QUICK").assertIsDisplayed()
    }
}
