package com.umamit.feature.brew.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.component.ActiveBrewBar
import com.umamit.core.designsystem.motion.MotionTokens
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepTasting
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.feature.brew.quick.QuickTimerActions
import com.umamit.feature.brew.quick.QuickTimerScreen
import com.umamit.feature.brew.quick.QuickTimerUiState
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Record-only renders of the light timer for the PO's design check (v0.8.0-08 #323, D-128 Q23): every state in the
 * light app theme at 384 × 832 dp (the alarm at full sage and at its washi phase, and the active-brew bar's alert in
 * the light app, #325), the same states dark for comparison, and the alternatives of the two open rows of
 * 03 §3.1 side by side (the band A13 in ink on washi against a plain washi top; the liquor fill as today against a
 * deeper one). The seal shows a synthetic tea photo (D-016). They are written to
 * `docs/design/review/v0.8.0/light-timer/` and are no goldens: skipped unless the Gradle property
 * `umamit.recordDesignCheck` is set (`./gradlew :feature:brew:testDebugUnitTest -Pumamit.recordDesignCheck=true
 * --tests '*LightTimerDesignCheckTest'`).
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w384dp-h832dp-xhdpi")
class LightTimerDesignCheckTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(false, true).map { arrayOf<Any>(it) }

        private const val OUTPUT_PROPERTY = "umamit.designCheckOutputDir"
        private const val ALERT_HALF_CYCLE_MILLIS = 750L
        private const val BAR_SETTLED_MILLIS = 1_000L
    }

    @get:Rule
    val compose = createComposeRule()

    private val outputDir: File get() = File(System.getProperty(OUTPUT_PROPERTY).orEmpty())

    private var start = 0L

    private val now: () -> Long = { NOW + compose.mainClock.currentTime - start }

    @Before
    fun onlyWhenRecording() {
        assumeTrue("The design check's renders are record-only", System.getProperty(OUTPUT_PROPERTY) != null)
    }

    private fun render(
        name: String,
        band: LightTimerBand = LightTimerBand.Ink,
        liquor: LightTimerLiquor = LightTimerLiquor.Today,
        reduceMotion: Boolean = false,
        afterFirstFrame: () -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        start = compose.mainClock.currentTime
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = reduceMotion) {
                CompositionLocalProvider(LocalLightTimerBand provides band, LocalLightTimerLiquor provides liquor) {
                    content()
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        afterFirstFrame()
        captureScreenRoboImage(File(outputDir, "${if (dark) "dark" else "light"}-$name.png").path)
    }

    @Composable
    private fun Brew(state: TimerUiState.Active, tasting: TimerTasting? = null) {
        TimerScreen(
            state = state,
            nowElapsedMs = now,
            onEvent = {},
            onBack = {},
            onAdjustBrew = {},
            onOpenTimerSettings = {},
            teaPhoto = PlaceholderTeaPhoto,
            teaStyleKey = "gyokuro",
            stock = TimerStock(38.0, StockLevel.AVAILABLE),
            tasting = tasting,
        )
    }

    @Test
    fun bereit() = render("bereit") { Brew(TimerStates.ready(stepIndex = 1)) }

    @Test
    fun laeuft() = render("laeuft") { Brew(TimerStates.running(remainingMs = 20_000)) }

    @Test
    fun pausiert() = render("pausiert") { Brew(TimerStates.paused()) }

    /**
     * Step 2 (0:20) at its pre-alert: 0:04 left, „Gleich fertig" and the marker's glow at its peak. The glow plays
     * only for a tick that arrives after the first frame, so the pre-alert comes then and the clock runs to the peak.
     */
    @Test
    fun vorwarnung() {
        val before = TimerStates.running(remainingMs = 4_000, preAlertPlayed = false, stepIndex = 1)
        var state by mutableStateOf(before)
        render(
            "vorwarnung",
            afterFirstFrame = {
                state = TimerStates.running(remainingMs = 4_000, preAlertPlayed = true, stepIndex = 1)
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeBy(MotionTokens.PRE_ALERT_GLOW_PEAK_MILLIS.toLong())
                compose.waitForIdle()
            },
        ) { Brew(state) }
    }

    /** The first frame, the strong phase: sage in the light timer (D-128 item 36), matcha in the dark (D-046). */
    @Test
    fun alarm() = render("alarm") { Brew(TimerStates.alerting(overtimeMs = 7_000)) }

    /** Half a cycle later, the washi phase of the light timer's sage alarm (D-128 item 36). */
    @Test
    fun alarmWashi() {
        assumeFalse("The re-render is for the light timer's sage alarm", dark)
        render(
            "alarm-washi",
            afterFirstFrame = {
                compose.mainClock.advanceTimeBy(ALERT_HALF_CYCLE_MILLIS)
                compose.waitForIdle()
            },
        ) { Brew(TimerStates.alerting(overtimeMs = 7_000)) }
    }

    /**
     * The active-brew bar's alert state in the light app, outside the timer: its colours are unchanged (static matcha
     * with ink, `ActiveBrewBar`), shown beside the sage alarm for the PO (#325).
     */
    @Test
    fun appBarAlert() {
        assumeFalse("The bar is shown in the light app", dark)
        render(
            "app-bar-alert",
            afterFirstFrame = {
                compose.mainClock.advanceTimeBy(BAR_SETTLED_MILLIS)
                compose.waitForIdle()
            },
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ActiveBrewBar(
                    brew = ActiveBrew(
                        title = "Gyokuro Asahi",
                        step = "Aufguss 2/4",
                        stepDescription = "Aufguss 2 von 4",
                        remainingSeconds = 0,
                        liquorColor = UmamiTheme.extendedColors.liquor.green,
                        alerting = true,
                        overtimeSeconds = 7,
                    ),
                    progress = { 1f },
                    onClick = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    @Test
    fun schnelltimerVorStart() = render("schnelltimer-bereit") {
        QuickTimerScreen(state = QuickTimerUiState(durationSeconds = 120), actions = QuickTimerActions())
    }

    @Test
    fun schnelltimerLaeuft() = render("schnelltimer-laeuft") {
        TimerScreen(
            state = TimerStates.quick(TimerPhase.RUNNING, seconds = 120, remainingMs = 48_000),
            nowElapsedMs = now,
            onEvent = {},
            onBack = {},
            onAdjustBrew = {},
            onOpenTimerSettings = {},
        )
    }

    /** The Geschmack sheet over a running step, opened without its slide (reduce motion). */
    @Test
    fun geschmack() = render(
        "geschmack",
        reduceMotion = true,
        afterFirstFrame = {
            compose.onNodeWithTag(TIMER_TASTE_BUTTON_TAG).performClick()
            repeat(2) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
        },
    ) {
        Brew(
            TimerStates.running(stepIndex = 1, remainingMs = 14_000),
            TimerTasting(
                mapOf(0 to StepTasting(TasteProfile(umami = 4, sweetness = 3, body = 2), note = "Süß, nach Nori")),
                onTaste = { _, _, _ -> },
                onReset = { _, _ -> },
                onNote = { _, _ -> },
            ),
        )
    }

    // The two open rows of 03 §3.1, light only: each alternative while running, side by side.

    @Test
    fun bandInk() = lightOnly("band-ink", band = LightTimerBand.Ink)

    @Test
    fun bandPlain() = lightOnly("band-plain", band = LightTimerBand.Plain)

    @Test
    fun fillToday() = lightOnly("fill-today", liquor = LightTimerLiquor.Today)

    @Test
    fun fillDeeper() = lightOnly("fill-deeper", liquor = LightTimerLiquor.Deeper)

    private fun lightOnly(
        name: String,
        band: LightTimerBand = LightTimerBand.Ink,
        liquor: LightTimerLiquor = LightTimerLiquor.Today,
    ) {
        assumeFalse("The alternatives are for the light timer", dark)
        render(name, band = band, liquor = liquor) { Brew(TimerStates.running(remainingMs = 20_000)) }
    }
}

/** A synthetic product shot (D-016): a few green leaves on white, like a shop photo; never a Tee-Kontor photo. */
private object PlaceholderTeaPhoto : Painter() {
    override val intrinsicSize: Size = Size(400f, 300f)

    override fun DrawScope.onDraw() {
        drawRect(Color.White)
        val leaves = listOf(
            Triple(Offset(0.38f, 0.42f), -30f, Color(0xFF4F6B2E)),
            Triple(Offset(0.58f, 0.50f), 25f, Color(0xFF5F7F45)),
            Triple(Offset(0.47f, 0.64f), 75f, Color(0xFF3F5A24)),
        )
        leaves.forEach { (at, degrees, color) ->
            val centre = Offset(at.x * size.width, at.y * size.height)
            rotate(degrees, centre) {
                drawOval(
                    color = color,
                    topLeft = centre - Offset(size.width * 0.16f, size.height * 0.06f),
                    size = Size(size.width * 0.32f, size.height * 0.12f),
                )
            }
        }
    }
}
