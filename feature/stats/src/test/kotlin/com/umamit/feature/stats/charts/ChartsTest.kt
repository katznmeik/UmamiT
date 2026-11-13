package com.umamit.feature.stats.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.cos
import kotlin.math.sin

/**
 * Behaviour of the charts (INS-08, SCR-18): the value label shows on a tap and hides on a second tap or a tap
 * elsewhere, TalkBack gets every period's value in one node, and reduce motion skips the grow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ChartsTest {

    @get:Rule
    val compose = createComposeRule()

    private val weeks = listOf(3.0, 4.0, 2.0, 5.0, 6.0).mapIndexed { index, value ->
        PeriodValue("KW ${40 + index}", value)
    }

    private fun show(reduceMotion: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent {
            UmamiTheme(reduceMotion = reduceMotion) { Box(Modifier.width(300.dp)) { content() } }
        }
        compose.waitForIdle()
    }

    private fun chart() = compose.onNodeWithTag(STATS_CHART_TAG)

    private fun tapBar(index: Int, count: Int, yFraction: Float = 0.75f) = chart().performTouchInput {
        click(Offset(width * (index + 0.5f) / count, height * yFraction))
    }

    @Test
    fun `a tap on a bar shows its value label, a second tap hides it`() {
        show { PeriodBarChart(weeks, ChartUnit.SESSIONS, "Sitzungen pro Woche") }
        compose.onNodeWithText("KW 44 · 6 Sitzungen").assertDoesNotExist()
        tapBar(index = 4, count = 5)
        compose.onNodeWithText("KW 44 · 6 Sitzungen").assertExists()
        tapBar(index = 4, count = 5)
        compose.onNodeWithText("KW 44 · 6 Sitzungen").assertDoesNotExist()
    }

    @Test
    fun `a tap elsewhere in the chart hides the value label and a tap on another bar moves it`() {
        show { PeriodBarChart(weeks, ChartUnit.SESSIONS, "Sitzungen pro Woche") }
        tapBar(index = 4, count = 5)
        compose.onNodeWithText("KW 44 · 6 Sitzungen").assertExists()
        tapBar(index = 3, count = 5)
        compose.onNodeWithText("KW 44 · 6 Sitzungen").assertDoesNotExist()
        compose.onNodeWithText("KW 43 · 5 Sitzungen").assertExists()
        // High above every bar (the plot's top edge, over the shortest one).
        chart().performTouchInput { click(Offset(width * (2 + 0.5f) / 5, 1f)) }
        compose.onNodeWithText("KW 43 · 5 Sitzungen").assertDoesNotExist()
    }

    @Test
    fun `the chart is one node that reads every period`() {
        show { PeriodBarChart(weeks, ChartUnit.SESSIONS, "Sitzungen pro Woche") }
        val description = chart().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertThat(description).isEqualTo(
            "Sitzungen pro Woche. KW 40: 3 Sitzungen. KW 41: 4 Sitzungen. KW 42: 2 Sitzungen. " +
                "KW 43: 5 Sitzungen. KW 44: 6 Sitzungen.",
        )
    }

    @Test
    fun `units format in German`() {
        show {
            PeriodBarChart(listOf(PeriodValue("Okt.", 1234.5), PeriodValue("Nov.", 1.0)), ChartUnit.GRAMS, "Gramm")
        }
        val description = chart().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertThat(description).isEqualTo("Gramm. Okt.: 1.234,5 g. Nov.: 1 g.")
    }

    @Test
    fun `the rating line reads gaps and a tap on a point shows its value`() {
        val points = listOf(
            RatingPoint("KW 41", 4.0),
            RatingPoint("KW 42", null),
            RatingPoint("KW 43", 4.5),
        )
        show { RatingLineChart(points, "Bewertung im Verlauf") }
        val description = chart().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertThat(description).isEqualTo("Bewertung im Verlauf. KW 41: 4,0 ★. KW 42: keine Bewertung. KW 43: 4,5 ★.")
        chart().performTouchInput {
            // 4.5 of 1–5 sits at 7/8 of the plot's height; the plot's bottom band holds the x labels.
            click(Offset(width * 2.5f / 3, 0.145f * height))
        }
        compose.onNodeWithText("KW 43 · 4,5 ★").assertExists()
    }

    @Test
    fun `a tap on a donut slice shows Sorte, share and sessions`() {
        val slices = listOf(
            DonutSlice("Sencha", 21, Color(0xFF7C9A5B)),
            DonutSlice("Gyokuro", 21, Color(0xFF5F7F45)),
            DonutSlice("Andere", 8, isOther = true),
        )
        show { SortenDonut(slices, "Tee-Sorten") }
        val description = chart().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertThat(
            description,
        ).isEqualTo("Tee-Sorten. Sencha: 42 %, 21 Sitzungen. Gyokuro: 42 %, 21 Sitzungen. Andere: 16 %, 8 Sitzungen.")
        chart().performTouchInput {
            // 45° clockwise from 12 o'clock is inside the first slice (0°–151°), on the ring.
            val radius = width * 58f / 148f
            val angle = Math.toRadians(45.0)
            click(Offset(width / 2f + radius * sin(angle).toFloat(), height / 2f - radius * cos(angle).toFloat()))
        }
        compose.onNodeWithText("Sencha · 42 % · 21 Sitzungen").assertExists()
    }

    @Test
    fun `reduce motion skips the grow and only fades`() {
        var reduced: ChartReveal? = null
        var normal: ChartReveal? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            reduced = rememberChartReveal(count = 5, reduceMotion = true)
            normal = rememberChartReveal(count = 5, reduceMotion = false)
            Box(Modifier.height(1.dp))
        }
        compose.mainClock.advanceTimeByFrame()
        assertThat(reduced!!.fraction(0)).isEqualTo(1f)
        assertThat(reduced!!.fraction(4)).isEqualTo(1f)
        assertThat(normal!!.fraction(0)).isLessThan(0.5f)
        assertThat(normal!!.fraction(4)).isEqualTo(0f)
        compose.mainClock.advanceTimeBy(150)
        assertThat(reduced!!.alpha).isEqualTo(1f)
        assertThat(normal!!.fraction(4)).isGreaterThan(0f)
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(normal!!.fraction(0)).isEqualTo(1f)
        assertThat(normal!!.fraction(4)).isEqualTo(1f)
    }

    @Test
    fun `a chart shown again in the same visit starts grown`() {
        var reveal: ChartReveal? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            reveal = rememberChartReveal(count = 5, animateIn = false, reduceMotion = false)
            Box(Modifier.height(1.dp))
        }
        compose.mainClock.advanceTimeByFrame()
        assertThat(reveal!!.fraction(4)).isEqualTo(1f)
        assertThat(reveal!!.alpha).isEqualTo(1f)
    }
}
