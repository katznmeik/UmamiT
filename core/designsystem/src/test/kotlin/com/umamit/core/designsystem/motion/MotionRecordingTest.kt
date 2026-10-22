package com.umamit.core.designsystem.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboVideoOptions
import com.github.takahirom.roborazzi.recordRoboVideo
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.component.ActiveBrewBar
import com.umamit.core.designsystem.component.AlertSurface
import com.umamit.core.designsystem.component.TimerRing
import com.umamit.core.designsystem.component.TimerRingDefaults
import com.umamit.core.designsystem.theme.UmamiTheme
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
import kotlin.math.ceil

/**
 * Record-only motion recordings for the PO's motion review (D-041, M1 plan
 * §3.2), made with Roborazzi's experimental `recordRoboVideo`. Skipped unless
 * the Gradle property `umamit.recordMotion` is set, so CI and
 * `verifyRoborazziDebug` never run them. Run `scripts/motion/record-gifs.sh`,
 * which also shrinks the GIFs into `docs/design/review/M1/motion/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class MotionRecordingTest {

    @get:Rule
    val compose = createComposeRule()

    private val outputDir: File get() = File(System.getProperty(OUTPUT_PROPERTY).orEmpty())

    @Before
    fun onlyWhenRecording() {
        assumeTrue("Motion recordings are record-only", System.getProperty(OUTPUT_PROPERTY) != null)
    }

    private fun record(
        name: String,
        millis: Long,
        dark: Boolean = true,
        reduceMotion: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = reduceMotion) {
                Box(
                    modifier = Modifier
                        .testTag(TAG)
                        .width(360.dp)
                        .height(360.dp)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) { content() }
            }
        }
        outputDir.mkdirs()
        compose.onNodeWithTag(TAG).recordRoboVideo(
            composeRule = compose,
            filePath = File(outputDir, "$name.gif").path,
            videoOptions = RoboVideoOptions(fps = FPS, settleTimeoutMillis = 0),
        ) { delay(millis) }
    }

    /** Progress driven by frame time: real per-frame progress, like a short step. */
    @Composable
    private fun rememberStepProgress(stepMillis: Long, startAt: Float = 0f): Float {
        var progress by remember { mutableFloatStateOf(startAt) }
        LaunchedEffect(Unit) {
            val start = withFrameMillis { it }
            while (progress < 1f) {
                withFrameMillis { now -> progress = (startAt + (now - start).toFloat() / stepMillis).coerceAtMost(1f) }
            }
        }
        return progress
    }

    /** Liquor fill of a short 6 s step, per frame, from empty to full. */
    @Test
    fun liquorFill() = record("liquor-fill", millis = 6_500) {
        val progress = rememberStepProgress(stepMillis = 6_000)
        TimerRing(
            progress = progress,
            remainingSeconds = ceil((1f - progress) * 6).toInt(),
            caption = "Zieht\u00A0…",
            liquorColor = UmamiTheme.extendedColors.liquor.green,
            preAlertPosition = 0.6f,
        )
    }

    /** The pre-alert glow, one swell with the 0.8 s tick, shown twice. */
    @Test
    fun preAlertGlow() = record("pre-alert-glow", millis = 2_400) {
        var tick by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            repeat(2) {
                delay(400)
                tick = true
                delay(800)
                tick = false
            }
        }
        TimerRing(
            progress = 0.875f,
            remainingSeconds = 10,
            caption = "Gleich fertig\u00A0…",
            liquorColor = UmamiTheme.extendedColors.liquor.green,
            preAlertPosition = 0.875f,
            preAlertGlow = tick,
        )
    }

    /** Long steps: coarse updates with the 600 ms glide (interval shortened from 10 s to 1.5 s for the recording). */
    @Test
    fun longStepGlide() = record("long-step-glide", millis = 4_500) {
        var progress by remember { mutableFloatStateOf(0.3f) }
        LaunchedEffect(Unit) {
            repeat(3) {
                delay(1_500)
                progress += 0.12f
            }
        }
        TimerRing(
            progress = progress,
            remainingSeconds = ((1f - progress) * 14_400).toInt(),
            caption = "Fertig um 14:30",
            liquorColor = UmamiTheme.extendedColors.liquor.green,
            longStep = true,
        )
    }

    @Test
    fun alertPulse() = record("alert-pulse", millis = 3_000) { AlertingRing() }

    @Test
    fun alertStatic() = record("alert-static-reduce-motion", millis = 1_500, reduceMotion = true) { AlertingRing() }

    @Composable
    private fun AlertingRing() {
        AlertSurface(modifier = Modifier.fillMaxWidth().height(360.dp)) {
            TimerRing(
                progress = 1f,
                remainingSeconds = 0,
                caption = "Jetzt abgießen!",
                liquorColor = UmamiTheme.extendedColors.liquor.green,
                colors = TimerRingDefaults.alertColors(),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    /** The active brew bar arriving and leaving (light theme, above where the navigation bar would be). */
    @Test
    fun activeBrewBar() = record("active-brew-bar", millis = 2_800, dark = false) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(300)
            visible = true
            delay(1_600)
            visible = false
        }
        val brew = ActiveBrew(
            title = "Bio Sencha Hisui",
            step = "Aufguss 2/4",
            remainingSeconds = 72,
            progress = 0.4f,
            liquorColor = UmamiTheme.extendedColors.liquor.green,
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(360.dp).padding(bottom = 24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            ActiveBrewBar(brew = brew.takeIf { visible }, onClick = {})
        }
    }

    private companion object {
        const val TAG = "motion"
        const val FPS = 20

        /** Set by the Gradle property `umamit.recordMotion` (see `core/designsystem/build.gradle.kts`). */
        const val OUTPUT_PROPERTY = "umamit.motionOutputDir"
    }
}
