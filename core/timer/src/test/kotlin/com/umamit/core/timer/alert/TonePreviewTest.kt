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
        preview.play("glockenspiel")
        scheduler.advanceBy(AlertSound.GLOCKENSPIEL.lengthMs)

        assertThat(sound.plays(AlertSound.GLOCKENSPIEL)).containsExactly(0L)
        assertThat(sound.focusHeld).isFalse()
    }

    @Test
    fun `an unknown key previews the Klangschale`() {
        preview.play("unknown")

        assertThat(sound.playing).isEqualTo(AlertSound.KLANGSCHALE)
    }

    @Test
    fun `stop ends the preview at once`() {
        preview.play("sanft")
        preview.stop()

        assertThat(sound.playing).isNull()
        assertThat(sound.focusHeld).isFalse()
    }

    @Test
    fun `in vibrate or silent mode, in a call or under Do Not Disturb the preview stays silent`() {
        listOf(
            AlertConditions(RingerMode.VIBRATE),
            AlertConditions(RingerMode.SILENT),
            AlertConditions(RingerMode.NORMAL, inCall = true),
            AlertConditions(RingerMode.NORMAL, dndBlocksAlerts = true),
        ).forEach {
            environment.conditions = it
            preview.play("glocke")
        }

        assertThat(sound.plays(AlertSound.GLOCKE)).isEmpty()
    }
}
