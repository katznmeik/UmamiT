package com.umamit.feature.collection.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.designsystem.motion.MotionTokens
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tea that arrives during the push (A-12, NFR-PERF-12): the top bar with its back arrow stays at full opacity under
 * the arriving content until that has faded in, so the back arrow exists in every frame and never blinks out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailArrivalTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf<TeaDetailUiState>(TeaDetailUiState.Loading)

    @Test
    fun `from Loading to Ready the back arrow is there in every frame`() {
        arrive(TeaDetailUiState.Ready(TeaDetailScreenTest.sample()))
    }

    @Test
    fun `from Loading to Gone the back arrow is there in every frame`() {
        arrive(TeaDetailUiState.Gone)
    }

    private fun arrive(next: TeaDetailUiState) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = false) {
                TeaDetailScreen(state, TeaDetailActions(), photo = { null })
            }
        }
        compose.mainClock.advanceTimeByFrame()
        assertFrame("loading", bar = true)

        state = next
        var elapsed = 0L
        while (elapsed < MotionTokens.STANDARD_MILLIS) {
            compose.mainClock.advanceTimeByFrame()
            elapsed += FRAME_MS
            assertFrame("$elapsed ms into the arrival", bar = elapsed < MotionTokens.STANDARD_MILLIS - FRAME_MS)
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertWithMessage("the loading bar after the fade")
            .that(compose.onAllNodesWithTag(TEA_DETAIL_LOADING_BAR_TAG).fetchSemanticsNodes())
            .isEmpty()
        assertWithMessage("the back arrow after the fade")
            .that(compose.onAllNodesWithContentDescription("Zurück").fetchSemanticsNodes())
            .isNotEmpty()
    }

    /** The back arrow exists; while the content still fades in ([bar]), the unfaded loading bar holds it. */
    private fun assertFrame(where: String, bar: Boolean) {
        assertWithMessage("$where: a back arrow")
            .that(compose.onAllNodesWithContentDescription("Zurück").fetchSemanticsNodes())
            .isNotEmpty()
        if (bar) {
            assertWithMessage("$where: the loading bar under the fade")
                .that(compose.onAllNodesWithTag(TEA_DETAIL_LOADING_BAR_TAG).fetchSemanticsNodes())
                .isNotEmpty()
        }
    }

    private companion object {
        const val FRAME_MS = 16L
        const val SETTLE_MS = 500L
    }
}
