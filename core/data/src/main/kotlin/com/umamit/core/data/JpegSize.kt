package com.umamit.core.data

/** Pixel size of a JPEG image. */
internal data class JpegSize(val width: Int, val height: Int)

private const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8
private const val MARKER = 0xFF
private const val SOI = 0xD8
private const val EOI = 0xD9
private const val SOS = 0xDA
private const val TEM = 0x01
private val RST = 0xD0..0xD7
private val SOF = (0xC0..0xCF) - setOf(0xC4, 0xC8, 0xCC)

/** Offsets from a segment's 0xFF: the 2-byte length, then in a frame header height and width. */
private const val LENGTH_OFFSET = 2
private const val HEIGHT_OFFSET = 5
private const val WIDTH_OFFSET = 7
private const val MIN_JPEG_BYTES = 4

private fun ByteArray.u8(i: Int) = this[i].toInt() and BYTE_MASK

private fun ByteArray.u16(i: Int) = (u8(i) shl BITS_PER_BYTE) or u8(i + 1)

/**
 * Reads the size from [bytes]' start-of-frame header, or null when they are not a JPEG
 * (no SOI marker, or no frame header before the scan data). Nothing is decoded.
 */
internal fun jpegSize(bytes: ByteArray): JpegSize? {
    if (bytes.size < MIN_JPEG_BYTES || bytes.u8(0) != MARKER || bytes.u8(1) != SOI) return null
    return segmentOffsets(bytes)
        .firstOrNull { bytes.u8(it + 1) in SOF }
        ?.takeIf { it + WIDTH_OFFSET + 1 < bytes.size }
        ?.let { JpegSize(width = bytes.u16(it + WIDTH_OFFSET), height = bytes.u16(it + HEIGHT_OFFSET)) }
        ?.takeIf { it.width > 0 && it.height > 0 }
}

/** The offset of each marker before the scan data, after SOI. */
private fun segmentOffsets(bytes: ByteArray): Sequence<Int> = sequence {
    var i = 2
    while (i + 1 < bytes.size && bytes.u8(i) == MARKER) {
        val marker = bytes.u8(i + 1)
        if (marker == EOI || marker == SOS) break
        yield(i)
        i += when {
            // A fill byte before the marker.
            marker == MARKER -> 1

            marker == TEM || marker in RST -> 2

            i + LENGTH_OFFSET + 1 < bytes.size -> LENGTH_OFFSET + bytes.u16(i + LENGTH_OFFSET)

            else -> bytes.size
        }
    }
}
