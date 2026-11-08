package com.umamit.feature.collection.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⋮ *Aus Katalog zurücksetzen*'s confirmation (PRF-06, SCR-09, D-104) in light and dark at font scale 1.0 and 2.0
 * (03 §6), with the limit's note (two catalog recipes left out). Invented tea, no photo (D-016).
 * Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailResetScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

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
    fun confirmation() {
        RuntimeEnvironment.setFontScale(fontScale)
        val tea = TeaDetailScreenTest.sample().copy(catalogReset = CatalogResetOffer(leftOut = 2))
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(tea), TeaDetailActions(), photo = { null })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Aus Katalog zurücksetzen").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_reset_$variant.png")
    }
}
