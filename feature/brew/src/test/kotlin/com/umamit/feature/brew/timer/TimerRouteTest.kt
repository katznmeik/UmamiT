package com.umamit.feature.brew.timer

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The timer route after process death (GEN-07, 03 §2.4; #54 UI S5, D-068): restored from recents
 * or opened from a notification, it composes before the new process has recovered the timer. It
 * waits on that `Idle` and shows the recovered session; it closes only on a known `Idle`.
 *
 * With both timers (D-098): each route shows one timer, the one it was opened for or, opened for none, the one in
 * front when it opened; a ringing alert of either takes it (rule 8). The other timer is its row; a tap on the row
 * asks for the other timer's route, the row's button acts on the other timer only. The route closes when its timer
 * ends and no alert rings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerRouteTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val clock = object : MonotonicClock {
        override fun elapsedMs() = TimerStates.NOW

        override fun bootCount() = 1
    }

    private var closed = 0
    private val shownOther = mutableListOf<TimerSlot>()

    private fun show(timer: FakeBrewTimer, slot: TimerSlot? = null) {
        val collection = FakeCollection()
        val viewModel = TimerViewModel(
            timer,
            clock,
            FakeSettingsRepository(),
            FakeTimerReliability(),
            FakeSessions(collection).sessions,
            collection.teas,
            PhotoFiles(folder.newFolder()),
            collection.stock,
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                BrewTimerRoute(
                    onClose = { closed++ },
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    slot = slot,
                    onShowOther = { shownOther += it },
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aRestoredRouteWaitsForRecoveryAndKeepsTheRecoveredTimer() {
        val timer = FakeBrewTimer(TimerUiState.Idle, recovered = false)
        show(timer)
        assertThat(closed).isEqualTo(0)

        // Recovery publishes the session, then says it has recovered.
        timer.show(TimerStates.ready())
        timer.recovered.value = true
        compose.waitForIdle()

        assertThat(closed).isEqualTo(0)
        compose.onNodeWithText("Start").assertExists()
    }

    @Test
    fun aRestoredRouteClosesOnceRecoveryFoundNoSession() {
        val timer = FakeBrewTimer(TimerUiState.Idle, recovered = false)
        show(timer)
        assertThat(closed).isEqualTo(0)

        timer.recovered.value = true
        compose.waitForIdle()

        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun theRouteClosesWhenTheSessionEnds() {
        val timer = FakeBrewTimer(TimerStates.ready(), recovered = true)
        show(timer)

        timer.show(TimerUiState.Idle)
        compose.waitForIdle()

        assertThat(closed).isEqualTo(1)
    }

    // --- Two timers (D-098) ----------------------------------------------------------------

    private val brewRunning = TimerStates.running()
    private val quickRunning = TimerStates.quick(TimerPhase.RUNNING)

    private fun rowText(text: String) = compose.onNode(hasText(text) and hasTestTag(TIMER_OTHER_ROW_TAG))

    @Test
    fun `opened for no timer it shows the one in front, with the other as its row, and keeps it`() {
        val timer = FakeBrewTimer()
        timer.show(brew = brewRunning, quick = quickRunning, front = brewRunning)
        show(timer)
        compose.onNodeWithTag(TIMER_SEAL_TAG, useUnmergedTree = true).assertExists()
        rowText("Schnelltimer").assertExists()

        // The quick timer's *Pause* from the row puts it in front of the controller: this route keeps the brew.
        timer.show(brew = brewRunning, quick = TimerStates.quick(TimerPhase.PAUSED), front = quickRunning)
        compose.waitForIdle()

        rowText("Schnelltimer").assertExists()
        compose.onNodeWithTag(TIMER_SEAL_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `opened for the quick timer it shows the quick timer with the brew's row`() {
        val timer = FakeBrewTimer()
        timer.show(brew = brewRunning, quick = quickRunning, front = brewRunning)
        show(timer, slot = TimerSlot.QUICK)

        compose.onNodeWithTag(TIMER_SEAL_TAG, useUnmergedTree = true).assertDoesNotExist()
        rowText("Bio Uji Gyokuro Gokō").assertExists()
    }

    @Test
    fun `a tap on the row asks for the other timer, its button acts on the other timer only`() {
        val timer = FakeBrewTimer()
        timer.show(brew = brewRunning, quick = quickRunning, front = brewRunning)
        show(timer, slot = TimerSlot.BREW)

        compose.onNodeWithTag(TIMER_OTHER_ROW_TAG).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Schnelltimer pausieren").performClick()
        compose.waitForIdle()

        assertThat(shownOther).containsExactly(TimerSlot.QUICK)
        assertThat(timer.sent).containsExactly(TimerSlot.QUICK to TimerEvent.Pause)
    }

    @Test
    fun `the brew's alert takes the quick timer's route, which shows the quick timer again after Abgegossen`() {
        val timer = FakeBrewTimer()
        timer.show(brew = brewRunning, quick = quickRunning, front = quickRunning)
        show(timer, slot = TimerSlot.QUICK)

        val alert = TimerStates.alerting(stepIndex = 0)
        timer.show(brew = alert, quick = quickRunning, front = alert)
        compose.waitForIdle()
        compose.onNodeWithText("Abgegossen").assertExists()
        compose.onNodeWithTag(TIMER_OTHER_ROW_TAG).assertDoesNotExist()

        timer.show(brew = TimerStates.ready(stepIndex = 1), quick = quickRunning, front = quickRunning)
        compose.waitForIdle()
        compose.onNodeWithText("Abgegossen").assertDoesNotExist()
        rowText("Bio Uji Gyokuro Gokō").assertExists()
        assertThat(closed).isEqualTo(0)
    }

    @Test
    fun `the quick timer's alert takes the brew's route, and Fertig returns to the brew`() {
        val timer = FakeBrewTimer()
        timer.show(brew = brewRunning, quick = quickRunning, front = brewRunning)
        show(timer, slot = TimerSlot.BREW)

        val quickAlert = TimerStates.quick(TimerPhase.ALERTING)
        timer.show(brew = brewRunning, quick = quickAlert, front = quickAlert)
        compose.waitForIdle()
        compose.onNodeWithText("Fertig").assertExists()

        timer.show(brew = brewRunning, quick = TimerUiState.Idle, front = brewRunning)
        compose.waitForIdle()
        compose.onNodeWithTag(TIMER_SEAL_TAG, useUnmergedTree = true).assertExists()
        assertThat(closed).isEqualTo(0)
    }

    @Test
    fun `a held quick alert never takes the screen from the brew's alert`() {
        val timer = FakeBrewTimer()
        val alert = TimerStates.alerting(stepIndex = 0)
        val held = TimerStates.quick(TimerPhase.ALERTING).copy(alertHeld = true)
        timer.show(brew = alert, quick = held, front = alert)
        show(timer, slot = TimerSlot.QUICK)

        compose.onNodeWithText("Abgegossen").assertExists()
        compose.onNodeWithText("Fertig").assertDoesNotExist()
    }

    @Test
    fun `the route closes when its timer ends while the other runs on`() {
        val timer = FakeBrewTimer()
        timer.show(brew = brewRunning, quick = quickRunning, front = quickRunning)
        show(timer, slot = TimerSlot.QUICK)

        timer.show(brew = brewRunning, quick = TimerUiState.Idle, front = brewRunning)
        compose.waitForIdle()

        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `the route waits for the other timer's ringing alert before it closes (rule 8)`() {
        val timer = FakeBrewTimer()
        val alert = TimerStates.alerting(stepIndex = 0)
        val held = TimerStates.quick(TimerPhase.ALERTING).copy(alertHeld = true)
        timer.show(brew = alert, quick = held, front = alert)
        show(timer, slot = TimerSlot.BREW)

        // *Sitzung beenden* during the brew's alert: the held quick alert rings in the same turn.
        val ringing = TimerStates.quick(TimerPhase.ALERTING)
        timer.show(brew = TimerUiState.Idle, quick = ringing, front = ringing)
        compose.waitForIdle()
        assertThat(closed).isEqualTo(0)
        compose.onNodeWithText("Fertig").assertExists()

        timer.show(TimerUiState.Idle)
        compose.waitForIdle()
        assertThat(closed).isEqualTo(1)
    }
}
