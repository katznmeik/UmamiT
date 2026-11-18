package com.umamit.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.app.timer.TimerRequest
import com.umamit.core.designsystem.theme.SteepingColorScheme
import com.umamit.core.designsystem.theme.UmamiAppTheme
import com.umamit.core.designsystem.theme.WashiColorScheme
import com.umamit.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SET-05 AC1: with *Design* „Hell" the cover shown while a notification opens the timer is washi, never the dark
 * Steeping background, also on a phone in its dark theme; with „Dunkel" it is Steeping, as until v0.7.2 (AC2).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerCoverThemeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun coverColour(mode: ThemeMode, systemDark: Boolean): Color {
        // The alert's full-screen intent arrived before the content was set (#47, D-058): the cover shows first.
        val request = MutableStateFlow(TimerRequest(1L))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiAppTheme(mode, systemDark = systemDark, reduceMotion = false) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(),
                    alertKey = MutableStateFlow("brew:0:60000"),
                    timerRequest = request,
                    settings = testSettingsScreens(),
                    startScreen = { Text("START") },
                    timerScreen = { _, _, _, _ -> Text("TIMER") },
                )
            }
        }
        compose.onNodeWithTag(SHELL_COVER_TAG).assertIsDisplayed()
        // Drawn here: `captureToImage()` waits for a redraw that never comes while the test clock is paused.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return Color(bitmap.getPixel(view.width / 2, view.height / 2))
    }

    @Test
    fun `with Design Hell the cover is washi on a phone in its dark theme`() {
        assertWithMessage("cover, Hell")
            .that(coverColour(ThemeMode.LIGHT, systemDark = true))
            .isEqualTo(WashiColorScheme.background)
    }

    @Test
    fun `with Design Dunkel the cover is Steeping on a phone in its light theme`() {
        assertWithMessage("cover, Dunkel")
            .that(coverColour(ThemeMode.DARK, systemDark = false))
            .isEqualTo(SteepingColorScheme.background)
    }
}
