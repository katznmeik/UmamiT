package com.umamit.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.stats.OverviewStats
import com.umamit.core.model.StatsRange
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * *Überblick* (SCR-18) with invented results, the whole tab on one tall page: 90 T with data, Gesamt (monthly, the
 * scrollable chart opened on the current month) and an empty range; light and dark at font scale 1.0 and 2.0 (03 §6).
 * Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A tall screen, so the whole tab is on one page.
@Config(sdk = [36], qualifiers = "w411dp-h3600dp-xhdpi")
class OverviewScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

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

    private fun shot(name: String, stats: OverviewStats, range: StatsRange) {
        RuntimeEnvironment.setFontScale(fontScale)
        val height = if (fontScale > 1f) 4400.dp else 2500.dp
        compose.setContent { Page(stats, range, height) }
        compose.waitForIdle()
        compose.onNodeWithTag("shot")
            .captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/Overview_${name}_$variant.png")
    }

    @Composable
    private fun Page(stats: OverviewStats, range: StatsRange, height: androidx.compose.ui.unit.Dp) {
        UmamiTheme(darkTheme = dark, reduceMotion = true) {
            Box(
                Modifier.width(411.dp).height(height).background(MaterialTheme.colorScheme.background).testTag("shot"),
            ) {
                OverviewTab(
                    figures = TabFigures(range, stats),
                    scrollState = rememberScrollState(),
                    onOpenTea = {},
                    thumbnail = { null },
                )
            }
        }
    }

    @Test
    fun days90() = shot("days90", OverviewFixtures.data(), StatsRange.DAYS_90)

    @Test
    fun gesamt() = shot("gesamt", OverviewFixtures.data(OverviewFixtures.monthly()), StatsRange.ALL)

    @Test
    fun emptyRange() = shot("empty", OverviewFixtures.emptyRange(), StatsRange.DAYS_30)
}
