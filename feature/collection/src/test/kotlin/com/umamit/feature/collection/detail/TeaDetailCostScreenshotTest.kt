package com.umamit.feature.collection.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.collection.LiterAverage
import com.umamit.core.domain.collection.TeaCost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * VORRAT with the cost lines (INV-08, SCR-09, D-104) in light and dark at font scale 1.0 and 2.0 (03 §6): price per
 * 100 g, the recipe estimate and the average of five sessions. Invented tea, no photo (D-016). Goldens:
 * `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailCostScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

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

    @Test
    fun cost() {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                TeaDetailScreen(
                    TeaDetailUiState.Ready(
                        TeaDetailScreenTest.sample().copy(
                            cost = TeaCost(
                                pricePer100gCents = 1240,
                                recipeLiterCents = 240,
                                average = LiterAverage(270, 5),
                            ),
                        ),
                    ),
                    TeaDetailActions(),
                    photo = { null },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(TEA_DETAIL_PURCHASE_TAG).performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_cost_$variant.png")
    }
}
