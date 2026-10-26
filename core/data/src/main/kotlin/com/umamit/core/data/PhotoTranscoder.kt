package com.umamit.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The file format a photo is stored in. */
internal enum class PhotoFormat(val compressFormat: Bitmap.CompressFormat) {
    /** The `photo` table's files (05 §3.9). */
    JPEG(Bitmap.CompressFormat.JPEG),

    /** The catalog's photos and thumbnails (CAT-08, 06 §5.2). */
    WEBP(Bitmap.CompressFormat.WEBP_LOSSY),
}

/**
 * What a photo is re-encoded to: scaled down (never up, aspect ratio kept) until its long side
 * is at most [maxLongSidePx] and its short side at most [maxShortSidePx], then encoded as
 * [format] at [quality].
 */
internal class PhotoTarget(
    val maxLongSidePx: Int,
    val format: PhotoFormat,
    val quality: Int,
    val maxShortSidePx: Int = Int.MAX_VALUE,
) {
    /** The scale factor (≤ 1) for a [width] × [height] image. */
    fun scaleFor(width: Int, height: Int): Float = minOf(
        1f,
        maxLongSidePx.toFloat() / max(width, height),
        maxShortSidePx.toFloat() / min(width, height),
    )

    companion object {
        /** A collection photo or cover (05 §3.9, 06 §5.2, D-050): ≤ 2048 px, JPEG 85. */
        val COVER = PhotoTarget(maxLongSidePx = 2048, format = PhotoFormat.JPEG, quality = 85)

        /** A catalog photo (CAT-08, 06 §5.2, D-073): ≤ 1200 px, WebP. */
        val CATALOG = PhotoTarget(maxLongSidePx = 1200, format = PhotoFormat.WEBP, quality = WEBP_QUALITY)

        /**
         * The catalog list thumbnail (CAT-08, D-074): short side 256 px, enough for a round list
         * thumbnail of up to 64 dp at 4× density, and never larger than [CATALOG].
         */
        val CATALOG_THUMBNAIL = PhotoTarget(
            maxLongSidePx = CATALOG.maxLongSidePx,
            format = PhotoFormat.WEBP,
            quality = WEBP_QUALITY,
            maxShortSidePx = 256,
        )

        private const val WEBP_QUALITY = 80
    }
}

/** A re-encoded photo: its [format]'s bytes and their pixel size. */
internal class EncodedPhoto(val bytes: ByteArray, val width: Int, val height: Int, val format: PhotoFormat)

/** A catalog photo re-encoded once into both of its files (CAT-08). */
internal class CatalogPhoto(val full: EncodedPhoto, val thumbnail: EncodedPhoto)

/**
 * Decodes a downloaded JPEG, PNG or WebP (D-091) and re-encodes it to one or more [PhotoTarget]s
 * (05 §3.9, 06 §5.2, CAT-08, D-050, D-074): scaled down to each target, no metadata (EXIF, XMP and
 * ICC are not carried over), transparency flattened onto white. Proves the image decodes before
 * it is stored. The source is decoded once, with sampling, at no less than the largest target
 * needs, so a large PNG is never held at full resolution.
 */
internal object PhotoTranscoder {
    /** Larger sources are refused before decoding (a decode-cost bomb). */
    const val MAX_SOURCE_SIDE_PX = 8192
    const val MAX_SOURCE_PIXELS = 40_000_000L

    /** The shop's image formats (08 §2.2; PNGs since full live run 1, D-091). */
    private val SHOP_SOURCES = setOf(CompleteImage.JPEG, CompleteImage.PNG, CompleteImage.WEBP)

    /**
     * [bytes] re-encoded to [target] ([PhotoTarget.COVER] by default), or null when they are no
     * complete JPEG, PNG or WebP (so also a truncated download), are too large, or don't decode.
     */
    fun transcode(bytes: ByteArray, target: PhotoTarget = PhotoTarget.COVER): EncodedPhoto? =
        transcode(bytes, listOf(target))?.single()

    /** [bytes] as a catalog photo and its list thumbnail, or null as for [transcode]. */
    fun transcodeForCatalog(bytes: ByteArray): CatalogPhoto? =
        transcode(bytes, listOf(PhotoTarget.CATALOG, PhotoTarget.CATALOG_THUMBNAIL))
            ?.let { (full, thumbnail) -> CatalogPhoto(full, thumbnail) }

    /**
     * The app's own catalog photo (`<key>.webp`, CAT-08) re-encoded as a collection photo
     * ([PhotoTarget.COVER], JPEG) when a tea is added from the catalog (CAT-12, 05 §3.9, D-076),
     * or null when [webp] is no WebP image of an acceptable size or doesn't decode. Only the
     * app's own files come here.
     */
    fun transcodeCatalogCopy(webp: ByteArray): EncodedPhoto? =
        transcode(webp, listOf(PhotoTarget.COVER), setOf(CompleteImage.WEBP))?.single()

    /** [bytes] re-encoded to each of [targets] in order, or null as for [transcode]. */
    fun transcode(bytes: ByteArray, targets: List<PhotoTarget>): List<EncodedPhoto>? =
        transcode(bytes, targets, SHOP_SOURCES)

