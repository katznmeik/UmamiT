package com.umamit.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The TMR-21 question on Start gives way to an alert in the UI itself (rule 8). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenQuestionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the question is removed in the frame an alert starts, before the view model closes it`() {
        var state by mutableStateOf(HomeUiState(loading = false, question = HomeQuestion("Bio Sencha Hisui")))
        compose.setContent { UmamiTheme(reduceMotion = true) { HomeScreen(state = state, nowElapsedMs = { 0L }) } }
        compose.onNodeWithText("Erst die Bio Sencha Hisui-Sitzung beenden?").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        // The alert's key arrives while the question is still in the state.
        state = state.copy(alertKey = "s1:0:1")
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

        compose.onNodeWithText("Erst die Bio Sencha Hisui-Sitzung beenden?").assertDoesNotExist()
    }
}
