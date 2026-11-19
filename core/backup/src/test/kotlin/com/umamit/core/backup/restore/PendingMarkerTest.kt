package com.umamit.core.backup.restore

import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.archive.Ids
import com.umamit.core.backup.archive.photo
import com.umamit.core.model.PhotoOrigin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * The replace while an earlier one's marker waits, and photo writers beside it (D-133 Q3, Q10, item 13; findings
 * 1–4 of the T4 review): a pending marker's finish keeps a staged import, the late-failure undo is offered from the
 * import's time and refused while the step fails, `photos.old/` is freed once the commit is durable, photo files of
 * rows in the new database are kept, and an import waits for the lock a seed photo download holds. Synthetic (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PendingMarkerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val h by lazy { ImportHarness(folder.root).apply { seedOld() } }

    @After
    fun tearDown() = h.close()

    private val failSettings = ImportCheckpoints { if (it == ImportPoint.SETTINGS_APPLIED) throw InjectedFailure() }

    /** An import whose settings step keeps failing: committed, its marker waits. Returns its time. */
    private fun lateFailure(): Long {
        h.replacer.checkpoints = failSettings
        val at = h.clock.wall
        val result = runBlocking { h.newContent().use { h.restore.restore(it, NO_TIMER) } }
        assertThat((result as RestoreResult.Done).finishedAtNextStart).isTrue()
        assertThat(h.paths.marker.exists()).isTrue()
        return at
    }

    private fun restore(content: com.umamit.core.backup.archive.BackupContent) =
        runBlocking { content.use { h.restore.restore(it, NO_TIMER) } }

    @Test
    fun `after a late failure the undo is offered from the import and refused while the step fails`() {
        val before = h.world()
        val importedAt = lateFailure()
        h.clock.advanceBy(3_600_000)

        assertThat(runBlocking { h.undo.preview() })
            .isEqualTo(UndoPreview(0, 0, importedAt + SAFETY_COPY_VALID_MS))
        assertThat(runBlocking { h.undo.undo(NO_TIMER) })
            .isEqualTo(RestoreResult.Refused(RestoreRefusal.REPLACE_FAILED))
        assertThat(h.world().rows).isEqualTo(h.newWorld().rows)

        // Once the step can finish, the undo goes through: the data before is back.
        h.replacer.checkpoints = ImportCheckpoints {}
        val undone = runBlocking { h.undo.undo(NO_TIMER) }

        assertThat(undone).isInstanceOf(RestoreResult.Done::class.java)
        assertThat(h.world()).isEqualTo(before)
        assertThat(h.paths.safetyCopy.exists()).isFalse()
        assertThat(h.stored().safetyCopyAt).isNull()
        assertThat(h.leftovers()).isEmpty()
    }

    @Test
    fun `a step finished at the next start keeps the import's time as safetyCopyAt`() {
        val importedAt = lateFailure()
        h.clock.advanceBy(2 * 3_600_000)

        h.restart()

        assertThat(h.stored().safetyCopyAt).isEqualTo(importedAt)
        assertThat(runBlocking { h.undo.preview() }?.availableUntil).isEqualTo(importedAt + SAFETY_COPY_VALID_MS)
    }

    @Test
    fun `photos old is freed once the commit is durable, before the settings write`() {
        lateFailure()

        assertThat(h.paths.photosOld.exists()).isFalse()
        assertThat(MarkerFile.read(h.paths)?.durable).isTrue()
    }

    @Test
    fun `a second import over a pending marker keeps every photo of the second file`() {
        lateFailure()
        h.replacer.checkpoints = ImportCheckpoints {}
        val second = newRows("Zweit")
        // Staged while the marker waits, as SCR-26's check stages a picked file; the import finishes the marker first.
        val content = h.newContent(second, Color.RED)

        val result = restore(content)

        assertThat(result).isInstanceOf(RestoreResult.Done::class.java)
        assertThat(h.world()).isEqualTo(h.newWorld(second, Color.RED))
        assertThat(h.leftovers()).isEmpty()
    }

    @Test
    fun `the undo preview finishing a marker leaves a staged import alone`() {
        lateFailure()
        h.replacer.checkpoints = ImportCheckpoints {}
        val content = h.newContent(newRows("Zweit"), Color.RED)

        assertThat(runBlocking { h.undo.preview() }).isNotNull()

        assertThat(h.paths.marker.exists()).isFalse()
        assertThat(content.photosDir.listFiles().orEmpty()).hasLength(Ids.EXPORTED_PHOTOS.size)
        assertThat(restore(content)).isInstanceOf(RestoreResult.Done::class.java)
        assertThat(h.world()).isEqualTo(h.newWorld(newRows("Zweit"), Color.RED))
    }

    @Test
    fun `a staging without one file per photo row is refused and changes nothing`() {
        val before = h.world()
        val storedBefore = h.stored()
        val content = h.newContent()
        File(content.photosDir, "${Ids.PHOTO_OWN}.jpg").delete()

        assertThat(restore(content)).isEqualTo(RestoreResult.Refused(RestoreRefusal.REPLACE_FAILED))

        assertThat(h.world()).isEqualTo(before)
        assertThat(h.stored()).isEqualTo(storedBefore)
        assertThat(h.leftovers()).isEmpty()
    }

    @Test
    fun `a photo written during the replace whose row is in the new database is kept`() {
        // As the seed download would without the lock: its file in the old photos/, its row in the new database.
        h.replacer.checkpoints = ImportCheckpoints { if (it == ImportPoint.COMMITTED) writePhoto("late-photo") }

        assertThat(restore(h.newContent())).isInstanceOf(RestoreResult.Done::class.java)

        assertThat(File(h.photosDir, "late-photo.jpg").readBytes()).isEqualTo(LATE_BYTES)
        assertThat(h.paths.photosOld.exists()).isFalse()
        // A file of the old set without a row in the new database still goes.
        assertThat(h.photosDir.list().orEmpty().toSet())
            .isEqualTo(h.newWorld().photos.keys + "late-photo.jpg")
    }

    @Test
    fun `a photos folder made again after the move out joins the old set and its row's file is kept`() {
        var once = true
        h.replacer.checkpoints = ImportCheckpoints {
            if (it == ImportPoint.PHOTOS_MOVED_OUT && once) {
                once = false
                // A photo writer while the folder is moved out: a new photos/ blocks the second rename.
                h.photosDir.mkdirs()
                writePhoto("while-moved")
            }
        }

        val result = restore(h.newContent())

        assertThat((result as RestoreResult.Done).finishedAtNextStart).isFalse()
        assertThat(File(h.photosDir, "while-moved.jpg").readBytes()).isEqualTo(LATE_BYTES)
        assertThat(h.paths.marker.exists()).isFalse()
        assertThat(h.leftovers()).isEmpty()
    }

    @Test
    fun `an import waits for the lock a seed photo download holds`() = runBlocking {
        val before = h.world()
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val download = launch(Dispatchers.IO) {
            h.lock.withLock {
                held.complete(Unit)
                release.await()
            }
        }
        held.await()
        val content = h.newContent()

        val importing = async(Dispatchers.IO) { content.use { h.restore.restore(it, NO_TIMER) } }
        delay(WAIT_MS)

        assertThat(importing.isCompleted).isFalse()
        assertThat(h.world()).isEqualTo(before)
        release.complete(Unit)
        assertThat(importing.await()).isInstanceOf(RestoreResult.Done::class.java)
        download.join()
        assertThat(h.world()).isEqualTo(h.newWorld())
    }

    /** A photo file in `photos/` and its row (on a tea of the new data) in the database, as a photo writer adds one. */
    private fun writePhoto(id: String) = runBlocking {
        File(h.photosDir, "$id.jpg").writeBytes(LATE_BYTES)
        h.db.teaDao().upsertPhoto(photo(id, PhotoOrigin.CATALOG_COPY, teaId = Ids.TEA))
    }

    private companion object {
        val LATE_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 42)
        const val WAIT_MS = 300L
    }
}
