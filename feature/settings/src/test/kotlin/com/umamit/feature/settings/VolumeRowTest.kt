package com.umamit.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SET-27 *Lautstärke* (v0.3.0-rc.1 feedback): when a drag ends, the slider stays on the chosen step while the
 * setting is written; it never shows the stored value from before for the moment until the write arrives
 * (the thumb jumped back and forth on release).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class VolumeRowTest {
    @get:Rule
    val compose = createComposeRule()

    private var stored by mutableIntStateOf(100)
    private val written = mutableListOf<Int>()

    private fun show() = compose.setContent {
        UmamiTheme { VolumeRow(percent = stored, onChange = { written += it }) }
    }

    private fun sliderValue(): Float = compose.onNodeWithTag(SettingsTags.VOLUME).fetchSemanticsNode()
        .config[SemanticsProperties.ProgressBarRangeInfo].current

    @Test
    fun `the chosen step stays while the write is on its way`() {
        show()

        compose.onNodeWithTag(SettingsTags.VOLUME).performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        compose.waitForIdle()

        assertThat(written).containsExactly(60)
        assertThat(sliderValue()).isEqualTo(60f)

        stored = 60
        compose.waitForIdle()
        assertThat(sliderValue()).isEqualTo(60f)
    }

    @Test
    fun `a drag ends on its step and does not show the old value before the write arrives`() {
        stored = 10
        show()

        compose.onNodeWithTag(SettingsTags.VOLUME).performTouchInput {
            down(centerLeft + Offset(STEP_PX, 0f))
            repeat(MOVES) { moveBy(Offset((centerX - left) / MOVES, 0f)) }
            up()
        }
        compose.waitForIdle()

        val chosen = written.single()
        assertThat(sliderValue()).isEqualTo(chosen.toFloat())
        assertThat(chosen).isNotEqualTo(10)
    }

    @Test
    fun `a value stored from elsewhere afterwards is shown`() {
        show()
        compose.onNodeWithTag(SettingsTags.VOLUME).performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        compose.waitForIdle()
        stored = 60
        compose.waitForIdle()

        stored = 80
        compose.waitForIdle()

        assertThat(sliderValue()).isEqualTo(80f)
    }

    private companion object {
        /** A little inside the track's start, past the thumb's padding. */
        const val STEP_PX = 40f

        const val MOVES = 10
    }
}
