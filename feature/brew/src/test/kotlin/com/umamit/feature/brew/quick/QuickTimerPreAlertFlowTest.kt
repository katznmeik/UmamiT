package com.umamit.feature.brew.quick

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.RoomActiveBrewStore
import com.umamit.core.data.RoomDatabaseTransactions
import com.umamit.core.data.RoomSessionRepository
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSavedSheet
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerAlarms
import com.umamit.core.testing.FakeTimerAlertPlayer
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.testing.FakeTimerNotifier
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.testing.FakeTimerService
import com.umamit.core.testing.FakeTimerWakeUp
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerPorts
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.feature.brew.timer.BrewTimerRoute
import com.umamit.feature.brew.timer.ControllerBrewTimer
import com.umamit.feature.brew.timer.TIMER_RING_TAG
import com.umamit.feature.brew.timer.TimerViewModel
import com.umamit.feature.brew.timer.preAlertPosition
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SCR-07's *Start* through the app's seams to the timer screen (TMR-22, SCR-05, D-097 point 5, #109): the real
 * [QuickTimerViewModel] starts the quick timer on the real controller (Room, fakes at its ports) behind
 * [ControllerBrewTimer], and the timer route shows it. With the global *Vorwarnung* 10 s, a 2:00 quick timer has
 * its mark at 0:10 on the ring, its tick at 0:10 left and the caption „Gleich fertig …". On the code of v0.2.1
 * this passes: it did not reproduce the PO's silent pre-alert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class QuickTimerPreAlertFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val settings = FakeSettingsRepository(UserSettings(preAlertSeconds = 10))
    private val db = Room.inMemoryDatabaseBuilder(context, UmamiDatabase::class.java).allowMainThreadQueries().build()
    private val wakeUp = FakeTimerWakeUp(clock)
    private val alert = FakeTimerAlertPlayer()
    private val alarms = FakeTimerAlarms()
    private val controller = BrewSessionController(
        clocks = TimerClocks(clock, clock),
        sessions = RoomSessionRepository(db, settings, clock),
        activeBrew = RoomActiveBrewStore(db, clock),
        transactions = RoomDatabaseTransactions(db),
        settings = settings,
        ports = TimerPorts(
            alarms = alarms,
            service = FakeTimerService(),
            notifier = FakeTimerNotifier(),
            alert = alert,
            diagnostics = FakeTimerDiagnostics(),
            savedSheet = FakeSavedSheet(),
            wakeUp = wakeUp,
        ),
    )
    private val timer = ControllerBrewTimer(controller)

    @After
    fun tearDown() = db.close()

    @Test
    fun `a 2_00 quick timer started on SCR-07 shows its mark and warns at 0_10 on the timer screen`() {
        val quick = QuickTimerViewModel(SavedStateHandle(), timer)
        quick.select(120)
        quick.start()
        awaitActive()
        showTimer()

        val running = controller.state.value as TimerUiState.Active
        assertThat(running.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(running.preAlertLeadSeconds).isEqualTo(10)
        assertThat(running.preAlertPosition()).isWithin(EPSILON).of(1f - 10f / 120f)
        ring().assert(hasContentDescription("Zieht …", substring = true))

        runBlocking { wakeUp.advanceBy(110_000) }
        compose.waitForIdle()

        assertThat(alert.ticks).isEqualTo(1)
        ring().assert(hasContentDescription("Gleich fertig …", substring = true))
    }

    private fun awaitActive() {
        val until = System.currentTimeMillis() + TIMEOUT_MS
        while (controller.state.value !is TimerUiState.Active && System.currentTimeMillis() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
    }

    private fun showTimer() {
        val collection = FakeCollection()
        val viewModel = TimerViewModel(
            timer,
            clock,
            settings,
            FakeTimerReliability(),
            FakeSessions(collection).sessions,
            collection.teas,
            PhotoFiles(folder.newFolder()),
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                BrewTimerRoute(onClose = {}, onAdjustBrew = {}, onOpenTimerSettings = {}, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    private fun ring() = compose.onNodeWithTag(TIMER_RING_TAG)

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
        const val EPSILON = 1e-6f
    }
}
