package com.umamit.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The active brew bar with a brew and a quick timer (03 §3.8, D-098): two halves side by side, brew first, each its
 * own tap target with its own description; an alerting half takes the alert style alone, a held quick alert reads
 * „fertig" without it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h832dp-xhdpi")
class ActiveBrewBarTwoTimersTest {

    @get:Rule
    val compose = createComposeRule()

    private val hisui = ActiveBrew(
        title = "Bio Sencha Hisui",
        step = "Aufguss 2/4",
        stepDescription = "Aufguss 2 von 4",
        remainingSeconds = 72,
        liquorColor = Color(0xFFC9D58F),
    )

    private val quick =
        ActiveBrew(title = "Schnelltimer", step = null, remainingSeconds = 134, liquorColor = Color.Gray)

    private val brewDescription =
        "Laufender Aufguss: Bio Sencha Hisui, Aufguss 2 von 4, noch 1 Minute 12 Sekunden. Zum Timer"
    private val quickDescription = "Laufender Timer: Schnelltimer, noch 2 Minuten 14 Sekunden. Zum Timer"

    private var brewClicks = 0
    private var quickClicks = 0

    private fun show(brew: ActiveBrew?, alongside: ActiveBrew?) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                ActiveBrewBar(
                    brew = brew,
                    progress = { 0.4f },
                    onClick = { brewClicks++ },
                    alongside = alongside,
                    alongsideProgress = { 0.7f },
                    onAlongsideClick = { quickClicks++ },
                )
            }
        }
    }

    @Test
    fun `two halves, brew first, each opens its own timer`() {
        show(hisui, quick)

        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        val brewHalf = compose.onNodeWithContentDescription(brewDescription).assertHasClickAction()
        val quickHalf = compose.onNodeWithContentDescription(quickDescription).assertHasClickAction()
        assertThat(brewHalf.getUnclippedBoundsInRoot().right).isAtMost(quickHalf.getUnclippedBoundsInRoot().left)
        assertThat(quickHalf.getUnclippedBoundsInRoot().height).isAtLeast(48.dp)

        brewHalf.performClick()
        quickHalf.performClick()
        quickHalf.performClick()

        assertThat(brewClicks).isEqualTo(1)
        assertThat(quickClicks).isEqualTo(2)
    }

    @Test
    fun `each half shows its countdown, the brew its step`() {
        show(hisui, quick)

        compose.onNodeWithText("1:12", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Aufguss 2/4", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("2:14", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `an alerting half says so alone`() {
        show(hisui, quick.copy(alerting = true, overtimeSeconds = 7))

        compose.onNodeWithContentDescription("Jetzt abgießen: Schnelltimer, 7 Sekunden überzogen. Zum Timer")
            .assertHasClickAction()
        compose.onNodeWithContentDescription(brewDescription).assertHasClickAction()
        compose.onNodeWithText("+0:07", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a held quick alert reads fertig, not the alert`() {
        show(hisui.copy(alerting = true, overtimeSeconds = 3), quick.copy(held = true))

        compose.onNodeWithText("fertig", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Schnelltimer, fertig, wartet auf den Aufguss. Zum Timer")
            .assertHasClickAction()
        compose.onNodeWithText("+0:03", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `one timer alone keeps the full bar`() {
        show(hisui, alongside = null)

        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithContentDescription(brewDescription).performClick()
        assertThat(brewClicks).isEqualTo(1)
    }
}
