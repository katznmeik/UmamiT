package com.umamit.feature.stats.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
        // High above every bar (the plot's top edge, over the shortest one): the column still counts.
        chart().performTouchInput { click(Offset(width * (2 + 0.5f) / 5, 1f)) }
        compose.onNodeWithText("KW 43 · 5 Sitzungen").assertDoesNotExist()
        compose.onNodeWithText("KW 42 · 2 Sitzungen").assertExists()
    }

    @Test
    fun `the value label fades out over 0,1 s and is gone at once with reduce motion`() {
        show(reduceMotion = false) {
            PeriodBarChart(weeks, ChartUnit.SESSIONS, "Sitzungen pro Woche", animateIn = false)
        }
        tapBar(index = 4, count = 5)
        compose.waitForIdle()
        compose.onNodeWithText("KW 44 · 6 Sitzungen").assertExists()

        compose.mainClock.autoAdvance = false
        tapBar(index = 4, count = 5)
        compose.mainClock.advanceTimeBy(FRAME_MS * 2)
        // Still fading out.
        compose.onNodeWithText("KW 44 · 6 Sitzungen").assertExists()
        compose.mainClock.advanceTimeBy(FADE_SETTLE_MS)
        compose.onNodeWithText("KW 44 · 6 Sitzungen").assertDoesNotExist()
    }

    @Test
    fun `a zero bar can be tapped`() {
        val entries = listOf(PeriodValue("KW 40", 3.0), PeriodValue("KW 41", 0.0), PeriodValue("KW 42", 5.0))
        show { PeriodBarChart(entries, ChartUnit.SESSIONS, "Sitzungen pro Woche") }
        tapBar(index = 1, count = 3, yFraction = 0.9f)
        compose.onNodeWithText("KW 41 · 0 Sitzungen").assertExists()
    }

    @Test
    fun `a tap outside the chart hides the value label`() {
        val holder = ChartSelectionHolder()
        show {
            ChartSelectionHost(holder) {
                Column {
                    PeriodBarChart(weeks, ChartUnit.SESSIONS, "Sitzungen pro Woche")
                    Box(Modifier.size(40.dp).testTag("outside"))
                }
            }
        }
        tapBar(index = 4, count = 5)
        compose.onNodeWithText("KW 44 · 6 Sitzungen").assertExists()
        compose.onNodeWithTag("outside").performTouchInput { click() }
        compose.onNodeWithText("KW 44 · 6 Sitzungen").assertDoesNotExist()
        // A second tap on the same bar shows it again (the outside tap did not leave a stale selection).
        tapBar(index = 4, count = 5)
        compose.onNodeWithText("KW 44 · 6 Sitzungen").assertExists()
    }

    @Test
    fun `only one chart shows a value label at a time`() {
        val holder = ChartSelectionHolder()
        show {
            ChartSelectionHost(holder) {
                Column {
                    PeriodBarChart(weeks, ChartUnit.SESSIONS, "Eins", Modifier.testTag("one"))
                    PeriodBarChart(weeks, ChartUnit.SESSIONS, "Zwei", Modifier.testTag("two"))
                }
            }
        }
        compose.onAllNodesWithTag(STATS_CHART_TAG)[0].performTouchInput { click(Offset(width * 0.9f, height * 0.75f)) }
        compose.onAllNodesWithText("KW 44 · 6 Sitzungen").assertCountEquals(1)
        compose.onAllNodesWithTag(STATS_CHART_TAG)[1].performTouchInput { click(Offset(width * 0.9f, height * 0.75f)) }
        compose.onAllNodesWithText("KW 44 · 6 Sitzungen").assertCountEquals(1)
    }

    @Test
    fun `a chart grows once per page, not again when the page comes back`() {
        val page = ChartPageState("OVERVIEW", mutableStateListOf(), active = false)
        var shown by mutableStateOf(true)
        show(reduceMotion = false) {
            CompositionLocalProvider(LocalChartPage provides page) {
                Box(Modifier.fillMaxSize().chartViewport(page)) {
                    if (shown) PeriodBarChart(weeks, ChartUnit.SESSIONS, "Sitzungen pro Woche")
                }
            }
        }
        // A page that is not the settled one does not grow its charts.
        assertThat(page.growStarts).isEqualTo(0)
        page.active = true
        compose.waitForIdle()
        assertThat(page.growStarts).isEqualTo(1)
        shown = false
        compose.waitForIdle()
        shown = true
        compose.waitForIdle()
        assertThat(page.growStarts).isEqualTo(1)
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
        assertThat(description).isEqualTo("Gramm. Okt.: 1.235 g. Nov.: 1,0 g.")
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
            DonutSlice("Sencha", 21, 42, Color(0xFF7C9A5B)),
            DonutSlice("Gyokuro", 21, 42, Color(0xFF5F7F45)),
            DonutSlice("Andere", 8, 16, isOther = true),
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
            reduced = rememberChartReveal(count = 5, key = "t", reduceMotion = true)
            normal = rememberChartReveal(count = 5, key = "t", reduceMotion = false)
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
            reveal = rememberChartReveal(count = 5, key = "t", animateIn = false, reduceMotion = false)
            Box(Modifier.height(1.dp))
        }
        compose.mainClock.advanceTimeByFrame()
        assertThat(reveal!!.fraction(4)).isEqualTo(1f)
        assertThat(reveal!!.alpha).isEqualTo(1f)
    }
}

private const val FRAME_MS = 16L
private const val FADE_SETTLE_MS = 300L
