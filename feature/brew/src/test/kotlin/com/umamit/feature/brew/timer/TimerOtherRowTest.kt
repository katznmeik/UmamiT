package com.umamit.feature.brew.timer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The row for the other timer (SCR-05, SCR-07, D-098 Q3): on the brew timer „Schnelltimer 2:14" with its own
 * *Pause*, on the quick timer the brew „Tanabata · Aufguss 2 · 0:48"; a tap opens the other timer, its button acts
 * on that timer only. On the alert surface it is hidden and its space kept (rule 8), and the ring and the pill
 * stand where they stand without a row, in every phase and on a short screen at the largest font.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerOtherRowTest {

    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf<TimerUiState.Active>(TimerStates.running())
    private var other by mutableStateOf<TimerUiState.Active?>(null)
    private val events = mutableListOf<TimerEvent>()
    private val otherEvents = mutableListOf<TimerEvent>()
    private var opened = 0

    private fun show(shown: TimerUiState.Active, otherTimer: TimerUiState.Active?) {
        state = shown
        other = otherTimer
        val start = compose.mainClock.currentTime
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TimerScreen(
                    state = state,
                    nowElapsedMs = { NOW + compose.mainClock.currentTime - start },
                    onEvent = { events += it },
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    teaPhoto = ColorPainter(Color.White),
                    teaStyleKey = "gyokuro",
                    stock = TimerStock(14.0, StockLevel.LOW),
                    other = other,
                    onOtherEvent = { otherEvents += it },
                    onOpenOther = { opened++ },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun row() = compose.onNodeWithTag(TIMER_OTHER_ROW_TAG, useUnmergedTree = true)

    /** The row is one element: its texts are read from it (the merged tree), never from its invisible twin. */
    private fun rowText(text: String) = compose.onNodeWithText(text)

    /** The screen's own control [text], not the row's button of the same name. */
    private fun pill(text: String) = compose.onNode(hasText(text) and !hasAnyAncestor(hasTestTag(TIMER_OTHER_ROW_TAG)))

    private fun top(text: String): Dp = pill(text).getUnclippedBoundsInRoot().top

    private fun ringTop(): Dp =
        compose.onNodeWithTag(TIMER_RING_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    @Test
    fun `without another timer there is no row`() {
        show(TimerStates.running(), otherTimer = null)

        row().assertDoesNotExist()
    }

    @Test
    fun `the brew shows the running quick timer, a tap opens it and its Pause pauses only the quick timer`() {
        show(TimerStates.running(), TimerStates.quick(TimerPhase.RUNNING, remainingMs = 134_000))

        rowText("Schnelltimer").assertIsDisplayed()
        rowText("2:14").assertIsDisplayed()
        row().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Schnelltimer pausieren").performClick()

        assertThat(opened).isEqualTo(1)
        assertThat(otherEvents).containsExactly(TimerEvent.Pause)
        assertThat(events).isEmpty()
    }

    @Test
    fun `the row's countdown ticks with the clock`() {
        show(TimerStates.running(), TimerStates.quick(TimerPhase.RUNNING, remainingMs = 134_000))

        compose.mainClock.advanceTimeBy(2_000)

        rowText("2:12").assertIsDisplayed()
    }

    @Test
    fun `a paused quick timer reads Pausiert and resumes from the row`() {
        show(TimerStates.running(), TimerStates.quick(TimerPhase.PAUSED, remainingMs = 134_000))

        rowText("· Pausiert 2:14").assertIsDisplayed()
        compose.onNodeWithContentDescription("Schnelltimer fortsetzen").performClick()

        assertThat(otherEvents).containsExactly(TimerEvent.Resume)
    }

    @Test
    fun `the quick timer shows the brew with its step, and pauses or resumes only the brew`() {
        show(TimerStates.quick(TimerPhase.RUNNING), TimerStates.running(remainingMs = 48_000))

        rowText("Bio Uji Gyokuro Gokō").assertIsDisplayed()
        rowText("· Aufguss 1 · 0:48").assertIsDisplayed()
        compose.onNodeWithContentDescription("Aufguss pausieren").performClick()
        row().performSemanticsAction(SemanticsActions.OnClick)

        other = TimerStates.paused(remainingMs = 31_400)
        compose.waitForIdle()
        rowText("· Aufguss 1 · Pausiert 0:32").assertIsDisplayed()
        compose.onNodeWithContentDescription("Aufguss fortsetzen").performClick()

        assertThat(otherEvents).containsExactly(TimerEvent.Pause, TimerEvent.Resume).inOrder()
        assertThat(opened).isEqualTo(1)
        assertThat(events).isEmpty()
    }

    /** At the largest font the name and the step do not both fit on the row's line: the step gives way first. */
    @Test
    @Config(fontScale = 2.0f)
    fun `at font scale 2_0 the brew's step gives way to its name`() {
        show(TimerStates.quick(TimerPhase.RUNNING), TimerStates.running(remainingMs = 48_000))

        rowText("· 0:48").assertIsDisplayed()
        rowText("· Aufguss 1 · 0:48").assertDoesNotExist()
    }

    @Test
    fun `a waiting or finished brew is named so, without a button`() {
        show(TimerStates.quick(TimerPhase.RUNNING), TimerStates.ready(stepIndex = 1))
        rowText("· Aufguss 2 · bereit").assertIsDisplayed()
        compose.onNodeWithContentDescription("Aufguss pausieren").assertDoesNotExist()

        other = TimerStates.finished()
        compose.waitForIdle()
        rowText("· fertig").assertIsDisplayed()
        compose.onNodeWithContentDescription("Aufguss fortsetzen").assertDoesNotExist()
    }

    @Test
    fun `on the alert surface the row is hidden, its space kept, and nothing moves (rule 8)`() {
        show(TimerStates.running(), TimerStates.quick(TimerPhase.RUNNING))
        val running = listOf(ringTop(), top("Pause"))

        // The quick timer's alert waits for the brew's (held): the brew's alert shows nothing of it.
        state = TimerStates.alerting(overtimeMs = 7_000, stepIndex = 0)
        other = TimerStates.quick(TimerPhase.ALERTING).copy(alertHeld = true)
        compose.waitForIdle()

        row().assertDoesNotExist()
        rowText("Schnelltimer").assertDoesNotExist()
        assertThat(listOf(ringTop(), top("Abgegossen"))).isEqualTo(running)
    }

    @Test
    fun `the ring and the pill stand where they stand without a row, from READY to FINISHED`() {
        show(TimerStates.ready(), otherTimer = null)
        val without = pillTops(otherTimer = null)
        val with = pillTops(otherTimer = TimerStates.quick(TimerPhase.RUNNING))

        assertThat(with).isEqualTo(without)
        assertThat(with.map { it[1] }.distinct()).hasSize(1)
    }

    /**
     * Where the band is down to the top bar already (D-096) it cannot give way by the row's height: the page below
     * the row moves down by it when the quick timer starts or ends (on Start, not here). While the row is there,
     * nothing moves from READY to FINISHED, and *Start* and *Pause* lie whole on the screen with the bottom margin.
     */
    @Test
    @Config(qualifiers = "w384dp-h832dp-xhdpi", fontScale = 2.0f)
    fun `on a 384 x 832 dp screen at font scale 2_0 nothing moves with the row and Start and Pause stay on it`() =
        shortScreen(teaName = "Bio Uji Gyokuro Gokō")

    @Test
    @Config(qualifiers = "w384dp-h832dp-xhdpi", fontScale = 2.0f)
    fun `on a 384 x 832 dp screen at font scale 2_0 with a two-line tea name Start and Pause stay on it`() =
        shortScreen(teaName = TWO_LINE_NAME)

    private fun shortScreen(teaName: String) {
        show(TimerStates.ready(), otherTimer = null)
        val with = pillTops(otherTimer = TimerStates.quick(TimerPhase.RUNNING), teaName = teaName, onScreen = true)

        assertThat(with.distinct()).hasSize(1)
    }

    @Test
    fun `where everything fits the seal stays on the band's lower edge above the row`() {
        show(TimerStates.running(), TimerStates.quick(TimerPhase.RUNNING))

        val band = compose.onNodeWithTag(TIMER_BAND_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val seal = compose.onNodeWithTag(TIMER_SEAL_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat((seal.top + seal.bottom) / 2).isEqualTo(band.bottom)
        assertThat(row().getUnclippedBoundsInRoot().top).isEqualTo(seal.bottom)
    }

    /** Rule 8 before decoration: where the band cannot give way any more, the seal makes room for the row. */
    @Test
    @Config(qualifiers = "w384dp-h832dp-xhdpi", fontScale = 2.0f)
    fun `on the tightest screen the seal yields to the row, and comes back without it`() {
        show(TimerStates.running().copy(teaName = TWO_LINE_NAME), TimerStates.quick(TimerPhase.RUNNING))
        compose.onNodeWithTag(TIMER_SEAL_TAG, useUnmergedTree = true).assertIsNotDisplayed()

        other = null
        compose.waitForIdle()
        compose.onNodeWithTag(TIMER_SEAL_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    /** The ring's and the pill's tops from READY to FINISHED, with [otherTimer]'s row or without one. */
    private fun pillTops(
        otherTimer: TimerUiState.Active?,
        teaName: String = "Bio Uji Gyokuro Gokō",
        onScreen: Boolean = false,
    ): List<List<Dp>> {
        val phases = listOf(
            TimerStates.ready() to "Start",
            TimerStates.running() to "Pause",
            TimerStates.paused() to "Weiter",
            TimerStates.alerting(overtimeMs = 7_000, stepIndex = 2) to "Abgegossen",
            TimerStates.finished() to "Sitzung beenden",
        ).map { (shown, pill) -> shown.copy(teaName = teaName) to pill }
        return phases.map { (shown, pill) ->
            state = shown
            other = otherTimer
            compose.waitForIdle()
            if (onScreen && (pill == "Start" || pill == "Pause")) assertOnScreen(pill)
            listOf(ringTop(), top(pill))
        }
    }

    private fun assertOnScreen(control: String) {
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val bounds = pill(control).getUnclippedBoundsInRoot()
        assertThat(bounds.top).isAtLeast(screen.top)
        assertThat(bounds.bottom + UmamiSpacing.Large).isAtMost(screen.bottom)
    }

    private companion object {
        /** A synthetic tea name (D-016) that takes two lines of the title. */
        const val TWO_LINE_NAME = "Bio Japan Sencha Kagoshima Asatsuyu"
    }
}
