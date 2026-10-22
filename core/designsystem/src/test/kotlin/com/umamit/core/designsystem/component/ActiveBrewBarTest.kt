package com.umamit.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `ActiveBrewBar` (03 §3.8, GEN-06): one clickable element with a German description; it leaves when the brew ends. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActiveBrewBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val hisui = ActiveBrew(
        title = "Bio Sencha Hisui",
        step = "Aufguss 2/4",
        stepDescription = "Aufguss 2 von 4",
        remainingSeconds = 72,
        progress = 0.4f,
        liquorColor = Color(0xFFC9D58F),
    )

    private val description =
        "Laufender Aufguss: Bio Sencha Hisui, Aufguss 2 von 4, noch 1 Minute 12 Sekunden. Zum Timer"

    @Test
    fun `the bar is one clickable element with a German description`() {
        var clicks = 0
        compose.setContent {
            UmamiTheme(reduceMotion = false) { ActiveBrewBar(brew = hisui, onClick = { clicks++ }) }
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithContentDescription(description).assertHasClickAction().performClick()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `a quick timer has no step`() {
        compose.setContent {
            UmamiTheme(reduceMotion = false) {
                ActiveBrewBar(
                    brew = hisui.copy(
                        title = "Schnelltimer",
                        step = null,
                        stepDescription = null,
                    ),
                    onClick = {
                    },
                )
            }
        }
        compose.onNodeWithContentDescription("Laufender Timer: Schnelltimer, noch 1 Minute 12 Sekunden. Zum Timer")
            .assertHasClickAction()
    }

    @Test
    fun `the bar leaves when no brew is active`() {
        var brew by mutableStateOf<ActiveBrew?>(hisui)
        compose.setContent {
            UmamiTheme(reduceMotion = false) { ActiveBrewBar(brew = brew, onClick = {}) }
        }
        brew = null
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription(description).assertCountEquals(0)
    }
}
