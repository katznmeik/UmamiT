package com.umamit.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.BrewSession
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.TimerSlot
import com.umamit.core.model.UserSettings
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `EditSession` (05 §3.7 „Edit", JRN-11, D-112, #230): each field's effect, the shift that keeps every duration, the
 * stock and the cost after a leaf edit, the refusal of a future start, a session in progress and its `active_brew`
 * row untouched (AC2), and the values after reopening the database (AC1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionEditorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var data = TestDatabase(now = NOW)
    private var editor = RoomSessionEditor(data.db, data.time)

    @After
    fun tearDown() = data.close()

    /**
     * Tea „t1" with 100 g for 8,50 € and the completed session „done" with 4 g: step 0 brewed (60 s planned, 62 s
     * with 3 s paused), step 1 skipped, step 2 brewed (20 s), step 3 never started. Cost 34 ct, BREW −4 g.
     */
    private suspend fun completedSession(priceCents: Long? = 850, id: String = "done") {
        if (data.teas.getTea("t1") == null) {
            data.teas.saveTea(tea("t1"))
            data.stock.addEvent(initial("buy", "t1", grams = 100.0, priceCents = priceCents, at = START - DAY_MS))
        }
        val steps = listOf(infusion(60), infusion(20), infusion(20), infusion(30))
        data.sessions.startSession(SessionStart(id, snapshotOf(), steps, 10, START, "Europe/Berlin"))
        data.sessions.recordStep(id, 0, StepRecord(60, 62, 3, 2, START, START + 65_000, false, false))
        data.sessions.recordStep(id, 1, StepRecord(20, 0, 0, 0, null, null, true, false))
        data.sessions.recordStep(id, 2, StepRecord(20, 20, 0, 0, START + 120_000, START + 140_000, false, false))
        data.sessions.completeSession(id, autoFinished = false)
    }

    private suspend fun session(id: String = "done"): BrewSession = checkNotNull(data.sessions.getSession(id))

    private suspend fun brewEvents(): List<InventoryEvent> =
        data.stock.observeEvents("t1").first().filter { it.type == InventoryEventType.BREW }

    private suspend fun remaining(): Double = data.stock.observeStock("t1").first().remainingGrams

    @Test
    fun `a new start shifts the session, its end, every step and the BREW event and keeps every duration`() = runTest {
        completedSession()
        val before = session()
        val newStart = START - DAY_MS + 2 * HOUR_MS

        assertThat(editor.editSession("done", SessionEdit(startedAtMs = newStart)))
            .isEqualTo(SessionEditResult.SAVED)

        val after = session()
        val shift = newStart - START
        assertThat(after.startedAtMs).isEqualTo(newStart)
        assertThat(after.endedAtMs).isEqualTo(checkNotNull(before.endedAtMs) + shift)
        assertThat(after.zoneId).isEqualTo("Europe/Berlin")
        assertThat(after.steps.map { it.startedAtWallMs })
            .isEqualTo(before.steps.map { it.startedAtWallMs?.plus(shift) })
        assertThat(
            after.steps.map {
                it.endedAtWallMs
            },
        ).isEqualTo(before.steps.map { it.endedAtWallMs?.plus(shift) })
        assertThat(after.steps.map { it.actualSeconds }).isEqualTo(before.steps.map { it.actualSeconds })
        assertThat(brewEvents().single().occurredAtMillis).isEqualTo(after.endedAtMs)
        assertThat(remaining()).isWithin(1e-9).of(96.0)
        assertThat(after.costCents).isEqualTo(34)
    }

    @Test
    fun `a start that puts the end after now is refused and changes nothing`() = runTest {
        completedSession()
        val before = session()
        val end = checkNotNull(before.endedAtMs)
        val tooLate = START + (NOW - end) + 1

        assertThat(editor.editSession("done", SessionEdit(startedAtMs = tooLate, waterMl = 300)))
            .isEqualTo(SessionEditResult.IN_FUTURE)
        assertThat(session()).isEqualTo(before)

        val latest = START + (NOW - end)
        assertThat(editor.editSession("done", SessionEdit(startedAtMs = latest))).isEqualTo(SessionEditResult.SAVED)
        assertThat(session().endedAtMs).isEqualTo(NOW)
    }

    @Test
    fun `the vessel is set with its name snapshot, Kein Gefaess clears both, and a vessel that is gone is refused`() =
        runTest {
            completedSession()
            data.vessels.saveVessel(Vessel("v1", "Shiboridashi", VesselType.SHIBORIDASHI, 120, isArchived = true))

            assertThat(editor.editSession("done", SessionEdit(vessel = VesselChange("v1"))))
                .isEqualTo(SessionEditResult.SAVED)
            assertThat(session().snapshot.vesselId).isEqualTo("v1")
            assertThat(session().snapshot.vesselName).isEqualTo("Shiboridashi")

            assertThat(editor.editSession("done", SessionEdit(vessel = VesselChange("gone"))))
                .isEqualTo(SessionEditResult.INVALID)
            assertThat(session().snapshot.vesselId).isEqualTo("v1")

            assertThat(editor.editSession("done", SessionEdit(vessel = VesselChange(null))))
                .isEqualTo(SessionEditResult.SAVED)
            assertThat(session().snapshot.vesselId).isNull()
            assertThat(session().snapshot.vesselName).isNull()
        }

    @Test
    fun `the session's own vessel keeps its snapshot name when it is chosen again`() = runTest {
        completedSession()
        data.vessels.saveVessel(Vessel("v1", "Shiboridashi", VesselType.SHIBORIDASHI, 120))
        editor.editSession("done", SessionEdit(vessel = VesselChange("v1")))
        data.vessels.saveVessel(Vessel("v1", "Umbenannt", VesselType.SHIBORIDASHI, 120))

        assertThat(editor.editSession("done", SessionEdit(vessel = VesselChange("v1"), waterMl = 150)))
            .isEqualTo(SessionEditResult.SAVED)
        assertThat(session().snapshot.vesselName).isEqualTo("Shiboridashi")
    }

    @Test
    fun `a leaf edit sets the BREW deduction, the stock and the cost of a priced tea`() = runTest {
        completedSession()

        assertThat(editor.editSession("done", SessionEdit(leafGrams = 5.0))).isEqualTo(SessionEditResult.SAVED)

        assertThat(session().snapshot.leafGrams).isEqualTo(5.0)
        assertThat(brewEvents().single().deltaGrams).isEqualTo(-5.0)
        assertThat(remaining()).isWithin(1e-9).of(95.0)
        // 5 g × 8,5 ct/g = 42,5 ct, half up (INV-08).
        assertThat(session().costCents).isEqualTo(43)
        assertThat(editor.editable("done")?.hasStockDeduction).isTrue()
    }

    @Test
    fun `a leaf edit uses the tea's cost per gram now`() = runTest {
        completedSession()
        data.stock.addEvent(InventoryEvent("more", "t1", InventoryEventType.PURCHASE, 100.0, START, priceCents = 1_150))

        editor.editSession("done", SessionEdit(leafGrams = 3.0))

        // (850 + 1150) ct / 200 g = 10 ct/g × 3 g.
        assertThat(session().costCents).isEqualTo(30)
    }

    @Test
    fun `a leaf edit of an unpriced tea moves the stock and leaves no cost`() = runTest {
        completedSession(priceCents = null)
        assertThat(session().costCents).isNull()

        editor.editSession("done", SessionEdit(leafGrams = 6.5))

        assertThat(brewEvents().single().deltaGrams).isEqualTo(-6.5)
        assertThat(remaining()).isWithin(1e-9).of(93.5)
        assertThat(session().costCents).isNull()
    }

    @Test
    fun `for a tea that is gone the cost scales with the leaf and no stock event is made`() = runTest {
        completedSession()
        data.teas.deleteTea("t1")
        assertThat(session().snapshot.teaId).isNull()

        assertThat(editor.editSession("done", SessionEdit(leafGrams = 5.0))).isEqualTo(SessionEditResult.SAVED)

        // 34 ct × 5 g ÷ 4 g = 42,5 ct, half up.
        assertThat(session().costCents).isEqualTo(43)
        assertThat(editor.editable("done")?.hasStockDeduction).isFalse()
    }

    @Test
    fun `a session without a BREW event gets none from a leaf edit, while its cost follows`() = runTest {
        data.close()
        data = TestDatabase(now = NOW, settings = FakeSettingsRepository(UserSettings(deductStock = false)))
        editor = RoomSessionEditor(data.db, data.time)
        completedSession()
        assertThat(brewEvents()).isEmpty()

        editor.editSession("done", SessionEdit(leafGrams = 2.0))

        assertThat(brewEvents()).isEmpty()
        assertThat(remaining()).isWithin(1e-9).of(100.0)
        assertThat(session().costCents).isEqualTo(17)
        assertThat(editor.editable("done")?.hasStockDeduction).isFalse()
    }

    @Test
    fun `leaf 0 deletes the BREW event, so the leaf is back in the stock`() = runTest {
        completedSession()

        assertThat(editor.editSession("done", SessionEdit(leafGrams = 0.0))).isEqualTo(SessionEditResult.SAVED)

        assertThat(brewEvents()).isEmpty()
        assertThat(remaining()).isWithin(1e-9).of(100.0)
        assertThat(session().costCents).isNull()
    }

    @Test
    fun `an unchanged leaf keeps the stored cost`() = runTest {
        completedSession()
        data.stock.addEvent(InventoryEvent("more", "t1", InventoryEventType.PURCHASE, 100.0, START, priceCents = 1_150))

        editor.editSession("done", SessionEdit(leafGrams = 4.0, waterMl = 250))

        assertThat(session().costCents).isEqualTo(34)
        assertThat(session().snapshot.waterMl).isEqualTo(250)
    }

    @Test
    fun `the water is set within its bounds`() = runTest {
        completedSession()

        assertThat(editor.editSession("done", SessionEdit(waterMl = 120))).isEqualTo(SessionEditResult.SAVED)
        assertThat(session().snapshot.waterMl).isEqualTo(120)

        listOf(9, 3001).forEach {
            assertThat(editor.editSession("done", SessionEdit(waterMl = it))).isEqualTo(SessionEditResult.INVALID)
        }
        assertThat(session().snapshot.waterMl).isEqualTo(120)
    }

    @Test
    fun `a brewed step's time sets its overtime and end, its plan and the session end stay`() = runTest {
        completedSession()
        val before = session()

        val edit = SessionEdit(steps = listOf(StepEdit(0, 75, 62), StepEdit(2, 15, 60)))
        assertThat(editor.editSession("done", edit)).isEqualTo(SessionEditResult.SAVED)

        val after = session()
        val first = after.steps[0]
        assertThat(first.actualSeconds).isEqualTo(75)
        assertThat(first.plannedSeconds).isEqualTo(60)
        assertThat(first.overtimeSeconds).isEqualTo(15)
        // started + actual + paused: 75 s + 3 s.
        assertThat(first.endedAtWallMs).isEqualTo(START + 78_000)
        // 62 °C snaps to 60 °C (GEN-12).
        assertThat(first.tempC).isEqualTo(60)
        val third = after.steps[2]
        assertThat(third.actualSeconds).isEqualTo(15)
        assertThat(third.overtimeSeconds).isEqualTo(0)
        assertThat(third.plannedSeconds).isEqualTo(20)
        assertThat(after.endedAtMs).isEqualTo(before.endedAtMs)
        assertThat(after.steps[1]).isEqualTo(before.steps[1])
        assertThat(after.steps[3]).isEqualTo(before.steps[3])
    }

    @Test
    fun `a time-only step edit keeps the recorded temperature`() = runTest {
        completedSession()
        val before = session()

        assertThat(editor.editSession("done", SessionEdit(steps = listOf(StepEdit(0, 75)))))
            .isEqualTo(SessionEditResult.SAVED)

        assertThat(session().steps[0].actualSeconds).isEqualTo(75)
        assertThat(session().steps[0].tempC).isEqualTo(before.steps[0].tempC)
    }

    @Test
    fun `a skipped or never started step, a second edit of one step and values out of bounds are refused`() = runTest {
        completedSession()
        val before = session()

        listOf(
            listOf(StepEdit(1, 20, 60)),
            listOf(StepEdit(3, 20, 60)),
            listOf(StepEdit(9, 20, 60)),
            listOf(StepEdit(0, 0, 60)),
            listOf(StepEdit(0, 86_401, 60)),
            listOf(StepEdit(0, 60, 0)),
            listOf(StepEdit(0, 60, 105)),
            listOf(StepEdit(0, 60, 60), StepEdit(0, 61, 60)),
        ).forEach { steps ->
            assertThat(editor.editSession("done", SessionEdit(steps = steps))).isEqualTo(SessionEditResult.INVALID)
        }
        assertThat(editor.editSession("done", SessionEdit(leafGrams = 100.1))).isEqualTo(SessionEditResult.INVALID)
        assertThat(editor.editSession("done", SessionEdit(leafGrams = -1.0))).isEqualTo(SessionEditResult.INVALID)
        assertThat(session()).isEqualTo(before)
    }

    @Test
    fun `a session in progress and its active_brew row are never touched`() = runTest {
        data.teas.saveTea(tea("t1"))
        val driver = TimerDriver(data)
        driver.send(startEvent(infusion(60)))
        driver.send(TimerEvent.Start)
        val sessionBefore = session(SESSION_ID)
        val activeBefore = data.activeBrew.read(TimerSlot.BREW)
        assertThat(activeBefore).isNotNull()

        val edit = SessionEdit(startedAtMs = START - DAY_MS, leafGrams = 6.0, waterMl = 300)
        assertThat(editor.editSession(SESSION_ID, edit)).isEqualTo(SessionEditResult.NOT_COMPLETED)
        assertThat(editor.editable(SESSION_ID)).isNull()

        assertThat(session(SESSION_ID)).isEqualTo(sessionBefore)
        assertThat(session(SESSION_ID).status).isEqualTo(SessionStatus.IN_PROGRESS)
        assertThat(data.activeBrew.read(TimerSlot.BREW)).isEqualTo(activeBefore)
        assertThat(editor.editSession("nobody", SessionEdit(waterMl = 100))).isEqualTo(SessionEditResult.NOT_COMPLETED)
    }

    @Test
    fun `the journal and the tea's stock follow an edit`() = runTest {
        completedSession()
        val journal = RoomJournalRepository(data.db, PhotoFiles(folder.newFolder("photos")))
        val stock = data.stock.observeStock("t1")
        assertThat(stock.first().remainingGrams).isWithin(1e-9).of(96.0)

        editor.editSession("done", SessionEdit(startedAtMs = START - DAY_MS, leafGrams = 7.0))

        // The entry moves to its new day (it lists by start); the stock chip reads the new remaining stock.
        assertThat(journal.observeSessions().first().single().session.startedAtMs).isEqualTo(START - DAY_MS)
        assertThat(stock.first().remainingGrams).isWithin(1e-9).of(93.0)
    }

    @Test
    fun `stock, cost and the session agree after reopening the database`() = runTest {
        data.close()
        val file = folder.newFile("umamit.db").also { it.delete() }
        data = TestDatabase(now = NOW, db = fileDatabase(file.path))
        editor = RoomSessionEditor(data.db, data.time)
        completedSession()
        editor.editSession("done", SessionEdit(leafGrams = 6.0, startedAtMs = START - HOUR_MS))
        data.close()

        data = TestDatabase(now = NOW, db = fileDatabase(file.path))
        val reopened = session()
        assertThat(reopened.snapshot.leafGrams).isEqualTo(6.0)
        assertThat(reopened.costCents).isEqualTo(51)
        assertThat(reopened.startedAtMs).isEqualTo(START - HOUR_MS)
        assertThat(brewEvents().single().deltaGrams).isEqualTo(-6.0)
        assertThat(brewEvents().single().occurredAtMillis).isEqualTo(reopened.endedAtMs)
        assertThat(remaining()).isWithin(1e-9).of(94.0)
    }

    private fun fileDatabase(path: String): UmamiDatabase = Room
        .databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), UmamiDatabase::class.java, path)
        .allowMainThreadQueries()
        .build()

    private companion object {
        val START = START_WALL_MS
        const val DAY_MS = 86_400_000L
        const val HOUR_MS = 3_600_000L

        /** A day after the session. */
        val NOW = START_WALL_MS + DAY_MS
    }
}
