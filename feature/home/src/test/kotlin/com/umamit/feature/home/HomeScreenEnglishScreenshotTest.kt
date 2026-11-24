package com.umamit.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.lowStockCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Start (SCR-02) in English (GEN-10, #359), light and dark at font scale 1.0: the seeded list, the empty state and
 * the low-stock card. The fixtures are [HomeScreenScreenshotTest]'s; the tea names are data and stay as they are.
 * Placeholder images only. Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp-xhdpi")
class HomeScreenEnglishScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))

        private const val NOW = 1_000_000L
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = if (dark) "dark" else "light"

    private fun capture(name: String, state: HomeUiState) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                HomeScreen(state = state, nowElapsedMs = { NOW })
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/HomeEn_${name}_$variant.png")
    }

    @Test
    fun seeded() = capture("seeded", HomeScreenScreenshotTest.seeded)

    @Test
    fun empty() = capture("empty", HomeUiState(loading = false, collectionEmpty = true))

    @Test
    fun lowStock() = capture(
        "lowStock",
        HomeScreenScreenshotTest.seeded.copy(lowStock = lowStockCard(teaName = "Bio Sencha Tanabata", grams = 14.0)),
    )

    @Test
    fun activeSession() =
        capture("activeSession", HomeScreenScreenshotTest.seeded.copy(active = HomeScreenScreenshotTest.running))
}
