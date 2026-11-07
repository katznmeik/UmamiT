package com.umamit.feature.journal.session

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.journal.session.SessionPageScreenshotTest.Companion.page
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The session page (SCR-16, D-100) on a short screen (360 × 640 dp) at font scale 2.0 (03 §6): the steps stack
 * instead of the table, and scrolled to its end the archived tea's greyed-out button with *Wiederherstellen* fits.
 * Goldens: `src/test/screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2.0f)
class SessionPageShortScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, state: SessionPageUiState, scrollToEnd: Boolean = false) {
        compose.setContent { UmamiTheme(darkTheme = false, reduceMotion = true) { SessionPageScreen(state) } }
        compose.waitForIdle()
        if (scrollToEnd) compose.onNodeWithTag(SESSION_RESTORE_TAG).performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/SessionPage_${name}_short_font2_0.png")
    }

    @Test
    fun top() = capture("top", SessionPageUiState.Ready(page))

    @Test
    fun archivedEnd() =
        capture("archivedEnd", SessionPageUiState.Ready(page.copy(tea = SessionTea.ARCHIVED)), scrollToEnd = true)
}
