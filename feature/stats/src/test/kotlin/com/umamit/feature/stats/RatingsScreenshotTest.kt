package com.umamit.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.stats.RatingsStats
import com.umamit.core.model.StatsRange
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** *Bewertungen* (SCR-18) with data, without ratings and for an empty range; light, dark, font 1.0 and 2.0 (03 §6). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h3000dp-xhdpi")
class RatingsScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

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

    private fun capture(name: String, stats: RatingsStats) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            Themed {
                RatingsTab(TabFigures(StatsRange.DAYS_90, stats), rememberScrollState(), onOpenTea = {
                }, thumbnail = { null })
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/Ratings_${name}_$variant.png")
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) = UmamiTheme(darkTheme = dark, reduceMotion = true) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }

    @Test
    fun withData() = capture("data", RatingsFixtures.withData)

    @Test
    fun withoutRatings() = capture("unrated", RatingsFixtures.withoutRatings)

    @Test
    fun emptyRange() = capture("empty", RatingsFixtures.emptyRange)
}
