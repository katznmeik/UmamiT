package com.umamit.app

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.restore.ImportRecovery
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The start gate of the replacing import (05 §8 crash safety, BAK-02, the D-130 build note of #336) as the app wires
 * it: [ImportRecovery] from the app's graph, on the app's own folders. A marker whose commit token `umamit.db` does not
 * hold (a crash before the commit) is rolled back: the staged photos and the unfinished safety copy go, `photos/`
 * stays.
 * The decisions themselves are tested in `core:backup` (`ImportCrashTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImportGateTest {
    private val app: UmamitApplication = ApplicationProvider.getApplicationContext()

    @Test
    fun theGateRollsBackAnImportThatDidNotCommit() {
        // The app's own start-up housekeeping finished first, so it never cleans the files laid out below (#349).
        runBlocking { app.importHousekeeping?.join() }
        val photos = File(app.filesDir, "photos").apply { mkdirs() }
        val ownPhoto = File(photos, "own.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val staged = File(app.filesDir, "photos.new").apply { mkdirs() }
        File(staged, "imported.jpg").writeBytes(byteArrayOf(4, 5, 6))
        val safetyNew = File(app.noBackupFilesDir, "safety/umamit-safety.new").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(7))
        }
        val marker = File(app.noBackupFilesDir, "import/marker.json").apply {
            parentFile!!.mkdirs()
            writeText("""{"v":1,"kind":"IMPORT","token":424242,"phase":"PREPARED"}""")
        }
        assertThat(ImportRecovery.isNeeded(app)).isTrue()

        assertThat(app.importRecovery.get().recoverAtStart()).isTrue()

        assertThat(ImportRecovery.isNeeded(app)).isFalse()
        assertThat(marker.exists()).isFalse()
        assertThat(staged.exists()).isFalse()
        assertThat(safetyNew.exists()).isFalse()
        assertThat(ownPhoto.readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
    }
}
