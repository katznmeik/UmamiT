package com.umamit.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** A photo re-encoded for the `photo` table: JPEG bytes and their pixel size. */
internal class EncodedPhoto(val bytes: ByteArray, val width: Int, val height: Int)

/**
 * Decodes a downloaded JPEG and re-encodes it the way the app stores photos (05 §3.9, 06 §5.2,
 * D-050): at most [MAX_SIDE_PX] on the long side, JPEG quality [JPEG_QUALITY], no metadata
 * (EXIF, XMP and ICC are not carried over). Proves the image decodes before it becomes a cover.
 */
internal object PhotoTranscoder {
    const val MAX_SIDE_PX = 2048
    const val JPEG_QUALITY = 85

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
     * The re-encoded photo, or null when [bytes] are not a complete JPEG (no SOI or EOI marker,
     * so also a truncated download), are too large, or don't decode.
     */
    fun transcode(bytes: ByteArray): EncodedPhoto? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (bytes.isCompleteJpeg()) BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val acceptable = bounds.outMimeType == JPEG_MIME &&
            bounds.outWidth in 1..MAX_SOURCE_SIDE_PX &&
            bounds.outHeight in 1..MAX_SOURCE_SIDE_PX &&
            bounds.outWidth.toLong() * bounds.outHeight <= MAX_SOURCE_PIXELS
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(max(bounds.outWidth, bounds.outHeight))
        }
        val decoded = if (acceptable) BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) else null
        return decoded?.let(::encode)
    }

    /** The largest power of two that keeps the decoded long side at or above [MAX_SIDE_PX]. */
    private fun sampleSize(longSide: Int): Int {
        var sample = 1
        while (longSide / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        return sample
    }

    private fun encode(decoded: Bitmap): EncodedPhoto {
        val longSide = max(decoded.width, decoded.height)
        val scaled = if (longSide > MAX_SIDE_PX) {
            val factor = MAX_SIDE_PX.toFloat() / longSide
            decoded.scale(
                (decoded.width * factor).roundToInt().coerceAtLeast(1),
                (decoded.height * factor).roundToInt().coerceAtLeast(1),
            )
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val photo = EncodedPhoto(out.toByteArray(), scaled.width, scaled.height)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return photo
    }

    private fun ByteArray.isCompleteJpeg() = size >= MIN_JPEG_BYTES &&
        this[0] == MARKER && this[1] == SOI && this[size - 2] == MARKER && this[size - 1] == EOI
}
