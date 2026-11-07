package com.umamit.feature.teaware

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-13 and SCR-14 on a short screen (360 × 640 dp) at font scale 2.0: the list scrolls under the
 * FAB, the editor's top bar keeps ✕, *Speichern* and ⋮. Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2.0f)
class TeawareShortScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { UmamiTheme(darkTheme = false, reduceMotion = true) { content() } }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/${name}_short_font2_0.png")
    }

    @Test
    fun list() = capture("Teaware_list") {
        TeawareScreen(state = TeawareStates.list, onBack = {}, onOpenVessel = {}, onAddVessel = {}, onRestore = {})
    }

    @Test
    fun editor() = capture("VesselEditor_edit") {
        VesselEditorScreen(state = TeawareStates.editor, actions = VesselEditorActions())
    }
}
