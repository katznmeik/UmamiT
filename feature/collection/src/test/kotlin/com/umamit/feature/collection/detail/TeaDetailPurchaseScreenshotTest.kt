package com.umamit.feature.collection.detail

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * *Nachkauf erfassen* (INV-03, SCR-09, D-104) in light and dark at font scale 1.0 and 2.0 (03 §6): VORRAT with
 * both stock actions, and the sheet filled in („50" g, „12,40" €, today). Invented tea, no photo (D-016).
 * Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailPurchaseScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

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

    private fun show() {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(purchaseSample()), TeaDetailActions(), photo = { null })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun vorrat() {
        show()
        compose.onNodeWithTag(TEA_DETAIL_REORDER_TAG).performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_vorrat_$variant.png")
    }

    @Test
    fun sheet() {
        show()
        fillSheet(compose)
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_purchase_$variant.png")
    }
}

/** The sheet on a short screen (384 × 832 dp) at font scale 2.0: every field and *Erfassen* stay reachable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w384dp-h832dp-xhdpi")
class TeaDetailPurchaseShortScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sheet() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(purchaseSample()), TeaDetailActions(), photo = { null })
            }
        }
        compose.waitForIdle()
        fillSheet(compose)
        compose.onNodeWithTag(PURCHASE_SHEET_SAVE_TAG).performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_short_purchase_font2_0.png")
    }
}

private fun purchaseSample() = TeaDetailScreenTest.sample().copy(today = LocalDate.of(2026, 11, 8))

private fun fillSheet(compose: ComposeContentTestRule) {
    compose.onNodeWithTag(TEA_DETAIL_PURCHASE_TAG).performScrollTo().performClick()
    compose.waitForIdle()
    compose.onNodeWithTag(PURCHASE_SHEET_GRAMS_TAG).performTextInput("50")
    compose.onNodeWithTag(PURCHASE_SHEET_PRICE_TAG).performTextInput("12,40")
    compose.waitForIdle()
}
