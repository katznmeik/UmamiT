package com.umamit.feature.brew.timer

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-05 in every state, at font scale 1.0 and 2.0 (03 §6), inside the light
 * and the dark app theme: the timer is always dark, so both look the same.
 * The alert is captured in both pulse phases (matcha and washi) and with the
 * prominent overtime; the content must stay readable in each (D-055). The top
 * is the ink-wash band A13; a brew's seal shows a synthetic white-backed tea
 * photo (never a Tee-Kontor photo, D-016), a plain white one, or the leaf
 * without a photo (D-095). Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerScreenScreenshotTest(private val appDark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "appDark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        /** Half an alert pulse cycle (03 §3.6 `alertPulse`): the washi phase. */
        private const val PULSE_HALF_CYCLE_MILLIS = 750L
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (appDark) "appDark" else "appLight"}_font${fontScale.toString().replace(
        '.',
        '_',
    )}"

    private fun capture(
        name: String,
        state: TimerUiState.Active,
        advanceMillis: Long = 0,
        photo: SealPhoto = SealPhoto.Leaves,
        stock: TimerStock? = TimerStock(38.0, StockLevel.AVAILABLE),
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.mainClock.autoAdvance = false
        val start = compose.mainClock.currentTime
        compose.setContent {
            UmamiTheme(darkTheme = appDark, reduceMotion = false) {
                TimerScreen(
                    state = state,
                    nowElapsedMs = { NOW + compose.mainClock.currentTime - start },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    teaPhoto = when (photo) {
                        SealPhoto.Leaves -> remember { SyntheticTeaPhoto() }
                        SealPhoto.White -> remember { ColorPainter(Color.White) }
                        SealPhoto.None -> null
                    },
                    teaStyleKey = "gyokuro",
                    stock = stock,
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        if (advanceMillis > 0) compose.mainClock.advanceTimeBy(advanceMillis)
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TimerScreen_${name}_$variant.png")
    }

    @Test
    fun ready() = capture("Ready", TimerStates.ready(stepIndex = 1))

    /** The tea is *Knapp* (BRW-11, D-098): „14 g übrig · knapp" under the leaf and water. */
    @Test
    fun readyLowStock() =
        capture("Ready_lowStock", TimerStates.ready(stepIndex = 1), stock = TimerStock(14.0, StockLevel.LOW))

    @Test
    fun running() = capture("Running", TimerStates.running(remainingMs = 48_000))

    /** A pure white photo, the usual shop background: the seal shows only the disc's cream (D-095, D-016). */
    @Test
    fun runningOnWhitePhoto() = capture("Running_whitePhoto", TimerStates.running(), photo = SealPhoto.White)

    /** A tea without a photo: the leaf in its type's colour (gyokuro) on the seal (D-095). */
    @Test
    fun runningWithoutPhoto() = capture("Running_noPhoto", TimerStates.running(), photo = SealPhoto.None)

    /** Step 2 (0:20) after its pre-alert: the automatic rule gives it 5 s (TMR-23), so 0:04 is left. */
    @Test
    fun preAlert() = capture("PreAlert", TimerStates.running(remainingMs = 4_000, preAlertPlayed = true, stepIndex = 1))

    @Test
    fun paused() = capture("Paused", TimerStates.paused())

    @Test
    fun alertingMatchaPhase() = capture("Alerting_matchaPhase", TimerStates.alerting(overtimeMs = 7_000))

    @Test
    fun alertingWashiPhase() = capture(
        "Alerting_washiPhase",
        TimerStates.alerting(overtimeMs = 7_000),
        advanceMillis = PULSE_HALF_CYCLE_MILLIS,
    )

    @Test
    fun alertingProminentOvertimeWashiPhase() = capture(
        "Alerting_overtime_washiPhase",
        TimerStates.alerting(overtimeMs = 150_000),
        advanceMillis = PULSE_HALF_CYCLE_MILLIS,
    )

    @Test
    fun alertingRinse() = capture("Alerting_rinse", TimerStates.alerting(rinse = true, stepIndex = 0))

    @Test
    fun finished() = capture("Finished", TimerStates.finished())

    @Test
    fun quickRunning() = capture("Quick_running", TimerStates.quick(TimerPhase.RUNNING), photo = SealPhoto.None)

    @Test
    fun quickPaused() = capture("Quick_paused", TimerStates.quick(TimerPhase.PAUSED), photo = SealPhoto.None)

    @Test
    fun quickAlerting() = capture("Quick_alerting", TimerStates.quick(TimerPhase.ALERTING), photo = SealPhoto.None)

    @Test
    fun quickReady() = capture("Quick_ready", TimerStates.quick(TimerPhase.READY), photo = SealPhoto.None)

    @Test
    fun readyColdBrew() = capture("Ready_coldBrew", TimerStates.coldReady(iceBrew = false))

    @Test
    fun readyIceBrew() = capture("Ready_iceBrew", TimerStates.coldReady(iceBrew = true))
}

/** What the seal shows in a capture. */
private enum class SealPhoto { Leaves, White, None }

/**
 * A synthetic product shot (D-016): a few green leaves on a white background,
 * like a shop photo. On the seal the white turns into the disc's cream.
 */
private class SyntheticTeaPhoto : Painter() {
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
