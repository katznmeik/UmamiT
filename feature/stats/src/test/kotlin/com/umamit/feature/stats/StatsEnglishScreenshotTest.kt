package com.umamit.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * Statistik (SCR-18) in English (GEN-13, #359), light and dark at font scale 1.0: the shell with the *Overview* tab
 * over invented results (90 d), and with a chosen month span. Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp-xhdpi")
class StatsEnglishScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = if (dark) "dark" else "light"

    private val bounds = MonthSpan(YearMonth.of(2025, 3), YearMonth.of(2026, 11))
    private val span = MonthSpan(YearMonth.of(2025, 12), YearMonth.of(2026, 8))

    private fun capture(name: String, state: StatsUiState.Data) {
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    StatsContent(
                        state = state,
                        onRangeChange = {},
                        onTabChange = {},
                        onOpenSettings = {},
                    ) { _, range, _ ->
                        OverviewTab(
                            figures = TabFigures(range, OverviewFixtures.data()),
                            scrollState = rememberScrollState(),
                            onOpenTea = {},
                            thumbnail = { null },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/StatsEn_${name}_$variant.png")
    }

    @Test
    fun overview90Days() =
        capture("overview", StatsUiState.Data(StatsRange.DAYS_90, StatsTab.OVERVIEW, months = bounds))

    @Test
    fun monthsChosen() = capture(
        "months",
        StatsUiState.Data(StatsRange.CUSTOM, StatsTab.OVERVIEW, custom = span, months = bounds),
    )
}
