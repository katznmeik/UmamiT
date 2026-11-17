package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.database.entity.SessionStepEntity
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepTasting
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TasteSet
import com.umamit.core.model.TeaCategory
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.infusion
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
import java.time.ZoneOffset

/**
 * The taste sets on Room (umamit.db v9, JRN-03, TEA-02, 05 §3.7, D-128, #320): the ten taste columns are written and
 * read, *Zurücksetzen* clears the six it is given, a value outside the set stays stored, the tea's averages take its
 * set's six axes only, `tea.taste_set` is read and written with the tea, the session reads carry the effective set,
 * and no timer or editor write touches the ten columns or the note.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TasteSetsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val data = TestDatabase()
    private val clock = FakeClock(wall = WRITE_AT, zone = ZoneOffset.UTC)
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

    /** Starts the current step, lets it run [seconds] and pours it. */
    private suspend fun pour(seconds: Long) {
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(seconds)
        driver.send(TimerEvent.DeadlineReached)
        driver.send(TimerEvent.Poured)
    }

    /** The 1st infusion (position 0) poured, the 2nd running. */
    private suspend fun runningAtSecondInfusion() {
        driver.send(startEvent(infusion(60), infusion(20), infusion(30)))
        pour(60)
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(5)
    }

    /** A finished session with one infusion (position 0). */
    private suspend fun finishedSession() {
        driver.send(startEvent(infusion(60)))
        pour(60)
        driver.send(TimerEvent.FinishSession)
    }

    /** Sets all ten axes of [position], each to its ordinal mod 6, and a note. */
    private suspend fun tasteAll(position: Int) {
        TasteAxis.entries.forEach { axis ->
            assertThat(tasting.setInfusionTaste(SESSION_ID, position, axis, axis.ordinal % 6))
                .isEqualTo(TastingWrite.SAVED)
        }
        assertThat(tasting.setInfusionNote(SESSION_ID, position, "Notiz")).isEqualTo(TastingWrite.SAVED)
    }

    private suspend fun row(position: Int): SessionStepEntity = dao.steps(SESSION_ID)[position]

    private suspend fun taste(position: Int): TasteProfile =
        checkNotNull(data.sessions.getSession(SESSION_ID)).stepTastings.getValue(position).taste

    @Test
    fun `every axis has its own column, the narrow read and the session read carry all ten`() = runTest {
        runningAtSecondInfusion()
        tasteAll(0)

        val all = taste(0)
        assertThat(TasteAxis.entries.map { all[it] }).containsExactly(0, 1, 2, 3, 4, 5, 0, 1, 2, 3).inOrder()
        assertThat(journal.observeStepTastings(SESSION_ID).first()).containsExactly(0, StepTasting(all, "Notiz"))

        // A row with a value on one of the four v9 axes only is in the narrow read too.
        tasting.clearInfusionTaste(SESSION_ID, 0, TasteAxis.entries)
        tasting.setInfusionNote(SESSION_ID, 0, null)
        tasting.setInfusionTaste(SESSION_ID, 0, TasteAxis.SPICE, 4)
        assertThat(journal.observeStepTastings(SESSION_ID).first())
            .containsExactly(0, StepTasting(TasteProfile(spice = 4), null))
    }

    @Test
    fun `Zuruecksetzen clears the six it is given, a value outside the set stays stored`() = runTest {
        runningAtSecondInfusion()
        tasteAll(0)

        assertThat(tasting.clearInfusionTaste(SESSION_ID, 0, TasteSet.HERBAL.axes)).isEqualTo(TastingWrite.SAVED)
        assertThat(taste(0)).isEqualTo(TasteProfile(umami = 0, astringency = 3, aroma = 5, roast = 0))
        assertThat(row(0).tastingNote).isEqualTo("Notiz")

        // The Grüntee six named: Röstung/Malz stays.
        assertThat(tasting.clearInfusionTaste(SESSION_ID, 0, TasteSet.GREEN.axes)).isEqualTo(TastingWrite.SAVED)
        assertThat(taste(0)).isEqualTo(TasteProfile(roast = 0))
    }

    @Test
    fun `a write of the set's six leaves the other four as they were`() = runTest {
        finishedSession()
        tasting.setInfusionTaste(SESSION_ID, 0, TasteAxis.UMAMI, 3)

        TasteSet.HERBAL.axes.forEach { tasting.setInfusionTaste(SESSION_ID, 0, it, 2) }

        assertThat(taste(0)).isEqualTo(
            TasteProfile(umami = 3, sweetness = 2, bitterness = 2, body = 2, acidity = 2, freshness = 2, spice = 2),
        )
    }

    @Test
    fun `the timer's RecordStep and Persist and the session editor keep all ten columns and the note`() = runTest {
        runningAtSecondInfusion()
        tasteAll(0)
        val tasted = row(0)

        // Persist of every transition, a RecordStep of the tasted row itself, the next steps and CompleteSession.
        data.sessions.recordStep(
            SESSION_ID,
            0,
            StepRecord(60, 70, 0, 10, checkNotNull(tasted.startedAt), 9L, false, true),
        )
        driver.clocks.advanceSeconds(15)
        driver.send(TimerEvent.DeadlineReached)
        driver.send(TimerEvent.Poured)
        pour(30)
        driver.send(TimerEvent.FinishSession)
        assertThat(dao.get(SESSION_ID)?.status).isEqualTo(SessionStatus.COMPLETED)
        val edited = RoomSessionEditor(data.db, clock).editSession(
            SESSION_ID,
            SessionEdit(steps = listOf(StepEdit(position = 0, actualSeconds = 75, tempC = 65))),
        )
        assertThat(edited).isEqualTo(SessionEditResult.SAVED)

        val after = row(0)
        assertThat(after.actualSeconds).isEqualTo(75)
        assertThat(after.tasteColumns()).isEqualTo(tasted.tasteColumns())
        assertThat(after.tasteColumns()).containsExactly(0, 1, 2, 3, 4, 5, 0, 1, 2, 3, "Notiz").inOrder()
    }

    @Test
    fun `the tea's averages take its set's six axes, the Geschmacksprofil wins`() = runTest {
        finishedSession()
        tasting.setInfusionTaste(SESSION_ID, 0, TasteAxis.UMAMI, 3)
        tasting.setInfusionTaste(SESSION_ID, 0, TasteAxis.SWEETNESS, 2)
        tasting.setInfusionTaste(SESSION_ID, 0, TasteAxis.BODY, 4)
        tasting.setInfusionTaste(SESSION_ID, 0, TasteAxis.ACIDITY, 1)
        assertThat(journal.observeTeaTasting("t1").first().tasteAverages)
            .containsExactly(TasteAxis.UMAMI, 3.0, TasteAxis.SWEETNESS, 2.0, TasteAxis.BODY, 4.0)

        // JRN-03 AC4: a herbal tea shows Süße, Säure and Körper; Umami stays stored.
        data.teas.saveTea(tea("t1").copy(category = TeaCategory.HERBAL))
        assertThat(journal.observeTeaTasting("t1").first().tasteAverages)
            .containsExactly(TasteAxis.SWEETNESS, 2.0, TasteAxis.ACIDITY, 1.0, TasteAxis.BODY, 4.0)

        // With *Geschmacksprofil* „Grüntee" Umami shows again.
        data.teas.saveTea(tea("t1").copy(category = TeaCategory.HERBAL, tasteSet = TasteSet.GREEN))
        assertThat(journal.observeTeaTasting("t1").first().tasteAverages)
            .containsExactly(TasteAxis.UMAMI, 3.0, TasteAxis.SWEETNESS, 2.0, TasteAxis.BODY, 4.0)
        assertThat(taste(0).umami).isEqualTo(3)
    }

    @Test
    fun `taste_set is read and written with the tea`() = runTest {
        assertThat(checkNotNull(data.teas.getTea("t1")).tasteSet).isNull()

        data.teas.saveTea(tea("t1").copy(tasteSet = TasteSet.OXIDISED))
        assertThat(data.db.teaDao().get("t1")?.tasteSet).isEqualTo(TasteSet.OXIDISED)
        assertThat(checkNotNull(data.teas.getTea("t1")).effectiveTasteSet).isEqualTo(TasteSet.OXIDISED)

        data.teas.saveTea(tea("t1"))
        assertThat(data.db.teaDao().get("t1")?.tasteSet).isNull()
    }

    @Test
    fun `the session reads carry the tea's set now, a deleted tea's session its snapshot category's`() = runTest {
        finishedSession()
        suspend fun sets() = listOf(
            checkNotNull(data.sessions.getSession(SESSION_ID)).tasteSet,
            journal.observeSessions().first().single().session.tasteSet,
            checkNotNull(journal.observeSession(SESSION_ID).first()).session.tasteSet,
            data.sessions.observeRecentSessions(1).first().single().tasteSet,
            checkNotNull(RoomSessionEditor(data.db, clock).editable(SESSION_ID)).session.tasteSet,
        ).distinct()
        assertThat(sets()).containsExactly(TasteSet.GREEN)

        data.teas.saveTea(tea("t1").copy(category = TeaCategory.BLACK))
        assertThat(sets()).containsExactly(TasteSet.OXIDISED)

        data.teas.saveTea(tea("t1").copy(category = TeaCategory.HERBAL))
        assertThat(sets()).containsExactly(TasteSet.HERBAL)

        data.teas.saveTea(tea("t1").copy(category = TeaCategory.HERBAL, tasteSet = TasteSet.OXIDISED))
        assertThat(sets()).containsExactly(TasteSet.OXIDISED)

        // The tea is gone: the snapshot's Grün.
        data.db.openHelper.writableDatabase.execSQL("DELETE FROM tea WHERE id = 't1'")
        assertThat(dao.get(SESSION_ID)?.teaId).isNull()
        assertThat(sets()).containsExactly(TasteSet.GREEN)
    }

    private fun SessionStepEntity.tasteColumns(): List<Any?> = listOf(
        tasteUmami,
        tasteSweetness,
        tasteBitterness,
        tasteAstringency,
        tasteBody,
        tasteAroma,
        tasteRoast,
        tasteAcidity,
        tasteFreshness,
        tasteSpice,
        tastingNote,
    )

    private companion object {
        const val WRITE_AT = 9_000_000L
    }
}
