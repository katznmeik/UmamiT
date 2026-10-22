package com.umamit.core.timer.alert

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The bundled sounds from `scripts/sounds/generate.sh` (04 §9.2): Ogg Vorbis,
 * mono, ≤ 60 KB, and as long as [AlertSound.lengthMs] says (the player's
 * repeat rhythm depends on it). Read from the merged resources, so this also
 * proves that `core:content` ships them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BundledSoundsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun soundsAreMonoVorbisOfAtMostSixtyKilobytes() {
        AlertSound.entries.forEach { sound ->
            val bytes = context.resources.openRawResource(sound.rawRes).use { it.readBytes() }
            val info = OggVorbis.parse(bytes)

            assertThat(bytes.size).isAtMost(MAX_BYTES)
            assertThat(info.channels).isEqualTo(1)
            assertThat(info.sampleRate).isEqualTo(SAMPLE_RATE)
            assertThat(info.durationMs).isWithin(DURATION_TOLERANCE_MS).of(sound.lengthMs)
        }
    }

    @Test
    fun tickIsShorterThan150Milliseconds() {
        assertThat(AlertSound.TICK.lengthMs).isLessThan(150L)
    }

    /** Just enough of RFC 3533 (Ogg) and the Vorbis I identification header. */
    private object OggVorbis {
        data class Info(val channels: Int, val sampleRate: Int, val durationMs: Long)

        private const val HEADER_SIZE = 27
        private const val SEGMENT_COUNT_OFFSET = 26
        private const val GRANULE_OFFSET = 6
        private const val VORBIS_ID_PACKET = 1
        private const val CHANNELS_OFFSET = 11
        private const val RATE_OFFSET = 12
        private const val MS_PER_SECOND = 1_000L

        fun parse(bytes: ByteArray): Info {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var offset = 0
            var firstPacketStart = -1
            var lastGranule = 0L
            while (offset < bytes.size) {
                check(String(bytes, offset, 4, Charsets.US_ASCII) == "OggS") { "No Ogg page at $offset" }
                val segments = bytes[offset + SEGMENT_COUNT_OFFSET].toInt() and 0xFF
                val bodySize = (0 until segments).sumOf { bytes[offset + HEADER_SIZE + it].toInt() and 0xFF }
                val bodyStart = offset + HEADER_SIZE + segments
                if (firstPacketStart < 0) firstPacketStart = bodyStart
                val granule = buffer.getLong(offset + GRANULE_OFFSET)
                if (granule >= 0) lastGranule = granule
                offset = bodyStart + bodySize
            }
            check(bytes[firstPacketStart].toInt() == VORBIS_ID_PACKET) { "Not a Vorbis identification header" }
            check(String(bytes, firstPacketStart + 1, 6, Charsets.US_ASCII) == "vorbis") { "Not Vorbis" }
            val channels = bytes[firstPacketStart + CHANNELS_OFFSET].toInt() and 0xFF
            val sampleRate = buffer.getInt(firstPacketStart + RATE_OFFSET)
            return Info(channels, sampleRate, lastGranule * MS_PER_SECOND / sampleRate)
        }
    }

    private companion object {
        const val MAX_BYTES = 60 * 1024
        const val SAMPLE_RATE = 48_000
        const val DURATION_TOLERANCE_MS = 20L
    }
}
