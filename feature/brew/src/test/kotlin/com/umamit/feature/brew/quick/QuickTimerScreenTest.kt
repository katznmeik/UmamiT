package com.umamit.feature.brew.quick

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.brew.setup.ActiveSessionQuestion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SCR-07 before its *Start* with fake state (spec #40 seam 4): chips, the
 * duration picker's bounds and increments, *Start*, the second-timer question,
 * and nothing of it over an alert (rule 8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class QuickTimerScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(QuickTimerUiState())
    private val calls = mutableListOf<String>()

    private fun show(initial: QuickTimerUiState = QuickTimerUiState()) {
        state = initial
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                QuickTimerScreen(
                    state = state,
                    actions = QuickTimerActions(
                        back = { calls += "back" },
                        select = {
                            calls += "select $it"
                            state = state.copy(durationSeconds = it)
                        },
                        start = { calls += "start" },
                        confirmRestart = { calls += "restart" },
                        dismissQuestion = { calls += "dismiss" },
                    ),
                )
            }
        }
    }

    private fun total() = compose.onNodeWithTag(QUICK_PICKER_TOTAL_TAG)

    @Test
    fun `a chip selects its duration and Start starts it (TMR-22)`() {
        show()

        compose.onNodeWithText("3:00").performClick()
        compose.onNodeWithText("3:00").assertIsSelected()
        compose.onNodeWithText("Start").performClick()

        assertThat(calls).containsExactly("select 180", "start").inOrder()
    }

    @Test
    fun `the ring shows the chosen duration and offers the picker`() {
        show(QuickTimerUiState(durationSeconds = 90))

        compose.onNodeWithTag(QUICK_RING_TAG).assertIsDisplayed()
        compose.onNodeWithTag(QUICK_RING_TAG).assert(
            SemanticsMatcher("describes 1:30") {
                it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .any { d -> "1 Minute 30 Sekunden" in d }
            },
        )
        compose.onNodeWithTag(QUICK_RING_TAG).performClick()

        compose.onNodeWithText("Dauer wählen").assertIsDisplayed()
    }

    @Test
    fun `the picker moves in 5 s below 10 min and applies the custom duration`() {
        show(QuickTimerUiState(durationSeconds = 90))
        compose.onNodeWithTag(QUICK_RING_TAG).performClick()

        compose.onNodeWithContentDescription("5 Sekunden mehr").performClick()
        compose.onNodeWithContentDescription("Eine Minute mehr").performClick()
        total().assert(hasText("2:35"))
        compose.onNodeWithText("Übernehmen").performClick()

        assertThat(calls).containsExactly("select 155")
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `the picker stops at 5 s and at 24 h, and has whole minutes from 10 min on (PRF-04)`() {
        show(QuickTimerUiState(durationSeconds = 5))
        compose.onNodeWithTag(QUICK_RING_TAG).performClick()
        compose.onNodeWithContentDescription("5 Sekunden weniger").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Eine Minute weniger").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Eine Stunde weniger").assertIsNotEnabled()

        repeat(24) { compose.onNodeWithContentDescription("Eine Stunde mehr").performClick() }
        total().assert(hasText("24:00:00"))
        compose.onNodeWithContentDescription("Eine Stunde mehr").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Eine Minute mehr").assertIsNotEnabled()
        compose.onNodeWithContentDescription("5 Sekunden mehr").assertIsNotEnabled()
    }

    @Test
    fun `Abbrechen in the picker keeps the duration`() {
        show(QuickTimerUiState(durationSeconds = 90))
        compose.onNodeWithTag(QUICK_RING_TAG).performClick()
        compose.onNodeWithContentDescription("Eine Minute mehr").performClick()

        compose.onNodeWithText("Abbrechen").performClick()

        assertThat(calls).isEmpty()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `while a session is active, Start asks first (TMR-21)`() {
        show(QuickTimerUiState(question = ActiveSessionQuestion("Bio Sencha Hisui")))

        compose.onNodeWithText("Beenden & neu starten").performClick()

        assertThat(calls).containsExactly("restart")
    }

    @Test
    fun `back leaves the screen`() {
        show()
        compose.onNodeWithContentDescription("Zurück").performClick()
        assertThat(calls).containsExactly("back")
    }

    @Test
    fun `an alert removes the picker at once, and it stays closed (rule 8)`() {
        show()
        compose.onNodeWithTag(QUICK_RING_TAG).performClick()
        compose.onNode(isDialog()).assertExists()

        compose.mainClock.autoAdvance = false
        state = state.copy(alertKey = "s1:0:1")
        // The same two frames the timer's own overlays are checked in (TimerScreenTest).
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

        compose.onNode(isDialog()).assertDoesNotExist()
        // It stays closed after the alert.
        compose.mainClock.autoAdvance = true
        state = state.copy(alertKey = null)
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `the second-timer question never shows over an alert (rule 8)`() {
        show(QuickTimerUiState(question = ActiveSessionQuestion(null), alertKey = "s1:0:1"))

        compose.onNode(isDialog()).assertDoesNotExist()
    }
}
