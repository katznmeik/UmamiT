package com.umamit.app

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.RoomActiveBrewStore
import com.umamit.core.data.RoomDatabaseTransactions
import com.umamit.core.data.RoomSessionRepository
import com.umamit.core.data.RoomTeaRepository
import com.umamit.core.data.SeedData
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeLowStockRepository
import com.umamit.core.testing.FakeSavedSheet
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerAlarms
import com.umamit.core.testing.FakeTimerAlertPlayer
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.testing.FakeTimerNotifier
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.testing.FakeTimerService
import com.umamit.core.testing.FakeTimerWakeUp
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerPorts
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.feature.brew.timer.ControllerBrewTimer
import com.umamit.feature.home.HomeEvent
import com.umamit.feature.home.HomeViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Start on the real controller (#47 hand-off, #48; BRW-11, TMR-01): *Nochmal brühen* on Start
 * starts the tea's last session again through the app's `BrewStarter` binding (the controller
 * behind `ControllerBrewTimer`), and the brew waits in READY. Real engine, controller, Room
 * repositories and Start's view model; fakes only at the controller's ports.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrewAgainOnControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val settings = FakeSettingsRepository()
    private val db = Room.inMemoryDatabaseBuilder(context, UmamiDatabase::class.java).allowMainThreadQueries().build()
    private val teas = RoomTeaRepository(db.teaDao(), clock)
    private val sessions = RoomSessionRepository(db, settings, clock)
    private val wakeUp = FakeTimerWakeUp(clock)
    private val controller = BrewSessionController(
        clocks = TimerClocks(clock, clock),
        sessions = sessions,
        activeBrew = RoomActiveBrewStore(db, clock),
        transactions = RoomDatabaseTransactions(db),
        settings = settings,
        ports = TimerPorts(
            alarms = FakeTimerAlarms(),
            service = FakeTimerService(),
            notifier = FakeTimerNotifier(),
            alert = FakeTimerAlertPlayer(),
            diagnostics = FakeTimerDiagnostics(),
            savedSheet = FakeSavedSheet(),
            wakeUp = wakeUp,
        ),
    )

    /** Start's view model lives in this store, so the test ends its scope before the database closes. */
    private val viewModels = ViewModelStore()
    private var viewModelJob: Job? = null

    /**
     * Start's view model is cleared first, and its work ends before the database closes (#50): its
     * scope reads the database on every change (the recent sessions in a transaction), so a scope
     * left running reads a closed database, and the failure surfaces in a later test's `runTest`.
     */
    @After
    fun tearDown() {
        viewModels.clear()
        viewModelJob?.let { job ->
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (!job.isCompleted && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(POLL_MS)
            }
            check(job.isCompleted) { "Start's view model still runs" }
        }
        db.close()
    }

    @Test
    fun `Nochmal bruehen on Start starts the last session again in READY on the real controller`() = runBlocking<Unit> {
        val hisui = SeedData.teas.first { it.id == SeedData.HISUI_TEA_ID }
        teas.saveTea(hisui)
        brewOnce(hisui.id, hisui.name)
        val start = startViewModel()
        assertThat(settled(start).brewAgain.map { it.id }).containsExactly(hisui.id)

        start.brewAgain(hisui.id)
        withTimeout(TIMEOUT_MS) {
            while (controller.state.value !is TimerUiState.Active) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(POLL_MS)
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val event = withTimeout(TIMEOUT_MS) { start.events.first() }

        assertThat(event).isEqualTo(HomeEvent.OpenTimer)
        val active = controller.state.value as TimerUiState.Active
        assertThat(active.phase).isEqualTo(TimerPhase.READY)
        assertThat(active.teaName).isEqualTo(hisui.name)
        assertThat(active.steps.map { it.plannedSeconds }).containsExactly(40, 20).inOrder()
        assertThat(active.sessionId).isNotNull()
    }

    private fun startViewModel(): HomeViewModel {
        val factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    teas,
                    sessions,
                    ControllerBrewTimer(controller),
                    clock,
                    PhotoFiles(File(context.cacheDir, "photos")),
                    FakeTimerReliability(),
                    FakeLowStockRepository(),
                )
            }
        }
        return ViewModelProvider.create(viewModels, factory)[HomeViewModel::class].also {
            viewModelJob = it.viewModelScope.coroutineContext.job
        }
    }

    /** A whole session of two steps through the controller, so Start has a last session to brew again. */
    private suspend fun brewOnce(teaId: String, teaName: String) {
        controller.startSession(
            BrewStart(
                snapshot = SessionSnapshot(
                    teaId = teaId,
                    teaName = teaName,
                    teaSecondaryName = null,
                    teaType = null,
                    styleKey = null,
                    category = null,
                    profileId = null,
                    profileName = null,
                    method = BrewMethod.KYUSU,
                    vesselId = null,
                    vesselName = null,
                    leafGrams = 4.0,
                    waterMl = 200,
                    baseTempC = 65,
                ),
                steps = listOf(PlannedStep(STEP_KIND, 40, 65), PlannedStep(STEP_KIND, 20, 65)),
                recipePreAlertSeconds = 0,
            ),
        )
        repeat(2) { step ->
            controller.send(TimerEvent.Start)
            wakeUp.advanceBy(if (step == 0) 40_000 else 20_000)
            controller.send(TimerEvent.Poured)
        }
        controller.send(TimerEvent.FinishSession)
        assertThat(controller.state.value).isEqualTo(TimerUiState.Idle)
    }

    /** Start's state once the repositories have answered. */
    private suspend fun settled(start: HomeViewModel) = withTimeout(TIMEOUT_MS) {
        while (start.state.value.loading || start.state.value.brewAgain.isEmpty()) {
            shadowOf(Looper.getMainLooper()).idle()
            delay(POLL_MS)
        }
        start.state.value
    }

    private companion object {
        val STEP_KIND = StepKind.INFUSION
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
