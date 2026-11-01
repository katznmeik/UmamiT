package com.umamit.feature.collection.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StockLevel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tea detail (SCR-09, D-100) in light and dark at font scale 1.0 and 2.0 (03 §6), with the placeholder leaf
 * instead of a photo and an invented tea (D-016): the top of the page with the figures and REZEPTE, and the tea
 * *Knapp* and archived, scrolled to VORRAT (the filled reorder button, *Brühen* greyed out).
 * Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

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

    private fun capture(name: String, tea: TeaDetail, scrollToTag: String? = null) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(tea), TeaDetailActions(), photo = null)
            }
        }
        compose.waitForIdle()
        scrollToTag?.let { compose.onNodeWithTag(it).performScrollTo() }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_${name}_$variant.png")
    }

    @Test
    fun ready() = capture("ready", TeaDetailScreenTest.sample())

    @Test
    fun lowAndArchived() = capture(
        "lowArchived",
        TeaDetailScreenTest.sample().copy(
            stockGrams = 12.0,
            stockLevel = StockLevel.LOW,
            reorder = Reorder(TeaDetailScreenTest.URL, prominent = true),
            isArchived = true,
            isFavorite = true,
        ),
        scrollToTag = TEA_DETAIL_REORDER_TAG,
    )
}

/** SCR-09 on a short screen (384 × 832 dp) at font scale 2.0: *Brühen* and the FAB stay reachable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w384dp-h832dp-xhdpi")
class TeaDetailShortScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun ready() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(TeaDetailScreenTest.sample()), TeaDetailActions(), photo = null)
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_short_ready_font2_0.png")
    }
}
