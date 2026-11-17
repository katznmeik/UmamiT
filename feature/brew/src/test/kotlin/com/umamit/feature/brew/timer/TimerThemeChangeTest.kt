package com.umamit.feature.brew.timer

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.designsystem.theme.DefaultExtendedColors
import com.umamit.core.designsystem.theme.LightTimerAlertColors
import com.umamit.core.designsystem.theme.SteepingColorScheme
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.designsystem.theme.WashiColorScheme
import com.umamit.core.model.StepTasting
import com.umamit.core.model.TasteProfile
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A theme change while a timer shows (*System* at night, *Design* changed in Timer-Einstellungen; SET-05, D-128 Q20)
 * recolours it in one frame like every other screen and never restarts, delays or hides the countdown, a sheet or the
 * alert: the alert's pulse keeps its phase (rule 8).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerThemeChangeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var dark by mutableStateOf(false)

    private var start = 0L

    private val now: () -> Long = { NOW + compose.mainClock.currentTime - start }

    /**
     * A pixel near the bottom-left corner of the activity's window: the timer's background, or the alert surface, with
     * nothing on it (a sheet is a window of its own). Drawn here: `captureToImage()` waits for a redraw that never
     * comes while the test clock is paused.
     */
    private fun background(): Color {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return Color(bitmap.getPixel(2, view.height - 2))
    }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun showTimer(state: TimerUiState.Active, reduceMotion: Boolean, tasting: TimerTasting? = null) {
        compose.mainClock.autoAdvance = false
        start = compose.mainClock.currentTime
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = reduceMotion) {
                TimerScreen(
                    state = state,
                    nowElapsedMs = now,
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    tasting = tasting,
                )
            }
        }
        frame()
    }

    @Test
    fun `a theme change while running recolours in one frame and keeps the countdown and the open sheet`() {
        val tasting = TimerTasting(
            mapOf(0 to StepTasting(TasteProfile(umami = 4), note = null)),
            onTaste = { _, _, _ -> },
            onReset = { _, _ -> },
            onNote = { _, _ -> },
        )
        showTimer(TimerStates.running(remainingMs = 20_000, stepIndex = 1), reduceMotion = true, tasting = tasting)
        assertWithMessage("light timer").that(background()).isEqualTo(WashiColorScheme.background)
        compose.onNodeWithText("0:20", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TIMER_TASTE_BUTTON_TAG).performClick()
        repeat(2) { frame() }
        compose.onNodeWithTag(TIMER_TASTE_SHEET_TAG).assertIsDisplayed()

        dark = true
        frame()
        assertWithMessage("dark timer, one frame later").that(background()).isEqualTo(SteepingColorScheme.background)
        compose.onNodeWithTag(TIMER_TASTE_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("0:20", useUnmergedTree = true).assertIsDisplayed()

        dark = false
        frame()
        assertWithMessage("light timer again").that(background()).isEqualTo(WashiColorScheme.background)
        compose.onNodeWithTag(TIMER_TASTE_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("0:20", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a theme change during the alert keeps its pulse running, never back at its first frame`() {
        showTimer(TimerStates.alerting(overtimeMs = 7_000), reduceMotion = false)
        assertWithMessage("first frame, full sage")
            .that(background().distanceTo(LightTimerAlertColors.matcha))
            .isLessThan(NEAR)

        // Half a cycle later the pulse is at washi; the theme changes there.
        compose.mainClock.advanceTimeBy(ALERT_HALF_CYCLE_MILLIS)
        compose.waitForIdle()
        assertWithMessage("washi phase")
            .that(background().distanceTo(DefaultExtendedColors.alert.washi))
            .isLessThan(NEAR)
        dark = true
        frame()
        // A restarted pulse would be full matcha in this frame; the running one is still near washi.
        assertWithMessage("one frame after the change, still near washi")
            .that(background().distanceTo(DefaultExtendedColors.alert.washi))
            .isLessThan(NEAR)
        compose.onNodeWithText("+0:07", useUnmergedTree = true).assertIsDisplayed()

        // The next strong phase is the dark timer's matcha (D-046).
        compose.mainClock.advanceTimeBy(ALERT_HALF_CYCLE_MILLIS)
        compose.waitForIdle()
        assertWithMessage("the strong phase in the dark timer")
            .that(background().distanceTo(DefaultExtendedColors.alert.matcha))
            .isLessThan(NEAR)
    }

    private fun Color.distanceTo(other: Color): Float =
        maxOf(kotlin.math.abs(red - other.red), kotlin.math.abs(green - other.green), kotlin.math.abs(blue - other.blue))

    private companion object {
        const val ALERT_HALF_CYCLE_MILLIS = 750L
        const val NEAR = 0.06f
    }
}
