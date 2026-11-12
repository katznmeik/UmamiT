package com.umamit.core.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.database.entity.PhotoEntity
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.timer.engine.StepRecord
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * A session's own photos (JRN-07, 05 §3.9 „Session photo writes", D-112, #229) on the database in memory: added
 * after the others up to 6, only to a `COMPLETED` session, the file first and gone again when the row isn't;
 * deleted row first, file after the commit; deleting the session takes rows and files. The picked images are
 * synthetic files reached by `file:` URIs (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionPhotosTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val data = TestDatabase()
    private val files by lazy { PhotoFiles(folder.newFolder("photos")) }
    private val photos by lazy {
        RoomSessionPhotos(data.db, data.time, files, ApplicationProvider.getApplicationContext<Context>())
    }

    /** The URIs a [loading] store's image loader was asked for. */
    private val loaded = mutableListOf<String>()

    /** A store whose loader records each read, runs [whileReading] and then returns a small processed photo. */
    private fun loading(whileReading: suspend () -> Unit = {}) = RoomSessionPhotos(data.db, data.time, files) { uri ->
        loaded += uri
        whileReading()
        PhotoTranscoder.transcodePicked(syntheticJpeg(width = 200, height = 150))
    }

    @Before
    fun setUp() = runTest {
        data.teas.saveTea(tea("t1"))
        completed("done")
        completed("other")
        data.sessions.startSession(SessionStart("running", snapshotOf(), listOf(infusion(60)), 10, 5_000, "UTC"))
    }

    @After
    fun tearDown() = data.close()

    private suspend fun completed(id: String) {
        data.sessions.startSession(SessionStart(id, snapshotOf(), listOf(infusion(60)), 10, 2_000, "UTC"))
        data.sessions.recordStep(id, 0, StepRecord(60, 61, 0, 1, 2_000, 63_000, false, false))
        data.sessions.completeSession(id, autoFinished = false)
    }

    private fun picked(bytes: ByteArray = syntheticJpeg(width = 400, height = 300)): String {
        val file = File.createTempFile("picked", ".img", folder.root).apply { writeBytes(bytes) }
        return Uri.fromFile(file).toString()
    }

    private fun storedFiles(): List<String> = files.dir.listFiles().orEmpty().map { it.name }

    private suspend fun added(sessionId: String = "done"): String =
        (photos.add(sessionId, picked()) as AddPhotoResult.Added).photoId

    /** A sixth row written behind the store's back, as a second add would. */
    private suspend fun insertRow(sessionId: String, id: String) = data.db.sessionPhotoDao().insert(
        PhotoEntity(id, null, sessionId, null, 99, 10, 10, 10, PhotoOrigin.OWN, 1, 1),
    )

    @Test
    fun `a photo is stored as a processed JPEG owned by the session, after the others`() = runTest {
        val first = photos.add("done", picked(syntheticJpeg(width = 4000, height = 3000))) as AddPhotoResult.Added
        val second = photos.add("done", picked()) as AddPhotoResult.Added

        val stored = photos.observePhotos("done").first()
        assertThat(stored.map { it.id }).containsExactly(first.photoId, second.photoId).inOrder()
        assertThat(stored.map { it.position }).containsExactly(0, 1).inOrder()
        assertThat(stored.first().owner).isEqualTo(PhotoOwner.OfSession("done"))
        assertThat(stored.first().origin).isEqualTo(PhotoOrigin.OWN)
        assertThat(stored.first().widthPx to stored.first().heightPx).isEqualTo(2048 to 1536)
        assertThat(decodedSize(files.file(first.photoId).readBytes())).isEqualTo(2048 to 1536)
        assertThat(photos.observePhotos("other").first()).isEmpty()
    }

    @Test
    fun `the 7th photo is refused before the image is read and leaves no file`() = runTest {
        repeat(Photo.MAX_PER_SESSION) { added() }
        val before = storedFiles()
        val store = loading()

        assertThat(store.add("done", "content://picked/7")).isEqualTo(AddPhotoResult.LimitReached)

        assertThat(loaded).isEmpty()
        assertThat(photos.observePhotos("done").first()).hasSize(Photo.MAX_PER_SESSION)
        assertThat(storedFiles()).containsExactlyElementsIn(before)
    }

    @Test
    fun `the 7th photo is refused again in the transaction and its file is removed`() = runTest {
        repeat(Photo.MAX_PER_SESSION - 1) { added() }
        val before = storedFiles()
        val store = loading { insertRow("done", "sixth") }

        assertThat(store.add("done", "content://picked/7")).isEqualTo(AddPhotoResult.LimitReached)

        assertThat(loaded).containsExactly("content://picked/7")
        assertThat(photos.observePhotos("done").first()).hasSize(Photo.MAX_PER_SESSION)
        assertThat(storedFiles()).containsExactlyElementsIn(before)
    }

    @Test
    fun `a session in progress or gone takes no photo and its image is not read`() = runTest {
        val store = loading()

        assertThat(store.add("running", "content://picked/1")).isEqualTo(AddPhotoResult.Failed)
        assertThat(store.add("gone", "content://picked/2")).isEqualTo(AddPhotoResult.Failed)

        assertThat(loaded).isEmpty()
        assertThat(storedFiles()).isEmpty()
    }

    @Test
    fun `an unreadable image stores nothing`() = runTest {
        val garbage = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(200) { 7 } +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val missing = Uri.fromFile(File(folder.root, "gone.jpg")).toString()

        listOf(picked(garbage), picked("<html>not an image</html>".encodeToByteArray()), missing).forEach { uri ->
            assertThat(photos.add("done", uri)).isEqualTo(AddPhotoResult.Unreadable)
        }
        assertThat(photos.observePhotos("done").first()).isEmpty()
        assertThat(storedFiles()).isEmpty()
    }

    @Test
    fun `a row that is not written takes its file with it`() = runTest {
        data.db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse BEFORE INSERT ON photo BEGIN SELECT RAISE(ABORT, 'refused'); END",
        )

        assertThat(photos.add("done", picked())).isEqualTo(AddPhotoResult.Failed)

        assertThat(storedFiles()).isEmpty()
    }

    @Test
    fun `deleting a photo removes its row and its file and keeps the others`() = runTest {
        val first = added()
        val second = added()

        assertThat(photos.delete("done", first)).isTrue()

        assertThat(photos.observePhotos("done").first().map { it.id }).containsExactly(second)
        assertThat(files.file(first).exists()).isFalse()
        assertThat(files.file(second).exists()).isTrue()
    }

    @Test
    fun `a photo of another session is not deleted`() = runTest {
        val theirs = added("other")

        assertThat(photos.delete("done", theirs)).isFalse()

        assertThat(photos.observePhotos("other").first().map { it.id }).containsExactly(theirs)
        assertThat(files.file(theirs).exists()).isTrue()
    }

    @Test
    fun `a failed delete keeps the row and the file`() = runTest {
        val only = added()
        data.db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse BEFORE DELETE ON photo BEGIN SELECT RAISE(ABORT, 'refused'); END",
        )

        assertThat(photos.delete("done", only)).isFalse()

        assertThat(photos.observePhotos("done").first().map { it.id }).containsExactly(only)
        assertThat(files.file(only).exists()).isTrue()
    }

    @Test
    fun `deleting the session removes its photo rows and files and leaves the others`() = runTest {
        val mine = listOf(added(), added())
        val theirs = added("other")

        RoomJournalRepository(data.db, files).deleteSession("done")

        assertThat(data.sessions.getSession("done")).isNull()
        assertThat(photos.observePhotos("done").first()).isEmpty()
        mine.forEach { assertThat(files.file(it).exists()).isFalse() }
        assertThat(files.file(theirs).exists()).isTrue()
        assertThat(photos.observePhotos("other").first().map { it.id }).containsExactly(theirs)
    }

    @Test
    fun `a caller cancelled during the delete still leaves no file of the deleted session`() = runTest {
        val mine = listOf(added(), added())

        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            RoomJournalRepository(data.db, files).deleteSession("done")
        }
        caller.cancel()
        caller.join()

        assertThat(data.sessions.getSession("done")).isNull()
        mine.forEach { assertThat(files.file(it).exists()).isFalse() }
    }

    @Test
    fun `a session that is not deleted keeps its photo files`() = runTest {
        insertRow("running", "running-photo")
        files.write("running-photo", byteArrayOf(1))

        RoomJournalRepository(data.db, files).deleteSession("running")

        assertThat(files.file("running-photo").exists()).isTrue()
    }
}
