package com.umamit.core.data

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
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
 * A vessel's one photo (WARE-01, SCR-14, #187) on the database in memory: set processes the picked image into
 * `photos/`, a second set replaces the first (its file goes after the commit), remove clears it. The picked
 * images are synthetic files reached by `file:` URIs (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VesselPhotosTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val data = TestDatabase()
    private val files by lazy { PhotoFiles(folder.newFolder("photos")) }
    private val photos by lazy {
        RoomVesselPhotos(data.db, data.time, files, ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = data.close()

    private fun picked(bytes: ByteArray): String {
        val file = File.createTempFile("picked", ".img", folder.root).apply { writeBytes(bytes) }
        return Uri.fromFile(file).toString()
    }

    private fun storedFiles(): List<String> = files.dir.listFiles().orEmpty().map { it.name }

    private suspend fun vessel(id: String = "v1") = data.vessels.saveVessel(Vessel(id, "Kyusu", VesselType.KYUSU, 250))

    @Test
    fun `a photo is stored as a 2048 px JPEG and becomes the vessel's cover`() = runTest {
        vessel()

        val result = photos.set("v1", picked(syntheticJpeg(width = 4000, height = 3000)))

        val id = (result as SetVesselPhotoResult.Set).photoId
        assertThat(data.vessels.getVessel("v1")!!.coverPhotoId).isEqualTo(id)
        assertThat(decodedSize(files.file(id).readBytes())).isEqualTo(2048 to 1536)
        assertThat(storedFiles()).containsExactly("$id.jpg")
    }

    @Test
    fun `a second photo replaces the first and deletes its file`() = runTest {
        vessel()
        val first = (photos.set("v1", picked(syntheticJpeg(400, 300))) as SetVesselPhotoResult.Set).photoId

        val second = (photos.set("v1", picked(syntheticJpeg(300, 400))) as SetVesselPhotoResult.Set).photoId

        assertThat(second).isNotEqualTo(first)
        assertThat(data.vessels.getVessel("v1")!!.coverPhotoId).isEqualTo(second)
        assertThat(data.db.vesselDao().photoIds("v1")).containsExactly(second)
        assertThat(storedFiles()).containsExactly("$second.jpg")
    }

    @Test
    fun `another vessel's photo is left alone`() = runTest {
        vessel("v1")
        vessel("v2")
        photos.set("v1", picked(syntheticJpeg(400, 300)))
        val other = (photos.set("v2", picked(syntheticJpeg(400, 300))) as SetVesselPhotoResult.Set).photoId

        photos.remove("v1")

        assertThat(data.vessels.getVessel("v2")!!.coverPhotoId).isEqualTo(other)
        assertThat(storedFiles()).containsExactly("$other.jpg")
    }

    @Test
    fun `remove clears the cover, the row and the file`() = runTest {
        vessel()
        photos.set("v1", picked(syntheticJpeg(400, 300)))

        assertThat(photos.remove("v1")).isTrue()

        assertThat(data.vessels.getVessel("v1")!!.coverPhotoId).isNull()
        assertThat(data.db.vesselDao().photoIds("v1")).isEmpty()
        assertThat(storedFiles()).isEmpty()
    }

    @Test
    fun `removing without a photo is fine`() = runTest {
        vessel()

        assertThat(photos.remove("v1")).isTrue()
    }

    @Test
    fun `an unreadable image changes nothing and keeps the old photo`() = runTest {
        vessel()
        val first = (photos.set("v1", picked(syntheticJpeg(400, 300))) as SetVesselPhotoResult.Set).photoId
        val garbage = picked("<html>not an image</html>".encodeToByteArray())

        assertThat(photos.set("v1", garbage)).isEqualTo(SetVesselPhotoResult.Unreadable)

        assertThat(data.vessels.getVessel("v1")!!.coverPhotoId).isEqualTo(first)
        assertThat(storedFiles()).containsExactly("$first.jpg")
    }

    @Test
    fun `a photo for a vessel that is gone leaves no file`() = runTest {
        assertThat(photos.set("gone", picked(syntheticJpeg(200, 150)))).isEqualTo(SetVesselPhotoResult.Failed)
        assertThat(storedFiles()).isEmpty()
    }
}
