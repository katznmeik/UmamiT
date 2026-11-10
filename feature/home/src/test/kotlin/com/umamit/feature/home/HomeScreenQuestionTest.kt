package com.umamit.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.timer.controller.TimerToEnd
import com.umamit.core.timer.engine.SessionKind
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
    fun `a brew whose tea is unknown is an Aufguss, never the Schnelltimer`() {
        val ended = mutableListOf<TimerSlot>()
        val unnamed = HomeQuestion(
            listOf(TimerToEnd(TimerSlot.BREW, "Bio Sencha Hisui", "s1"), TimerToEnd(TimerSlot.BREW_2, null, "s2")),
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                HomeScreen(
                    state = HomeUiState(loading = false, question = unnamed),
                    nowElapsedMs = { 0L },
                    actions = HomeActions(endTimer = { ended += it }),
                )
            }
        }

        compose.onNodeWithContentDescription("Schnelltimer beenden").assertDoesNotExist()
        compose.onNodeWithContentDescription("Aufguss beenden").performClick()

        assertThat(ended).containsExactly(TimerSlot.BREW_2)
    }

    @Test
    fun `Aufguss starten shows beside one active brew and is gone with two timers (SCR-02, D-108)`() {
        val brew = FakeBrewStarter.quickRunningState(
            60,
        ).copy(kind = SessionKind.Brew("s1"), teaName = "Bio Sencha Hisui")
        var state by mutableStateOf(HomeUiState(loading = false, active = brew))
        var picks = 0
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                HomeScreen(state = state, nowElapsedMs = { 0L }, actions = HomeActions(startBrew = { picks++ }))
            }
        }
        compose.onNodeWithTag(HOME_ACTIVE_CARD_TAG).assertIsDisplayed()

        compose.onNodeWithText("Aufguss starten").assertIsDisplayed().performClick()
        assertThat(picks).isEqualTo(1)

        state = state.copy(twoTimersActive = true)
        compose.waitForIdle()

        compose.onNodeWithText("Aufguss starten").assertDoesNotExist()
        compose.onNodeWithTag(HOME_ACTIVE_CARD_TAG).assertIsDisplayed()
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
