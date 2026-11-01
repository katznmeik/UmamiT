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
 * *Löschen* of a tea (TEA-04, D-100) on the database in memory: the tea, its recipes, stock events and
 * photo rows go; its sessions stay in the journal with their name snapshot; its photo files are removed
 * after the commit; the tea of the active brew session can't be deleted (E15).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TeaDeleteTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val data = TestDatabase()
    private val photos by lazy { PhotoFiles(folder.newFolder("photos")) }
    private val archive by lazy { RoomTeaArchive(data.db, data.time, photos) }

    @After
    fun tearDown() = data.close()

    private suspend fun TimerDriver.brewOneAndFinish() {
        send(TimerEvent.Start)
        clocks.advanceSeconds(60)
        send(TimerEvent.DeadlineReached)
        send(TimerEvent.Poured)
        send(TimerEvent.FinishSession)
    }

    private suspend fun photo(id: String, teaId: String) {
        photos.write(id, byteArrayOf(1, 2, 3))
        data.teas.savePhoto(Photo(id, PhotoOwner.OfTea(teaId), PhotoOrigin.OWN, 1, 1, 3L))
    }

    @Test
    fun `deleting removes the tea with its recipes, stock and photos, the journal keeps its sessions by name`() =
        runTest {
            data.teas.saveTea(tea("t1", "Bio Sencha Hisui"))
            data.teas.saveTea(tea("t2", "Bio Sencha Tanabata"))
            data.recipes.saveRecipe(recipe("r1", "t1", isDefault = true))
            data.recipes.saveRecipe(recipe("r2", "t2", isDefault = true))
            data.stock.addEvent(initial("e1", "t1", 100.0, 850))
            photo("p1", "t1")
            photo("p2", "t1")
            photo("p3", "t2")
            data.teas.setCoverPhoto("t1", "p1")
            val snapshot = snapshotOf(teaId = "t1").copy(profileId = "r1")
            TimerDriver(data) { id, steps, preAlert -> sessionStart(id, steps, preAlert, TestClocks(), snapshot) }
                .apply {
                    send(startEvent(infusion(60)))
                    brewOneAndFinish()
                }

            assertThat(archive.delete("t1")).isEqualTo(DeleteResult.Deleted)

            assertThat(data.teas.getTea("t1")).isNull()
            assertThat(data.recipes.getRecipe("r1")).isNull()
            assertThat(data.stock.observeEvents("t1").first()).isEmpty()
            assertThat(data.db.teaDao().photo("p1")).isNull()
            assertThat(data.db.teaDao().photo("p2")).isNull()
            // The tea's photo files are gone; another tea's stay.
            assertThat(photos.file("p1").exists()).isFalse()
            assertThat(photos.file("p2").exists()).isFalse()
            assertThat(photos.file("p3").exists()).isTrue()
            assertThat(data.teas.getTea("t2")).isNotNull()
            // The session stays with its name; its links to the tea and the recipe are cleared (05 §3.1).
            val session = data.sessions.getSession(SESSION_ID)!!.snapshot
            assertThat(session.teaId).isNull()
            assertThat(session.profileId).isNull()
            assertThat(session.teaName).isEqualTo("Bio Sencha Hisui")
        }

    @Test
    fun `a missing photo file doesn't stop the delete`() = runTest {
        data.teas.saveTea(tea("t1"))
        data.teas.savePhoto(Photo("p1", PhotoOwner.OfTea("t1"), PhotoOrigin.OWN, 1, 1, 3L))

        assertThat(archive.delete("t1")).isEqualTo(DeleteResult.Deleted)

        assertThat(data.teas.getTea("t1")).isNull()
    }

    @Test
    fun `the tea of the active brew session can't be deleted until the session ends`() = runTest {
        data.teas.saveTea(tea("t1"))
        photo("p1", "t1")
        val driver = TimerDriver(data)
        driver.send(startEvent(infusion(60)))

        assertThat(archive.delete("t1")).isEqualTo(DeleteResult.InActiveSession)
        assertThat(data.teas.getTea("t1")).isNotNull()
        assertThat(photos.file("p1").exists()).isTrue()

        driver.brewOneAndFinish()

        assertThat(archive.delete("t1")).isEqualTo(DeleteResult.Deleted)
        assertThat(photos.file("p1").exists()).isFalse()
    }

    @Test
    fun `a tea that is gone is reported`() = runTest {
        assertThat(archive.delete("gone")).isEqualTo(DeleteResult.NotFound)
    }

    @Test
    fun `Wiederherstellen brings an archived tea back to Start and the picker`() = runTest {
        data.teas.saveTea(tea("t1"))
        archive.archive("t1")
        assertThat(data.teas.observeOwnedTeas().first()).isEmpty()

        archive.restore("t1")

        assertThat(data.teas.getTea("t1")!!.isArchived).isFalse()
        assertThat(data.teas.observeOwnedTeas().first().map { it.tea.id }).containsExactly("t1")
    }
}
