package com.umamit.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tour (SCR-33) in English (GEN-10, ONB-07), light and dark: each of the six pages at font scale 1.0 and page 1
 * at 2.0. Goldens: `src/test/screenshots/TourEn_*`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp-xhdpi")
class TourEnglishScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun page1() = compose.captureTourPage("TourEn", 0, dark, 1f)

    @Test
    fun page2() = compose.captureTourPage("TourEn", 1, dark, 1f)

    @Test
    fun page3() = compose.captureTourPage("TourEn", 2, dark, 1f)

    @Test
    fun page4() = compose.captureTourPage("TourEn", 3, dark, 1f)

    @Test
    fun page5() = compose.captureTourPage("TourEn", 4, dark, 1f)

    @Test
    fun page6() = compose.captureTourPage("TourEn", 5, dark, 1f)

    @Test
    fun page1AtFont2() = compose.captureTourPage("TourEn", 0, dark, 2f)
}
