package com.umamit.core.backup.restore

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.archive.Ids
import com.umamit.core.backup.archive.tea
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.umamiDatabaseBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * How a commit is recognised (the D-130 build note of #336): `PRAGMA application_id`, set inside a Room transaction,
 * commits and rolls back with the rows, and the start gate reads it on its own connection after Room is closed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommitTokenTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbFile by lazy { File(folder.root, "umamit.db") }

    private fun open(): UmamiDatabase = umamiDatabaseBuilder(
        context,
        dbFile.path,
        File(folder.root, "marker"),
        File(folder.root, "photos"),
    ).build()

    @Test
    fun `the token rolls back with the rows and commits with them`() = runBlocking {
        val db = open()
        assertThat(CommitToken.read(db.openHelper.writableDatabase)).isEqualTo(0)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                db.withTransaction {
                    db.teaDao().upsert(tea(Ids.TEA, "Abgebrochen"))
                    CommitToken.set(db.openHelper.writableDatabase, 7)
                    error("before the commit")
                }
            }
        }
        assertThat(CommitToken.read(db.openHelper.writableDatabase)).isEqualTo(0)
        assertThat(db.teaDao().get(Ids.TEA)).isNull()

        db.withTransaction {
            db.teaDao().upsert(tea(Ids.TEA, "Eingespielt"))
            CommitToken.set(db.openHelper.writableDatabase, 8)
        }
        db.close()

        assertThat(CommitToken.readFile(dbFile)).isEqualTo(8)
        val reopened = open()
        assertThat(reopened.teaDao().get(Ids.TEA)?.name).isEqualTo("Eingespielt")
        assertThat(CommitToken.read(reopened.openHelper.writableDatabase)).isEqualTo(8)
        reopened.close()
    }

    @Test
    fun `the next token always differs from the current one and is never 0`() {
        assertThat(CommitToken.next(0)).isEqualTo(1)
        assertThat(CommitToken.next(41)).isEqualTo(42)
        assertThat(CommitToken.next(Int.MAX_VALUE)).isEqualTo(1)
        assertThat(CommitToken.next(-5)).isEqualTo(1)
        assertThat(CommitToken.readFile(File(folder.root, "none.db"))).isNull()
    }
}
