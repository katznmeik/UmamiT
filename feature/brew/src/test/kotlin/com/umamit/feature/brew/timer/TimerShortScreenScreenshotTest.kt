package com.umamit.feature.brew.timer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-05 on a short screen (384 × 832 dp) at font scale 2.0 with a two-line tea name: the band
 * and the seal give way, so the primary control lies whole on the screen (rule 8, D-096), READY's
 * stock line „14 g übrig · knapp" included (BRW-11, D-098). In the dark and the light app theme, which the timer
 * follows (SET-05, D-128 Q20): the dark goldens keep their names from before v0.8.0 (the alert in its matcha phase),
 * the light ones end in `_appLight` (the alert in its sage phase). The seal shows a plain white synthetic photo
 * (D-016). Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w384dp-h832dp-xhdpi", fontScale = 2.0f)
class TimerShortScreenScreenshotTest(private val appDark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "appDark={0}")
        fun parameters(): List<Array<Any>> = listOf(true, false).map { arrayOf<Any>(it) }

        private val TASTING =
            TimerTasting(emptyMap(), onTaste = { _, _, _ -> }, onReset = { _, _ -> }, onNote = { _, _ -> })

        /** A synthetic tea name (D-016) that takes two lines of the title here. */
        private const val TWO_LINE_NAME = "Bio Japan Sencha Kagoshima Asatsuyu"
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(
        name: String,
        state: TimerUiState.Active,
        other: TimerUiState.Active? = null,
        tasting: TimerTasting? = null,
    ) {
        compose.mainClock.autoAdvance = false
        val start = compose.mainClock.currentTime
        compose.setContent {
            UmamiTheme(darkTheme = appDark, reduceMotion = false) {
                TimerScreen(
                    state = state.copy(teaName = TWO_LINE_NAME),
                    nowElapsedMs = { NOW + compose.mainClock.currentTime - start },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    teaPhoto = ColorPainter(Color.White),
                    teaStyleKey = "sencha",
                    // The longest stock line (BRW-11, D-098): it shares the space the band gives way for.
                    stock = TimerStock(14.0, StockLevel.LOW),
                    other = other,
                    tasting = tasting,
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val theme = if (appDark) "" else "_appLight"
        captureScreenRoboImage(
            "${roborazziSystemPropertyOutputDirectory()}/TimerScreen_short_${name}${theme}_font2_0.png",
        )
    }

    @Test
    fun ready() = capture("Ready", TimerStates.ready(stepIndex = 1))

    /**
     * With the quick timer's row (D-098): the band is down to the top bar already, so the seal yields its lower half
     * to the row, and *Start* lies whole on the screen.
     */
    @Test
    fun readyWithQuickRow() =
        capture("Ready_quickRow", TimerStates.ready(stepIndex = 1), other = TimerStates.quick(TimerPhase.RUNNING))

    @Test
    fun runningWithQuickRow() =
        capture("Running_quickRow", TimerStates.running(), other = TimerStates.quick(TimerPhase.RUNNING))

    @Test
    fun running() = capture("Running", TimerStates.running())

    /** The alert's strong phase: matcha in the dark theme, washi-sage in the light one (03 §3.6), hence the names. */
    @Test
    fun alerting() = capture(
        if (appDark) "Alerting_matchaPhase" else "Alerting_strongPhase",
        TimerStates.alerting(overtimeMs = 7_000),
    )

    /**
     * With the taste button (D-114): the band is down to the top bar already, so the button goes below the control
     * and *Start* stays on the screen (D-096).
     */
    @Test
    fun readyWithTaste() = capture("Ready_taste", TimerStates.ready(stepIndex = 1), tasting = TASTING)

    /** The session's first READY: the button's space kept below the control, *Start* where it stays (MIN-1). */
    @Test
    fun readyBeforeTheFirstInfusionWithTaste() =
        capture("Ready_tasteSpace", TimerStates.ready(stepIndex = 0), tasting = TASTING)

    /** The alert with the taste button's space kept below the control: *Abgegossen* where *Start* was. */
    @Test
    fun alertingWithTaste() =
        capture("Alerting_taste", TimerStates.alerting(overtimeMs = 7_000, stepIndex = 1), tasting = TASTING)
}
