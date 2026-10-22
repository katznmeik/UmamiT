package com.umamit.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.DefaultExtendedColors
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.designsystem.theme.WashiColorScheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The active brew bar while the step alerts (03 §3.8, GEN-06, rule 8): static
 * matcha from the first frame, „Jetzt abgießen!" with the overtime counting up,
 * no pulse in the bar, and a tap still opens the timer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ActiveBrewBarAlertingTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val alert = DefaultExtendedColors.alert

    private val running = ActiveBrew(
        title = "Bio Sencha Hisui",
        step = "Aufguss 2/4",
        stepDescription = "Aufguss 2 von 4",
        remainingSeconds = 1,
        liquorColor = Color(0xFFC9D58F),
    )
    private val alerting = running.copy(remainingSeconds = 0, alerting = true, overtimeSeconds = 7)

    private val alertingDescription =
        "Jetzt abgießen: Bio Sencha Hisui, Aufguss 2 von 4, 7 Sekunden überzogen. Zum Timer"

    private var brew by mutableStateOf(running)
    private var clicks = 0

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = false) {
                ActiveBrewBar(brew = brew, progress = { 1f }, onClick = { clicks++ })
            }
        }
        compose.mainClock.advanceTimeBy(BAR_SETTLED_MILLIS)
    }

    /** The bar's background, left of the mini ring, at the bar's vertical centre. */
    private fun barBackground(): Color {
        val node = compose.onNodeWithContentDescription("Bio Sencha Hisui", substring = true).fetchSemanticsNode()
        val bounds = node.boundsInWindow
        return compose.windowBitmap().colorAt(bounds.left + EDGE_PX, bounds.center.y)
    }

    @Test
    fun `entering ALERTING turns the bar matcha on the next frame`() {
        show()
        assertThat(barBackground().distanceTo(WashiColorScheme.surfaceContainerHigh)).isLessThan(SAME)

        brew = alerting
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()

        assertThat(barBackground().distanceTo(alert.matcha)).isLessThan(SAME)
    }

    @Test
    fun `the alerting bar does not pulse`() {
        brew = alerting
        show()
        assertThat(barBackground().distanceTo(alert.matcha)).isLessThan(SAME)
        compose.mainClock.advanceTimeBy(HALF_PULSE_MILLIS)
        assertThat(barBackground().distanceTo(alert.matcha)).isLessThan(SAME)
    }

    @Test
    fun `the alerting bar says Jetzt abgießen with the overtime, and a tap opens the timer`() {
        brew = alerting
        show()
        val bar = compose.onNodeWithContentDescription(alertingDescription).assertHasClickAction()
        bar.performClick()
        assertThat(clicks).isEqualTo(1)
    }

    private companion object {
        const val SAME = 0.04f
        const val EDGE_PX = 4f
        const val BAR_SETTLED_MILLIS = 1_000L
        const val HALF_PULSE_MILLIS = 750L
    }
}
