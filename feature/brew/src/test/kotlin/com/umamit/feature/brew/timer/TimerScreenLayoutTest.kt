package com.umamit.feature.brew.timer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.component.TimerRingColors
import com.umamit.core.designsystem.component.TimerRingDefaults
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.SteepingTheme
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SCR-05's layout and colours: the pill stays where the finger is (spatial
 * consistency, TMR-03), overlays survive a configuration change (GEN-07), and
 * the paused ring keeps its text readable (03 §6).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerScreenLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf<TimerUiState.Active>(TimerStates.ready())

    private fun show(initial: TimerUiState.Active) {
        state = initial
        val start = compose.mainClock.currentTime
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TimerScreen(
                    state = state,
                    nowElapsedMs = { NOW + compose.mainClock.currentTime - start },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    reliabilityProblem = true,
                    teaPhoto = painterResource(UmamiIcons.LandscapePlaceholder),
                )
            }
        }
    }

    private fun top(text: String): Dp = compose.onNodeWithText(text).getUnclippedBoundsInRoot().top

    @Test
    fun `the pill does not move at Start, at the alert, nor when the overtime turns large`() {
        show(TimerStates.ready())
        val ready = top("Start")

        state = TimerStates.running()
        compose.waitForIdle()
        val running = top("Pause")

        state = TimerStates.alerting(overtimeMs = 7_000)
        compose.waitForIdle()
        val alert = top("Abgegossen")

        compose.mainClock.advanceTimeBy(8_000)
        compose.onNodeWithText("+0:15 überzogen", useUnmergedTree = true).assertIsDisplayed()
        val prominent = top("Abgegossen")

        assertThat(listOf(running, alert, prominent)).containsExactly(ready, ready, ready)
    }

    @Test
    fun `the discard question and the Schritte-Sheet survive a configuration change`() {
        val restoration = StateRestorationTester(compose)
        state = TimerStates.running(stepIndex = 1)
        restoration.setContent {
            UmamiTheme(reduceMotion = true) {
                TimerScreen(
                    state = state,
                    nowElapsedMs = { NOW },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Sitzung verwerfen").performClick()
        compose.onNodeWithText("Nichts wird gespeichert.").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Nichts wird gespeichert.").assertIsDisplayed()
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    @Test
    fun `the paused ring dims its stroke and liquor, while digits and caption keep at least 4,5 to 1`() {
        lateinit var colors: TimerRingColors
        var background = Color.Unspecified
        var liquor = Color.Unspecified
        compose.setContent {
            SteepingTheme(reduceMotion = true) {
                colors = TimerRingDefaults.colors()
                background = MaterialTheme.colorScheme.background
                liquor = UmamiTheme.extendedColors.liquor.green
            }
        }
        compose.waitForIdle()

        val paused = colors.dimmed(0.6f)
        val underText = liquor.copy(alpha = paused.liquorAlpha).compositeOver(background)

        assertThat(paused.liquorAlpha).isLessThan(colors.liquorAlpha)
        assertThat(paused.track.alpha).isLessThan(colors.track.alpha)
        assertThat(paused.content).isEqualTo(colors.content)
        assertThat(paused.caption).isEqualTo(colors.caption)
        assertThat(contrast(paused.caption, underText)).isAtLeast(4.5)
        assertThat(contrast(paused.content, underText)).isAtLeast(4.5)
    }

    @Test
    fun `dimming never makes a darker phone brighter (TMR-17)`() {
        assertThat(dimmedBrightness(systemLevel = 0.8f)).isEqualTo(DIMMED_BRIGHTNESS)
        assertThat(dimmedBrightness(systemLevel = 0.05f)).isEqualTo(0.05f)
        assertThat(dimmedBrightness(systemLevel = null)).isEqualTo(DIMMED_BRIGHTNESS)
    }
}
