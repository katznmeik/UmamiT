package com.umamit.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
 * Decodes a downloaded JPEG and re-encodes it to one or more [PhotoTarget]s (05 §3.9, 06 §5.2,
 * CAT-08, D-050, D-074): scaled down to each target, no metadata (EXIF, XMP and ICC are not
 * carried over). Proves the image decodes before it is stored. The source is decoded once, with
 * sampling, at no less than the largest target needs.
 */
internal object PhotoTranscoder {
    /** Larger sources are refused before decoding (a decode-cost bomb). */
    const val MAX_SOURCE_SIDE_PX = 8192
    const val MAX_SOURCE_PIXELS = 40_000_000L

    private const val JPEG_MIME = "image/jpeg"
    private const val MARKER = 0xFF.toByte()
    private const val SOI = 0xD8.toByte()
    private const val EOI = 0xD9.toByte()

    /** SOI plus EOI. */
    private const val MIN_JPEG_BYTES = 4

    /**
     * [bytes] re-encoded to [target] ([PhotoTarget.COVER] by default), or null when they are no
     * complete JPEG (no SOI or EOI marker, so also a truncated download), are too large, or
     * don't decode.
     */
    fun transcode(bytes: ByteArray, target: PhotoTarget = PhotoTarget.COVER): EncodedPhoto? =
        transcode(bytes, listOf(target))?.single()

    /** [bytes] as a catalog photo and its list thumbnail, or null as for [transcode]. */
    fun transcodeForCatalog(bytes: ByteArray): CatalogPhoto? =
        transcode(bytes, listOf(PhotoTarget.CATALOG, PhotoTarget.CATALOG_THUMBNAIL))
            ?.let { (full, thumbnail) -> CatalogPhoto(full, thumbnail) }

    /** [bytes] re-encoded to each of [targets] in order, or null as for [transcode]. */
    fun transcode(bytes: ByteArray, targets: List<PhotoTarget>): List<EncodedPhoto>? {
        require(targets.isNotEmpty()) { "no target" }
        val bounds = acceptableBounds(bytes) ?: return null
        val longSide = max(bounds.outWidth, bounds.outHeight)
        val neededLongSide = targets.maxOf { (longSide * it.scaleFor(bounds.outWidth, bounds.outHeight)).roundToInt() }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(longSide, neededLongSide) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { decoded ->
            targets.map { encode(decoded, it) }.also { decoded.recycle() }
        }
    }

    /** [bytes]' bounds when they are a complete JPEG of an acceptable size, else null. */
    private fun acceptableBounds(bytes: ByteArray): BitmapFactory.Options? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (bytes.isCompleteJpeg()) BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val acceptable = bounds.outMimeType == JPEG_MIME &&
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
        val out = ByteArrayOutputStream()
        scaled.compress(target.format.compressFormat, target.quality, out)
        val photo = EncodedPhoto(out.toByteArray(), scaled.width, scaled.height, target.format)
        if (scaled !== decoded) scaled.recycle()
        return photo
    }

    private fun ByteArray.isCompleteJpeg() = size >= MIN_JPEG_BYTES &&
        this[0] == MARKER && this[1] == SOI && this[size - 2] == MARKER && this[size - 1] == EOI
}
