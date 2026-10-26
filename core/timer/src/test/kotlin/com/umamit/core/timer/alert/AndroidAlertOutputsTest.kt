package com.umamit.core.timer.alert

import android.app.Application
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Looper
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

/** The thin Android adapters: what they read and the attributes they play with (04 §9.1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidAlertOutputsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    @Test
    fun environmentReadsRingerModeCallStateAndDoNotDisturb() {
        val environment = AndroidAlertEnvironment(context)

        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        assertThat(environment.conditions()).isEqualTo(AlertConditions(RingerMode.NORMAL))
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        assertThat(environment.conditions().ringerMode).isEqualTo(RingerMode.VIBRATE)
        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        assertThat(environment.conditions().ringerMode).isEqualTo(RingerMode.SILENT)

        audioManager.mode = AudioManager.MODE_IN_CALL
        assertThat(environment.conditions().inCall).isTrue()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        assertThat(environment.conditions().inCall).isTrue()
        audioManager.mode = AudioManager.MODE_NORMAL
        assertThat(environment.conditions().inCall).isFalse()

        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        assertThat(environment.conditions().dndBlocksAlerts).isFalse()
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        assertThat(environment.conditions().dndBlocksAlerts).isTrue()
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        assertThat(environment.conditions().dndBlocksAlerts).isTrue()
    }

    @Test
    fun soundRequestsTransientDuckingFocusWithNotificationEventAttributes() {
        val output = MediaPlayerSoundOutput(context)

        assertThat(output.requestFocus(onLost = {})).isTrue()

        val request = shadowOf(audioManager).lastAudioFocusRequest.audioFocusRequest
        assertThat(request.focusGain).isEqualTo(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        assertThat(request.audioAttributes.usage).isEqualTo(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        assertThat(request.audioAttributes.contentType).isEqualTo(AudioAttributes.CONTENT_TYPE_SONIFICATION)

        output.abandonFocus()
        assertThat(shadowOf(audioManager).lastAbandonedAudioFocusRequest).isSameInstanceAs(request)
    }

    @Test
    fun refusedFocusIsReported() {
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        assertThat(MediaPlayerSoundOutput(context).requestFocus(onLost = {})).isFalse()
    }

    @Test
    fun soundPlaysTheBundledFileAndStops() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(3_500, 0) }
        val output = MediaPlayerSoundOutput(context)

        output.play(AlertSound.KLANGSCHALE)
        assertThat(output.isPlaying).isTrue()

        output.stop()
        assertThat(output.isPlaying).isFalse()
        output.stop()
    }

    @Test
    fun aLoadedSoundStartsWithoutLoadingAndIsLoadedAgainAfterwards() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
        val loads = mutableListOf<AlertSound>()
        val output = MediaPlayerSoundOutput(context, loader = {
            loads += it
            MediaPlayerSoundOutput.load(context, it)
        })

        output.prepare(setOf(AlertSound.TICK, AlertSound.KLANGSCHALE))
        assertThat(loads).containsExactly(AlertSound.TICK, AlertSound.KLANGSCHALE).inOrder()
        assertThat(output.ready).containsExactly(AlertSound.TICK, AlertSound.KLANGSCHALE)
        loads.clear()

        output.play(AlertSound.TICK)

        // The cue only started the loaded player (D-092); it loads the next tick after the cue.
        assertThat(loads).isEmpty()
        assertThat(output.isPlaying).isTrue()
        assertThat(output.ready).containsExactly(AlertSound.KLANGSCHALE)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(loads).containsExactly(AlertSound.TICK)
        assertThat(output.ready).containsExactly(AlertSound.TICK, AlertSound.KLANGSCHALE)
    }

    @Test
    fun aSoundThatIsNotLoadedStillPlaysLoadedOnPlay() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(3_500, 0) }
        val loads = mutableListOf<AlertSound>()
        val output = MediaPlayerSoundOutput(context, loader = {
            loads += it
            MediaPlayerSoundOutput.load(context, it)
        })
        output.prepare(setOf(AlertSound.TICK))
        loads.clear()

        output.play(AlertSound.GLOCKE)

        assertThat(loads).containsExactly(AlertSound.GLOCKE)
        assertThat(output.isPlaying).isTrue()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(output.ready).containsExactly(AlertSound.TICK)
    }

    @Test
    fun soundsNoLongerWantedAreReleased() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
        val players = mutableListOf<MediaPlayer>()
        val output = MediaPlayerSoundOutput(context, loader = { sound ->
            MediaPlayerSoundOutput.load(context, sound)?.also { players += it }
        })
        output.prepare(setOf(AlertSound.TICK, AlertSound.KLANGSCHALE))

        // The tone setting changed: the old tone goes, the new one is loaded.
        output.prepare(setOf(AlertSound.TICK, AlertSound.GLOCKE))
        assertThat(output.ready).containsExactly(AlertSound.TICK, AlertSound.GLOCKE)
        assertThat(players).hasSize(3)
        assertThat(shadowOf(players[1]).state).isEqualTo(ShadowMediaPlayer.State.END)

        // The timer ended right after a tick: nothing stays loaded, and the tick is not loaded again.
        output.play(AlertSound.TICK)
        output.prepare(emptySet())
        output.stop()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(output.ready).isEmpty()
        assertThat(players).hasSize(3)
        assertThat(players.map { shadowOf(it).state }).containsExactly(
            ShadowMediaPlayer.State.END,
            ShadowMediaPlayer.State.END,
            ShadowMediaPlayer.State.END,
        )
    }

    @Test
    fun aLoadedSoundThatBrokeWhileWaitingIsDroppedAndTheCueLoadsItsOwn() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
        val players = mutableListOf<MediaPlayer>()
        val output = MediaPlayerSoundOutput(context, loader = { sound ->
            MediaPlayerSoundOutput.load(context, sound)?.also { players += it }
        })
        output.prepare(setOf(AlertSound.TICK))

        // The media server died while the tick waited.
        shadowOf(players.single()).invokeErrorListener(MediaPlayer.MEDIA_ERROR_SERVER_DIED, 0)
        assertThat(output.ready).isEmpty()
        assertThat(shadowOf(players.single()).state).isEqualTo(ShadowMediaPlayer.State.END)

        output.play(AlertSound.TICK)

        assertThat(players).hasSize(2)
        assertThat(output.isPlaying).isTrue()
    }

    @Test
    fun aLoadedSoundThatFailsWhenStartedIsLoadedAgainAndPlays() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
        val players = mutableListOf<MediaPlayer>()
        val output = MediaPlayerSoundOutput(context, loader = { sound ->
            MediaPlayerSoundOutput.load(context, sound)?.also { players += it }
        })
        output.prepare(setOf(AlertSound.TICK))
        output.play(AlertSound.TICK)

        // The start reported an error instead of playing (rule 8: the cue must still sound).
        shadowOf(players[0]).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)

        assertThat(shadowOf(players[0]).state).isEqualTo(ShadowMediaPlayer.State.END)
        assertThat(players).hasSize(2)
        assertThat(output.isPlaying).isTrue()
    }

    @Test
    fun aLoadedSoundWhoseStartThrowsIsLoadedAgainAndPlays() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
        var loads = 0
        val output = MediaPlayerSoundOutput(context, loader = { sound ->
            if (loads++ == 0) UnstartablePlayer() else MediaPlayerSoundOutput.load(context, sound)
        })
        output.prepare(setOf(AlertSound.TICK))

        output.play(AlertSound.TICK)

        assertThat(loads).isEqualTo(2)
        assertThat(output.isPlaying).isTrue()
    }

    @Test
    fun soundFailureDoesNotThrow() {
        // Without media info Robolectric's MediaPlayer cannot open the file; create() returns null.
        val output = MediaPlayerSoundOutput(context)

        output.play(AlertSound.TICK)

        assertThat(output.isPlaying).isFalse()
    }

    @Test
    fun hapticsUseTheWaveformWithNotificationUsage() {
        val haptics = VibratorHaptics(context)

        haptics.play(Haptic.ALERT)

        val shadow = shadowOf(vibrator)
        assertThat(shadow.isVibrating).isTrue()
        assertThat(shadow.pattern.toList()).isEqualTo(Haptic.ALERT.timings)
        assertThat(shadow.repeat).isEqualTo(-1)
        val attributes = shadow.vibrationAttributesFromLastVibration as VibrationAttributes
        assertThat(attributes.usage).isEqualTo(VibrationAttributes.USAGE_NOTIFICATION)

        haptics.cancel()
        assertThat(shadow.isVibrating).isFalse()
    }

    @Test
    fun everyAlertSoundHasABundledFile() {
        AlertSound.entries.forEach { sound ->
            context.resources.openRawResourceFd(sound.rawRes).use { assertThat(it.length).isGreaterThan(0L) }
        }
    }
}

/** A loaded player whose start fails, as one in a wrong state does. */
private class UnstartablePlayer : MediaPlayer() {
    override fun start(): Unit = throw IllegalStateException("start in a wrong state (test)")
}
