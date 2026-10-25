package com.umamit.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
import com.umamit.core.timer.reliability.ReliabilityStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Einrichtung (SCR-01) in light and dark at font scale 1.0 and 2.0 (03 §6): the welcome, the
 * reliability page with two missing checks while the seed photos load, and the catalog page
 * (ONB-03) before and during the load. Placeholder art only.
 * Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class OnboardingScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private val TWO_MISSING = ReliabilityStatus(
            ReliabilityCheck.entries.associateWith { CheckState.Ok } +
                mapOf(
                    ReliabilityCheck.Battery to CheckState.Missing,
                    ReliabilityCheck.FullScreenAlert to CheckState.Missing,
                ),
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, page: OnboardingPage, catalogLoad: CatalogRefreshState? = null) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                OnboardingScreen(
                    page = page,
                    status = TWO_MISSING,
                    photosLoading = true,
                    actions = OnboardingActions(),
                    catalogLoad = catalogLoad,
                )
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/Onboarding_${name}_$variant.png")
    }

    @Test
    fun welcome() = capture("welcome", OnboardingPage.Welcome)

    @Test
    fun reliability() = capture("reliability", OnboardingPage.Reliability)

    @Test
    fun catalog() = capture("catalog", OnboardingPage.Catalog)

    @Test
    fun catalogLoading() = capture(
        "catalogLoading",
        OnboardingPage.Catalog,
        CatalogRefreshState.Running(CatalogRefreshProgress(pagesDone = 312, pagesTotal = 1560, products = 54)),
    )
}
