package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.database.entity.SessionDescriptorEntity
import com.umamit.core.model.DescriptorCount
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TeaRating
import com.umamit.core.model.TeaTasting
import com.umamit.core.timer.engine.StepRecord
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

/**
 * The tasting reads (05 §3.6, §3.8, JRN-02–JRN-05, TEA-08, TEA-13, #223): the session's inputs and descriptor keys
 * with the journal row and the session; per tea the average rating over its rated sessions, the taste averages per
 * axis over the infusions that set that axis (D-114), and the descriptor counts. Sessions in progress never count,
 * and a deleted session takes its descriptors with it (JRN-10).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TeaTastingTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val data = TestDatabase()
    private val tasting = RoomSessionTasting(data.db, data.time)
    private val journal by lazy { RoomJournalRepository(data.db, PhotoFiles(folder.newFolder("photos"))) }

    @Before
    fun setUp() = runTest {
        data.teas.saveTea(tea("t1"))
        data.teas.saveTea(tea("t2"))
    }

    @After
    fun tearDown() = data.close()

    private suspend fun completed(id: String, teaId: String = "t1", startedAtMs: Long = 2_000) {
        data.sessions.startSession(SessionStart(id, snapshotOf(teaId), listOf(infusion(60)), 10, startedAtMs, "UTC"))
        data.sessions.recordStep(id, 0, StepRecord(60, 60, 0, 0, startedAtMs, startedAtMs + 60_000, false, false))
        data.sessions.completeSession(id, autoFinished = false)
    }

    /** Three finished sessions of t1 (two rated), one of t2, and t1's session in progress with values set directly. */
    private suspend fun brewed() {
        completed("a", startedAtMs = 2_000)
        tasting.setRating("a", 4.0)
        tasting.setInfusionTaste("a", 0, TasteAxis.UMAMI, 5)
        tasting.setDescriptor("a", "marine.nori", ticked = true)
        tasting.setDescriptor("a", "vegetal.spinat", ticked = true)
        completed("b", startedAtMs = 3_000)
        tasting.setRating("b", 3.5)
        tasting.setInfusionTaste("b", 0, TasteAxis.UMAMI, 2)
        tasting.setInfusionTaste("b", 0, TasteAxis.SWEETNESS, 3)
        tasting.setDescriptor("b", "marine.nori", ticked = true)
        // A key a later version wrote: stored and counted, though a tick of it is refused (05 §3.8).
        data.db.tastingDao().insertDescriptor(SessionDescriptorEntity("b", "unknown.later", createdAt = 3_500))
        completed("c", startedAtMs = 4_000)
        completed("other", teaId = "t2", startedAtMs = 5_000)
        tasting.setRating("other", 5.0)
        data.sessions.startSession(SessionStart("running", snapshotOf("t1"), listOf(infusion(60)), 10, 6_000, "UTC"))
        val running = checkNotNull(data.db.sessionDao().get("running"))
        data.db.sessionDao().update(running.copy(rating = 0.5))
        data.db.openHelper.writableDatabase.execSQL(
            "UPDATE session_step SET taste_umami = 0, taste_body = 1 WHERE session_id = 'running'",
        )
    }

    @Test
    fun `a tea's average rating counts its rated finished sessions only`() = runTest {
        brewed()

        assertThat(journal.observeTeaTasting("t1").first().rating).isEqualTo(TeaRating(average = 3.75, count = 2))
    }

    @Test
    fun `taste averages are per axis over the infusions that set that axis`() = runTest {
        brewed()

        assertThat(journal.observeTeaTasting("t1").first().tasteAverages)
            .containsExactly(TasteAxis.UMAMI, 3.5, TasteAxis.SWEETNESS, 3.0)
    }

    @Test
    fun `descriptor counts are most frequent first and keep unknown keys`() = runTest {
        brewed()

        assertThat(journal.observeTeaTasting("t1").first().descriptorCounts).containsExactly(
            DescriptorCount("marine.nori", 2),
            DescriptorCount("unknown.later", 1),
            DescriptorCount("vegetal.spinat", 1),
        ).inOrder()
    }

    @Test
    fun `a tea without tasting data has the empty summary and no rating`() = runTest {
        completed("plain", teaId = "t2")

        assertThat(journal.observeTeaTasting("t2").first()).isEqualTo(TeaTasting.EMPTY)
        assertThat(journal.observeTeaTasting("nope").first()).isEqualTo(TeaTasting.EMPTY)
    }

    @Test
    fun `the journal row and the session carry the tasting inputs and the descriptor keys`() = runTest {
        brewed()
        tasting.setNotes("b", "Notiz")

        val row = journal.observeSessions().first().single { it.session.id == "b" }.session
        assertThat(row.rating).isEqualTo(3.5)
        assertThat(row.infusions.single().taste).isEqualTo(TasteProfile(umami = 2, sweetness = 3))
        assertThat(row.notes).isEqualTo("Notiz")
        assertThat(row.descriptorKeys).containsExactly("marine.nori", "unknown.later")
        assertThat(data.sessions.getSession("b")).isEqualTo(row)
        assertThat(data.sessions.observeRecentSessions(limit = 10).first().single { it.id == "b" }).isEqualTo(row)
    }

    @Test
    fun `deleting a session takes its descriptors and its values out of the tea's summary`() = runTest {
        brewed()

        journal.deleteSession("a")

        assertThat(data.db.tastingDao().descriptors("a")).isEmpty()
        val summary = journal.observeTeaTasting("t1").first()
        assertThat(summary.rating).isEqualTo(TeaRating(3.5, 1))
        assertThat(summary.tasteAverages).containsExactly(TasteAxis.UMAMI, 2.0, TasteAxis.SWEETNESS, 3.0)
        assertThat(summary.descriptorCounts.map { it.key }).containsExactly("marine.nori", "unknown.later").inOrder()
    }
}