    private fun transcode(bytes: ByteArray, targets: List<PhotoTarget>, mimes: Set<String>): List<EncodedPhoto>? {
        require(targets.isNotEmpty()) { "no target" }
        val bounds = acceptableBounds(bytes, mimes) ?: return null
        val longSide = max(bounds.outWidth, bounds.outHeight)
        val neededLongSide = targets.maxOf { (longSide * it.scaleFor(bounds.outWidth, bounds.outHeight)).roundToInt() }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(longSide, neededLongSide) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { decoded ->
            targets.map { encode(decoded, it) }.also { decoded.recycle() }
        }
    }

    /** [bytes]' bounds when they are a complete image of one of [mimes] of an acceptable size, else null. */
    private fun acceptableBounds(bytes: ByteArray, mimes: Set<String>): BitmapFactory.Options? {
        val format = CompleteImage.formatOf(bytes)?.takeIf { it in mimes } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val acceptable = bounds.outMimeType == format &&
            bounds.outWidth in 1..MAX_SOURCE_SIDE_PX &&
            bounds.outHeight in 1..MAX_SOURCE_SIDE_PX &&
            bounds.outWidth.toLong() * bounds.outHeight <= MAX_SOURCE_PIXELS
        return bounds.takeIf { acceptable }
    }

    /** The largest power of two that keeps the decoded long side at or above [neededLongSide]. */
    private fun sampleSize(longSide: Int, neededLongSide: Int): Int {
        var sample = 1
        while (longSide / (sample * 2) >= neededLongSide) sample *= 2
        return sample
    }

    private fun encode(decoded: Bitmap, target: PhotoTarget): EncodedPhoto {
        val factor = target.scaleFor(decoded.width, decoded.height)
        val scaled = if (factor < 1f) {
            decoded.scale(
                (decoded.width * factor).roundToInt().coerceAtLeast(1),
                (decoded.height * factor).roundToInt().coerceAtLeast(1),
            )
        } else {
            decoded
        }
        // A transparent PNG or WebP goes onto white, as the shop shows it; JPEG has no alpha (D-091).
        val opaque = if (scaled.hasAlpha()) onWhite(scaled) else scaled
        val out = ByteArrayOutputStream()
        opaque.compress(target.format.compressFormat, target.quality, out)
        val photo = EncodedPhoto(out.toByteArray(), opaque.width, opaque.height, target.format)
        if (opaque !== scaled) opaque.recycle()
        if (scaled !== decoded) scaled.recycle()
        return photo
    }

    /** [image] drawn onto a white, opaque bitmap of its size. */
    private fun onWhite(image: Bitmap): Bitmap =
        createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).also { flat ->
            Canvas(flat).apply {
                drawColor(Color.WHITE)
                drawBitmap(image, 0f, 0f, null)
            }
            flat.setHasAlpha(false)
        }
}

/** The image formats the transcoder reads, told apart by their bytes, only when complete (D-091). */
private object CompleteImage {
    const val JPEG = "image/jpeg"
    const val PNG = "image/png"
    const val WEBP = "image/webp"

    private const val MARKER = 0xFF.toByte()
    private const val SOI = 0xD8.toByte()
    private const val EOI = 0xD9.toByte()

    /** SOI plus EOI. */
    private const val MIN_JPEG_BYTES = 4

    /** The PNG signature, and the IEND chunk (empty, with its CRC) that ends every complete PNG. */
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val PNG_END = byteArrayOf(0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte())

    /** A WebP's RIFF header: „RIFF", the little-endian size of what follows it, „WEBP". */
    private const val RIFF_HEADER_BYTES = 12
    private const val RIFF_SIZE_OFFSET = 4
    private const val RIFF_SIZE_BYTES = 4
    private const val RIFF_FORM_OFFSET = 8
    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFFL

    /**
     * The format of [bytes] when they are a complete JPEG (SOI to EOI), PNG (signature to IEND) or
     * WebP (as long as its RIFF header says), else null: a truncated download is never stored.
     */
    fun formatOf(bytes: ByteArray): String? = when {
        bytes.isCompleteJpeg() -> JPEG
        bytes.isCompletePng() -> PNG
        bytes.isCompleteWebP() -> WEBP
        else -> null
    }

    private fun ByteArray.isCompleteJpeg() = size >= MIN_JPEG_BYTES &&
        this[0] == MARKER && this[1] == SOI && this[size - 2] == MARKER && this[size - 1] == EOI

    private fun ByteArray.isCompletePng() = size >= PNG_SIGNATURE.size + PNG_END.size &&
        copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE) &&
        copyOfRange(size - PNG_END.size, size).contentEquals(PNG_END)

    private fun ByteArray.isCompleteWebP(): Boolean {
        val riff = size >= RIFF_HEADER_BYTES &&
            ascii(0, RIFF_SIZE_OFFSET) == "RIFF" &&
            ascii(RIFF_FORM_OFFSET, RIFF_HEADER_BYTES) == "WEBP"
        if (!riff) return false
        val declared = (0 until RIFF_SIZE_BYTES).fold(0L) { sum, i ->
            sum or ((this[RIFF_SIZE_OFFSET + i].toLong() and BYTE_MASK) shl (BITS_PER_BYTE * i))
        }
        return size >= declared + RIFF_FORM_OFFSET
    }

    private fun ByteArray.ascii(from: Int, to: Int) = String(copyOfRange(from, to), Charsets.US_ASCII)
}
