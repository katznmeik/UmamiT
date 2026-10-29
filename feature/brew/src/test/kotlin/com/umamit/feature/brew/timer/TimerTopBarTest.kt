package com.umamit.feature.brew.timer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.component.AlertSurface
import com.umamit.core.designsystem.theme.AlertColors
import com.umamit.core.designsystem.theme.SteepingTheme
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-05 „Top-bar icons" (D-093 point 2, #85): ← and ⋮ sit on a 40 dp circle
 * of the Steeping surface at 60 % opacity and keep at least 3:1 against it
 * over any photo, a pure white one included, in both app themes; the touch
 * targets stay 48 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerTopBarTest {

    @get:Rule
    val compose = createComposeRule()

    /** WCAG 2.x contrast ratio of two opaque colours. */
    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    /** Pure white and black, the primaries and a grey ramp: what a tea photo can put behind a circle. */
    private val photoColors: List<Color> = listOf(
        Color.White,
        Color.Black,
        Color.Red,
        Color.Green,
        Color.Blue,
        Color.Yellow,
        Color.Cyan,
        Color.Magenta,
    ) + (0..255 step 15).map { Color(it, it, it) }

    private fun steepingColors(appDark: Boolean): TimerTopBarColors {
        lateinit var colors: TimerTopBarColors
        compose.setContent {
            UmamiTheme(darkTheme = appDark, reduceMotion = true) {
                SteepingTheme(reduceMotion = true) { colors = TimerTopBarDefaults.colors() }
            }
        }
        compose.waitForIdle()
        return colors
    }

    private fun assertLegibleOverAnyPhoto(colors: TimerTopBarColors) {
        assertThat(colors.circle.alpha).isWithin(0.001f).of(0.6f)
        photoColors.forEach { photo ->
            val circle = colors.circle.compositeOver(photo)
            assertThat(contrast(colors.icon, circle)).isAtLeast(3.0)
        }
    }

    @Test
    fun `the icons keep 3 to 1 against their circle over any photo in the light app theme`() {
        assertLegibleOverAnyPhoto(steepingColors(appDark = false))
    }

    @Test
    fun `the icons keep 3 to 1 against their circle over any photo in the dark app theme`() {
        assertLegibleOverAnyPhoto(steepingColors(appDark = true))
    }

    @Test
    fun `on a pure white photo the icon is at least 3 to 1 against its circle`() {
        val colors = steepingColors(appDark = false)
        // Worked example (03 §3.1): #232B24 at 60 % over white is ≈ #7B807C; #ECE8DE on it is ≈ 3.3:1.
        val ratio = contrast(colors.icon, colors.circle.compositeOver(Color.White))
        assertThat(ratio).isWithin(0.1).of(3.3)
    }

    @Test
    fun `on the alert surface the circle takes the alert's container colour in both pulse phases`() {
        lateinit var colors: TimerTopBarColors
        lateinit var alert: AlertColors
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                SteepingTheme(reduceMotion = true) {
                    alert = UmamiTheme.extendedColors.alert
                    AlertSurface { colors = TimerTopBarDefaults.colors() }
                }
            }
        }
        compose.waitForIdle()
        assertThat(colors.circle).isEqualTo(alert.content)
        assertThat(colors.icon).isEqualTo(alert.washi)
        listOf(alert.washi, alert.matcha).forEach { phase ->
            assertThat(contrast(colors.icon, colors.circle.compositeOver(phase))).isAtLeast(3.0)
        }
    }

    @Test
    fun `the touch targets stay 48 dp`() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TimerScreen(
                    state = TimerStates.running(),
                    nowElapsedMs = { NOW },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                )
            }
        }
        listOf("Zurück", "Weitere Optionen").forEach { label ->
            compose.onNodeWithContentDescription(label)
                .assertTouchWidthIsEqualTo(48.dp)
                .assertTouchHeightIsEqualTo(48.dp)
        }
    }
}
