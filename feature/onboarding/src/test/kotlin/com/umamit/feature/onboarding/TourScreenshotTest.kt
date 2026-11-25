package com.umamit.feature.onboarding

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
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

/** Captures one page of the tour (SCR-33) into `src/test/screenshots/<prefix>_page<N>_<variant>.png`. */
@OptIn(ExperimentalRoborazziApi::class)
internal fun ComposeContentTestRule.captureTourPage(prefix: String, page: Int, dark: Boolean, fontScale: Float) {
    RuntimeEnvironment.setFontScale(fontScale)
    setContent {
        UmamiTheme(darkTheme = dark, reduceMotion = true) {
            TourScreen(onFinish = {}, onSkip = {}, initialPage = page)
        }
    }
    waitForIdle()
    val theme = if (dark) "dark" else "light"
    val font = if (fontScale == 1f) "" else "_font${fontScale.toString().replace('.', '_')}"
    captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/${prefix}_page${page + 1}_$theme$font.png")
}

/**
 * Die Einführung (SCR-33, ONB-07) in German, light and dark: each of the six pages at font scale 1.0 and page 1
 * at 2.0 (the text scrolls, the buttons stay in view). The pages show the existing art A3, A13, A8, A6, A9, A5.
 * Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TourScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun page1() = compose.captureTourPage("Tour", 0, dark, 1f)

    @Test
    fun page2() = compose.captureTourPage("Tour", 1, dark, 1f)

    @Test
    fun page3() = compose.captureTourPage("Tour", 2, dark, 1f)

    @Test
    fun page4() = compose.captureTourPage("Tour", 3, dark, 1f)

    @Test
    fun page5() = compose.captureTourPage("Tour", 4, dark, 1f)

    @Test
    fun page6() = compose.captureTourPage("Tour", 5, dark, 1f)

    @Test
    fun page1AtFont2() = compose.captureTourPage("Tour", 0, dark, 2f)
}
