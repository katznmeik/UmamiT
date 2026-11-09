package com.umamit.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.TimerToEnd
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** „Welchen Timer beenden?" on Start (TMR-21, D-108): its answers, and it gives way to an alert in the UI (rule 8). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenQuestionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val question = HomeQuestion(
        listOf(TimerToEnd(TimerSlot.BREW, "Bio Sencha Hisui"), TimerToEnd(TimerSlot.QUICK, null)),
    )

    @Test
    fun `each active timer is an answer, read as ending it, and ends its own slot`() {
        val ended = mutableListOf<TimerSlot>()
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                HomeScreen(
                    state = HomeUiState(loading = false, question = question),
                    nowElapsedMs = { 0L },
                    actions = HomeActions(endTimer = { ended += it }),
                )
            }
        }
        compose.onNodeWithText("Welchen Timer beenden?").assertIsDisplayed()

        compose.onNodeWithContentDescription("Schnelltimer beenden").performClick()
        compose.onNodeWithContentDescription("Bio Sencha Hisui beenden").performClick()

        assertThat(ended).containsExactly(TimerSlot.QUICK, TimerSlot.BREW).inOrder()
    }

    @Test
    fun `the question is removed in the frame an alert starts, before the view model closes it`() {
        var state by mutableStateOf(HomeUiState(loading = false, question = question))
        compose.setContent { UmamiTheme(reduceMotion = true) { HomeScreen(state = state, nowElapsedMs = { 0L }) } }
        compose.onNodeWithText("Welchen Timer beenden?").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        // The alert's key arrives while the question is still in the state.
        state = state.copy(alertKey = "s1:0:1")
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

        compose.onNodeWithText("Welchen Timer beenden?").assertDoesNotExist()
    }
}
