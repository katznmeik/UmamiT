package com.umamit.core.backup.restore

import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.archive.BackupReadResult
import com.umamit.core.backup.archive.ReadBackup
import com.umamit.core.backup.archive.settingsJson
import com.umamit.core.database.umamiDatabaseBuilder
import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * *Sicherung einspielen* (BAK-02, 05 §8 „Replace", D-130 Q5, items 21, 22): every table, setting and photo replaced,
 * the safety copy equal to the data before, refusals that change nothing, and no restore-detection sweep.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RestoreBackupTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val h by lazy { ImportHarness(folder.root).apply { seedOld() } }

    @After
    fun tearDown() = h.close()

    private fun restore(timer: TimerActivity = NO_TIMER): RestoreResult = runBlocking {
        h.newContent().use { h.restore.restore(it, timer) }
    }

    /** The safety copy's rows and settings, read back with the reader. */
    private fun safetyCopyWorld(): World = runBlocking {
        val staging = File(folder.root, "check-copy")
        val content = (ReadBackup().read(h.paths.safetyCopy, staging) as BackupReadResult.Valid).content
        content.use { copy ->
            World(
                rows = copy.rows,
                photos = copy.photosDir.listFiles().orEmpty().associate { it.name to it.readBytes().toHex() },
                settings = copy.settings,
            )
        }
    }

    @Test
    fun `an import replaces every table, setting and photo and keeps a safety copy of the data before`() {
        val before = h.world()
        val markerTime = h.installMarker.lastModified()

        val result = restore()

        assertThat(result).isInstanceOf(RestoreResult.Done::class.java)
        result as RestoreResult.Done
        assertThat(result.finishedAtNextStart).isFalse()
        assertThat(result.preview.sessions).isEqualTo(2)
        assertThat(result.preview.teas).isEqualTo(2)
        assertThat(h.world()).isEqualTo(h.newWorld())
        // The device-bound keys stay (05 §8, D-130 item 22); the step 5 date is now.
        val stored = h.stored()
        assertThat(stored.onboardingCompleted).isTrue()
        assertThat(stored.lastBackupAt).isEqualTo(1L)
        assertThat(stored.safetyCopyAt).isEqualTo(h.clock.wall)
        // The safety copy is the data before, photos and settings included.
        assertThat(safetyCopyWorld()).isEqualTo(before)
        assertThat(h.leftovers()).isEmpty()
        // No timer row, and the install marker untouched (the device-restore detection does not run).
        assertThat(h.hasActiveBrew()).isFalse()
        assertThat(h.installMarker.exists()).isTrue()
        assertThat(h.installMarker.lastModified()).isEqualTo(markerTime)
    }

    @Test
    fun `after an import the reopened database keeps a row the restore detection would clear`() {
        restore()
        h.activeBrew(TimerSlot.QUICK)

        h.restart()

        // RestoreDetection clears active_brew when it runs (05 §8, D-052); the row is still there, so it did not.
        assertThat(h.hasActiveBrew()).isTrue()
        assertThat(h.world().rows).isEqualTo(h.newWorld().rows)
    }

    @Test
    fun `a second import replaces the safety copy with the data of the first`() {
        restore()
        val afterFirst = h.world()
        h.clock.advanceBy(60_000)

        val second = runBlocking {
            h.newContent(newRows("Zweit"), Color.RED).use { h.restore.restore(it, NO_TIMER) }
        }

        assertThat(second).isInstanceOf(RestoreResult.Done::class.java)
        assertThat(h.world()).isEqualTo(h.newWorld(newRows("Zweit"), Color.RED))
        assertThat(safetyCopyWorld()).isEqualTo(afterFirst)
        assertThat(h.stored().safetyCopyAt).isEqualTo(h.clock.wall)
    }

    @Test
    fun `a failed second import keeps the first copy and its date`() {
        restore()
        val copyBytes = h.paths.safetyCopy.readBytes().toHex()
        val copyAt = h.stored().safetyCopyAt
        val afterFirst = h.world()
        val storedBefore = h.stored()
        h.clock.advanceBy(60_000)
        h.replacer.checkpoints = ImportCheckpoints { if (it == ImportPoint.IN_TRANSACTION) throw InjectedFailure() }

        val second = runBlocking { h.newContent(newRows("Zweit"), Color.RED).use { h.restore.restore(it, NO_TIMER) } }

        assertThat(second).isEqualTo(RestoreResult.Refused(RestoreRefusal.REPLACE_FAILED))
        assertThat(h.world()).isEqualTo(afterFirst)
        assertThat(h.stored()).isEqualTo(storedBefore)
        assertThat(h.paths.safetyCopy.readBytes().toHex()).isEqualTo(copyBytes)
        assertThat(h.stored().safetyCopyAt).isEqualTo(copyAt)
        assertThat(h.leftovers()).isEmpty()
    }

    @Test
    fun `a safety copy that cannot be written refuses the import and keeps the previous copy`() {
        restore()
        val copyBytes = h.paths.safetyCopy.readBytes().toHex()
        val copyAt = h.stored().safetyCopyAt
        val afterFirst = h.world()
        val storedBefore = h.stored()
        h.replacer.checkpoints =
            ImportCheckpoints { if (it == ImportPoint.SAFETY_COPY_WRITTEN) throw InjectedFailure() }

        val second = runBlocking { h.newContent(newRows("Zweit"), Color.RED).use { h.restore.restore(it, NO_TIMER) } }

        assertThat(second).isEqualTo(RestoreResult.Refused(RestoreRefusal.SAFETY_COPY_FAILED))
        assertThat(h.world()).isEqualTo(afterFirst)
        assertThat(h.stored()).isEqualTo(storedBefore)
        assertThat(h.paths.safetyCopy.readBytes().toHex()).isEqualTo(copyBytes)
        assertThat(h.stored().safetyCopyAt).isEqualTo(copyAt)
        assertThat(h.leftovers()).isEmpty()
    }

    @Test
    fun `a running brew, a second brew or a quick timer refuses the import and changes nothing`() {
        for (slot in TimerSlot.entries) {
            h.activeBrew(slot)
            val before = h.world()
            val storedBefore = h.stored()

            assertThat(restore()).isEqualTo(RestoreResult.Refused(RestoreRefusal.TIMER_ACTIVE))

            assertThat(h.world()).isEqualTo(before)
            assertThat(h.stored()).isEqualTo(storedBefore)
            assertThat(h.paths.safetyCopy.exists()).isFalse()
            assertThat(h.stored().safetyCopyAt).isNull()
            assertThat(h.leftovers()).isEmpty()
            runBlocking { h.db.activeBrewDao().delete(slot) }
        }
    }

    @Test
    fun `a timer that runs without a row refuses the import`() {
        val before = h.world()
        val storedBefore = h.stored()

        assertThat(restore { true }).isEqualTo(RestoreResult.Refused(RestoreRefusal.TIMER_ACTIVE))

        assertThat(h.world()).isEqualTo(before)
        assertThat(h.stored()).isEqualTo(storedBefore)
        assertThat(h.paths.safetyCopy.exists()).isFalse()
    }

    @Test
    fun `a quick timer started while the safety copy is written stops the replace in its transaction`() {
        val before = h.world()
        val storedBefore = h.stored()
        h.replacer.checkpoints = ImportCheckpoints {
            if (it == ImportPoint.SAFETY_COPY_WRITTEN) h.activeBrew(TimerSlot.QUICK)
        }

        assertThat(restore()).isEqualTo(RestoreResult.Refused(RestoreRefusal.TIMER_ACTIVE))

        assertThat(h.world()).isEqualTo(before)
        assertThat(h.stored()).isEqualTo(storedBefore)
        assertThat(h.hasActiveBrew()).isTrue()
        assertThat(h.paths.safetyCopy.exists()).isFalse()
        assertThat(h.stored().safetyCopyAt).isNull()
        assertThat(h.leftovers()).isEmpty()
    }

    @Test
    fun `a running catalog refresh refuses the import and changes nothing`() {
        val before = h.world()
        val storedBefore = h.stored()
        h.refresh.running()

        assertThat(restore()).isEqualTo(RestoreResult.Refused(RestoreRefusal.CATALOG_REFRESH))

        assertThat(h.world()).isEqualTo(before)
        assertThat(h.stored()).isEqualTo(storedBefore)
        assertThat(h.paths.safetyCopy.exists()).isFalse()
        assertThat(h.leftovers()).isEmpty()
    }

    @Test
    fun `a step that fails after the commit still reports the import, and the next start finishes it`() {
        h.replacer.checkpoints = ImportCheckpoints { if (it == ImportPoint.SETTINGS_APPLIED) throw InjectedFailure() }

        val result = restore()

        assertThat(result).isInstanceOf(RestoreResult.Done::class.java)
        assertThat((result as RestoreResult.Done).finishedAtNextStart).isTrue()
        assertThat(h.paths.marker.exists()).isTrue()
        assertThat(h.world().rows).isEqualTo(h.newWorld().rows)
        // Until it is finished the copy is not yet this import's: no undo is offered meanwhile.
        assertThat(runBlocking { h.undo.preview() }).isNull()

        h.restart()

        assertThat(h.world()).isEqualTo(h.newWorld())
        assertThat(h.stored().safetyCopyAt).isEqualTo(h.clock.wall)
        assertThat(h.paths.safetyCopy.exists()).isTrue()
        assertThat(h.leftovers()).isEmpty()
        assertThat(runBlocking { h.undo.preview() }).isNotNull()
    }

    @Test
    fun `the device-bound settings stay as they are`() {
        val before = h.stored()

        restore()

        val after = h.stored()
        assertThat(after.onboardingCompleted).isEqualTo(before.onboardingCompleted)
        assertThat(after.seedDataCreated).isEqualTo(before.seedDataCreated)
        assertThat(after.dismissedHints).isEqualTo(before.dismissedHints)
        assertThat(after.lastBackupAt).isEqualTo(before.lastBackupAt)
        assertThat(settingsJson(after)).isEqualTo(h.newWorld().settings)
    }

    @Test
    fun `the restore detection still runs for a database without its install marker`() {
        // The sweep itself is unchanged: a database opened without the marker clears active_brew (D-052).
        h.activeBrew(TimerSlot.QUICK)
        h.close()
        h.installMarker.delete()
        val db = umamiDatabaseBuilder(h.context, h.dbFile.path, h.installMarker, h.photosDir).build()
        try {
            assertThat(runBlocking { db.activeBrewDao().get(TimerSlot.QUICK) }).isNull()
        } finally {
            db.close()
        }
        h.restart(gate = false)
    }
}
