package com.umamit.feature.brew.timer

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.RoomActiveBrewStore
import com.umamit.core.data.RoomDatabaseTransactions
import com.umamit.core.data.RoomJournalRepository
import com.umamit.core.data.RoomSessionRepository
import com.umamit.core.data.RoomSessionTasting
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.StepTasting
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
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
import com.umamit.core.timer.controller.send
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.TimerEvent
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
 * The timer's taste sheet on the real path (04 §10 v0.6.1, D-114, rule 8): the real controller (Room, fakes at its
 * ports), the real [TimerViewModel] with Room's `SessionTasting` and journal, and the timer route. A taste write in
 * READY and in RUNNING leaves the engine state, the due times (alarms, wake-up) and the next step's recorded values
 * as they were; the pre-alert leaves the sheet open; the alert starts at its deadline, closes the sheet and shows the
 * alert surface, and what was entered is stored. The 2nd infusion, poured, records the same times as in a brew
 * without any taste write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerTasteFlowTest {
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
    private val sessions = RoomSessionRepository(db, settings, clock)
    private val controller = BrewSessionController(
        clocks = TimerClocks(clock, clock),
        sessions = sessions,
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
    private val photos by lazy { PhotoFiles(folder.newFolder("photos")) }
    private val viewModel by lazy {
        val collection = FakeCollection()
        TimerViewModel(
            ControllerBrewTimer(controller),
            clock,
            settings,
            FakeTimerReliability(),
            sessions,
            collection.teas,
            photos,
            collection.stock,
            RoomJournalRepository(db, photos),
            RoomSessionTasting(db, clock),
        )
    }

    @After
    fun tearDown() = db.close()

    /** What the timer holds: the UI state, the stored timer row, the armed due times and the step rows from [from]. */
    private fun timerSnapshot(sessionId: String, from: Int) = runBlocking {
        listOf(
            controller.state.value,
            db.activeBrewDao().get(TimerSlot.BREW),
            alarms.armed(TimerSlot.BREW),
            db.sessionDao().steps(sessionId).drop(from).map {
                listOf(
                    it.position,
                    it.kind,
                    it.plannedSeconds,
                    it.actualSeconds,
                    it.pausedSeconds,
                    it.overtimeSeconds,
                    it.tempC,
                    it.startedAt,
                    it.endedAt,
                    it.skipped,
                    it.preAlertSeconds,
                )
            },
        )
    }

    private fun active() = controller.state.value as TimerUiState.Active

    /** A session of 1:00, 0:20 and 0:40 whose 1st infusion is poured: the 2nd waits in READY. */
    private fun pouredFirst(): String {
        runBlocking {
            controller.startSession(BrewStart(SNAPSHOT, STEPS, recipePreAlertSeconds = null))
            controller.send(TimerEvent.Start)
            wakeUp.advanceBy(60_000)
            controller.send(TimerEvent.Poured)
        }
        idle()
        return checkNotNull(active().sessionId)
    }

    private fun showRoute() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                BrewTimerRoute(onClose = {}, onAdjustBrew = {}, onOpenTimerSettings = {}, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    /** The recorded times of the step at [position]: its actual, paused and overtime seconds. */
    private fun recorded(sessionId: String, position: Int) = runBlocking {
        db.sessionDao().steps(sessionId).single { it.position == position }
            .let { listOf(it.actualSeconds, it.pausedSeconds, it.overtimeSeconds) }
    }

    /** The 2nd infusion (0:20) started, run to its deadline and poured at once. */
    private fun pourSecondAtItsDeadline() {
        runBlocking { controller.send(TimerEvent.Poured) }
        idle()
    }

    private fun storedTaste(sessionId: String, position: Int) =
        runBlocking { checkNotNull(sessions.getSession(sessionId)).stepTastings[position] }

    /** The writes run on Room's executor: waits until [position]'s stored taste is [taste], or fails after a while. */
    private fun awaitStored(sessionId: String, position: Int, taste: TasteProfile, note: String?) {
        val until = System.currentTimeMillis() + TIMEOUT_MS
        while (storedTaste(sessionId, position) != StepTasting(taste, note) && System.currentTimeMillis() < until) {
            idle()
            Thread.sleep(POLL_MS)
        }
        idle()
        assertThat(storedTaste(sessionId, position)).isEqualTo(StepTasting(taste, note))
    }

    @Test
    fun `a taste write in READY and in RUNNING changes nothing of the timer, the alert closes the sheet`() {
        val id = pouredFirst()
        showRoute()
        assertThat(active().phase).isEqualTo(TimerPhase.READY)
        compose.onNodeWithText("Geschmack · 1. Aufguss").assertIsDisplayed()

        // READY: the writes of the 1st infusion leave the waiting 2nd step and the timer as they were.
        val ready = timerSnapshot(id, from = 1)
        compose.onNodeWithTag(TIMER_TASTE_BUTTON_TAG).performClick()
        idle()
        compose.onNodeWithTag(TIMER_TASTE_SHEET_TAG).assertExists()
        viewModel.tasteWrites.setTaste(id, 0, TasteAxis.UMAMI, 5)
        viewModel.tasteWrites.setNote(id, 0, "Süß")
        awaitStored(id, 0, TasteProfile(umami = 5), "Süß")
        assertThat(timerSnapshot(id, from = 1)).isEqualTo(ready)
        compose.onNodeWithText("Geschmack · 1. Aufguss ✓").assertExists()
        compose.onNodeWithText("Fertig").performClick()
        idle()

        // RUNNING: the 2nd step steeps; writes leave its deadline, its pre-alert and the 3rd step as they were.
        runBlocking { controller.send(TimerEvent.Start) }
        idle()
        val deadline = checkNotNull(active().deadlineElapsedMs)
        val running = timerSnapshot(id, from = 1)
        compose.onNodeWithTag(TIMER_TASTE_BUTTON_TAG).performClick()
        idle()
        viewModel.tasteWrites.setTaste(id, 0, TasteAxis.BODY, 3)
        viewModel.tasteWrites.clearTaste(id, 0)
        viewModel.tasteWrites.setTaste(id, 0, TasteAxis.AROMA, 4)
        awaitStored(id, 0, TasteProfile(aroma = 4), "Süß")
        assertThat(timerSnapshot(id, from = 1)).isEqualTo(running)

        // The 0:20 step's pre-alert (5 s before its end) ticks with the sheet open, and the sheet stays.
        val ticks = alert.ticks
        runBlocking { wakeUp.advanceBy(15_000) }
        idle()
        assertThat(alert.ticks).isEqualTo(ticks + 1)
        compose.onNodeWithTag(TIMER_TASTE_SHEET_TAG).assertExists()
        val started = alert.started.size

        // The deadline: the alert rings on time, the sheet is gone and the alert surface shows *Abgegossen*.
        runBlocking { wakeUp.advanceBy(5_000) }
        idle()
        assertThat(active().phase).isEqualTo(TimerPhase.ALERTING)
        // Started once, at that reading, for its deadline (TMR-30).
        assertThat(alert.started).hasSize(started + 1)
        assertThat(alert.scheduled.last()?.atMs).isEqualTo(deadline)
        assertThat(clock.elapsedMs()).isEqualTo(deadline)
        compose.onNodeWithTag(TIMER_TASTE_SHEET_TAG).assertDoesNotExist()
        compose.onNodeWithText("Abgegossen").assertIsDisplayed()
        compose.onNodeWithText("Geschmack", substring = true).assertDoesNotExist()
        assertThat(storedTaste(id, 0)?.taste).isEqualTo(TasteProfile(aroma = 4))

        // Poured: the 2nd infusion records what it records without a taste write.
        pourSecondAtItsDeadline()
        assertThat(recorded(id, 1)).isEqualTo(UNTASTED_SECOND)
    }

    @Test
    fun `without a taste write the 2nd infusion records the same times`() {
        val id = pouredFirst()
        showRoute()
        runBlocking {
            controller.send(TimerEvent.Start)
            wakeUp.advanceBy(20_000)
        }
        idle()
        assertThat(active().phase).isEqualTo(TimerPhase.ALERTING)
        pourSecondAtItsDeadline()
        assertThat(recorded(id, 1)).isEqualTo(UNTASTED_SECOND)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L

        /** The 2nd infusion's actual, paused and overtime seconds: 0:20 run through, poured at its deadline. */
        val UNTASTED_SECOND = listOf(20, 0, 0)
        val STEPS = listOf(
            PlannedStep(StepKind.INFUSION, 60, 60),
            PlannedStep(StepKind.INFUSION, 20, 60),
            PlannedStep(StepKind.INFUSION, 40, 60),
        )
        val SNAPSHOT = SessionSnapshot(
            teaId = null,
            teaName = "Bio Sencha Hisui",
            teaSecondaryName = null,
            teaType = "Sencha",
            styleKey = "sencha",
            category = null,
            profileId = null,
            profileName = "Kyusu",
            method = BrewMethod.KYUSU,
            vesselId = null,
            vesselName = "Kyusu",
            leafGrams = 4.0,
            waterMl = 250,
            baseTempC = 60,
        )
    }
}
