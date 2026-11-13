package com.umamit.feature.brew.timer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.model.StepTasting
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerUiStep
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.ui.INFUSION_NOTE_TAG
import com.umamit.core.ui.INFUSION_TASTE_RESET_TAG
import com.umamit.core.ui.tasteSliderTag
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The timer's „Geschmack · N. Aufguss" button and sheet (SCR-05 v0.6.1, D-114; JRN-03, JRN-05): no button before the
 * first infusion ends but its space from the session's first READY (PO, T4 MIN-1), the infusion ended last named,
 * „✓" with data, hidden on the alert surface with every bound kept (rule 8, D-096), none on the quick timer; the
 * sheet removed in an alert's first frame with its pending note handed over, kept by the pre-alert, its writes
 * addressed to the infusion's step position only, a drag the alert cut off dropped, and the stored values shown
 * again after a refused write.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerTasteTest {

    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf<TimerUiState.Active>(TimerStates.ready(stepIndex = 1))
    private var stored by mutableStateOf<Map<Int, StepTasting>?>(emptyMap())
    private var refusals by mutableIntStateOf(0)

    private val tastes = mutableListOf<Triple<Int, TasteAxis, Int>>()
    private val resets = mutableListOf<Int>()
    private val notes = mutableListOf<Pair<Int, String>>()

    private fun show(initial: TimerUiState.Active) {
        state = initial
        val start = compose.mainClock.currentTime
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TimerScreen(
                    state = state,
                    nowElapsedMs = { NOW + compose.mainClock.currentTime - start },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    stock = TimerStock(14.0, StockLevel.LOW),
                    tasting = TimerTasting(
                        byPosition = stored,
                        onTaste = { position, axis, value -> tastes += Triple(position, axis, value) },
                        onReset = { resets += it },
                        onNote = { position, note -> notes += position to note },
                        refusals = refusals,
                    ),
                )
            }
        }
    }

    private fun button() = compose.onNodeWithTag(TIMER_TASTE_BUTTON_TAG)

    /** The button's slot, there also while the button is hidden. */
    private fun slot() = compose.onNodeWithTag(TIMER_TASTE_SLOT_TAG, useUnmergedTree = true)

    /**
     * One frame, the one that recomposes after the state change: the change is applied first, so this frame is the
     * first one that can show it (the clock does not advance by itself).
     */
    private fun advanceOneFrame() {
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun sheet() = compose.onNodeWithTag(TIMER_TASTE_SHEET_TAG)

    /** The sheet in the composition at all, in any window and unmerged. */
    private fun sheetComposed() = compose.onNodeWithTag(TIMER_TASTE_SHEET_TAG, useUnmergedTree = true)

    private fun top(text: String): Dp = compose.onNodeWithText(text).getUnclippedBoundsInRoot().top

    private fun ringTop(): Dp = compose.onNodeWithTag(TIMER_RING_TAG).getUnclippedBoundsInRoot().top

    private fun openSheet() {
        button().performClick()
        compose.waitForIdle()
        sheet().assertExists()
    }

    @Test
    fun `no button before the first infusion ends, in any phase, but its space from the first READY`() {
        show(TimerStates.ready(stepIndex = 0))
        slot().assertExists()
        val space = slot().getUnclippedBoundsInRoot()
        assertThat(space.bottom - space.top).isGreaterThan(Dp(0f))
        listOf(
            TimerStates.running(),
            TimerStates.paused(),
            TimerStates.alerting(stepIndex = 0),
        ).forEach {
            state = it
            compose.waitForIdle()
            button().assertDoesNotExist()
            compose.onNodeWithText("Geschmack", substring = true).assertDoesNotExist()
            compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(TIMER_TASTE_SLOT_TAG))).assertDoesNotExist()
            assertThat(slot().getUnclippedBoundsInRoot()).isEqualTo(space)
        }
    }

    @Test
    fun `a poured rinse is no infusion, and a skipped infusion is never offered`() {
        val rinseDone = TimerStates.ready(stepIndex = 1).let {
            it.copy(steps = listOf(TimerUiStep(StepKind.RINSE, 10, 80, StepStatus.DONE)) + it.steps.drop(1))
        }
        show(rinseDone)
        button().assertDoesNotExist()
        compose.onNodeWithText("Geschmack", substring = true).assertDoesNotExist()

        state = TimerStates.ready(stepIndex = 2).let {
            it.copy(
                steps = it.steps.mapIndexed { i, step ->
                    if (i ==
                        1
                    ) {
                        step.copy(status = StepStatus.SKIPPED)
                    } else {
                        step
                    }
                },
            )
        }
        compose.waitForIdle()
        compose.onNodeWithText("Geschmack · 1. Aufguss").assertIsDisplayed()
    }

    @Test
    fun `it names the infusion ended last, in READY, RUNNING and PAUSED`() {
        show(TimerStates.ready(stepIndex = 1))
        compose.onNodeWithText("Geschmack · 1. Aufguss").assertIsDisplayed()
        compose.onNodeWithContentDescription("Geschmack zum 1. Aufguss eintragen").assertExists()

        state = TimerStates.running(stepIndex = 1)
        compose.waitForIdle()
        compose.onNodeWithText("Geschmack · 1. Aufguss").assertIsDisplayed()

        state = TimerStates.running(stepIndex = 1).copy(phase = TimerPhase.PAUSED, pausedRemainingMs = 9_000)
        compose.waitForIdle()
        compose.onNodeWithText("Geschmack · 1. Aufguss").assertIsDisplayed()

        state = TimerStates.ready(stepIndex = 2)
        compose.waitForIdle()
        compose.onNodeWithText("Geschmack · 2. Aufguss").assertIsDisplayed()
    }

    @Test
    fun `with a value or a note the button carries a check`() {
        stored = mapOf(0 to StepTasting(note = "Süß"))
        show(TimerStates.ready(stepIndex = 1))
        compose.onNodeWithText("Geschmack · 1. Aufguss ✓").assertIsDisplayed()
        compose.onNodeWithContentDescription("Geschmack zum 1. Aufguss eintragen, eingetragen").assertExists()

        // The 2nd infusion has none yet: no check, though the 1st has one.
        state = TimerStates.ready(stepIndex = 2)
        compose.waitForIdle()
        compose.onNodeWithText("Geschmack · 2. Aufguss").assertIsDisplayed()

        stored = mapOf(0 to StepTasting(note = "Süß"), 1 to StepTasting(TasteProfile(umami = 4)))
        compose.waitForIdle()
        compose.onNodeWithText("Geschmack · 2. Aufguss ✓").assertIsDisplayed()
    }

    @Test
    fun `the check coming moves nothing`() {
        show(TimerStates.ready(stepIndex = 1))
        val before = listOf(ringTop(), top("Start"))
        stored = mapOf(0 to StepTasting(TasteProfile(umami = 4)))
        compose.waitForIdle()
        compose.onNodeWithText("Geschmack · 1. Aufguss ✓").assertIsDisplayed()
        assertThat(listOf(ringTop(), top("Start"))).isEqualTo(before)
    }

    /** Nit 3: the slot is as tall as the longest label, whatever infusion it names. */
    @Test
    @Config(qualifiers = "w384dp-h832dp-xhdpi", fontScale = 2.0f)
    fun `at font scale 2_0 a longer infusion name keeps the slot's height`() {
        val whisk = TimerStates.ready(stepIndex = 1).let {
            it.copy(steps = listOf(it.steps[0].copy(kind = StepKind.WHISK)) + it.steps.drop(1))
        }
        show(TimerStates.ready(stepIndex = 1))
        val slot = slot().getUnclippedBoundsInRoot()
        val pill = top("Start")
        state = whisk
        compose.waitForIdle()
        compose.onNodeWithText("Geschmack · Aufschlagen", substring = true).assertExists()
        assertThat(slot().getUnclippedBoundsInRoot()).isEqualTo(slot)
        assertThat(top("Start")).isEqualTo(pill)
    }

    @Test
    fun `the quick timer has none, whatever the host passes`() {
        show(TimerStates.quick(TimerPhase.RUNNING))
        slot().assertDoesNotExist()
        state = TimerStates.quick(TimerPhase.ALERTING)
        compose.waitForIdle()
        slot().assertDoesNotExist()
    }

    @Test
    fun `hidden on the alert surface, its space kept, and nothing moves (rule 8, D-096)`() = staysPut()

    @Test
    @Config(fontScale = 2.0f)
    fun `at font scale 2_0 nothing moves at the alert either`() = staysPut()

    @Test
    @Config(qualifiers = "w384dp-h832dp-xhdpi", fontScale = 2.0f)
    fun `on a 384 x 832 dp screen at font scale 2_0 nothing moves at the alert either`() = staysPut()

    /**
     * From the session's first READY, before the first *Abgegossen* (PO, T4 MIN-1): READY → RUNNING → ALERTING of the
     * 1st step → READY → RUNNING → ALERTING of the 2nd → the last step's ALERTING → FINISHED, with the button's slot.
     */
    private fun staysPut() {
        show(TimerStates.ready(stepIndex = 0))
        val slot = slot().getUnclippedBoundsInRoot()
        val ready = listOf(ringTop(), top("Start"))

        state = TimerStates.running()
        compose.waitForIdle()
        assertThat(slot().getUnclippedBoundsInRoot()).isEqualTo(slot)
        val firstRunning = listOf(ringTop(), top("Pause"))

        state = TimerStates.alerting(stepIndex = 0)
        compose.waitForIdle()
        assertThat(slot().getUnclippedBoundsInRoot()).isEqualTo(slot)
        val firstAlert = listOf(ringTop(), top("Abgegossen"))

        // The first *Abgegossen*: the button comes in its kept space.
        state = TimerStates.ready(stepIndex = 1)
        compose.waitForIdle()
        compose.onNodeWithText("Geschmack · 1. Aufguss").assertIsDisplayed()
        assertThat(slot().getUnclippedBoundsInRoot()).isEqualTo(slot)
        val secondReady = listOf(ringTop(), top("Start"))

        state = TimerStates.running(stepIndex = 1)
        compose.waitForIdle()
        assertThat(slot().getUnclippedBoundsInRoot()).isEqualTo(slot)
        val running = listOf(ringTop(), top("Pause"))

        state = TimerStates.alerting(stepIndex = 1)
        compose.waitForIdle()
        // Hidden: not shown, not read out, not tappable, and its slot the same.
        compose.onNodeWithText("Geschmack · 1. Aufguss").assertDoesNotExist()
        button().assertDoesNotExist()
        compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(TIMER_TASTE_SLOT_TAG))).assertDoesNotExist()
        assertThat(slot().getUnclippedBoundsInRoot()).isEqualTo(slot)
        val alert = listOf(ringTop(), top("Abgegossen"))

        state = TimerStates.alerting(stepIndex = 2)
        compose.waitForIdle()
        val lastAlert = listOf(ringTop(), top("Abgegossen"))

        state = TimerStates.finished()
        compose.waitForIdle()
        compose.onNodeWithText("Geschmack", substring = true).assertDoesNotExist()
        val finished = listOf(ringTop(), top("Sitzung beenden"))

        assertThat(listOf(firstRunning, firstAlert, secondReady, running, alert, lastAlert, finished))
            .containsExactly(ready, ready, ready, ready, ready, ready, ready)
    }

    /**
     * The band rule (D-096) with the button: on a short screen at the largest font, with a two-line tea name, READY's
     * *Start*, RUNNING's *Pause* and the alert's *Abgegossen* lie on the screen with the bottom margin below them.
     */
    @Test
    @Config(qualifiers = "w384dp-h832dp-xhdpi", fontScale = 2.0f)
    fun `on a 384 x 832 dp screen at font scale 2_0 with a two-line tea name the primary control is on the screen`() {
        show(TimerStates.ready(stepIndex = 1).copy(teaName = TWO_LINE_NAME))
        compose.onNodeWithText("Geschmack · 1. Aufguss").assertExists()
        assertWithMargin(compose.onNodeWithText("Start").getUnclippedBoundsInRoot())

        state = TimerStates.running(stepIndex = 1).copy(teaName = TWO_LINE_NAME)
        compose.waitForIdle()
        assertWithMargin(compose.onNodeWithText("Pause").getUnclippedBoundsInRoot())

        state = TimerStates.alerting(stepIndex = 1).copy(teaName = TWO_LINE_NAME)
        compose.waitForIdle()
        assertWithMargin(compose.onNodeWithText("Abgegossen").getUnclippedBoundsInRoot())
    }

    private fun assertWithMargin(pill: DpRect) {
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        assertThat(pill.top).isAtLeast(screen.top)
        assertThat(pill.bottom + UmamiSpacing.Large).isAtMost(screen.bottom)
    }

    @Test
    fun `the sheet names the infusion and writes to its step position only`() {
        stored = mapOf(0 to StepTasting(TasteProfile(umami = 3), note = "Nori"))
        show(TimerStates.running(stepIndex = 1))
        openSheet()
        compose.onNode(hasText("Geschmack · 1. Aufguss") and hasAnyAncestor(hasTestTag(TIMER_TASTE_SHEET_TAG)))
            .assertExists()
        compose.onNodeWithTag(TIMER_TASTE_DONE_TAG).assertExists()
        compose.onNodeWithText("Nori").assertExists()

        compose.onNodeWithTag(tasteSliderTag(TasteAxis.BODY)).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        compose.waitForIdle()
        compose.onNodeWithTag(INFUSION_TASTE_RESET_TAG).performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(tastes).containsExactly(Triple(0, TasteAxis.BODY, 4))
        assertThat(resets).containsExactly(0)
    }

    @Test
    fun `Fertig closes the sheet and hands the note over`() {
        show(TimerStates.ready(stepIndex = 1))
        openSheet()
        compose.onNodeWithTag(INFUSION_NOTE_TAG).performTextInput("Mild")
        compose.onNodeWithTag(TIMER_TASTE_DONE_TAG).performClick()
        compose.waitForIdle()

        sheet().assertDoesNotExist()
        assertThat(notes).containsExactly(0 to "Mild")
    }

    @Test
    fun `the pre-alert leaves the sheet open`() {
        show(TimerStates.running(stepIndex = 1, remainingMs = 8_000))
        openSheet()

        // The 0:20 step's pre-alert is 5 s before its end.
        state = TimerStates.running(stepIndex = 1, remainingMs = 4_000, preAlertPlayed = true)
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()

        sheet().assertExists()
    }

    @Test
    fun `an alert removes the sheet in its first frame, the pending note handed over, and it stays closed`() {
        show(TimerStates.running(stepIndex = 1))
        openSheet()
        compose.onNodeWithTag(INFUSION_NOTE_TAG).performTextInput("Kräftig")

        compose.mainClock.autoAdvance = false
        state = TimerStates.alerting(stepIndex = 1)
        advanceOneFrame()

        // Not in the composition after the alert's first frame, in no window (Nit 1).
        sheetComposed().assertDoesNotExist()
        compose.onNodeWithText("Abgegossen").assertExists()
        assertThat(notes).containsExactly(0 to "Kräftig")

        compose.mainClock.autoAdvance = true
        // After *Abgegossen* the next step waits: the sheet does not come back by itself.
        state = TimerStates.ready(stepIndex = 2)
        compose.waitForIdle()
        sheet().assertDoesNotExist()
        compose.onNodeWithText("Geschmack · 2. Aufguss").assertIsDisplayed()
    }

    @Test
    fun `an alert of the other timer, which the route then shows, removes the sheet too`() {
        show(TimerStates.running(stepIndex = 1))
        openSheet()

        compose.mainClock.autoAdvance = false
        state = TimerStates.quick(TimerPhase.ALERTING)
        advanceOneFrame()
        sheetComposed().assertDoesNotExist()

        compose.mainClock.autoAdvance = true
        state = TimerStates.running(stepIndex = 1)
        compose.waitForIdle()
        sheet().assertDoesNotExist()
    }

    /** Nit 6: a release saves; a drag still in progress when the alert removes the sheet is dropped. */
    @Test
    fun `a drag the alert cuts off saves nothing`() {
        show(TimerStates.running(stepIndex = 1))
        openSheet()
        compose.onNodeWithTag(tasteSliderTag(TasteAxis.UMAMI)).performScrollTo().performTouchInput {
            down(centerLeft)
            moveTo(center)
        }
        compose.waitForIdle()

        state = TimerStates.alerting(stepIndex = 1)
        compose.waitForIdle()

        sheet().assertDoesNotExist()
        assertThat(tastes).isEmpty()
    }

    /** MIN-5: after a refused write the sheet shows the stored values again, not the refused ones; no message. */
    @Test
    fun `after a refused write the sheet shows the stored taste and note again`() {
        stored = mapOf(0 to StepTasting(TasteProfile(umami = 2), note = "Nori"))
        show(TimerStates.ready(stepIndex = 1))
        openSheet()
        val body = compose.onNodeWithTag(tasteSliderTag(TasteAxis.BODY))
        body.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        compose.onNodeWithTag(INFUSION_NOTE_TAG).performTextInput(" und Gras")
        compose.waitForIdle()
        body.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "4 von 5"))

        // The writes were refused: nothing changed in the store.
        refusals = 1
        compose.waitForIdle()

        sheet().assertExists()
        compose.onNodeWithTag(tasteSliderTag(TasteAxis.BODY))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "nicht gesetzt"))
        compose.onNodeWithTag(tasteSliderTag(TasteAxis.UMAMI))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 von 5"))
        compose.onNodeWithText("Nori").assertExists()
        compose.onNodeWithText("Nori und Gras").assertDoesNotExist()
    }

    /** MIN-4 (6): the open sheet survives a configuration change; an alert in the first restored state closes it. */
    @Test
    fun `the open sheet is restored, but not over an alert in the first restored state`() {
        val restoration = StateRestorationTester(compose)
        state = TimerStates.running(stepIndex = 1)
        restoration.setContent {
            UmamiTheme(reduceMotion = true) {
                TimerScreen(
                    state = state,
                    nowElapsedMs = { NOW },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    tasting = TimerTasting(stored, { _, _, _ -> }, {}, { _, _ -> }),
                )
            }
        }
        openSheet()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        sheet().assertExists()

        // Restored with the alert ringing: the sheet does not show, and it does not come back after it.
        state = TimerStates.alerting(stepIndex = 1)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        sheetComposed().assertDoesNotExist()
        compose.onNodeWithText("Abgegossen").assertExists()

        state = TimerStates.ready(stepIndex = 2)
        compose.waitForIdle()
        sheet().assertDoesNotExist()
    }

    private companion object {
        const val TWO_LINE_NAME = "Bio Japan Sencha Kagoshima Asatsuyu"
    }
}
