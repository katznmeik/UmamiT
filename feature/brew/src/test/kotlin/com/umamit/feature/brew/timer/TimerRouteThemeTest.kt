package com.umamit.feature.brew.timer

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.data.PhotoFiles
import com.umamit.core.designsystem.theme.SteepingColorScheme
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.designsystem.theme.WashiColorScheme
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SET-05 AC1 (D-128 Q21): with *Design* „Hell" the timer route's background is washi from its first frame, also
 * restored before recovery, when it draws nothing else, so no dark frame shows under the timer; with „Dunkel" it is
 * Steeping as until v0.7.2 (AC2).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerRouteThemeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private fun routeBackground(dark: Boolean): Color {
        val collection = FakeCollection()
        val sessions = FakeSessions(collection)
        val viewModel = TimerViewModel(
            // Restored before recovery: the route draws only its background (A-05).
            FakeBrewTimer(TimerUiState.Idle, recovered = false),
            FakeClock(elapsed = NOW),
            FakeSettingsRepository(),
            FakeTimerReliability(),
            sessions.sessions,
            collection.teas,
            PhotoFiles(folder.newFolder()),
            collection.stock,
            sessions.journal,
            sessions.tasting,
        )
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                BrewTimerRoute(onClose = {}, onAdjustBrew = {}, onOpenTimerSettings = {}, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
        // Drawn here: `captureToImage()` waits for a redraw the route never asks for before recovery.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return Color(bitmap.getPixel(view.width / 2, view.height / 2))
    }

    @Test
    fun `with Design Hell the route background is washi`() {
        assertWithMessage("route background, Hell").that(routeBackground(dark = false))
            .isEqualTo(WashiColorScheme.background)
    }

    @Test
    fun `with Design Dunkel the route background is Steeping`() {
        assertWithMessage("route background, Dunkel").that(routeBackground(dark = true))
            .isEqualTo(SteepingColorScheme.background)
    }
}
