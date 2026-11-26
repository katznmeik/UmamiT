package com.umamit.feature.onboarding.mockup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val CAPTURE_TAG = "mockup_capture"

/** Captures [id] in its frame on the theme's paper into `src/test/screenshots/<prefix>_<id>_<theme><suffix>.png`. */
@OptIn(ExperimentalRoborazziApi::class)
internal fun ComposeContentTestRule.captureMockup(
    prefix: String,
    id: HelpMockupId,
    dark: Boolean,
    size: HelpMockupSize = HelpMockupSize.Tour,
    markerNumber: Int? = null,
    fontScale: Float = 1f,
    suffix: String = "",
) {
    RuntimeEnvironment.setFontScale(fontScale)
    setContent {
        UmamiTheme(darkTheme = dark, reduceMotion = true) {
            Surface(Modifier.testTag(CAPTURE_TAG), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.padding(16.dp)) { HelpMockup(id, size, markerNumber = markerNumber) }
            }
        }
    }
    waitForIdle()
    val theme = if (dark) "dark" else "light"
    onNodeWithTag(CAPTURE_TAG)
        .captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/${prefix}_${id.name}_$theme$suffix.png")
}

/**
 * The tour's six mock-ups (SCR-33, SCR-36 „Mock-ups"; ONB-07, ONB-08) in German, light and dark, at the tour's size;
 * M-START also at an Anleitung's size with the step badge, and at font 2.0 (the mock-up keeps its size).
 * Goldens: `src/test/screenshots/Mockup_*`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class HelpMockupScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun start() = compose.captureMockup("Mockup", HelpMockupId.Start, dark)

    @Test
    fun two() = compose.captureMockup("Mockup", HelpMockupId.Two, dark)

    @Test
    fun myTeasMenu() = compose.captureMockup("Mockup", HelpMockupId.MyTeasMenu, dark)

    @Test
    fun session() = compose.captureMockup("Mockup", HelpMockupId.Session, dark)

    @Test
    fun stats() = compose.captureMockup("Mockup", HelpMockupId.Stats, dark)

    @Test
    fun data() = compose.captureMockup("Mockup", HelpMockupId.Data, dark)

    @Test
    fun startGuideWithBadge() = compose.captureMockup(
        "Mockup",
        HelpMockupId.Start,
        dark,
        HelpMockupSize.Guide,
        markerNumber = 2,
        suffix = "_guide",
    )

    @Test
    fun startAtFont2() = compose.captureMockup("Mockup", HelpMockupId.Start, dark, fontScale = 2f, suffix = "_font2_0")
}
