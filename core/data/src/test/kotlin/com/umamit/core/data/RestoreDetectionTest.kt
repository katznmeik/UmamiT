package com.umamit.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.umamiDatabaseBuilder
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.timer.engine.TimerEvent
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
    private val opened = mutableListOf<UmamiDatabase>()

    @After
    fun tearDown() {
        opened.forEach { it.close() }
        context.deleteDatabase(DB_NAME)
    }

    /** Opens `umamit.db` as the app does, with the restore check, and the repositories on top. */
    private fun open(): TestDatabase {
        val db = umamiDatabaseBuilder(context, DB_NAME, marker).allowMainThreadQueries().build()
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
        val stored = first.activeBrew.read()
        assertThat(marker.exists()).isTrue()
        first.closeDb()

        val restarted = open()

        assertThat(restarted.activeBrew.read()).isEqualTo(stored)
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

        assertThat(newPhone.activeBrew.read()).isNull()
        assertThat(marker.exists()).isTrue()
        // Only the timer state goes; the personal data stays.
        assertThat(newPhone.sessions.getSession(SESSION_ID)).isNotNull()
        assertThat(newPhone.teas.getTea("t1")).isNotNull()

        newPhone.teas.saveTea(tea("t1"))
        TimerDriver(newPhone).send(startEvent(infusion(60), id = "s2"))
        val newTimer = newPhone.activeBrew.read()
        newPhone.closeDb()

        assertThat(open().activeBrew.read()).isEqualTo(newTimer)
    }

    private companion object {
        const val DB_NAME = "restore-test.db"
    }
}
