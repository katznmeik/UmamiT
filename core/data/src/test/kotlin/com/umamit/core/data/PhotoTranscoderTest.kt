package com.umamit.core.data

import android.graphics.BitmapFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Re-encoding downloaded images to a target size and format (#63; 05 §3.9, 06 §5.2, CAT-08,
 * D-050, D-073, D-074). The sources are synthetic JPEGs encoded on the spot (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoTranscoderTest {
    @Test
    fun catalogPhotoIsAtMost1200PxWebPWithAThumbnailKeepingTheAspectRatio() {
        val photo = checkNotNull(PhotoTranscoder.transcodeForCatalog(syntheticJpeg(width = 3000, height = 2000)))

        assertThat(photo.full.width to photo.full.height).isEqualTo(1200 to 800)
        assertThat(photo.full.format).isEqualTo(PhotoFormat.WEBP)
        assertThat(mimeType(photo.full.bytes)).isEqualTo("image/webp")
        assertThat(decodedSize(photo.full.bytes)).isEqualTo(1200 to 800)

        assertThat(photo.thumbnail.width to photo.thumbnail.height).isEqualTo(384 to 256)
        assertThat(photo.thumbnail.format).isEqualTo(PhotoFormat.WEBP)
        assertThat(mimeType(photo.thumbnail.bytes)).isEqualTo("image/webp")
        assertThat(decodedSize(photo.thumbnail.bytes)).isEqualTo(384 to 256)
    }

    @Test
    fun portraitAndSquarePhotosAreCappedOnTheirLongSideAndThumbnailedOnTheirShortSide() {
        val portrait = checkNotNull(PhotoTranscoder.transcodeForCatalog(syntheticJpeg(width = 1000, height = 2500)))
        assertThat(portrait.full.width to portrait.full.height).isEqualTo(480 to 1200)
        assertThat(portrait.thumbnail.width to portrait.thumbnail.height).isEqualTo(256 to 640)

        val square = checkNotNull(PhotoTranscoder.transcodeForCatalog(syntheticJpeg(width = 1600, height = 1600)))
        assertThat(square.full.width to square.full.height).isEqualTo(1200 to 1200)
        assertThat(square.thumbnail.width to square.thumbnail.height).isEqualTo(256 to 256)
    }

    @Test
    fun smallPhotosAreNeverUpscaled() {
        val photo = checkNotNull(PhotoTranscoder.transcodeForCatalog(syntheticJpeg(width = 200, height = 150)))

        assertThat(photo.full.width to photo.full.height).isEqualTo(200 to 150)
        assertThat(photo.thumbnail.width to photo.thumbnail.height).isEqualTo(200 to 150)
        assertThat(decodedSize(photo.full.bytes)).isEqualTo(200 to 150)
    }

    @Test
    fun theThumbnailIsNeverLargerThanThePhoto() {
        val panorama = checkNotNull(PhotoTranscoder.transcodeForCatalog(syntheticJpeg(width = 6000, height = 200)))

        assertThat(panorama.full.width to panorama.full.height).isEqualTo(1200 to 40)
        assertThat(panorama.thumbnail.width to panorama.thumbnail.height).isEqualTo(1200 to 40)
    }

    @Test
    fun catalogPhotosCarryNoMetadata() {
        val photo = checkNotNull(
            PhotoTranscoder.transcodeForCatalog(withExifSegment(syntheticJpeg(width = 1600, height = 1200))),
        )

        listOf(photo.full, photo.thumbnail).forEach { encoded ->
            val text = encoded.bytes.decodeToString(throwOnInvalidSequence = false)
            assertThat(text).doesNotContain("Exif")
            assertThat(text).doesNotContain("synthetic-metadata")
        }
    }

    @Test
    fun theCoverTargetStaysA2048PxJpeg() {
        val cover = checkNotNull(PhotoTranscoder.transcode(syntheticJpeg(width = 3000, height = 2000)))

        assertThat(cover.width to cover.height).isEqualTo(2048 to 1365)
        assertThat(cover.format).isEqualTo(PhotoFormat.JPEG)
        assertThat(mimeType(cover.bytes)).isEqualTo("image/jpeg")
    }

    @Test
    fun truncatedOversizedOrUndecodableSourcesGiveNoPhoto() {
        val jpeg = syntheticJpeg(width = 400, height = 300)
        val garbage = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(GARBAGE_BYTES) { 7 } +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val sources = listOf(
            jpeg.copyOf(jpeg.size / 2),
            garbage,
            syntheticJpeg(width = 8200, height = 8),
            "<html>not an image</html>".encodeToByteArray(),
            ByteArray(0),
        )

        sources.forEach { source ->
            assertThat(PhotoTranscoder.transcodeForCatalog(source)).isNull()
            assertThat(PhotoTranscoder.transcode(source)).isNull()
        }
    }

    private fun mimeType(bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outMimeType
    }

    private companion object {
        const val GARBAGE_BYTES = 200
    }
}
