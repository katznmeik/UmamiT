package com.umamit.feature.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
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
 * *Meine Tees* (SCR-08) on a short screen (360 × 640 dp) at font scale 2.0: the chips scroll sideways, the rows
 * under the FAB, and „Noch keine Tees" scrolls instead of being cut off. Goldens: `src/test/screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2.0f)
class CollectionShortScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, state: MyTeasUiState) {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    CollectionScreen(
                        segment = CollectionSegment.MyTeas,
                        onSegmentChange = {},
                        onOpenSettings = {},
                        catalogContent = { _, _ -> },
                        myTeasContent = { modifier, _ ->
                            MyTeasContent(state = state, actions = MyTeasActions(), modifier = modifier)
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/Collection_${name}_short_font2_0.png")
    }

    @Test
    fun myTeas() = capture("myTeas", MyTeasStates.list)

    @Test
    fun noTeas() = capture("noTeas", MyTeasStates.noTeas)
}
