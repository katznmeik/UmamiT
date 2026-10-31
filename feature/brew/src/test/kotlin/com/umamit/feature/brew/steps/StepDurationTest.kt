package com.umamit.feature.brew.steps

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.feature.brew.quick.QUICK_PICKER_TOTAL_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Setting a step's duration fast in the Schritte-Sheet (SCR-31, PRF-04, D-098, #141): a tap on the time opens
 * „Dauer wählen", and a held − / + repeats and speeds up, one PRF-04 step per repeat, with a haptic tick.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class StepDurationTest {

    @get:Rule
    val compose = createComposeRule()

    private val doneRequests = mutableListOf<TimerEvent.EditSteps>()
    private val ticks = mutableListOf<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            ticks += hapticFeedbackType
        }
    }

    private fun show(vararg steps: StepsSheetStep): StepsSheetState {
        val state = StepsSheetState(StepsSheetInput(steps.toList(), true, 60, 10))
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    StepsSheetContent(state = state, onDone = { doneRequests += it }, onSaveToRecipe = {})
                }
            }
        }
        return state
    }

    private fun pending(seconds: Int) = StepsSheetStep(StepKind.INFUSION, seconds, 60, StepStatus.PENDING)

    /** Step [number]'s duration as TalkBack reads it: a button that opens „Dauer wählen". */
    private fun duration(number: Int, spoken: String) =
        compose.onNodeWithContentDescription("Ziehzeit, Schritt $number: $spoken. Ändern")

    private fun openPicker(number: Int, spoken: String) = duration(number, spoken)
        .performScrollTo()
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        .performClick()

    private fun total() = compose.onNodeWithTag(QUICK_PICKER_TOTAL_TAG)

    /** Holds − or + of step 1 for [millis] ms on the test clock, then releases it. */
    private fun hold(description: String, millis: Long) {
        compose.mainClock.autoAdvance = false
        val button = compose.onNodeWithContentDescription(description).performScrollTo()
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(millis)
        button.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun shownSeconds(state: StepsSheetState, index: Int = 0) = state.steps[index].plannedSeconds

    @Test
    fun `a tap on the duration opens Dauer wählen with its value, and Übernehmen sets 10_00 in one go`() {
        show(pending(300))

        openPicker(1, "5 Minuten")
        compose.onNodeWithText("Dauer wählen").assertIsDisplayed()
        total().assert(hasText("5:00"))
        repeat(5) { compose.onNodeWithContentDescription("Eine Minute mehr").performClick() }
        total().assert(hasText("10:00"))
        compose.onNodeWithText("Übernehmen").performClick()

        compose.onNode(isDialog()).assertDoesNotExist()
        duration(1, "10 Minuten").assertIsDisplayed()
        compose.onNodeWithText("Fertig").performClick()
        assertThat(doneRequests.single().steps.single().plannedSeconds).isEqualTo(600)
    }

    @Test
    fun `Abbrechen in Dauer wählen changes nothing`() {
        val state = show(pending(300))

        openPicker(1, "5 Minuten")
        compose.onNodeWithContentDescription("Eine Minute mehr").performClick()
        compose.onNodeWithText("Abbrechen").performClick()

        compose.onNode(isDialog()).assertDoesNotExist()
        assertThat(shownSeconds(state)).isEqualTo(300)
        assertThat(state.isDirty).isFalse()
    }

    @Test
    fun `the dialog keeps PRF-04's bounds and rounding, 5 s to 24 h, whole minutes from 10 min`() {
        val state = show(pending(5), pending(595))

        openPicker(1, "5 Sekunden")
        compose.onNodeWithContentDescription("5 Sekunden weniger").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Eine Minute weniger").assertIsNotEnabled()
        compose.onNodeWithText("Abbrechen").performClick()

        // 9:55 + 1 min has no seconds from 10 min on: 10:00, and the seconds no longer move.
        openPicker(2, "9 Minuten 55 Sekunden")
        compose.onNodeWithContentDescription("Eine Minute mehr").performClick()
        total().assert(hasText("10:00"))
        compose.onNodeWithContentDescription("5 Sekunden mehr").assertIsNotEnabled()
        // 24 h is the bound: 23:10:00 has no hour more.
        repeat(23) { compose.onNodeWithContentDescription("Eine Stunde mehr").performClick() }
        total().assert(hasText("23:10:00"))
        compose.onNodeWithContentDescription("Eine Stunde mehr").assertIsNotEnabled()
        compose.onNodeWithText("Übernehmen").performClick()

        assertThat(shownSeconds(state, 1)).isEqualTo(83_400)
    }

    @Test
    fun `a done or running step's duration is no button`() {
        show(
            StepsSheetStep(StepKind.INFUSION, 60, 60, StepStatus.DONE),
            StepsSheetStep(StepKind.INFUSION, 60, 60, StepStatus.STARTED),
            pending(60),
        )

        compose.onAllNodes(hasContentDescription("Ziehzeit, Schritt", substring = true)).assertCountEquals(1)
        duration(3, "1 Minute").assertIsDisplayed()
    }

    @Test
    fun `holding + from 5_00 reaches 10_00 within about 3 s, then moves in whole minutes`() {
        val state = show(pending(300))

        hold("Ziehzeit verlängern, Schritt 1", 3_120)
        assertThat(shownSeconds(state)).isEqualTo(600)

        hold("Ziehzeit verlängern, Schritt 1", 1_000)
        // 400, 550, 700, 850, 1000 ms: five repeats, one minute each.
        assertThat(shownSeconds(state)).isEqualTo(900)
    }

    @Test
    fun `each repeat gives a light haptic tick, a plain tap still moves one step`() {
        val state = show(pending(300))

        hold("Ziehzeit verlängern, Schritt 1", 1_000)
        assertThat(shownSeconds(state)).isEqualTo(325)
        assertThat(ticks).containsExactlyElementsIn(List(5) { HapticFeedbackType.SegmentFrequentTick })

        ticks.clear()
        hold("Ziehzeit verlängern, Schritt 1", 300)
        assertThat(shownSeconds(state)).isEqualTo(330)
        assertThat(ticks).isEmpty()
    }

    @Test
    fun `releasing stops at once`() {
        val state = show(pending(300))

        hold("Ziehzeit verkürzen, Schritt 1", 1_000)
        compose.mainClock.advanceTimeBy(2_000)

        assertThat(shownSeconds(state)).isEqualTo(275)
    }

    @Test
    fun `a hold stops at the bound, without further ticks`() {
        val state = show(pending(30))

        hold("Ziehzeit verkürzen, Schritt 1", 3_000)

        assertThat(shownSeconds(state)).isEqualTo(5)
        assertThat(ticks).hasSize(5)
        compose.onNodeWithContentDescription("Ziehzeit verkürzen, Schritt 1").assertIsNotEnabled()
    }

    @Test
    fun `TalkBack reaches every step as a single tap, and the dialog through the duration`() {
        val state = show(pending(300))

        compose.onNodeWithContentDescription("Ziehzeit verlängern, Schritt 1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(shownSeconds(state)).isEqualTo(305)
        duration(1, "5 Minuten 5 Sekunden").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Dauer wählen").assertIsDisplayed()
    }
}
