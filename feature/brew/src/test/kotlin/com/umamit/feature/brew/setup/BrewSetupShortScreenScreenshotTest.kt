package com.umamit.feature.brew.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StockLevel
import com.umamit.feature.brew.setup.BrewSetupScreenshotTest.Companion.kyusu
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The setup (SCR-04) of v0.4.0 on a short screen (360 × 640 dp) at font scale 2.0 (03 §6, D-100): the stock
 * warning and a recipe's archived vessel wrap without losing the ⋮, and the *Gefäß* list reaches *Neues Gefäß …*.
 * Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2.0f)
class BrewSetupShortScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = kyusu.copy(
        stockGrams = 3.0,
        stockLevel = StockLevel.LOW,
        vessels = kyusu.vessels.drop(1),
        archivedVessel = kyusu.vessels.first(),
    )

    private fun capture(name: String, act: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent { UmamiTheme(darkTheme = false, reduceMotion = true) { content() } }
        compose.waitForIdle()
        act()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/${name}_short_font2_0.png")
    }

    @Test
    fun setup() = capture("BrewSetup_v040") { BrewSetupScreen(state) }

    @Test
    fun vesselList() = capture(
        "BrewSetup_v040_vesselList",
        act = { compose.onNodeWithContentDescription("Gefäß: Kyusu 250 ml (archiviert). Ändern").performClick() },
    ) {
        BrewSetupScreen(state)
    }
}
