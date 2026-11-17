package com.umamit.feature.brew.timer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepTasting
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TasteSet
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.ui.TASTE_INFO_TAG
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-05's „Geschmack · N. Aufguss" button and sheet (v0.6.1, D-114), at font scale 1.0 and 2.0, inside the light
 * and the dark app theme (the timer pinned dark until #325, [UmamiThemeWithDarkTimer]): READY and RUNNING with
 * the button, the button with its „✓", and the sheet with a value and a note: per taste set, with „Was bedeuten die
 * Regler?" folded (as it opens) and unfolded (v0.8.0, D-128). The seal shows a plain white synthetic photo (D-016).
 * Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerTasteScreenshotTest(private val appDark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "appDark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }
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
        stored: Map<Int, StepTasting> = emptyMap(),
        openSheet: Boolean = false,
        set: TasteSet = TasteSet.GREEN,
        unfoldInfo: Boolean = false,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.mainClock.autoAdvance = false
        val start = compose.mainClock.currentTime
        compose.setContent {
            // Reduce motion for the sheet: it opens expanded, without the slide.
            UmamiThemeWithDarkTimer(darkTheme = appDark, reduceMotion = openSheet) {
                TimerScreen(
                    state = state,
                    nowElapsedMs = { NOW + compose.mainClock.currentTime - start },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    teaPhoto = ColorPainter(Color.White),
                    teaStyleKey = "gyokuro",
                    stock = TimerStock(38.0, StockLevel.AVAILABLE),
                    tasting = TimerTasting(
                        stored,
                        onTaste = { _, _, _ -> },
                        onReset = { _, _ -> },
                        onNote = { _, _ -> },
                        set = set,
                    ),
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        if (openSheet) {
            compose.onNodeWithTag(TIMER_TASTE_BUTTON_TAG).performClick()
            repeat(2) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
        }
        if (unfoldInfo) {
            compose.onNodeWithTag(TASTE_INFO_TAG).performClick()
            // The chevron's spring settles; the height does not animate under reduce motion.
            compose.mainClock.advanceTimeBy(SETTLE_MS)
            compose.waitForIdle()
        }
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TimerScreen_${name}_$variant.png")
    }

    /** The session's first READY: no button yet, its space kept, so the first *Abgegossen* moves nothing (MIN-1). */
    @Test
    fun readyBeforeTheFirstInfusion() = capture("Ready_tasteSpace", TimerStates.ready(stepIndex = 0))

    @Test
    fun readyWithTaste() = capture("Ready_taste", TimerStates.ready(stepIndex = 1))

    @Test
    fun runningWithTaste() = capture("Running_taste", TimerStates.running(stepIndex = 1, remainingMs = 14_000))

    @Test
    fun readyWithTasteDone() = capture(
        "Ready_tasteDone",
        TimerStates.ready(stepIndex = 2),
        stored = mapOf(1 to StepTasting(TasteProfile(umami = 4))),
    )

    @Test
    fun tasteSheet() = sheet("TasteSheet", TasteSet.GREEN, GREEN_TASTE)

    @Test
    fun tasteSheetOxidised() = sheet("TasteSheet_oxidised", TasteSet.OXIDISED, OXIDISED_TASTE)

    @Test
    fun tasteSheetHerbal() = sheet("TasteSheet_herbal", TasteSet.HERBAL, HERBAL_TASTE)

    @Test
    fun tasteSheetUnfolded() = sheet("TasteSheet_unfolded", TasteSet.GREEN, GREEN_TASTE, unfoldInfo = true)

    @Test
    fun tasteSheetUnfoldedOxidised() =
        sheet("TasteSheet_unfoldedOxidised", TasteSet.OXIDISED, OXIDISED_TASTE, unfoldInfo = true)

    @Test
    fun tasteSheetUnfoldedHerbal() =
        sheet("TasteSheet_unfoldedHerbal", TasteSet.HERBAL, HERBAL_TASTE, unfoldInfo = true)

    /** The sheet of the 1st infusion of a tea of [set], with [taste] and a note. */
    private fun sheet(name: String, set: TasteSet, taste: TasteProfile, unfoldInfo: Boolean = false) = capture(
        name,
        TimerStates.running(stepIndex = 1, remainingMs = 14_000),
        stored = mapOf(0 to StepTasting(taste, note = "Süß, nach Nori")),
        openSheet = true,
        set = set,
        unfoldInfo = unfoldInfo,
    )
}

/** Long enough for the info's chevron spring to settle. */
private const val SETTLE_MS = 1_000L

private val GREEN_TASTE = TasteProfile(umami = 4, sweetness = 3, body = 2)
private val OXIDISED_TASTE = TasteProfile(roast = 4, sweetness = 3, body = 2)
private val HERBAL_TASTE = TasteProfile(sweetness = 3, acidity = 2, freshness = 4)
