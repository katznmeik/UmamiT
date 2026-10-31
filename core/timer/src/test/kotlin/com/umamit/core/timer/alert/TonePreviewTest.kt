package com.umamit.core.timer.alert

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * *Anhören* in the tone picker (SET-09): the chosen tone once, in the ringer mode's sound rule,
 * never the full alert, and stoppable.
 */
class TonePreviewTest {
    private val scheduler = FakeScheduler()
    private val sound = FakeSoundOutput(scheduler)
    private val environment = FakeEnvironment()
    private val preview = RingerTonePreview(environment, sound, scheduler)

    @Test
    fun `the tone plays once and lets the audio focus go when it ends`() {
        preview.play("glockenspiel", volumePercent = 100, ringUnderDnd = true)
        scheduler.advanceBy(AlertSound.GLOCKENSPIEL.lengthMs)

        assertThat(sound.plays(AlertSound.GLOCKENSPIEL)).containsExactly(0L)
        assertThat(sound.focusHeld).isFalse()
    }

    @Test
    fun `an unknown key previews the Klangschale`() {
        preview.play("unknown", volumePercent = 100, ringUnderDnd = true)

        assertThat(sound.playing).isEqualTo(AlertSound.KLANGSCHALE)
    }

    @Test
    fun `a Vorwarnton plays once by its stored key and lets the focus go when it has rung out`() {
        // SET-26 *Anhören*: the sound alone, as long as it rings (04 §9.2).
        preview.playPreAlert("klangstab", volumePercent = 100, ringUnderDnd = true)
        scheduler.advanceBy(3_000L)
        assertThat(sound.focusHeld).isTrue()
        scheduler.advanceBy(200L)

        assertThat(sound.plays(AlertSound.KLANGSTAB)).containsExactly(0L)
        assertThat(sound.focusHeld).isFalse()
    }

    @Test
    fun `Ting plays the tick, and an unknown Vorwarnton key the default`() {
        preview.playPreAlert("ting", volumePercent = 100, ringUnderDnd = true)
        assertThat(sound.playing).isEqualTo(AlertSound.TICK)

        preview.playPreAlert("unknown", volumePercent = 100, ringUnderDnd = true)
        assertThat(sound.playing).isEqualTo(PreAlertSound.DEFAULT.sound)
    }

    @Test
    fun `stop ends the preview at once`() {
        preview.play("sanft", volumePercent = 100, ringUnderDnd = true)
        preview.stop()

        assertThat(sound.playing).isNull()
        assertThat(sound.focusHeld).isFalse()
    }

    @Test
    fun `the preview plays at the Lautstaerke it is given`() {
        // SET-27: Anhören sounds like the cue would, at the stored Lautstärke on the alarm stream (D-098).
        preview.play("glocke", volumePercent = 40, ringUnderDnd = true)
        preview.playPreAlert("ting", volumePercent = 100, ringUnderDnd = true)
        preview.play("sanft", volumePercent = 0, ringUnderDnd = true)

        assertThat(sound.gains).containsExactly("GLOCKE 0.4 @ 0", "TICK 1.0 @ 0", "SANFT 0.1 @ 0").inOrder()
    }

    @Test
    fun `in vibrate or silent mode, in a call or under Do Not Disturb total silence the preview stays silent`() {
        listOf(
            AlertConditions(RingerMode.VIBRATE),
            AlertConditions(RingerMode.SILENT),
            AlertConditions(RingerMode.NORMAL, inCall = true),
            AlertConditions(RingerMode.NORMAL, dnd = DoNotDisturb.TOTAL_SILENCE),
        ).forEach {
            environment.conditions = it
            preview.play("glocke", volumePercent = 100, ringUnderDnd = true)
        }

        assertThat(sound.plays(AlertSound.GLOCKE)).isEmpty()
    }

    @Test
    fun `under Do Not Disturb with alarms allowed a silent report plays the preview while the switch is on`() {
        // D-099, SET-32: Anhören follows the cues' rule, so the PO hears what will ring.
        environment.conditions = AlertConditions(RingerMode.SILENT, dnd = DoNotDisturb.PRIORITY)

        preview.play("glocke", volumePercent = 100, ringUnderDnd = true)
        assertThat(sound.plays(AlertSound.GLOCKE)).containsExactly(0L)

        preview.playPreAlert("klangstab", volumePercent = 100, ringUnderDnd = false)
        preview.play("sanft", volumePercent = 100, ringUnderDnd = false)
        assertThat(sound.plays(AlertSound.KLANGSTAB)).isEmpty()
        assertThat(sound.plays(AlertSound.SANFT)).isEmpty()

        preview.playPreAlert("klangstab", volumePercent = 100, ringUnderDnd = true)
        assertThat(sound.plays(AlertSound.KLANGSTAB)).containsExactly(0L)
    }
}
