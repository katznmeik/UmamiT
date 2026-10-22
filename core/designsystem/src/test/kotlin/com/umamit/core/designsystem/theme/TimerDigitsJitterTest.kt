package com.umamit.core.designsystem.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Digit jitter (03 §3.2): the timer digits must keep their width for every digit
 * 0–9 in every position, so a counting timer never shifts sideways.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class TimerDigitsJitterTest {

    @get:Rule
    val compose = createComposeRule()

    private val digits = 0..9

    /** „m:ss" and „h:mm:ss" shapes (04 §5) with the same digit in every position. */
    private fun timerTexts(d: Int) = listOf("$d:$d$d", "$d$d:$d$d", "$d:$d$d:$d$d")

    private fun measuredWidths(style: @Composable () -> TextStyle): Map<String, List<Int>> {
        var widths: Map<String, List<Int>> = emptyMap()
        compose.setContent {
            UmamiTheme(darkTheme = true) {
                val measurer = rememberTextMeasurer()
                val resolved = style()
                widths = digits.flatMap(::timerTexts)
                    .groupBy { it.length }
                    .mapKeys { (length, _) -> "length $length" }
                    .mapValues { (_, texts) -> texts.map { measurer.measure(it, resolved).size.width } }
            }
        }
        compose.waitForIdle()
        return widths
    }

    @Test
    fun `timer digits keep the same width for every digit in every position`() {
        val widths = measuredWidths { UmamiTheme.textStyles.timerDigits }

        widths.forEach { (shape, values) ->
            assertWithMessage(shape).that(values.toSet()).hasSize(1)
        }
    }

    /**
     * Guards the test above: Cormorant Garamond's default figures are proportional,
     * so without `tnum` the widths differ. If this fails, the bundled font was not
     * used (the system fallback has tabular figures anyway) and the test above
     * would prove nothing.
     */
    @Test
    fun `without the tabular figure feature the bundled serif digits would jitter`() {
        val widths = measuredWidths { UmamiTheme.textStyles.timerDigits.copy(fontFeatureSettings = null) }

        widths.forEach { (shape, values) ->
            assertWithMessage(shape).that(values.toSet().size).isGreaterThan(1)
        }
    }

    @Test
    fun `timer digits render all digits without jitter`() {
        compose.setContent {
            UmamiTheme(darkTheme = true) {
                DigitColumn()
            }
        }
        compose.onRoot().captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/TimerDigits_all.png")
    }
}

@Composable
private fun DigitColumn() {
    Surface {
        Row {
            listOf(0..4, 5..9).forEach { digits ->
                Column {
                    digits.forEach { d ->
                        Text(text = "$d$d:$d$d", style = UmamiTheme.textStyles.timerDigits)
                    }
                }
            }
        }
    }
}
