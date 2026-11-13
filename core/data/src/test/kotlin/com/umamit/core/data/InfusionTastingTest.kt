package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.common.WallClock
import com.umamit.core.database.entity.SessionStepEntity
import com.umamit.core.model.Infusion
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepTasting
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The taste and note per infusion (05 §3.7 „Infusion tasting writes", umamit.db v7, JRN-03, JRN-05, D-114, #242):
 * the writes go to an ended step that is not *Spülen*, of a session in progress or completed, column by column; a
 * write while a step runs leaves the running and pending rows and `active_brew` as they were; the engine's own writes
 * (`RecordStep`, `ReplacePendingSteps`, `Persist`, `CompleteSession`) and the session editor keep what was written;
 * and the reads: the infusions numbered as the step table, the session's average and the tea's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InfusionTastingTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val data = TestDatabase()
    private var now = WRITE_AT
    private val clock = object : WallClock {
        override fun nowMs(): Long = now

        override fun zone(): ZoneId = ZoneOffset.UTC
    }
    private val tasting = RoomSessionTasting(data.db, clock)
    private val journal by lazy { RoomJournalRepository(data.db, PhotoFiles(folder.newFolder("photos"))) }
    private val driver = TimerDriver(data)
    private val dao = data.db.sessionDao()

    @Before
    fun setUp() = runTest {
        data.teas.saveTea(tea("t1"))
    }

    @After
    fun tearDown() = data.close()

    /** The engine at the 3rd step (position 2) running: the rinse and the 1st infusion poured, two infusions to go. */
    private suspend fun runningAtSecondInfusion() {
        driver.send(startEvent(rinse(10), infusion(60), infusion(20), infusion(30)))
        pour(10)
        pour(60)
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(5)
    }

    /** Starts the current step, lets it run [seconds] and pours it. */
    private suspend fun pour(seconds: Long) {
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(seconds)
        driver.send(TimerEvent.DeadlineReached)
        driver.send(TimerEvent.Poured)
    }

    private suspend fun rows(): List<SessionStepEntity> = dao.steps(SESSION_ID)

    private suspend fun session() = checkNotNull(data.sessions.getSession(SESSION_ID))

    @Test
    fun `the fixture ends the rinse and the 1st infusion and runs the 2nd`() = runTest {
        runningAtSecondInfusion()

        assertThat(rows().map { it.actualSeconds != null }).containsExactly(true, true, false, false).inOrder()
        val active = checkNotNull(data.db.activeBrewDao().get(TimerSlot.BREW))
        assertThat(active.stepIndex).isEqualTo(2)
        assertThat(active.phase).isEqualTo(TimerPhase.RUNNING)
    }

    @Test
    fun `a write on the ended infusion of a running session is stored, column by column`() = runTest {
        runningAtSecondInfusion()

        assertThat(tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.UMAMI, 5)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.BITTERNESS, 0)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.AROMA, 3)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setInfusionNote(SESSION_ID, 1, "  Süß, nach Nori \n")).isEqualTo(TastingWrite.SAVED)

        assertThat(session().infusions).containsExactly(
            Infusion(1, 1, TasteProfile(umami = 5, bitterness = 0, aroma = 3), "Süß, nach Nori"),
        )

        assertThat(tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.AROMA, null)).isEqualTo(TastingWrite.SAVED)
        assertThat(session().infusions.single().taste).isEqualTo(TasteProfile(umami = 5, bitterness = 0))
    }

    @Test
    fun `every axis has its own column, Zuruecksetzen clears the six and keeps the note`() = runTest {
        runningAtSecondInfusion()
        TasteAxis.entries.forEachIndexed { i, axis -> tasting.setInfusionTaste(SESSION_ID, 1, axis, i) }
        tasting.setInfusionNote(SESSION_ID, 1, "Notiz")

        val taste = session().infusions.single().taste
        assertThat(TasteAxis.entries.map { taste[it] }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()

        assertThat(tasting.clearInfusionTaste(SESSION_ID, 1)).isEqualTo(TastingWrite.SAVED)
        assertThat(session().infusions.single()).isEqualTo(Infusion(1, 1, TasteProfile.EMPTY, "Notiz"))
    }

    @Test
    fun `values out of bounds and a note over 200 characters write nothing`() = runTest {
        runningAtSecondInfusion()
        tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.BODY, 2)
        val longest = "a".repeat(200)
        assertThat(tasting.setInfusionNote(SESSION_ID, 1, " $longest ")).isEqualTo(TastingWrite.SAVED)

        assertThat(tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.BODY, 6)).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.BODY, -1)).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setInfusionNote(SESSION_ID, 1, longest + "b")).isEqualTo(TastingWrite.INVALID)

        assertThat(session().infusions.single()).isEqualTo(Infusion(1, 1, TasteProfile(body = 2), longest))
        assertThat(tasting.setInfusionNote(SESSION_ID, 1, "  \n")).isEqualTo(TastingWrite.SAVED)
        assertThat(session().infusions.single().note).isNull()
    }

    @Test
    fun `a write on a rinse, a running or pending row, a missing row or a gone session is refused`() = runTest {
        runningAtSecondInfusion()
        val before = rows()

        listOf(0, 2, 3, 9).forEach { position ->
            val results = listOf(
                tasting.setInfusionTaste(SESSION_ID, position, TasteAxis.UMAMI, 4),
                tasting.clearInfusionTaste(SESSION_ID, position),
                tasting.setInfusionNote(SESSION_ID, position, "Notiz"),
            )
            assertWithMessage("position $position").that(results.distinct())
                .containsExactly(TastingWrite.NOT_AN_INFUSION)
        }
        assertThat(tasting.setInfusionTaste("gone", 1, TasteAxis.UMAMI, 4)).isEqualTo(TastingWrite.NOT_COMPLETED)

        assertThat(rows()).isEqualTo(before)
    }

    @Test
    fun `a skipped step takes a write, so a migrated value left there can be changed or reset`() = runTest {
        // A skipped step records 0 s (04 §5.1): ended for the writes (05 §3.7). Only a v0.6.0 session whose steps
        // were all skipped has values there after the v7 migration (D-114 build note, T3 m1); its chip shows them.
        driver.send(startEvent(infusion(60), infusion(20)))
        driver.send(TimerEvent.SkipStep)
        pour(20)
        driver.send(TimerEvent.FinishSession)
        assertThat(rows().map { it.skipped to it.actualSeconds }).containsExactly(true to 0, false to 20).inOrder()

        assertThat(tasting.setInfusionTaste(SESSION_ID, 0, TasteAxis.UMAMI, 3)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setInfusionNote(SESSION_ID, 0, "Übersprungen")).isEqualTo(TastingWrite.SAVED)
        assertThat(session().infusions.first()).isEqualTo(Infusion(0, 1, TasteProfile(umami = 3), "Übersprungen"))

        assertThat(tasting.clearInfusionTaste(SESSION_ID, 0)).isEqualTo(TastingWrite.SAVED)
        assertThat(session().infusions.first().taste).isEqualTo(TasteProfile.EMPTY)
    }

    @Test
    fun `a write on an aborted session is refused and the session is gone with its values`() = runTest {
        runningAtSecondInfusion()
        tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.UMAMI, 4)

        driver.send(TimerEvent.Discard)

        assertThat(dao.get(SESSION_ID)).isNull()
        assertThat(tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.UMAMI, 5)).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(tasting.setInfusionNote(SESSION_ID, 1, "Notiz")).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(rows()).isEmpty()
    }

    @Test
    fun `a session that is neither in progress nor completed takes no write`() = runTest {
        runningAtSecondInfusion()
        val manual = checkNotNull(dao.get(SESSION_ID)).copy(status = SessionStatus.MANUAL)
        dao.update(manual)
        val before = rows()

        assertThat(tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.UMAMI, 4)).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(tasting.clearInfusionTaste(SESSION_ID, 1)).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(tasting.setInfusionNote(SESSION_ID, 1, "Notiz")).isEqualTo(TastingWrite.NOT_COMPLETED)

        assertThat(rows()).isEqualTo(before)
        assertThat(dao.get(SESSION_ID)).isEqualTo(manual)
    }

    @Test
    fun `a write while a step runs leaves the running and pending rows, active_brew and the session as they were`() =
        runTest {
            runningAtSecondInfusion()
            val before = rows()
            val active = data.db.activeBrewDao().get(TimerSlot.BREW)
            val brewSession = dao.get(SESSION_ID)
            val snapshot = data.activeBrew.read(TimerSlot.BREW)

            tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.UMAMI, 5)
            tasting.setInfusionNote(SESSION_ID, 1, "Notiz")
            tasting.clearInfusionTaste(SESSION_ID, 1)
            tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.BODY, 3)

            val after = rows()
            assertThat(after.filter { it.position != 1 }).isEqualTo(before.filter { it.position != 1 })
            // The ended row changes in its tasting columns only: not even its updated_at.
            assertThat(after[1]).isEqualTo(before[1].copy(tasteBody = 3, tastingNote = "Notiz"))
            assertThat(data.db.activeBrewDao().get(TimerSlot.BREW)).isEqualTo(active)
            assertThat(dao.get(SESSION_ID)).isEqualTo(brewSession)
            assertThat(data.activeBrew.read(TimerSlot.BREW)).isEqualTo(snapshot)
        }

    @Test
    fun `the engine's writes after a taste write keep it, to the completed session`() = runTest {
        runningAtSecondInfusion()
        tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.UMAMI, 5)
        tasting.setInfusionNote(SESSION_ID, 1, "Erster")

        // RecordStep and Persist of the running step, then ReplacePendingSteps (an edit adds a step) and Persist,
        // then the last steps and CompleteSession.
        driver.clocks.advanceSeconds(15)
        driver.send(TimerEvent.DeadlineReached)
        driver.send(TimerEvent.Poured)
        tasting.setInfusionTaste(SESSION_ID, 2, TasteAxis.SWEETNESS, 2)
        driver.send(TimerEvent.EditSteps(listOf(rinse(10), infusion(60), infusion(20), infusion(30), infusion(45))))
        pour(30)
        pour(45)
        driver.send(TimerEvent.FinishSession)

        assertThat(dao.get(SESSION_ID)?.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(session().infusions).containsExactly(
            Infusion(1, 1, TasteProfile(umami = 5), "Erster"),
            Infusion(2, 2, TasteProfile(sweetness = 2), null),
            Infusion(3, 3, TasteProfile.EMPTY, null),
            Infusion(4, 4, TasteProfile.EMPTY, null),
        ).inOrder()
    }

    @Test
    fun `a RecordStep of the tasted row itself keeps its taste`() = runTest {
        runningAtSecondInfusion()
        tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.UMAMI, 5)
        tasting.setInfusionNote(SESSION_ID, 1, "Erster")
        val row = rows()[1]

        data.sessions.recordStep(
            SESSION_ID,
            1,
            StepRecord(60, 70, 0, 10, checkNotNull(row.startedAt), 9L, false, true),
        )

        val after = rows()[1]
        assertThat(after.actualSeconds).isEqualTo(70)
        assertThat(after.tasteUmami).isEqualTo(5)
        assertThat(after.tastingNote).isEqualTo("Erster")
    }

    @Test
    fun `a completed session takes writes with its updated_at, and the session editor keeps them`() = runTest {
        runningAtSecondInfusion()
        driver.send(TimerEvent.FinishSession)
        val before = checkNotNull(dao.get(SESSION_ID))
        now = WRITE_AT + 1

        assertThat(tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.UMAMI, 4)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setInfusionNote(SESSION_ID, 1, "Später")).isEqualTo(TastingWrite.SAVED)
        assertThat(dao.get(SESSION_ID)).isEqualTo(before.copy(updatedAt = WRITE_AT + 1))

        val edited = RoomSessionEditor(data.db, clock).editSession(
            SESSION_ID,
            SessionEdit(steps = listOf(StepEdit(position = 1, actualSeconds = 75, tempC = 65))),
        )

        assertThat(edited).isEqualTo(SessionEditResult.SAVED)
        val row = rows()[1]
        assertThat(row.actualSeconds).isEqualTo(75)
        assertThat(row.tasteUmami).isEqualTo(4)
        assertThat(row.tastingNote).isEqualTo("Später")
    }

    @Test
    fun `the infusions are the ended steps but the rinse, numbered as the step table`() = runTest {
        driver.send(startEvent(infusion(60), rinse(10), infusion(20), infusion(30)))
        pour(60)
        pour(10)
        pour(20)
        driver.send(TimerEvent.FinishSession)

        assertThat(session().infusions.map { it.position to it.number }).containsExactly(0 to 1, 2 to 2).inOrder()
    }

    @Test
    fun `the session's average is per axis over the infusions that set it`() = runTest {
        driver.send(startEvent(rinse(10), infusion(60), infusion(20), infusion(30)))
        pour(10)
        pour(60)
        pour(20)
        pour(30)
        driver.send(TimerEvent.FinishSession)
        tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.UMAMI, 5)
        tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.BODY, 2)
        tasting.setInfusionTaste(SESSION_ID, 2, TasteAxis.UMAMI, 2)
        tasting.setInfusionTaste(SESSION_ID, 3, TasteAxis.AROMA, 4)

        // The session page's *Alle* averages its infusions per axis (D-114 Q18).
        assertThat(TasteProfile.averages(session().infusions.map { it.taste })).containsExactly(
            TasteAxis.UMAMI,
            3.5,
            TasteAxis.BODY,
            2.0,
            TasteAxis.AROMA,
            4.0,
        )
    }

    @Test
    fun `the tea's average is per axis over every infusion of its finished sessions`() = runTest {
        // Session 1: three infusions, two of them tasted.
        driver.send(startEvent(infusion(60), infusion(20), infusion(30)))
        pour(60)
        pour(20)
        pour(30)
        driver.send(TimerEvent.FinishSession)
        tasting.setInfusionTaste(SESSION_ID, 0, TasteAxis.UMAMI, 5)
        tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.UMAMI, 3)
        tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.SWEETNESS, 1)
        // Session 2, finished: one infusion.
        val second = TimerDriver(data)
        second.send(startEvent(infusion(60), id = "s2"))
        second.send(TimerEvent.Start)
        second.clocks.advanceSeconds(60)
        second.send(TimerEvent.DeadlineReached)
        second.send(TimerEvent.Poured)
        second.send(TimerEvent.FinishSession)
        tasting.setInfusionTaste("s2", 0, TasteAxis.UMAMI, 1)
        // Session 3, in progress: its tasted infusion does not count.
        val third = TimerDriver(data)
        third.send(startEvent(infusion(60), infusion(60), id = "s3"))
        third.send(TimerEvent.Start)
        third.clocks.advanceSeconds(60)
        third.send(TimerEvent.DeadlineReached)
        third.send(TimerEvent.Poured)
        assertThat(tasting.setInfusionTaste("s3", 0, TasteAxis.UMAMI, 0)).isEqualTo(TastingWrite.SAVED)

        assertThat(journal.observeTeaTasting("t1").first().tasteAverages).containsExactly(
            TasteAxis.UMAMI,
            3.0,
            TasteAxis.SWEETNESS,
            1.0,
        )
    }

    @Test
    fun `the journal row and the session read carry the infusions' taste and notes`() = runTest {
        runningAtSecondInfusion()
        tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.UMAMI, 5)
        tasting.setInfusionNote(SESSION_ID, 1, "Nori")

        val row = journal.observeSessions().first().single().session
        assertThat(row.infusions.single()).isEqualTo(Infusion(1, 1, TasteProfile(umami = 5), "Nori"))
        assertThat(row.hasInfusionNotes).isTrue()
        assertThat(data.sessions.getSession(SESSION_ID)).isEqualTo(row)
    }

    @Test
    fun `the timer's narrow read has the steps with a taste or a note, as the session read has them`() = runTest {
        runningAtSecondInfusion()
        assertThat(journal.observeStepTastings(SESSION_ID).first()).isEmpty()

        tasting.setInfusionTaste(SESSION_ID, 1, TasteAxis.BODY, 3)
        tasting.setInfusionNote(SESSION_ID, 1, "Nori")

        val narrow = journal.observeStepTastings(SESSION_ID).first()
        assertThat(narrow).containsExactly(1, StepTasting(TasteProfile(body = 3), "Nori"))
        assertThat(narrow).isEqualTo(session().stepTastings)
        assertThat(journal.observeStepTastings("gone").first()).isEmpty()
    }

    private companion object {
        const val WRITE_AT = 9_000_000L
    }
}
