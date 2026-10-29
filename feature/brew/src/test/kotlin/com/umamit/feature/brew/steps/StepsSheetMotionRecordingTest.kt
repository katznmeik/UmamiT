package com.umamit.feature.brew.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.StepStatus
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Record-only recording of the Schritte-Sheet for the PO's motion review
 * (D-041, D-051): it opens, is dragged down and flung away, opens again and
 * closes with *Fertig*'s programmatic hide. Material's sheet has its own
 * window, which `recordRoboVideo` cannot see, so this writes a frame sequence
 * of whole-screen captures that `scripts/motion/record-gifs.sh` turns into a
 * GIF. Skipped unless the Gradle property `umamit.recordMotion` is set.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-mdpi")
class StepsSheetMotionRecordingTest {

    @get:Rule
    val compose = createComposeRule()

    private val outputDir: File get() = File(System.getProperty(OUTPUT_PROPERTY).orEmpty())

    @Before
    fun onlyWhenRecording() {
        assumeTrue("Motion recordings are record-only", System.getProperty(OUTPUT_PROPERTY) != null)
    }

    private val input = StepsSheetInput(
        steps = listOf(
            StepsSheetStep(StepKind.INFUSION, 90, 55, StepStatus.DONE),
            StepsSheetStep(StepKind.INFUSION, 30, 60, StepStatus.PENDING),
            StepsSheetStep(StepKind.INFUSION, 20, 60, StepStatus.PENDING),
        ),
        hasRecipe = true,
        baseTempC = 60,
        preAlertBaseSeconds = 10,
    )

    private var visible by mutableStateOf(false)
    private var frame = 0
    private val frames: File get() = File(outputDir, "steps-sheet")

    private fun capture() {
        captureScreenRoboImage(File(frames, "frame_%03d.png".format(frame++)).path)
    }

    private fun record(millis: Long) = repeat((millis / FRAME_MILLIS).toInt()) {
        compose.mainClock.advanceTimeBy(FRAME_MILLIS)
        capture()
    }

    @Test
    fun stepsSheet() {
        frames.deleteRecursively()
        frames.mkdirs()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = false) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Text(
                        text = "Bio Sencha Hisui",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(UmamiSpacing.SideMargin),
                    )
                    StepsSheet(
                        visible = visible,
                        state = remember { StepsSheetState(input) },
                        onDone = { visible = false },
                        onSaveToRecipe = {},
                        onDismissRequest = { visible = false },
                    )
                }
            }
        }
        record(300)

        // Open, then a drag down that is flung away.
        visible = true
        record(1_000)
        val sheet = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle) and
                hasAnyDescendant(hasText("Schritte für diese Sitzung")),
        )
        sheet.performTouchInput { down(Offset(centerX, top + 60f)) }
        repeat(DRAG_STEPS) {
            sheet.performTouchInput { moveBy(Offset(0f, DRAG_STEP_PX), delayMillis = FRAME_MILLIS) }
            capture()
        }
        sheet.performTouchInput { up() }
        record(900)

        // Open again, then the programmatic hide that follows *Fertig*.
        visible = true
        record(1_000)
        visible = false
        record(800)
    }

    private companion object {
        const val FRAME_MILLIS = 50L
        const val DRAG_STEPS = 5
        const val DRAG_STEP_PX = 40f

        /** Set by the Gradle property `umamit.recordMotion` (see `feature/brew/build.gradle.kts`). */
        const val OUTPUT_PROPERTY = "umamit.motionOutputDir"
    }
}
