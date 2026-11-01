package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.model.SessionStatus
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
 * The journal's read side (SCR-15, JRN-01, JRN-08): every session with its
 * steps, newest first, with the tea's cover photo, emitted again as soon as
 * the session repository writes (a finished brew appears at once).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JournalRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val data = TestDatabase()
    private val photos by lazy { PhotoFiles(folder.newFolder("photos")) }
    private val journal by lazy { RoomJournalRepository(data.db, photos) }

    @After
    fun tearDown() = data.close()

    private suspend fun TimerDriver.brewOneAndFinish() {
        send(TimerEvent.Start)
        clocks.advanceSeconds(60)
        send(TimerEvent.DeadlineReached)
        send(TimerEvent.Poured)
        send(TimerEvent.FinishSession)
    }

    @Test
    fun `an empty database has an empty journal`() = runTest {
        assertThat(journal.observeSessions().first()).isEmpty()
    }

    @Test
    fun `sessions come newest first with their steps, in progress or finished`() = runTest {
        data.teas.saveTea(tea("t1"))
        val clocks = TestClocks()
        val first = TimerDriver(data, clocks)
        first.send(startEvent(infusion(60), infusion(20), id = "early"))
        first.brewOneAndFinish()
        clocks.advanceSeconds(3_600)
        TimerDriver(data, clocks).send(startEvent(infusion(45), id = "late"))

        val sessions = journal.observeSessions().first()

        assertThat(sessions.map { it.session.id }).containsExactly("late", "early").inOrder()
        assertThat(sessions.map { it.session.status })
            .containsExactly(SessionStatus.IN_PROGRESS, SessionStatus.COMPLETED).inOrder()
        val early = sessions.last().session
        // „1 von 2 Aufgüssen": the plan keeps its count (TMR-12); the never-started row stays unended (Q-19).
        assertThat(early.plannedStepCount).isEqualTo(2)
        assertThat(early.steps.map { it.actualSeconds }).containsExactly(60, null).inOrder()
    }

    @Test
    fun `a session completed later is emitted again at once`() = runTest {
        data.teas.saveTea(tea("t1"))
        val driver = TimerDriver(data)
        driver.send(startEvent(infusion(60)))
        assertThat(journal.observeSessions().first().single().session.status).isEqualTo(SessionStatus.IN_PROGRESS)

        driver.brewOneAndFinish()

        assertThat(journal.observeSessions().first().single().session.status).isEqualTo(SessionStatus.COMPLETED)
    }

    @Test
    fun `the tea's cover photo is the row's thumbnail, none without a tea or a photo`() = runTest {
        data.teas.saveTea(tea("t1"))
        data.teas.saveTea(tea("t2"))
        data.teas.savePhoto(photo("p1", "t1"))
        data.teas.setCoverPhoto("t1", "p1")
        val clocks = TestClocks()
        fun driverFor(teaId: String?) =
            TimerDriver(data, clocks) { id, steps, pre -> sessionStart(id, steps, pre, clocks, snapshotOf(teaId)) }
        driverFor("t1").send(startEvent(infusion(60), id = "with-photo"))
        clocks.advanceSeconds(10)
        driverFor("t2").send(startEvent(infusion(60), id = "without-photo"))
        clocks.advanceSeconds(10)
        driverFor(null).send(startEvent(infusion(60), id = "without-tea"))

        val photosById = journal.observeSessions().first().associate { it.session.id to it.coverPhoto }

        assertThat(photosById).containsExactly(
            "without-tea",
            null,
            "without-photo",
            null,
            "with-photo",
            photos.file("p1"),
        )
    }

    private fun photo(id: String, teaId: String) = Photo(
        id = id,
        owner = PhotoOwner.OfTea(teaId),
        origin = PhotoOrigin.CATALOG_COPY,
        widthPx = 1,
        heightPx = 1,
        byteSize = 1,
    )
}
