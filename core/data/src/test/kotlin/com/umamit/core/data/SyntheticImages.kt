package com.umamit.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream

/** A synthetic one-colour JPEG of the given size, encoded on the spot: no shop photo (D-016). */
internal fun syntheticJpeg(width: Int, height: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(74, 86, 67)) }
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
}

/** A synthetic one-colour image of the given size in [format] (PNG, WebP), encoded on the spot (D-016). */
internal fun syntheticImage(width: Int, height: Int, format: Bitmap.CompressFormat): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(74, 86, 67)) }
    return ByteArrayOutputStream().also { bitmap.compress(format, 90, it) }.toByteArray()
}

/** [jpeg] with an APP1 „Exif" segment after SOI, as camera and shop images carry. */
internal fun withExifSegment(jpeg: ByteArray): ByteArray {
    // "Exif\0\0", a big-endian TIFF header pointing at an empty IFD, then a marker string.
    val payload = "Exif\u0000\u0000MM\u0000*\u0000\u0000\u0000\u0008\u0000\u0000\u0000\u0000\u0000\u0000"
        .encodeToByteArray() + "synthetic-metadata".encodeToByteArray()
    val length = payload.size + 2
    val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    return jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
}

/**
 * [jpeg] with an APP1 „Exif" segment after SOI whose IFD0 holds only the orientation tag (0x0112) set to
 * [orientation] (1–8), as a phone camera writes it (#58).
 */
internal fun withOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
    // "Exif\0\0", big-endian TIFF header (IFD0 at 8), one entry: tag 0x0112, SHORT, count 1, value; no next IFD.
    val tiff = byteArrayOf(
        0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
        0x00, 0x01,
        0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, orientation.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
    )
    val payload = "Exif\u0000\u0000".encodeToByteArray() + tiff
    val length = payload.size + 2
    val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    return jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
}

/** The decoded pixel size of [bytes]. */
internal fun decodedSize(bytes: ByteArray): Pair<Int, Int> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    return bounds.outWidth to bounds.outHeight
}
