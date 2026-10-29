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
 * needs are loaded when the controller keeps them ready, and a cue only starts them. The loads
 * run on the loader thread, so a cue never waits behind one (D-096).
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

    /** How long each load takes on [clock], as a cold `MediaPlayer.create` can (D-092). */
    private var loadMs = 0L

    /** Runs at the start of each load, while it takes its time: e.g. an alarm that comes meanwhile. */
    private var duringLoad: (AlertSound) -> Unit = {}

    /** The loader thread's tasks, run by [settle] on the test thread. */
    private val loaderThread = ArrayDeque<Runnable>()

    private val sound =
        MediaPlayerSoundOutput(
            context,
            loader = {
                loads += it
                duringLoad(it)
                clock.advanceBy(loadMs)
                MediaPlayerSoundOutput.load(context, it)
            },
            background = loaderThread::addLast,
        )

    @Before
    fun setUp() {
        ShadowMediaPlayer.setMediaInfoProvider { _: DataSource -> ShadowMediaPlayer.MediaInfo(800, 0) }
    }

    @Test
    fun theFirstTickAndAlertOnlyStartTheSoundsLoadedWhenTheSessionStarted() = runTest {
        val playback = playback()

        playback.keepReady(true)
        // Asked for, not loaded on the alert thread (D-096).
        assertThat(loads).isEmpty()
        settle()
        assertThat(loads).containsExactly(AlertSound.ZIMBEL, AlertSound.KLANGSCHALE).inOrder()
        loads.clear()

        playback.playPreAlert(ScheduledTime(TimerAlarmKind.PRE_ALERT, clock.elapsed))
        assertThat(loads).isEmpty()
        assertThat(sound.isPlaying).isTrue()

        settle()
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
        settle()
        loads.clear()

        settings.update { it.copy(preAlertSound = "klangstab") }
        settle()
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

    @Test
    fun aCueThatComesWhileAToneChangedInTheSettingsLoadsIsNotDelayedByThatLoad() = runTest {
        // Rule 8, D-096: the PO picks another tone while a step runs, and the step's pre-alert comes due while the
        // new tone loads. The pre-alert's own sound is loaded: it starts at once, not after the tone's load.
        val playback = playback()
        playback.keepReady(true)
        settle()
        loadMs = 800
        duringLoad = { loading ->
            if (loading == AlertSound.GLOCKENSPIEL) {
                playback.playPreAlert(ScheduledTime(TimerAlarmKind.PRE_ALERT, clock.elapsed))
            }
        }

        settings.update { it.copy(alertSound = "glockenspiel") }
        settle()

        val played = diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>().single()
        assertThat(played.start).isEqualTo(CueStart(queuedMs = 0, settingsMs = 0, outputsMs = 0, soundLoaded = true))
        assertThat(played.scheduled?.lateMs(played.at)).isEqualTo(0L)
        // The new tone is still loaded ahead for the alert, the old one released.
        assertThat(sound.ready).containsExactly(AlertSound.ZIMBEL, AlertSound.GLOCKENSPIEL)
    }

    @Test
    fun aCueWhoseNewSoundHasNotLoadedYetLoadsItOnTheSpotAndPlaysIt() = runTest {
        // D-092's fallback stays: the Vorwarnton was changed just before the pre-alert, whose load has not come back.
        val playback = playback()
        playback.keepReady(true)
        settle()
        settings.update { it.copy(preAlertSound = "klangstab") }
        loads.clear()

        playback.playPreAlert(ScheduledTime(TimerAlarmKind.PRE_ALERT, clock.elapsed))

        assertThat(loads).containsExactly(AlertSound.KLANGSTAB)
        assertThat(sound.isPlaying).isTrue()
        val played = diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>().single()
        assertThat(played.tone).isEqualTo("klangstab")
        assertThat(played.start?.soundLoaded).isFalse()
        // The load asked for by the settings change still arrives, for the next step's pre-alert.
        settle()
        assertThat(sound.ready).containsExactly(AlertSound.KLANGSTAB, AlertSound.KLANGSCHALE)
    }

    /**
     * Runs the loader thread's loads and the alert thread's tasks (the main looper stands in for
     * it) until both are done; the test thread stands in for both threads.
     */
    private fun settle() {
        val alertThread = shadowOf(Looper.getMainLooper())
        alertThread.idle()
        while (loaderThread.isNotEmpty()) {
            loaderThread.removeFirst().run()
            alertThread.idle()
        }
    }

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
