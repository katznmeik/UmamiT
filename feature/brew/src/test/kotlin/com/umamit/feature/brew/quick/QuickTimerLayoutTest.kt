package com.umamit.feature.brew.quick

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.engine.PreAlertChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-07's layout before *Start* with real text measurement (native graphics): the calm *Vorwarnung* row (polish 1,
 * D-098). The ring and the pill at *Start* are in `TimerScreenLayoutTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class QuickTimerLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the label Vorwarnung keeps its place for every choice (SCR-07 polish 1)`() = labelStaysPut()

    @Test
    @Config(qualifiers = "w384dp-h832dp-xhdpi", fontScale = 2.0f)
    fun `on a 384 x 832 dp screen at font scale 2_0 the label Vorwarnung keeps its place too`() = labelStaysPut()

    /** „Auto · 10 s", „Auto · keine", „Aus", „20 s", „20 s · zu kurz", „5 s", „30 s · keine". */
    private fun labelStaysPut() {
        val cases = listOf(
            QuickTimerUiState(durationSeconds = 120),
            QuickTimerUiState(durationSeconds = 10),
            QuickTimerUiState(durationSeconds = 120, preAlertChoice = PreAlertChoice.Off),
            QuickTimerUiState(durationSeconds = 120, preAlertChoice = PreAlertChoice.Seconds(20)),
            QuickTimerUiState(durationSeconds = 20, preAlertChoice = PreAlertChoice.Seconds(20)),
            QuickTimerUiState(durationSeconds = 120, preAlertChoice = PreAlertChoice.Seconds(5)),
            QuickTimerUiState(durationSeconds = 4_500, preAlertChoice = PreAlertChoice.Seconds(30)),
        )
        var state by mutableStateOf(cases.first())
        compose.setContent {
            UmamiTheme(reduceMotion = true) { QuickTimerScreen(state = state, actions = QuickTimerActions()) }
        }
        val positions = cases.map { shown ->
            state = shown
            compose.waitForIdle()
            compose.onNodeWithText("Vorwarnung").fetchSemanticsNode().boundsInRoot.topLeft
        }
        assertThat(positions.distinct()).hasSize(1)
    }
}
