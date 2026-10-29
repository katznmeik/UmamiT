package com.umamit.core.timer.alert

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.timer.controller.ScheduledTime
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerDiagnostic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

/**
 * The first tick and alert of a fresh process (D-092): the accuracy run's first step heard its
 * tick 1.2 s late because the cue loaded its sound first (`MediaPlayer.create` prepares
 * synchronously). With the production player and the real sound output, the sounds a session
 * needs are loaded when the controller keeps them ready, and a cue only starts them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirstCueTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val scheduler = FakeScheduler()
    private val diagnostics = FakeTimerDiagnostics()
    private val clock = FakeClock()
    private val settings = FakeSettingsRepository(UserSettings(preAlertSound = "zimbel"))

    /** Every load of a sound, in order. */
    private val loads = mutableListOf<AlertSound>()
    private val sound =
        MediaPlayerSoundOutput(context, loader = {
            loads += it
            MediaPlayerSoundOutput.load(context, it)
        })

    @Before
    fun setUp() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
    }

    @Test
    fun theFirstTickAndAlertOnlyStartTheSoundsLoadedWhenTheSessionStarted() = runTest {
        val playback = playback()

        playback.keepReady(true)
        assertThat(loads).containsExactly(AlertSound.ZIMBEL, AlertSound.KLANGSCHALE).inOrder()
        loads.clear()

        playback.playPreAlert(ScheduledTime(TimerAlarmKind.PRE_ALERT, clock.elapsed))
        assertThat(loads).isEmpty()
        assertThat(sound.isPlaying).isTrue()

        idleAlertThread()
        playback.startAlert(ScheduledTime(TimerAlarmKind.DEADLINE, clock.elapsed), onFinished = {})
        // Only the pre-alert sound was loaded again, after its own cue; the alert started its loaded tone.
        assertThat(loads).containsExactly(AlertSound.ZIMBEL)
        assertThat(sound.isPlaying).isTrue()

        val played = diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>()
        assertThat(played.map { it.start?.soundLoaded }).containsExactly(true, true).inOrder()
    }

    @Test
    fun aVorwarntonChangedDuringTheSessionIsLoadedAtOnceAndTheNextTickLoadsNothing() = runTest {
        // SET-26: the PO changes the sound in the settings while a session runs; no turn comes in between.
        val playback = playback()
        playback.keepReady(true)
        loads.clear()

        settings.update { it.copy(preAlertSound = "klangstab") }
        assertThat(loads).containsExactly(AlertSound.KLANGSTAB)
        assertThat(sound.ready).containsExactly(AlertSound.KLANGSTAB, AlertSound.KLANGSCHALE)
        loads.clear()

        playback.playPreAlert(ScheduledTime(TimerAlarmKind.PRE_ALERT, clock.elapsed))
        assertThat(loads).isEmpty()
        assertThat(sound.isPlaying).isTrue()
        val played = diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>().single()
        assertThat(played.tone).isEqualTo("klangstab")
        assertThat(played.start?.soundLoaded).isTrue()
    }

    @Test
    fun withoutASessionACueStillLoadsAndPlaysItsSound() = runTest {
        val playback = playback()

        playback.playPreAlert(ScheduledTime(TimerAlarmKind.PRE_ALERT, clock.elapsed))

        assertThat(loads).containsExactly(AlertSound.ZIMBEL)
        assertThat(sound.isPlaying).isTrue()
        val played = diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>().single()
        assertThat(played.start?.soundLoaded).isFalse()
    }

    /** The reloads the sound output posts after a cue (the main looper stands in for the alert thread). */
    private fun idleAlertThread() = shadowOf(Looper.getMainLooper()).idle()

    private fun TestScope.playback() = AlertPlayback(
        environment = FakeEnvironment(),
        sound = sound,
        haptics = FakeHaptics(scheduler),
        scheduler = scheduler,
        settings = settings.settings,
        scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        diagnostics = diagnostics,
        clock = clock::reading,
    )
}
