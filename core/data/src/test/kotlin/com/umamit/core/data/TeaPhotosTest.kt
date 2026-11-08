package com.umamit.core.data

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
 * Adding the user's own photos to a tea (TEA-05, SCR-28, #183) on the database in memory: a picked image is
 * processed into `photos/`, at most 10 per tea, the first becomes the cover, an unreadable one adds nothing.
 * The picked images are synthetic files reached by `file:` URIs, as the picker's content URIs are (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TeaPhotosTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val data = TestDatabase()
    private val files by lazy { PhotoFiles(folder.newFolder("photos")) }
    private val photos by lazy {
        RoomTeaPhotos(data.db, data.time, files, ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = data.close()

    private fun picked(bytes: ByteArray): String {
        val file = File.createTempFile("picked", ".img", folder.root).apply { writeBytes(bytes) }
        return Uri.fromFile(file).toString()
    }

    private fun storedFiles(): List<String> = files.dir.listFiles().orEmpty().map { it.name }

    @Test
    fun `the first photo is stored as a 2048 px JPEG and becomes the cover`() = runTest {
        data.teas.saveTea(tea("t1"))

        val result = photos.add("t1", picked(syntheticJpeg(width = 4000, height = 3000)))

        val id = (result as AddPhotoResult.Added).photoId
        val stored = photos.observePhotos("t1").first().single()
        assertThat(stored.id).isEqualTo(id)
        assertThat(stored.owner).isEqualTo(PhotoOwner.OfTea("t1"))
        assertThat(stored.origin).isEqualTo(PhotoOrigin.OWN)
        assertThat(stored.widthPx to stored.heightPx).isEqualTo(2048 to 1536)
        assertThat(stored.byteSize).isEqualTo(files.file(id).length())
        assertThat(decodedSize(files.file(id).readBytes())).isEqualTo(2048 to 1536)
        assertThat(data.teas.observeTea("t1").first()!!.coverPhoto?.id).isEqualTo(id)
    }

    @Test
    fun `a later photo goes after the others and leaves the cover`() = runTest {
        data.teas.saveTea(tea("t1"))
        data.teas.savePhoto(Photo("shop", PhotoOwner.OfTea("t1"), PhotoOrigin.CATALOG_COPY, 10, 10, 100))
        data.teas.setCoverPhoto("t1", "shop")

        val second = photos.add("t1", picked(syntheticJpeg(width = 400, height = 300))) as AddPhotoResult.Added
        val third = photos.add("t1", picked(syntheticJpeg(width = 300, height = 400))) as AddPhotoResult.Added

        assertThat(photos.observePhotos("t1").first().map { it.id })
            .containsExactly("shop", second.photoId, third.photoId).inOrder()
        assertThat(photos.observePhotos("t1").first().map { it.position }).containsExactly(0, 1, 2).inOrder()
        assertThat(data.teas.observeTea("t1").first()!!.coverPhoto?.id).isEqualTo("shop")
    }

    @Test
    fun `up to 10 photos are added and the 11th is refused without a file`() = runTest {
        data.teas.saveTea(tea("t1"))
        val image = syntheticJpeg(width = 200, height = 150)

        repeat(Photo.MAX_PER_TEA) {
            assertThat(photos.add("t1", picked(image))).isInstanceOf(AddPhotoResult.Added::class.java)
        }
        val filesBefore = storedFiles()

        assertThat(photos.add("t1", picked(image))).isEqualTo(AddPhotoResult.LimitReached)
        assertThat(photos.observePhotos("t1").first()).hasSize(10)
        assertThat(storedFiles()).containsExactlyElementsIn(filesBefore)
    }

    @Test
    fun `an unreadable image adds nothing`() = runTest {
        data.teas.saveTea(tea("t1"))
        val garbage = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(200) { 7 } +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val missing = Uri.fromFile(File(folder.root, "gone.jpg")).toString()

        listOf(picked(garbage), picked("<html>not an image</html>".encodeToByteArray()), missing).forEach { uri ->
            assertThat(photos.add("t1", uri)).isEqualTo(AddPhotoResult.Unreadable)
        }
        assertThat(photos.observePhotos("t1").first()).isEmpty()
        assertThat(data.teas.getTea("t1")!!.coverPhotoId).isNull()
        assertThat(storedFiles()).isEmpty()
    }

    @Test
    fun `a photo for a tea that is gone leaves no file`() = runTest {
        assertThat(
            photos.add("gone", picked(syntheticJpeg(width = 200, height = 150))),
        ).isEqualTo(AddPhotoResult.Failed)
        assertThat(storedFiles()).isEmpty()
    }

    @Test
    fun `the photo is turned upright by its EXIF orientation`() = runTest {
        data.teas.saveTea(tea("t1"))

        val id = (photos.add("t1", picked(withOrientation(syntheticJpeg(400, 200), 6))) as AddPhotoResult.Added).photoId

        assertThat(decodedSize(files.file(id).readBytes())).isEqualTo(200 to 400)
    }

    private suspend fun added(teaId: String = "t1"): String =
        (photos.add(teaId, picked(syntheticJpeg(200, 150))) as AddPhotoResult.Added).photoId

    private suspend fun coverId(teaId: String = "t1"): String? = data.teas.getTea(teaId)!!.coverPhotoId

    @Test
    fun `Als Titelbild makes a photo the cover and keeps the order`() = runTest {
        data.teas.saveTea(tea("t1"))
        val first = added()
        val second = added()

        assertThat(photos.setCover("t1", second)).isTrue()

        assertThat(coverId()).isEqualTo(second)
        assertThat(data.teas.observeTea("t1").first()!!.coverPhoto?.id).isEqualTo(second)
        assertThat(photos.observePhotos("t1").first().map { it.id }).containsExactly(first, second).inOrder()
    }

    @Test
    fun `Als Titelbild refuses a photo of another tea`() = runTest {
        data.teas.saveTea(tea("t1"))
        data.teas.saveTea(tea("t2"))
        val own = added("t1")
        val foreign = added("t2")

        assertThat(photos.setCover("t1", foreign)).isFalse()

        assertThat(coverId("t1")).isEqualTo(own)
    }

    @Test
    fun `deleting a photo removes its row and its file and leaves the cover`() = runTest {
        data.teas.saveTea(tea("t1"))
        val first = added()
        val second = added()

        assertThat(photos.delete("t1", second)).isTrue()

        assertThat(photos.observePhotos("t1").first().map { it.id }).containsExactly(first)
        assertThat(files.file(second).exists()).isFalse()
        assertThat(files.file(first).exists()).isTrue()
        assertThat(coverId()).isEqualTo(first)
    }

    @Test
    fun `deleting the cover makes the next photo by position the cover`() = runTest {
        data.teas.saveTea(tea("t1"))
        val first = added()
        val second = added()
        val third = added()
        photos.setCover("t1", second)

        photos.delete("t1", second)

        assertThat(coverId()).isEqualTo(third)
        photos.delete("t1", third)
        // Nothing after the last one: the first by position.
        assertThat(coverId()).isEqualTo(first)
    }

    @Test
    fun `deleting the last photo leaves the tea without a cover`() = runTest {
        data.teas.saveTea(tea("t1"))
        val only = added()

        assertThat(photos.delete("t1", only)).isTrue()

        assertThat(photos.observePhotos("t1").first()).isEmpty()
        assertThat(coverId()).isNull()
        assertThat(storedFiles()).isEmpty()
    }

    @Test
    fun `a catalog tea's shop photo can lose the cover and be deleted`() = runTest {
        data.teas.saveTea(tea("t1"))
        data.teas.savePhoto(Photo("shop", PhotoOwner.OfTea("t1"), PhotoOrigin.CATALOG_COPY, 10, 10, 4))
        files.write("shop", byteArrayOf(1, 2, 3, 4))
        data.teas.setCoverPhoto("t1", "shop")
        val own = added()

        assertThat(photos.setCover("t1", own)).isTrue()
        assertThat(coverId()).isEqualTo(own)
        photos.setCover("t1", "shop")
        assertThat(photos.delete("t1", "shop")).isTrue()

        assertThat(coverId()).isEqualTo(own)
        assertThat(files.file("shop").exists()).isFalse()
        assertThat(photos.observePhotos("t1").first().map { it.id }).containsExactly(own)
    }

    @Test
    fun `a photo of another tea is not deleted`() = runTest {
        data.teas.saveTea(tea("t1"))
        data.teas.saveTea(tea("t2"))
        val foreign = added("t2")

        assertThat(photos.delete("t1", foreign)).isFalse()

        assertThat(photos.observePhotos("t2").first().map { it.id }).containsExactly(foreign)
        assertThat(files.file(foreign).exists()).isTrue()
    }

    @Test
    fun `a failed delete keeps the file`() = runTest {
        data.teas.saveTea(tea("t1"))
        val only = added()
        data.db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse BEFORE DELETE ON photo BEGIN SELECT RAISE(ABORT, 'refused'); END",
        )

        assertThat(photos.delete("t1", only)).isFalse()

        assertThat(files.file(only).exists()).isTrue()
        assertThat(coverId()).isEqualTo(only)
    }

    @Test
    fun `deleting the tea still removes its own photo files`() = runTest {
        data.teas.saveTea(tea("t1"))
        val id = (photos.add("t1", picked(syntheticJpeg(200, 150))) as AddPhotoResult.Added).photoId

        RoomTeaArchive(data.db, data.time, files).delete("t1")

        assertThat(files.file(id).exists()).isFalse()
    }
}
