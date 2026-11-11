package com.umamit.feature.collection.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
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
 * The tea detail's rating, GESCHMACK and LETZTE SITZUNGEN (TEA-13, D-112, #232) in light and dark at font scale 1.0
 * and 2.0 (03 §6): the header with its rating, GESCHMACK with the average radar and the top descriptors, and the last
 * three sessions with *Alle anzeigen*. The detail without a rating is `TeaDetailScreenshotTest`'s. Invented tea, no
 * photo (D-016). Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailTastingScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

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
                TeaDetailScreen(
                    TeaDetailUiState.Ready(TeaDetailTastingScreenTest.TASTED),
                    TeaDetailActions(openSession = {}, showAllSessions = {}),
                    photo = { null },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) =
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_${name}_$variant.png")

    @Test
    fun rating() {
        show()
        capture("rating")
    }

    @Test
    fun taste() {
        show()
        compose.onNodeWithTag(TEA_DETAIL_TASTE_TAG).performScrollTo()
        compose.waitForIdle()
        capture("taste")
    }

    @Test
    fun recentSessions() {
        show()
        compose.onNodeWithTag(TEA_DETAIL_RECENT_TAG).performScrollTo()
        compose.waitForIdle()
        capture("recent")
    }
}
