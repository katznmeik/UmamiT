package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
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
 * *Archivieren* (TEA-04, SCR-03, D-093 point 8) on the database in memory: the tea is
 * archived, never deleted, and comes back with undo; the tea of the active brew session can't be
 * removed (E15); Start and the tea picker no longer list it while the journal keeps its sessions
 * with name and photo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TeaArchiveTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val data = TestDatabase()
    private val archive by lazy { RoomTeaArchive(data.db, data.time) }

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
    fun `removing archives the tea and undo brings it back unchanged`() = runTest {
        data.teas.saveTea(tea("t1", "Bio Sencha Tanabata"))
        data.recipes.saveRecipe(recipe("r1", "t1", isDefault = true))

        assertThat(archive.archive("t1")).isEqualTo(ArchiveResult.Archived)

        val archived = data.teas.getTea("t1")!!
        assertThat(archived.isArchived).isTrue()
        // Archived, not deleted: its recipes stay.
        assertThat(data.recipes.observeRecipes("t1").first()).hasSize(1)

        archive.restore("t1")

        assertThat(data.teas.getTea("t1")).isEqualTo(tea("t1", "Bio Sencha Tanabata"))
        assertThat(data.teas.observeOwnedTeas().first().map { it.tea.id }).containsExactly("t1")
    }

    @Test
    fun `a tea that is gone is reported, nothing else changes`() = runTest {
        data.teas.saveTea(tea("t1"))

        assertThat(archive.archive("gone")).isEqualTo(ArchiveResult.NotFound)
        assertThat(data.teas.getTea("t1")!!.isArchived).isFalse()
    }

    @Test
    fun `the tea of the active brew session can't be removed until the session ends`() = runTest {
        data.teas.saveTea(tea("t1"))
        data.teas.saveTea(tea("t2"))
        assertThat(archive.observeActiveTeaId().first()).isNull()
        val driver = TimerDriver(data)
        driver.send(startEvent(infusion(60)))

        assertThat(archive.observeActiveTeaId().first()).isEqualTo("t1")
        assertThat(archive.archive("t1")).isEqualTo(ArchiveResult.InActiveSession)
        assertThat(data.teas.getTea("t1")!!.isArchived).isFalse()
        // Another tea can go meanwhile.
        assertThat(archive.archive("t2")).isEqualTo(ArchiveResult.Archived)

        driver.brewOneAndFinish()

        assertThat(archive.observeActiveTeaId().first()).isNull()
        assertThat(archive.archive("t1")).isEqualTo(ArchiveResult.Archived)
    }

    @Test
    fun `a running quick timer blocks no tea`() = runTest {
        data.teas.saveTea(tea("t1"))
        TimerDriver(data).send(TimerEvent.StartQuickTimer(durationSeconds = 120, globalPreAlertSeconds = 10))

        assertThat(archive.observeActiveTeaId().first()).isNull()
        assertThat(archive.archive("t1")).isEqualTo(ArchiveResult.Archived)
    }

    @Test
    fun `Start and the picker no longer list an archived tea, the journal keeps its sessions with name and photo`() =
        runTest {
            data.teas.saveTea(tea("t1", "Bio Sencha Hisui"))
            data.teas.saveTea(tea("t2", "Bio Sencha Tanabata"))
            val photo = Photo("p1", PhotoOwner.OfTea("t1"), PhotoOrigin.CATALOG_COPY, 10, 10, 100)
            data.teas.savePhoto(photo)
            data.teas.setCoverPhoto("t1", "p1")
            TimerDriver(data).apply {
                send(startEvent(infusion(60)))
                brewOneAndFinish()
            }

            archive.archive("t1")

            // The query of Start and the tea picker (SCR-02, SCR-03).
            assertThat(data.teas.observeOwnedTeas().first().map { it.tea.id }).containsExactly("t2")
            // The journal keeps the session with its name and the tea's photo (D-093 Q13).
            val photos = PhotoFiles(folder.newFolder("photos"))
            val entry = RoomJournalRepository(data.db, photos).observeSessions().first().single()
            assertThat(entry.session.snapshot.teaId).isEqualTo("t1")
            assertThat(entry.session.snapshot.teaName).isEqualTo("Bio Sencha Hisui")
            assertThat(entry.coverPhoto).isEqualTo(photos.file("p1"))
            // Start's recent sessions keep it too; *Nochmal brühen* leaves it out by the owned teas.
            assertThat(data.sessions.observeRecentSessions(3).first().map { it.snapshot.teaId }).containsExactly("t1")
        }
}
