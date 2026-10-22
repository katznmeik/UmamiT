package com.umamit.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `TimerRing` states, the alert surface and `ActiveBrewBar` in light and dark at
 * font scale 1.0 and 2.0, with reduce-motion variants (M1-04, 03 §3.6, §3.8).
 * The clock is paused, so animated states are captured at a fixed time.
 * Goldens: `src/test/screenshots/`, verified by `verifyRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerComponentScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private const val TAG = "timerComponent"

        /** The pre-alert glow peaks 200 ms after the tick (03 §3.6 `preAlertGlow`). */
        private const val GLOW_PEAK_MILLIS = 200L

        /** Half an alert pulse cycle: the washi phase. */
        private const val PULSE_HALF_CYCLE_MILLIS = 750L
    }

    @get:Rule
    val compose = createComposeRule()

    private var tick by mutableStateOf<Long?>(null)

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(
        name: String,
        reduceMotion: Boolean = false,
        advanceMillis: Long = 0L,
        preAlertTick: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = reduceMotion) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(
                        modifier = Modifier
                            .testTag(TAG)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = UmamiSpacing.Medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        content()
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        if (preAlertTick) {
            // The tick arrives while the ring is on screen, as on the timer.
            tick = 1L
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
        }
        if (advanceMillis > 0) compose.mainClock.advanceTimeBy(advanceMillis)
        compose.onNodeWithTag(TAG)
            .captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/${name}_$variant.png")
    }

    @Composable
    private fun liquor() = UmamiTheme.extendedColors.liquor

    @Test
    fun ready() = capture("TimerRing_Ready") {
        TimerRing(
            progress = { 0f },
            remainingSeconds = 20,
            caption = "Bereit",
            liquorColor = liquor().green,
            stepDescription = "Aufguss 2 von 3",
            preAlertPosition = 0.5f,
        )
    }

    @Test
    fun runningFillLevels() = capture("TimerRing_Running") {
        Column(verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small)) {
            TimerRing(
                progress = { 0.25f },
                remainingSeconds = 68,
                caption = "Zieht\u00A0…",
                liquorColor = liquor().green,
                preAlertPosition = 0.89f,
                size = 220.dp,
            )
            TimerRing(
                progress = { 0.6f },
                remainingSeconds = 36,
                caption = "Zieht\u00A0…",
                liquorColor = liquor().gyokuro,
                preAlertPosition = 0.89f,
                size = 220.dp,
            )
            TimerRing(
                progress = { 0.9f },
                remainingSeconds = 9,
                caption = "Gleich fertig\u00A0…",
                liquorColor = liquor().hojicha,
                preAlertPosition = 0.89f,
                size = 220.dp,
            )
        }
    }

    @Test
    fun preAlertGlow() = capture("TimerRing_PreAlertGlow", advanceMillis = GLOW_PEAK_MILLIS, preAlertTick = true) {
        TimerRing(
            progress = { 0.875f },
            remainingSeconds = 10,
            caption = "Gleich fertig\u00A0…",
            liquorColor = liquor().green,
            preAlertPosition = 0.875f,
            preAlertGlowTick = tick,
        )
    }

    /** Reduce motion: no glow at the moment the glow would peak. */
    @Test
    fun preAlertGlowReduceMotion() = capture(
        "TimerRing_PreAlertGlow_reduceMotion",
        reduceMotion = true,
        advanceMillis = GLOW_PEAK_MILLIS,
        preAlertTick = true,
    ) {
        TimerRing(
            progress = { 0.875f },
            remainingSeconds = 10,
            caption = "Gleich fertig\u00A0…",
            liquorColor = liquor().green,
            preAlertPosition = 0.875f,
            preAlertGlowTick = tick,
        )
    }

    /** Frame 1 of ALERTING: full matcha, never a soft onset. */
    @Test
    fun alerting() = capture("TimerRing_Alerting") {
        AlertSurface(modifier = Modifier.fillMaxWidth()) {
            AlertingRing(Modifier.align(Alignment.Center))
        }
    }

    /** The washi phase of the pulse: the content stays readable. */
    @Test
    fun alertSurfaceWashiPhase() = capture("AlertSurface_washiPhase", advanceMillis = PULSE_HALF_CYCLE_MILLIS) {
        AlertSurface(modifier = Modifier.fillMaxWidth()) {
            AlertingRing(Modifier.align(Alignment.Center))
        }
    }

    /** Reduce motion: still static matcha half a cycle later. */
    @Test
    fun alertSurfaceReduceMotion() = capture(
        "AlertSurface_reduceMotion",
        reduceMotion = true,
        advanceMillis = PULSE_HALF_CYCLE_MILLIS,
    ) {
        AlertSurface(modifier = Modifier.fillMaxWidth()) {
            AlertingRing(Modifier.align(Alignment.Center))
        }
    }

    /** Default parameters on purpose: the alert surface makes the ring and the pill alert-safe itself. */
    @Composable
    private fun AlertingRing(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier.padding(vertical = UmamiSpacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Medium),
        ) {
            TimerRing(
                progress = { 1f },
                remainingSeconds = 0,
                caption = "Jetzt abgießen! +0:07",
                liquorColor = liquor().green,
                preAlertPosition = 0.875f,
            )
            PillButton(text = "Abgegossen", onClick = {})
        }
    }

    @Test
    fun longStep() = capture("TimerRing_LongStep") {
        TimerRing(
            progress = { 0.4f },
            remainingSeconds = 8_640,
            caption = "Fertig um 14:30",
            liquorColor = liquor().green,
            longStep = true,
        )
    }

    @Test
    fun activeBrewBar() = capture("ActiveBrewBar") {
        Column(verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small)) {
            ActiveBrewBar(
                brew = ActiveBrew(
                    title = "Bio Sencha Hisui",
                    step = "Aufguss 2/4",
                    stepDescription = "Aufguss 2 von 4",
                    remainingSeconds = 72,
                    liquorColor = liquor().green,
                ),
                progress = { 0.4f },
                onClick = {},
            )
            ActiveBrewBar(
                brew = ActiveBrew(
                    title = "Gyokuro Asahi Kōridashi mit überlangem Namen",
                    step = "Aufguss 1/3",
                    remainingSeconds = 8_640,
                    liquorColor = liquor().gyokuro,
                ),
                progress = { 0.4f },
                onClick = {},
            )
            ActiveBrewBar(
                brew = ActiveBrew(
                    title = "Bio Sencha Hisui",
                    step = "Aufguss 2/4",
                    remainingSeconds = 0,
                    liquorColor = liquor().green,
                    alerting = true,
                    overtimeSeconds = 7,
                ),
                progress = { 1f },
                onClick = {},
            )
            ActiveBrewBar(
                brew = ActiveBrew(
                    title = "Schnelltimer",
                    step = null,
                    remainingSeconds = 25,
                    liquorColor = liquor().green,
                ),
                progress = { 0.8f },
                onClick = {},
            )
        }
    }
}
