package com.umamit.feature.brew.timer

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.flow.MutableStateFlow
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
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerRouteTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private class FakeBrewTimer(initial: TimerUiState, recovered: Boolean) : BrewTimer {
        override val state = MutableStateFlow(initial)
        override val recovered = MutableStateFlow(recovered)

        override suspend fun send(slot: TimerSlot, event: TimerEvent): TransitionOutcome = TransitionOutcome.Accepted
    }

    private val clock = object : MonotonicClock {
        override fun elapsedMs() = TimerStates.NOW

        override fun bootCount() = 1
    }

    private var closed = 0

    private fun show(timer: FakeBrewTimer) {
        val collection = FakeCollection()
        val viewModel = TimerViewModel(
            timer,
            clock,
            FakeSettingsRepository(),
            FakeTimerReliability(),
            FakeSessions(collection).sessions,
            collection.teas,
            PhotoFiles(folder.newFolder()),
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                BrewTimerRoute(
                    onClose = { closed++ },
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
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
        timer.state.value = TimerStates.ready()
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

        timer.state.value = TimerUiState.Idle
        compose.waitForIdle()

        assertThat(closed).isEqualTo(1)
    }
}
