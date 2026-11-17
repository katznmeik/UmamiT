package com.umamit.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.umamit.core.designsystem.R as DsR

/**
 * The ⋮ menu on the Statistik header opens below its button, end edge on the button's (SCR-18, 03 §2.2), in LTR at
 * 384 dp at font scales 1.0 and 2.0; and its golden, light and dark. Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w384dp-h891dp-xhdpi")
class StatsOverflowMenuTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun openMenu() {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    StatsContent(
                        state = StatsUiState.Data(StatsRange.DAYS_90, StatsTab.OVERVIEW),
                        onRangeChange = {},
                        onTabChange = {},
                        onOpenSettings = {},
                    )
                }
            }
        }
        compose.onNodeWithContentDescription(str(DsR.string.ds_more_options)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the menu's end edge lies within the button, not at the screen's start`() {
        openMenu()

        val button = compose.onNodeWithContentDescription(str(DsR.string.ds_more_options)).fetchSemanticsNode()
        val item = compose.onNodeWithText(str(DsR.string.ds_settings)).fetchSemanticsNode()
        // Screen coordinates: the popup is a window of its own, so window-relative bounds do not compare.
        // The menu is clamped to a small margin from the window edge, so its end may lie 4 dp beyond the button's.
        val slack = 8f * RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val buttonLeft = button.positionOnScreen.x
        val buttonRight = buttonLeft + button.size.width
        val buttonTop = button.positionOnScreen.y
        val itemRight = item.positionOnScreen.x + item.size.width
        assertThat(itemRight).isAtLeast(buttonLeft)
        assertThat(itemRight).isAtMost(buttonRight + slack)
        assertThat(item.positionOnScreen.y).isAtLeast(buttonTop)
    }

    @Test
    fun `golden of the open menu`() {
        openMenu()
        val variant = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/StatsOverflowMenu_$variant.png")
    }
}
