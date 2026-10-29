package com.umamit.core.timer.alert

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.UserSettings
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

            assertWithMessage("$sound size").that(bytes.size).isAtMost(MAX_BYTES)
            assertWithMessage("$sound channels").that(info.channels).isEqualTo(1)
            assertWithMessage("$sound sample rate").that(info.sampleRate).isEqualTo(SAMPLE_RATE)
            assertWithMessage("$sound length").that(info.durationMs)
                .isWithin(DURATION_TOLERANCE_MS).of(sound.lengthMs)
        }
    }

    @Test
    fun theTenGeneratedFilesAreBundled() {
        // scripts/sounds/generate.sh: five alert tones (SET-09), the pre-alert tick (D-045, D-059) and
        // the four pre-alert sounds of v0.2.1 (SET-26, 04 §9.2, D-093).
        assertThat(AlertSound.entries.associate { it.key to it.lengthMs }).containsExactly(
            "klangschale", 3_500L,
            "glocke", 3_000L,
            "holzblock", 1_200L,
            "glockenspiel", 2_200L,
            "sanft", 3_000L,
            "tick", 800L,
            "zimbel", 3_200L,
            "gloeckchen", 3_000L,
            "klangstab", 3_200L,
            "tiefe_schale", 3_200L,
        )
    }

    @Test
    fun theToneChoicesAreTheFiveAlertTonesWithTheKlangschaleFirst() {
        assertThat(AlertSound.TONES.map { it.key })
            .containsExactly("klangschale", "glocke", "holzblock", "glockenspiel", "sanft").inOrder()
    }

    @Test
    fun thePreAlertChoicesAreTheFourNewSoundsThenTheTickStoredAsTing() {
        // 05 §6: the stored keys of SET-26 (Vorwarnton); „Ting (leise)" plays today's tick.
        assertThat(PreAlertSound.entries.map { it.key to it.sound }).containsExactly(
            "zimbel" to AlertSound.ZIMBEL,
            "gloeckchen" to AlertSound.GLOECKCHEN,
            "klangstab" to AlertSound.KLANGSTAB,
            "tiefe_schale" to AlertSound.TIEFE_SCHALE,
            "ting" to AlertSound.TICK,
        ).inOrder()
    }

    @Test
    fun theDefaultPreAlertSoundIsTheZimbelAndAnUnknownKeyReadsAsIt() {
        // Zimbel until the PO picks the default with the first v0.2.1 rc (D-093).
        assertThat(PreAlertSound.of(UserSettings.DEFAULT_PRE_ALERT_SOUND)).isEqualTo(PreAlertSound.ZIMBEL)
        assertThat(PreAlertSound.of("gong")).isEqualTo(PreAlertSound.ZIMBEL)
        assertThat(PreAlertSound.of("tick")).isEqualTo(PreAlertSound.ZIMBEL)
        assertThat(PreAlertSound.of("ting")).isEqualTo(PreAlertSound.TING)
    }

    @Test
    fun everyPreAlertSoundEndsBeforeTheDeadline() {
        // 04 §9.2, E7: at least 5 s lie between a pre-alert and its deadline.
        PreAlertSound.entries.forEach {
            assertWithMessage("${it.sound} length").that(it.sound.lengthMs).isLessThan(5_000L)
        }
    }

    @Test
    fun tickIsAtMostOneSecond() {
        assertThat(AlertSound.TICK.lengthMs).isAtMost(1_000L)
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
