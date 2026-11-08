package com.umamit.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * Re-encoding downloaded images to a target size and format (#63; 05 §3.9, 06 §5.2, CAT-08,
 * D-050, D-073, D-074, D-091). The sources are synthetic JPEGs, PNGs and WebPs encoded on the spot
 * (D-016).
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

    @Test
    fun pngAndWebPSourcesAreAcceptedToo() {
        // Full live run 1 (counts only, D-091): 27 product photos and some guide images were PNGs.
        val png = syntheticImage(width = 3000, height = 2000, format = Bitmap.CompressFormat.PNG)
        val fromPng = checkNotNull(PhotoTranscoder.transcodeForCatalog(png))
        assertThat(decodedSize(fromPng.full.bytes)).isEqualTo(1200 to 800)
        assertThat(mimeType(fromPng.full.bytes)).isEqualTo("image/webp")
        assertThat(decodedSize(fromPng.thumbnail.bytes)).isEqualTo(384 to 256)
        val cover = checkNotNull(PhotoTranscoder.transcode(png))
        assertThat(cover.width to cover.height).isEqualTo(2048 to 1365)
        assertThat(mimeType(cover.bytes)).isEqualTo("image/jpeg")
        val guide = checkNotNull(PhotoTranscoder.transcode(png, PhotoTarget.CATALOG))
        assertThat(guide.width to guide.height).isEqualTo(1200 to 800)

        val webp = syntheticImage(width = 1600, height = 1600, format = Bitmap.CompressFormat.WEBP_LOSSY)
        val fromWebP = checkNotNull(PhotoTranscoder.transcodeForCatalog(webp))
        assertThat(decodedSize(fromWebP.full.bytes)).isEqualTo(1200 to 1200)
    }

    @Test
    fun aTransparentPngIsFlattenedOntoWhite() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            Canvas(this).drawRect(100f, 100f, 300f, 200f, Paint().apply { color = Color.rgb(74, 86, 67) })
        }
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        val cover = checkNotNull(PhotoTranscoder.transcode(png))
        val catalog = checkNotNull(PhotoTranscoder.transcodeForCatalog(png))

        listOf(cover.bytes, catalog.full.bytes, catalog.thumbnail.bytes).forEach { bytes ->
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val corner = decoded.getPixel(2, 2)
            assertThat(Color.alpha(corner)).isEqualTo(255)
            assertThat(listOf(Color.red(corner), Color.green(corner), Color.blue(corner)).min()).isAtLeast(240)
            val centre = decoded.getPixel(decoded.width / 2, decoded.height / 2)
            assertThat(Color.red(centre)).isLessThan(120)
        }
    }

    @Test
    fun truncatedPngOrWebPGivesNoPhoto() {
        val png = syntheticImage(width = 400, height = 300, format = Bitmap.CompressFormat.PNG)
        val webp = syntheticImage(width = 400, height = 300, format = Bitmap.CompressFormat.WEBP_LOSSY)

        listOf(png.copyOf(png.size - 12), png.copyOf(png.size / 2), webp.copyOf(webp.size - 8)).forEach { source ->
            assertThat(PhotoTranscoder.transcodeForCatalog(source)).isNull()
            assertThat(PhotoTranscoder.transcode(source)).isNull()
        }
    }

    @Test
    fun aPickedPhotoIsAtMost2048PxJpegWithoutMetadata() {
        val photo =
            checkNotNull(PhotoTranscoder.transcodePicked(withExifSegment(syntheticJpeg(width = 4000, height = 3000))))

        assertThat(photo.width to photo.height).isEqualTo(2048 to 1536)
        assertThat(photo.format).isEqualTo(PhotoFormat.JPEG)
        assertThat(mimeType(photo.bytes)).isEqualTo("image/jpeg")
        assertThat(decodedSize(photo.bytes)).isEqualTo(2048 to 1536)
        val text = photo.bytes.decodeToString(throwOnInvalidSequence = false)
        assertThat(text).doesNotContain("Exif")
        assertThat(text).doesNotContain("synthetic-metadata")
    }

    @Test
    fun aSmallPickedPhotoIsNeverUpscaled() {
        val photo = checkNotNull(PhotoTranscoder.transcodePicked(syntheticJpeg(width = 640, height = 480)))

        assertThat(photo.width to photo.height).isEqualTo(640 to 480)
    }

    @Test
    fun aPickedPhotoLargerThanTheDownloadLimitIsSampledDownNotRefused() {
        // A panorama wider than the downloads' 8192 px; a picked photo is the PO's own (#58).
        val photo = checkNotNull(PhotoTranscoder.transcodePicked(syntheticJpeg(width = 9000, height = 1200)))

        assertThat(photo.width to photo.height).isEqualTo(2048 to 273)
    }

    @Test
    fun aPickedPhotoIsTurnedUprightByItsExifOrientation() {
        // Red left half, blue right half, 400 × 200: turned by 90° clockwise (orientation 6) red is on top.
        val source = halves(width = 400, height = 200)

        val upright = checkNotNull(PhotoTranscoder.transcodePicked(withOrientation(source, ORIENTATION_ROTATE_90)))
        assertThat(upright.width to upright.height).isEqualTo(200 to 400)
        assertThat(colourAt(upright.bytes, x = 100, y = 50)).isEqualTo(RED)
        assertThat(colourAt(upright.bytes, x = 100, y = 350)).isEqualTo(BLUE)

        val half = checkNotNull(PhotoTranscoder.transcodePicked(withOrientation(source, ORIENTATION_ROTATE_180)))
        assertThat(half.width to half.height).isEqualTo(400 to 200)
        assertThat(colourAt(half.bytes, x = 50, y = 100)).isEqualTo(BLUE)

        val left = checkNotNull(PhotoTranscoder.transcodePicked(withOrientation(source, ORIENTATION_ROTATE_270)))
        assertThat(left.width to left.height).isEqualTo(200 to 400)
        assertThat(colourAt(left.bytes, x = 100, y = 50)).isEqualTo(BLUE)

        val mirrored =
            checkNotNull(PhotoTranscoder.transcodePicked(withOrientation(source, ORIENTATION_FLIP_HORIZONTAL)))
        assertThat(mirrored.width to mirrored.height).isEqualTo(400 to 200)
        assertThat(colourAt(mirrored.bytes, x = 50, y = 100)).isEqualTo(BLUE)

        val normal = checkNotNull(PhotoTranscoder.transcodePicked(withOrientation(source, ORIENTATION_NORMAL)))
        assertThat(colourAt(normal.bytes, x = 50, y = 100)).isEqualTo(RED)
        val text = upright.bytes.decodeToString(throwOnInvalidSequence = false)
        assertThat(text).doesNotContain("Exif")
    }

    @Test
    fun aRotatedPickedPhotoIsCappedOnItsUprightLongSide() {
        val photo = checkNotNull(
            PhotoTranscoder.transcodePicked(
                withOrientation(syntheticJpeg(width = 4000, height = 3000), ORIENTATION_ROTATE_90),
            ),
        )

        assertThat(photo.width to photo.height).isEqualTo(1536 to 2048)
    }

    @Test
    fun pickedPngAndWebPAreAccepted() {
        val png = checkNotNull(
            PhotoTranscoder.transcodePicked(
                syntheticImage(width = 3000, height = 2000, format = Bitmap.CompressFormat.PNG),
            ),
        )
        assertThat(png.width to png.height).isEqualTo(2048 to 1365)
        assertThat(mimeType(png.bytes)).isEqualTo("image/jpeg")

        val webp = syntheticImage(width = 800, height = 600, format = Bitmap.CompressFormat.WEBP_LOSSY)
        assertThat(checkNotNull(PhotoTranscoder.transcodePicked(webp)).width).isEqualTo(800)
    }

    @Test
    fun anUndecodablePickedFileGivesNoPhoto() {
        val garbage = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(GARBAGE_BYTES) { 7 } +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())

        listOf(garbage, "<html>not an image</html>".encodeToByteArray(), ByteArray(0)).forEach { source ->
            assertThat(PhotoTranscoder.transcodePicked(source)).isNull()
        }
    }

    /** A JPEG whose left half is red and right half blue. */
    private fun halves(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
                drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
            }
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
    }

    /** The pixel at ([x], [y]) of [bytes], as [RED], [BLUE] or null for anything else. */
    private fun colourAt(bytes: ByteArray, x: Int, y: Int): Int? {
        val pixel = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).getPixel(x, y)
        return when {
            Color.red(pixel) > 200 && Color.blue(pixel) < 60 -> RED
            Color.blue(pixel) > 200 && Color.red(pixel) < 60 -> BLUE
            else -> null
        }
    }

    private fun mimeType(bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outMimeType
    }

    private companion object {
        const val GARBAGE_BYTES = 200
        const val RED = 1
        const val BLUE = 2
        const val ORIENTATION_NORMAL = 1
        const val ORIENTATION_FLIP_HORIZONTAL = 2
        const val ORIENTATION_ROTATE_180 = 3
        const val ORIENTATION_ROTATE_90 = 6
        const val ORIENTATION_ROTATE_270 = 8
    }
}
