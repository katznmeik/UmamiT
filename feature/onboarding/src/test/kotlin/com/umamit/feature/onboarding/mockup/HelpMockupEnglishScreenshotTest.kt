package com.umamit.feature.onboarding.mockup

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tour's six mock-ups in English (GEN-13; SCR-36 „Mock-ups"), light and dark: the real English names, the
 * numbers in English („5.0 g"). Goldens: `src/test/screenshots/MockupEn_*`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp-xhdpi")
class HelpMockupEnglishScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun start() = compose.captureMockup("MockupEn", HelpMockupId.Start, dark)

    @Test
    fun two() = compose.captureMockup("MockupEn", HelpMockupId.Two, dark)

    @Test
    fun myTeasMenu() = compose.captureMockup("MockupEn", HelpMockupId.MyTeasMenu, dark)

    @Test
    fun session() = compose.captureMockup("MockupEn", HelpMockupId.Session, dark)

    @Test
    fun stats() = compose.captureMockup("MockupEn", HelpMockupId.Stats, dark)

    @Test
    fun data() = compose.captureMockup("MockupEn", HelpMockupId.Data, dark)
}
