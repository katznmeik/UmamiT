package com.umamit.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.MonthSpan
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * The shell's *Monate* segment, the open *Monate* sheet and a folded section (SCR-18, INS-07, INS-08, D-115 A1 R7,
 * R8) in light and dark at font scale 1.0 and 2.0 (03 §6). Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class StatsShellScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private val bounds = MonthSpan(YearMonth.of(2025, 3), YearMonth.of(2026, 11))
    private val span = MonthSpan(YearMonth.of(2025, 12), YearMonth.of(2026, 8))

    private fun capture(name: String, state: StatsUiState.Data, onShown: () -> Unit = {}) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Shell(state)
                }
            }
        }
        compose.waitForIdle()
        onShown()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/StatsShell_${name}_$variant.png")
    }

    @Composable
    private fun Shell(state: StatsUiState.Data) {
        StatsContent(
            state = state,
            onRangeChange = {},
            onTabChange = {},
            onOpenSettings = {},
        ) { _, range, scrollState ->
            OverviewTab(
                figures = TabFigures(range, OverviewFixtures.data()),
                scrollState = rememberScrollState(),
                onOpenTea = {},
                thumbnail = { null },
            )
        }
    }

    @Test
    fun monthsChosenAcrossYears() = capture(
        "months_years",
        StatsUiState.Data(StatsRange.CUSTOM, StatsTab.OVERVIEW, custom = span, months = bounds),
    )

    @Test
    fun foldedSections() = capture(
        "folded",
        StatsUiState.Data(
            StatsRange.DAYS_90,
            StatsTab.OVERVIEW,
            months = bounds,
            folded = setOf("overview.streak", "overview.most_brewed"),
        ),
    )

    @Test
    fun monthsSheet() = capture(
        "months_sheet",
        StatsUiState.Data(StatsRange.CUSTOM, StatsTab.OVERVIEW, custom = span, months = bounds),
    ) { compose.onNodeWithText("Dez.\u00A025–Aug.\u00A026").performClick() }
}

/** The selector at the narrowest phone and the font scale cap 1,15: the five segments fit (INS-07, R7). */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h740dp-xhdpi")
class StatsSelectorNarrowScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val bounds = MonthSpan(YearMonth.of(2025, 3), YearMonth.of(2026, 11))

    private fun capture(name: String, range: StatsRange, custom: MonthSpan?) {
        RuntimeEnvironment.setFontScale(1.15f)
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    StatsContent(
                        state = StatsUiState.Data(range, StatsTab.OVERVIEW, custom = custom, months = bounds),
                        onRangeChange = {},
                        onTabChange = {},
                        onOpenSettings = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/StatsSelector_${name}_light_font1_15.png")
    }

    @Test
    fun withoutMonths() = capture("plain", StatsRange.DAYS_90, null)

    @Test
    fun monthsAcrossYears() =
        capture("years", StatsRange.CUSTOM, MonthSpan(YearMonth.of(2025, 12), YearMonth.of(2026, 8)))

    @Test
    fun monthsInOneYear() = capture("year", StatsRange.CUSTOM, MonthSpan(YearMonth.of(2026, 1), YearMonth.of(2026, 8)))
}
