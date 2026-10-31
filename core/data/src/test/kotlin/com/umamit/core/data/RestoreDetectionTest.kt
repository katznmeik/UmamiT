package com.umamit.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.umamiDatabaseBuilder
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeSettingsRepository
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
import java.io.File

/**
 * Restore detection (05 §8, 04 §11 E11): a database that comes back from a
 * device backup or transfer has no install marker in no-backup storage, so
 * its `active_brew` is cleared once, before anything reads it. No timer of
 * the old phone is ever resumed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RestoreDetectionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val marker: File get() = File(folder.root, "no_backup/install-marker")
    private val photosDir: File get() = File(folder.root, "files/photos")
    private val opened = mutableListOf<UmamiDatabase>()

    @After
    fun tearDown() {
        opened.forEach { it.close() }
        context.deleteDatabase(DB_NAME)
    }

    /** Opens `umamit.db` as the app does, with the restore check, and the repositories on top. */
    private fun open(): TestDatabase {
        val db = umamiDatabaseBuilder(context, DB_NAME, marker, photosDir).allowMainThreadQueries().build()
        opened += db
        return TestDatabase(db = db, settings = FakeSettingsRepository())
    }

    private fun TestDatabase.closeDb() {
        db.close()
        opened -= db
    }

    @Test
    fun `a fresh install writes the marker and keeps its active brew across restarts`() = runTest {
        val first = open()
        first.teas.saveTea(tea("t1"))
        TimerDriver(first).send(startEvent(infusion(60)))
        val stored = first.activeBrew.read(TimerSlot.BREW)
        assertThat(marker.exists()).isTrue()
        first.closeDb()

        val restarted = open()

        assertThat(restarted.activeBrew.read(TimerSlot.BREW)).isEqualTo(stored)
    }

    @Test
    fun `a restored database without the marker loses its active brew exactly once`() = runTest {
        val oldPhone = open()
        oldPhone.teas.saveTea(tea("t1"))
        TimerDriver(oldPhone).send(startEvent(infusion(60)))
        oldPhone.closeDb()
        // The new phone gets the database from the backup, but not the no-backup storage.
        marker.delete()

        val newPhone = open()

        assertThat(newPhone.activeBrew.read(TimerSlot.BREW)).isNull()
        assertThat(marker.exists()).isTrue()
        // Only the timer state goes; the personal data stays.
        assertThat(newPhone.sessions.getSession(SESSION_ID)).isNotNull()
        assertThat(newPhone.teas.getTea("t1")).isNotNull()

        newPhone.teas.saveTea(tea("t1"))
        TimerDriver(newPhone).send(startEvent(infusion(60), id = "s2"))
        val newTimer = newPhone.activeBrew.read(TimerSlot.BREW)
        newPhone.closeDb()

        assertThat(open().activeBrew.read(TimerSlot.BREW)).isEqualTo(newTimer)
    }

    /**
     * S6 of the #27 slice review: `photos/` is never backed up, so after a restore the rows
     * of photos whose files did not come along are deleted and their covers cleared, which
     * lets CAT-15 refill the seed covers (05 §8). Rows whose file exists stay.
     */
    @Test
    fun `a restore deletes the photo rows whose files are missing and clears their covers`() = runTest {
        val oldPhone = open()
        oldPhone.teas.saveTea(tea("t1"))
        oldPhone.teas.saveTea(tea("t2"))
        oldPhone.teas.savePhoto(photo("gone", "t1"))
        oldPhone.teas.setCoverPhoto("t1", "gone")
        oldPhone.teas.savePhoto(photo("kept", "t2"))
        oldPhone.teas.setCoverPhoto("t2", "kept")
        File(photosDir, "kept.jpg").apply { parentFile?.mkdirs() }.writeBytes(byteArrayOf(1))
        oldPhone.closeDb()
        marker.delete()

        val newPhone = open()

        assertThat(newPhone.teas.getTea("t1")!!.coverPhotoId).isNull()
        assertThat(newPhone.teas.observeTea("t1").first()!!.coverPhoto).isNull()
        assertThat(newPhone.teas.getTea("t2")!!.coverPhotoId).isEqualTo("kept")
        assertThat(newPhone.teas.observeTea("t2").first()!!.coverPhoto!!.id).isEqualTo("kept")
    }

    @Test
    fun `without a restore photo rows stay even when their file is missing`() = runTest {
        val first = open()
        first.teas.saveTea(tea("t1"))
        first.teas.savePhoto(photo("gone", "t1"))
        first.teas.setCoverPhoto("t1", "gone")
        first.closeDb()

        val restarted = open()

        assertThat(restarted.teas.getTea("t1")!!.coverPhotoId).isEqualTo("gone")
    }

    private fun photo(id: String, teaId: String) = Photo(
        id = id,
        owner = PhotoOwner.OfTea(teaId),
        origin = PhotoOrigin.CATALOG_COPY,
        widthPx = 1,
        heightPx = 1,
        byteSize = 1,
    )

    private companion object {
        const val DB_NAME = "restore-test.db"
    }
}
