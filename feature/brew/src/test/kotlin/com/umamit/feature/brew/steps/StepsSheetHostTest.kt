package com.umamit.feature.brew.steps

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The modal sheet around the content (SCR-31, D-051): Material's sheet in its
 * own window, closed by *Fertig*, drag, back, scrim and handle, reopened
 * cleanly, and removed at once when the timer alerts (rule 8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class StepsSheetHostTest {

    @get:Rule
    val compose = createComposeRule()

    private val input = StepsSheetInput(
        steps = listOf(
            StepsSheetStep(StepKind.INFUSION, 60, 60, StepStatus.DONE),
            StepsSheetStep(StepKind.INFUSION, 30, 60, StepStatus.PENDING),
        ),
        hasRecipe = true,
        baseTempC = 60,
        preAlertBaseSeconds = 10,
    )

    private var visible by mutableStateOf(true)
    private var alerting by mutableStateOf(false)
    private val done = mutableListOf<TimerEvent.EditSteps>()
    private var dismissals = 0

    private val title = "Schritte für diese Sitzung"
    private val sheet =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle) and hasAnyDescendant(hasText(title))

    private fun showHost(reduceMotion: Boolean = false) {
        val state = StepsSheetState(input)
        compose.setContent {
            UmamiTheme(reduceMotion = reduceMotion) {
                Box {
                    StepsSheet(
                        visible = visible,
                        state = state,
                        onDone = {
                            done += it
                            visible = false
                        },
                        onSaveToRecipe = {},
                        onDismissRequest = {
                            dismissals++
                            visible = false
                        },
                        alerting = alerting,
                    )
                }
            }
        }
    }

    @Test
    fun `the sheet has its own window, so focus stays inside it`() {
        showHost()

        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun `Fertig emits the request and the host hides the sheet`() {
        showHost()

        compose.onNodeWithText("Fertig").performClick()

        assertThat(done).hasSize(1)
        compose.onNodeWithText(title).assertDoesNotExist()
        assertThat(dismissals).isEqualTo(0)
    }

    @Test
    fun `a fling down closes the sheet without applying anything`() {
        showHost()

        compose.onNode(sheet).performTouchInput { swipeDown(startY = top + 10f, endY = bottom, durationMillis = 120) }

        compose.waitForIdle()
        assertThat(dismissals).isEqualTo(1)
        assertThat(done).isEmpty()
        compose.onNodeWithText(title).assertDoesNotExist()
    }

    @Test
    fun `a short slow drag springs back`() {
        showHost()

        compose.onNode(sheet).performTouchInput {
            swipeDown(startY = top + 10f, endY = top + 40f, durationMillis = 1_000)
        }

        compose.waitForIdle()
        assertThat(dismissals).isEqualTo(0)
        compose.onNodeWithText("Fertig").assertIsDisplayed()
    }

    @Test
    fun `back closes the sheet without applying anything`() {
        showHost()

        Espresso.pressBack()

        compose.waitForIdle()
        assertThat(dismissals).isEqualTo(1)
        assertThat(done).isEmpty()
    }

    @Test
    fun `the scrim and the handle close the sheet`() {
        showHost()

        compose.onNodeWithContentDescription("Close sheet").performClick()
        compose.waitForIdle()
        assertThat(dismissals).isEqualTo(1)

        visible = true
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithContentDescription("Drag handle").performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()
        assertThat(dismissals).isEqualTo(2)
        compose.onNodeWithText(title).assertDoesNotExist()
    }

    @Test
    fun `the handle is a 48 dp touch target`() {
        showHost()

        val handle = compose.onNodeWithContentDescription("Drag handle").fetchSemanticsNode()
        val heightDp = handle.boundsInRoot.height / handle.layoutInfo.density.density
        assertThat(heightDp).isAtLeast(48f)
    }

    @Test
    fun `a sheet reopened during its exit comes back fully open`() {
        showHost()
        compose.mainClock.autoAdvance = false

        visible = false
        compose.mainClock.advanceTimeBy(60)
        visible = true
        compose.mainClock.advanceTimeBy(2_000)

        compose.onNodeWithText("Fertig").assertIsDisplayed()
    }

    @Test
    fun `the save confirmation does not come back when the sheet reopens`() {
        showHost()
        compose.onNodeWithText("Im Rezept speichern").performClick()
        compose.onNodeWithText("Im Rezept gespeichert").assertIsDisplayed()

        visible = false
        compose.waitForIdle()
        visible = true
        compose.waitForIdle()

        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("Im Rezept gespeichert").assertDoesNotExist()
    }

    @Test
    fun `entering ALERTING removes the sheet at once, without the close animation`() {
        showHost()
        compose.mainClock.autoAdvance = false

        visible = false
        alerting = true
        advanceTwoFrames()

        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `a normal close still animates over the same two frames`() {
        showHost()
        compose.mainClock.autoAdvance = false

        visible = false
        advanceTwoFrames()

        compose.onNodeWithText(title).assertExists()
    }

    /** The frame that recomposes after the state change and the frame that drops the sheet's window: 32 ms. */
    private fun advanceTwoFrames() {
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    @Test
    fun `while alerting the sheet stays hidden`() {
        alerting = true
        showHost()

        compose.onNodeWithText(title).assertDoesNotExist()
    }

    @Test
    fun `under reduce motion the sheet closes at once`() {
        showHost(reduceMotion = true)
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.mainClock.autoAdvance = false

        visible = false
        advanceTwoFrames()

        compose.onNodeWithText(title).assertDoesNotExist()
    }

    @Test
    fun `a hidden sheet shows nothing`() {
        visible = false
        showHost()

        compose.onNodeWithText(title).assertDoesNotExist()
    }

    private val discardTitle = "Änderungen verwerfen?"

    private fun lengthenStep2() {
        compose.onNodeWithContentDescription("Ziehzeit verlängern, Schritt 2").performScrollTo().performClick()
    }

    @Test
    fun `a swipe with unsaved edits asks before discarding them`() {
        showHost()
        lengthenStep2()

        compose.onNode(sheet).performTouchInput { swipeDown(startY = top + 10f, endY = bottom, durationMillis = 120) }
        compose.waitForIdle()

        compose.onNodeWithText(discardTitle).assertIsDisplayed()
        compose.onNodeWithText(title).assertExists()
        assertThat(dismissals).isEqualTo(0)
    }

    @Test
    fun `back with unsaved edits asks, and Weiter bearbeiten keeps the edits`() {
        showHost()
        lengthenStep2()

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(discardTitle).assertIsDisplayed()
        assertThat(dismissals).isEqualTo(0)

        compose.onNodeWithText("Weiter bearbeiten").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(discardTitle).assertDoesNotExist()
        compose.onNodeWithText("Fertig").performClick()
        assertThat(done.single().steps.last().plannedSeconds).isEqualTo(35)
    }

    @Test
    fun `the scrim with unsaved edits asks, and Verwerfen closes without applying`() {
        showHost()
        lengthenStep2()

        compose.onNodeWithContentDescription("Close sheet").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(discardTitle).assertIsDisplayed()

        compose.onNodeWithText("Verwerfen").performClick()
        compose.waitForIdle()
        assertThat(dismissals).isEqualTo(1)
        assertThat(done).isEmpty()
        compose.onNodeWithText(title).assertDoesNotExist()

        // Reopened, the discarded edit is gone.
        visible = true
        compose.waitForIdle()
        compose.onNodeWithText("Fertig").performClick()
        assertThat(done.single().steps.last().plannedSeconds).isEqualTo(30)
    }

    @Test
    fun `edits undone by hand count as no edits`() {
        showHost()
        lengthenStep2()
        compose.onNodeWithContentDescription("Ziehzeit verkürzen, Schritt 2").performScrollTo().performClick()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithText(discardTitle).assertDoesNotExist()
        assertThat(dismissals).isEqualTo(1)
    }

    /** Picks [option] in step [number]'s pre-alert menu, opened through its TalkBack click action. */
    private fun choosePreAlert(number: Int, spoken: String, option: String) {
        compose.onNodeWithContentDescription("Vorwarnung, Schritt $number: $spoken. Ändern")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(option).performClick()
        // The menu's exit has ended, so a back press reaches the sheet.
        compose.waitForIdle()
    }

    @Test
    fun `a changed pre-alert is an unsaved edit, and set back by hand it is none`() {
        showHost()
        choosePreAlert(2, "Automatisch, 10 Sekunden vorher", "Aus")

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(discardTitle).assertIsDisplayed()
        compose.onNodeWithText("Weiter bearbeiten").performClick()
        compose.waitForIdle()
        choosePreAlert(2, "Aus", "Automatisch · 10 s")
        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithText(discardTitle).assertDoesNotExist()
        assertThat(dismissals).isEqualTo(1)
        assertThat(done).isEmpty()
    }

    @Test
    fun `Fertig with edits closes without asking`() {
        showHost()
        lengthenStep2()

        compose.onNodeWithText("Fertig").performClick()
        compose.waitForIdle()

        compose.onNodeWithText(discardTitle).assertDoesNotExist()
        compose.onNodeWithText(title).assertDoesNotExist()
        assertThat(done).hasSize(1)
    }

    @Test
    fun `entering ALERTING removes the sheet and the discard question at once`() {
        showHost()
        lengthenStep2()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(discardTitle).assertIsDisplayed()
        compose.mainClock.autoAdvance = false

        visible = false
        alerting = true
        advanceTwoFrames()

        compose.onNodeWithText(discardTitle).assertDoesNotExist()
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `the edits survive a configuration change`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            UmamiTheme(reduceMotion = true) {
                StepsSheetContent(state = rememberStepsSheetState(input), onDone = { done += it }, onSaveToRecipe = {})
            }
        }
        compose.onNodeWithContentDescription("Ziehzeit verlängern, Schritt 2").performScrollTo().performClick()

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Fertig").performClick()

        assertThat(done.single().steps.last().plannedSeconds).isEqualTo(35)
    }

    @Test
    fun `each step's pre-alert and the session's B survive a configuration change`() {
        // B = 5 s: the 0:30 step shows „Auto · 5 s", not the 10 s a default would give.
        val restoration = StateRestorationTester(compose)
        val input = input.copy(preAlertBaseSeconds = 5)
        restoration.setContent {
            UmamiTheme(reduceMotion = true) {
                StepsSheetContent(state = rememberStepsSheetState(input), onDone = { done += it }, onSaveToRecipe = {})
            }
        }
        choosePreAlert(2, "Automatisch, 5 Sekunden vorher", "15 s")

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("15 s", useUnmergedTree = true).assertIsDisplayed()
        choosePreAlert(2, "15 Sekunden vorher", "Automatisch · 5 s")
        compose.onNodeWithText("Fertig").performClick()

        assertThat(done.single().steps.map { it.preAlertSeconds }).containsExactly(null, null).inOrder()
    }

    @Test
    fun `a cold brew's long steps survive a configuration change`() {
        val restoration = StateRestorationTester(compose)
        val input = input.copy(longStepsOnly = true)
        restoration.setContent {
            UmamiTheme(reduceMotion = true) {
                StepsSheetContent(state = rememberStepsSheetState(input), onDone = { done += it }, onSaveToRecipe = {})
            }
        }

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithContentDescription("Vorwarnung, Schritt 2: Automatisch, keine. Ändern").assertExists()
    }
}
