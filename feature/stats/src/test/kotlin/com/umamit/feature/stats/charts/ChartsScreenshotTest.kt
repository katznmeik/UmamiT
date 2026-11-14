package com.umamit.feature.stats.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The chart components of SCR-18 with placeholder data, in light and dark at font scale 1.0 and 2.0 (03 §6):
 * 13 and 30 weekly bars, a scrolled Gesamt chart, the rating line with a gap, the donut with 6 slices and with 1,
 * both habit rows. Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ChartsScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private val MONTHS =
            listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez.")
        private val SORTEN = listOf("Sencha", "Gyokuro", "Genmaicha", "Hojicha", "Matcha", "Kukicha")
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun shot(name: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                Box(
                    Modifier.width(
                        360.dp,
                    ).background(MaterialTheme.colorScheme.background).padding(16.dp).testTag("shot"),
                ) {
                    content()
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(
            "shot",
        ).captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/Chart_${name}_$variant.png")
    }

    private fun weeks(count: Int) = List(count) { index ->
        PeriodValue("KW ${45 - count + index + 1}", (2 + (index * 7) % 9).toDouble())
    }

    @Test
    fun bars13() = shot("bars13") { PeriodBarChart(weeks(13), ChartUnit.SESSIONS, "Sitzungen pro Woche") }

    @Test
    fun bars30() = shot("bars30") { PeriodBarChart(weeks(30), ChartUnit.SESSIONS, "Sitzungen pro Woche") }

    @Test
    fun barsGesamtScrolled() = shot("barsGesamt") {
        val months =
            List(40) {
                PeriodValue(MONTHS[it % 12], (10 + (it * 13) % 41).toDouble(), "${MONTHS[it % 12]} ${2023 + it / 12}")
            }
        PeriodBarChart(months, ChartUnit.GRAMS, "Gramm pro Monat", scrollable = true)
    }

    @Test
    fun ratingLineWithGap() = shot("line") {
        val ratings = listOf(3.5, 4.0, null, null, 4.5, 4.0, 5.0, null, 3.0, 4.5, 4.0, 4.5, 5.0)
        RatingLineChart(
            ratings.mapIndexed { index, rating ->
                RatingPoint("KW ${32 + index}", rating)
            },
            "Bewertung im Verlauf",
        )
    }

    @Test
    fun donut6() = shot("donut6") {
        val counts = listOf(21, 13, 8, 5, 3, 4)
        val percents = counts.map { (it * 100.0 / counts.sum()).roundToInt() }
        val tea = com.umamit.core.designsystem.theme.DefaultExtendedColors.teaTypes
        val own = listOf(tea.sencha, tea.gyokuro, tea.genmaicha, tea.hojicha, null)
        SortenDonut(
            List(6) { index ->
                if (index ==
                    5
                ) {
                    DonutSlice("Andere", counts[5], percents[5], isOther = true)
                } else {
                    DonutSlice(SORTEN[index], counts[index], percents[index], own[index])
                }
            },
            "Tee-Sorten",
        )
    }

    @Test
    fun donut1() = shot("donut1") {
        SortenDonut(listOf(DonutSlice("Sencha", 12, 100, Color(0xFF7C9A5B))), "Tee-Sorten")
    }

    @Test
    fun habitsDayPart() = shot("habitsDayPart") {
        HabitBars(
            listOf(
                HabitShare("Morgen", 0.46),
                HabitShare("Mittag", 0.12),
                HabitShare("Nachmittag", 0.30),
                HabitShare("Abend", 0.12),
            ),
            "Tageszeit",
        )
    }

    @Test
    fun habitsWeekday() = shot("habitsWeekday") {
        HabitBars(
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").zip(listOf(0.18, 0.12, 0.10, 0.14, 0.16, 0.20, 0.10))
                .map { HabitShare(it.first, it.second) },
            "Wochentag",
        )
    }
}
