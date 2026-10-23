package com.umamit.core.timer.alert

import android.app.Application
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.AudioManager
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
