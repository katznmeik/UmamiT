package com.umamit.feature.brew.setup

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.KettleWater
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockLevel
import com.umamit.core.timer.engine.PlannedStep
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The setup (SCR-04) in English (GEN-10, #359), light and dark at font scale 1.0: the Kyusu setup and a cold brew
 * with low stock. The fixtures are [BrewSetupScreenshotTest]'s; the tea, recipe and vessel names are data and stay as
 * they are. Placeholder images only. Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp-xhdpi")
class BrewSetupEnglishScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = if (dark) "dark" else "light"

    private fun capture(name: String, state: BrewSetupUiState) {
        compose.setContent { UmamiTheme(darkTheme = dark, reduceMotion = true) { BrewSetupScreen(state) } }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/BrewSetupEn_${name}_$variant.png")
    }

    @Test
    fun setup() = capture("kyusu", BrewSetupScreenshotTest.kyusu)

    @Test
    fun coldBrewLowStock() = capture(
        "coldBrew",
        BrewSetupScreenshotTest.kyusu.copy(
            stockGrams = 14.0,
            stockLevel = StockLevel.LOW,
            selectedRecipeId = "c",
            method = BrewMethod.COLD_BREW,
            selectedVesselId = null,
            waterMl = 1000,
            leafGrams = 10.0,
            ratioPer100Ml = 1.0,
            baseTempC = 5,
            kettle = Kettle(5, KettleWater.COLD),
            steps = listOf(PlannedStep(StepKind.INFUSION, 14_400, 5)),
        ),
    )
}
