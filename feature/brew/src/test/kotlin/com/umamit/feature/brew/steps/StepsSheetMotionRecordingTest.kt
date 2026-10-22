package com.umamit.feature.brew.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboVideoOptions
import com.github.takahirom.roborazzi.recordRoboVideo
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.StepStatus
import kotlinx.coroutines.delay
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
 * Record-only recording of the Schritte-Sheet opening and closing with the
 * `sheet` spring (03 §3.6) for the PO's motion review. Skipped unless the
 * Gradle property `umamit.recordMotion` is set; run `scripts/motion/record-gifs.sh`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class StepsSheetMotionRecordingTest {

    @get:Rule
    val compose = createComposeRule()

    private val outputDir: File get() = File(System.getProperty(OUTPUT_PROPERTY).orEmpty())

    @Before
    fun onlyWhenRecording() {
        assumeTrue("Motion recordings are record-only", System.getProperty(OUTPUT_PROPERTY) != null)
    }

    @Test
    fun stepsSheet() {
        val input = StepsSheetInput(
            steps = listOf(
                StepsSheetStep(StepKind.INFUSION, 90, 55, StepStatus.DONE),
                StepsSheetStep(StepKind.INFUSION, 30, 60, StepStatus.PENDING),
                StepsSheetStep(StepKind.INFUSION, 20, 60, StepStatus.PENDING),
            ),
            preAlert = PreAlertChoice.Inherit,
            hasRecipe = true,
            baseTempC = 60,
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = false) {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(300)
                    visible = true
                    delay(1_400)
                    visible = false
                }
                Box(
                    modifier = Modifier
                        .testTag(TAG)
                        .width(360.dp)
                        .height(640.dp)
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Text(
                        text = "Bio Sencha Hisui",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(UmamiSpacing.SideMargin),
                    )
                    StepsSheet(
                        visible = visible,
                        state = remember { StepsSheetState(input) },
                        onDone = {},
                        onSaveToRecipe = {},
                        onDismissRequest = { visible = false },
                    )
                }
            }
        }
        outputDir.mkdirs()
        compose.onNodeWithTag(TAG).recordRoboVideo(
            composeRule = compose,
            filePath = File(outputDir, "steps-sheet.gif").path,
            videoOptions = RoboVideoOptions(fps = FPS, settleTimeoutMillis = 0),
        ) { delay(2_600) }
    }

    private companion object {
        const val TAG = "motion"
        const val FPS = 20

        /** Set by the Gradle property `umamit.recordMotion` (see `feature/brew/build.gradle.kts`). */
        const val OUTPUT_PROPERTY = "umamit.motionOutputDir"
    }
}
