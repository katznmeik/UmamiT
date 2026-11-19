package com.umamit.core.backup.restore

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.archive.T0
import com.umamit.core.backup.archive.insertFixture
import com.umamit.core.backup.archive.session
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.SessionStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * „N neue Sessions" (SCR-26), the undo's count and the reminder's ≥ 20 (D-133 Q9): completed and manual sessions
 * saved after a moment (`created_at`), whatever date they carry; the observed count follows a new session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionCountTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val db = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), UmamiDatabase::class.java)
        .build()
    private val since = T0 + 100_000

    @After
    fun tearDown() = db.close()

    @Test
    fun `sessions count by when they were saved, not by their date`() = runBlocking {
        insertFixture(db, File(folder.root, "photos"))
        val sessions = db.sessionDao()
        // Backdated before [since] but saved after it: counts (it is not in a backup made at [since]).
        sessions.insert(session("backdated", SessionStatus.MANUAL, T0).copy(createdAt = since + 1))
        // Dated after [since] but saved before it: in that backup already, so it does not count.
        sessions.insert(session("predated", SessionStatus.COMPLETED, since + 5).copy(createdAt = since - 1))
        sessions.insert(session("running", SessionStatus.IN_PROGRESS, since + 6).copy(createdAt = since + 6))

        assertThat(db.restoreDao().sessionsCreatedAfter(since)).isEqualTo(1)
    }

    @Test
    fun `the observed count follows a session saved while it is watched`() = runBlocking {
        insertFixture(db, File(folder.root, "photos"))
        val counts = db.restoreDao().observeSessionsCreatedAfter(since)
        assertThat(counts.first()).isEqualTo(0)

        val next = withTimeout(TIMEOUT_MS) {
            coroutineScope {
                val second = async { counts.drop(1).first() }
                // Room delivers the first value before the insert's invalidation.
                delay(SETTLE_MS)
                db.sessionDao().insert(session("new", SessionStatus.COMPLETED, since + 10))
                second.await()
            }
        }

        assertThat(next).isEqualTo(1)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 200L
    }
}
