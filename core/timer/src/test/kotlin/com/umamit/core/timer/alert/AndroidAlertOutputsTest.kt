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

    /** The loader thread's tasks (D-096), run by [settle] on the test thread. */
    private val loaderThread = ArrayDeque<Runnable>()

    /** The output on the main looper (standing in for the alert thread) with [loaderThread] and [loader]. */
    private fun output(loader: (AlertSound) -> MediaPlayer? = { MediaPlayerSoundOutput.load(context, it) }) =
        MediaPlayerSoundOutput(context, loader = loader, background = loaderThread::addLast)

    /** Runs the loader thread's tasks and the alert thread's until both are done. */
    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
        while (loaderThread.isNotEmpty()) {
            loaderThread.removeFirst().run()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    @Test
    fun environmentReadsRingerModeCallStateAndDoNotDisturb() {
        val environment = AndroidAlertEnvironment(context)
        // Robolectric starts every stream at 0; a phone's alarm volume is above it.
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 5, 0)

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
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        assertThat(environment.conditions().dnd).isEqualTo(DoNotDisturb.OFF)
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        assertThat(environment.conditions().dnd).isEqualTo(DoNotDisturb.PRIORITY)
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        assertThat(environment.conditions().dnd).isEqualTo(DoNotDisturb.ALARMS_ONLY)
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        assertThat(environment.conditions().dnd).isEqualTo(DoNotDisturb.TOTAL_SILENCE)
    }

    @Test
    fun environmentReadsTheAlarmVolumeAtZero() {
        // TMR-31, D-098: the cue still runs at alarm volume 0; the reading goes into its log line.
        val environment = AndroidAlertEnvironment(context)

        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        assertThat(environment.conditions().alarmVolumeOff).isTrue()

        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 3, 0)
        assertThat(environment.conditions().alarmVolumeOff).isFalse()
    }

    @Test
    fun soundRequestsTransientDuckingFocusWithAlarmAttributes() {
        // D-098: both cues play on the phone's alarm volume (USAGE_ALARM), no longer the notification volume.
        val output = output()

        assertThat(output.requestFocus(onLost = {})).isTrue()

        val request = shadowOf(audioManager).lastAudioFocusRequest.audioFocusRequest
        assertThat(request.focusGain).isEqualTo(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        assertThat(request.audioAttributes.usage).isEqualTo(AudioAttributes.USAGE_ALARM)
        assertThat(request.audioAttributes.contentType).isEqualTo(AudioAttributes.CONTENT_TYPE_SONIFICATION)

        output.abandonFocus()
        assertThat(shadowOf(audioManager).lastAbandonedAudioFocusRequest).isSameInstanceAs(request)
    }

    @Test
    fun theSoundsLoadWithAlarmAttributes() {
        assertThat(MediaPlayerSoundOutput.ATTRIBUTES.usage).isEqualTo(AudioAttributes.USAGE_ALARM)
        assertThat(MediaPlayerSoundOutput.ATTRIBUTES.volumeControlStream).isEqualTo(AudioManager.STREAM_ALARM)
    }

    @Test
    fun eachPlayStartsItsSoundAtTheGainItIsGiven() {
        // SET-27: Lautstärke / 100 as the player's volume on top of the alarm stream, for a loaded sound
        // and for one loaded on the spot (D-092).
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
        val players = mutableListOf<MediaPlayer>()
        val output = output(loader = { sound -> MediaPlayerSoundOutput.load(context, sound)?.also { players += it } })
        output.prepare(setOf(AlertSound.TICK))
        settle()

        output.play(AlertSound.TICK, gain = 0.1f)
        assertThat(shadowOf(players[0]).leftVolume).isEqualTo(0.1f)
        assertThat(shadowOf(players[0]).rightVolume).isEqualTo(0.1f)

        output.play(AlertSound.GLOCKE, gain = 0.5f)
        val onTheSpot = players.last()
        assertThat(shadowOf(onTheSpot).leftVolume).isEqualTo(0.5f)
        assertThat(shadowOf(onTheSpot).rightVolume).isEqualTo(0.5f)
        settle()

        // The tick loaded again after its cue starts at the next cue's gain, not the last one's.
        output.play(AlertSound.TICK, gain = 1f)
        val reloaded = players.first { it !== players[0] && it !== onTheSpot }
        assertThat(shadowOf(reloaded).leftVolume).isEqualTo(1f)
        assertThat(output.isPlaying).isTrue()
    }

    @Test
    fun refusedFocusIsReported() {
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        assertThat(output().requestFocus(onLost = {})).isFalse()
    }

    @Test
    fun soundPlaysTheBundledFileAndStops() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(3_500, 0) }
        val output = output()

        output.play(AlertSound.KLANGSCHALE, gain = 1f)
        assertThat(output.isPlaying).isTrue()

        output.stop()
        assertThat(output.isPlaying).isFalse()
        output.stop()
    }

    @Test
    fun aLoadedSoundStartsWithoutLoadingAndIsLoadedAgainAfterwards() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
        val loads = mutableListOf<AlertSound>()
        val output = output(loader = {
            loads += it
            MediaPlayerSoundOutput.load(context, it)
        })

        output.prepare(setOf(AlertSound.TICK, AlertSound.KLANGSCHALE))
        // It returns at once: the loads run on the loader thread, never on the alert thread (D-096).
        assertThat(loads).isEmpty()
        assertThat(output.ready).isEmpty()
        settle()
        assertThat(loads).containsExactly(AlertSound.TICK, AlertSound.KLANGSCHALE).inOrder()
        assertThat(output.ready).containsExactly(AlertSound.TICK, AlertSound.KLANGSCHALE)
        loads.clear()

        output.play(AlertSound.TICK, gain = 1f)

        // The cue only started the loaded player (D-092); the next tick loads after the cue, on the loader thread.
        assertThat(loads).isEmpty()
        assertThat(output.isPlaying).isTrue()
        assertThat(output.ready).containsExactly(AlertSound.KLANGSCHALE)
        settle()
        assertThat(loads).containsExactly(AlertSound.TICK)
        assertThat(output.ready).containsExactly(AlertSound.TICK, AlertSound.KLANGSCHALE)
    }

    @Test
    fun aSoundThatIsNotLoadedStillPlaysLoadedOnPlay() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(3_500, 0) }
        val loads = mutableListOf<AlertSound>()
        val output = output(loader = {
            loads += it
            MediaPlayerSoundOutput.load(context, it)
        })
        output.prepare(setOf(AlertSound.TICK))
        settle()
        loads.clear()

        output.play(AlertSound.GLOCKE, gain = 1f)

        assertThat(loads).containsExactly(AlertSound.GLOCKE)
        assertThat(output.isPlaying).isTrue()
        settle()
        assertThat(output.ready).containsExactly(AlertSound.TICK)
    }

    @Test
    fun soundsNoLongerWantedAreReleased() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
        val players = mutableListOf<MediaPlayer>()
        val output = output(loader = { sound ->
            MediaPlayerSoundOutput.load(context, sound)?.also { players += it }
        })
        output.prepare(setOf(AlertSound.TICK, AlertSound.KLANGSCHALE))
        settle()

        // The tone setting changed: the old tone goes, the new one is loaded.
        output.prepare(setOf(AlertSound.TICK, AlertSound.GLOCKE))
        settle()
        assertThat(output.ready).containsExactly(AlertSound.TICK, AlertSound.GLOCKE)
        assertThat(players).hasSize(3)
        assertThat(shadowOf(players[1]).state).isEqualTo(ShadowMediaPlayer.State.END)

        // The timer ended right after a tick: nothing stays loaded, and the tick is not loaded again.
        output.play(AlertSound.TICK, gain = 1f)
        output.prepare(emptySet())
        output.stop()
        settle()

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
        val output = output(loader = { sound ->
            MediaPlayerSoundOutput.load(context, sound)?.also { players += it }
        })
        output.prepare(setOf(AlertSound.TICK))
        settle()

        // The media server died while the tick waited.
        shadowOf(players.single()).invokeErrorListener(MediaPlayer.MEDIA_ERROR_SERVER_DIED, 0)
        settle()
        assertThat(output.ready).isEmpty()
        assertThat(shadowOf(players.single()).state).isEqualTo(ShadowMediaPlayer.State.END)

        output.play(AlertSound.TICK, gain = 1f)

        assertThat(players).hasSize(2)
        assertThat(output.isPlaying).isTrue()
    }

    @Test
    fun aLoadedSoundThatFailsWhenStartedIsLoadedAgainAndPlays() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
        val players = mutableListOf<MediaPlayer>()
        val output = output(loader = { sound ->
            MediaPlayerSoundOutput.load(context, sound)?.also { players += it }
        })
        output.prepare(setOf(AlertSound.TICK))
        settle()
        output.play(AlertSound.TICK, gain = 1f)

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
        val output = output(loader = { sound ->
            if (loads++ == 0) UnstartablePlayer() else MediaPlayerSoundOutput.load(context, sound)
        })
        output.prepare(setOf(AlertSound.TICK))
        settle()

        output.play(AlertSound.TICK, gain = 1f)

        assertThat(loads).isEqualTo(2)
        assertThat(output.isPlaying).isTrue()
    }

    @Test
    fun aCueWhileItsSoundIsStillLoadingLoadsItsOwnAndTheLoadThatComesBackIsKept() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
        val players = mutableListOf<MediaPlayer>()
        val output = output(loader = { sound -> MediaPlayerSoundOutput.load(context, sound)?.also { players += it } })
        output.prepare(setOf(AlertSound.ZIMBEL))

        // The pre-alert comes before the loader thread has loaded its sound: loaded on the spot (D-092).
        output.play(AlertSound.ZIMBEL, gain = 1f)
        assertThat(players).hasSize(1)
        assertThat(output.isPlaying).isTrue()

        // The load asked for earlier arrives and stays loaded for the next cue; nothing is loaded twice ahead.
        settle()
        assertThat(players).hasSize(2)
        assertThat(output.ready).containsExactly(AlertSound.ZIMBEL)
        assertThat(shadowOf(players[1]).state).isNotEqualTo(ShadowMediaPlayer.State.END)
    }

    @Test
    fun aSoundNoLongerWantedWhenItsTurnOnTheLoaderComesIsNotLoaded() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
        val loads = mutableListOf<AlertSound>()
        val output = output(loader = {
            loads += it
            MediaPlayerSoundOutput.load(context, it)
        })

        // Two tone changes in a row: only the last one is loaded.
        output.prepare(setOf(AlertSound.GLOCKE))
        output.prepare(setOf(AlertSound.SANFT))
        settle()

        assertThat(loads).containsExactly(AlertSound.SANFT)
        assertThat(output.ready).containsExactly(AlertSound.SANFT)
    }

    @Test
    fun aLoadThatComesBackForASoundNoLongerWantedIsReleased() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
        val players = mutableListOf<MediaPlayer>()
        val output = output(loader = { sound -> MediaPlayerSoundOutput.load(context, sound)?.also { players += it } })
        output.prepare(setOf(AlertSound.GLOCKE))
        // The loader thread has loaded it; its player is on its way to the alert thread.
        loaderThread.removeFirst().run()

        output.prepare(setOf(AlertSound.SANFT))
        settle()

        assertThat(output.ready).containsExactly(AlertSound.SANFT)
        assertThat(shadowOf(players.first()).state).isEqualTo(ShadowMediaPlayer.State.END)
    }

    @Test
    fun anErrorOfALoadedPlayerReportedOnTheLoaderThreadIsHandledOnTheAlertThread() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
        val players = mutableListOf<MediaPlayer>()
        val output = output(loader = { sound -> MediaPlayerSoundOutput.load(context, sound)?.also { players += it } })
        output.prepare(setOf(AlertSound.TICK))
        settle()

        // A player made on the loader thread reports its errors there (MediaPlayer's own looper).
        Thread { shadowOf(players.single()).invokeErrorListener(MediaPlayer.MEDIA_ERROR_SERVER_DIED, 0) }
            .apply { start() }
            .join()
        assertThat(output.ready).containsExactly(AlertSound.TICK)

        settle()
        assertThat(output.ready).isEmpty()
        assertThat(shadowOf(players.single()).state).isEqualTo(ShadowMediaPlayer.State.END)
    }

    @Test
    fun soundFailureDoesNotThrow() {
        // Without media info Robolectric's MediaPlayer cannot open the file; create() returns null.
        val output = output()

        output.play(AlertSound.TICK, gain = 1f)

        assertThat(output.isPlaying).isFalse()
    }

    @Test
    fun hapticsUseTheWaveformWithAlarmUsage() {
        val haptics = VibratorHaptics(context)

        haptics.play(Haptic.ALERT)

        val shadow = shadowOf(vibrator)
        assertThat(shadow.isVibrating).isTrue()
        assertThat(shadow.pattern.toList()).isEqualTo(Haptic.ALERT.timings)
        assertThat(shadow.repeat).isEqualTo(-1)
        val attributes = shadow.vibrationAttributesFromLastVibration as VibrationAttributes
        assertThat(attributes.usage).isEqualTo(VibrationAttributes.USAGE_ALARM)

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
