package com.umamit.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The *Statistik* screen (SCR-18) in light and dark at font scale 1.0 and 2.0 (03 §6): the empty state (A9) and the
 * shell with its range selector, tabs and placeholder tab content. Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class StatsScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

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

    private fun capture(name: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/Stats_${name}_$variant.png")
    }

    @Test
    fun empty() = capture("empty") {
        StatsContent(
            state = StatsUiState.Empty,
            onRangeChange = {},
            onTabChange = {},
            onOpenSettings = {},
        )
    }

    @Test
    fun shell() = capture("shell") {
        StatsContent(
            state = StatsUiState.Data(StatsRange.DAYS_90, StatsTab.OVERVIEW),
            onRangeChange = {},
            onTabChange = {},
            onOpenSettings = {},
        )
    }
}
