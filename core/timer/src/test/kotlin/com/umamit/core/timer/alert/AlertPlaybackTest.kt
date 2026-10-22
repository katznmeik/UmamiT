package com.umamit.core.timer.alert

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.AlertLength
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * The production player behind the controller's alert port and *Alarm
 * testen* (M1-10, #44): it plays with the stored settings (SET-09, SET-11,
 * SET-14) through the ringer-mode player, with fake outputs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertPlaybackTest {
    private val scheduler = FakeScheduler()
    private val environment = FakeEnvironment()
    private val sound = FakeSoundOutput(scheduler)
    private val haptics = FakeHaptics(scheduler)
    private val settings = FakeSettingsRepository()
    private val diagnostics = FakeTimerDiagnostics()
    private val clock = FakeClock()

    @Test
    fun theAlertPlaysTheStoredToneLengthAndVibration() = runTest {
        settings.update { it.copy(alertSound = "holzblock", alertLength = AlertLength.SHORT, vibration = false) }
        val playback = playback(settings.settings)

        playback.startAlert(onFinished = {})
        scheduler.advanceBy(300_000L)

        assertThat(sound.plays(AlertSound.HOLZBLOCK)).containsExactly(0L)
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).isEmpty()
        assertThat(haptics.events).isEmpty()
    }

    @Test
    fun eachAlertReadsTheSettingsAgain() = runTest {
        val playback = playback(settings.settings)
        playback.startAlert(onFinished = {})
        scheduler.advanceBy(300_000L)

        settings.update { it.copy(alertSound = "glockenspiel", alertLength = AlertLength.SHORT) }
        playback.startAlert(onFinished = {})
        scheduler.advanceBy(300_000L)

        assertThat(sound.plays(AlertSound.KLANGSCHALE)).hasSize(4)
        assertThat(sound.plays(AlertSound.GLOCKENSPIEL)).containsExactly(300_000L)
    }

    @Test
    fun theEndOfTheAlertIsReportedOnceAfterItsLength() = runTest {
        settings.update { it.copy(alertSound = "sanft") }
        val playback = playback(settings.settings)
        val reported = mutableListOf<Long>()

        playback.startAlert(onFinished = { reported += scheduler.now })
        scheduler.advanceBy(300_000L)

        assertThat(reported).containsExactly(15_000L)
    }

    @Test
    fun aStoppedAlertIsNotReported() = runTest {
        val playback = playback(settings.settings)
        val reported = mutableListOf<Long>()
        playback.startAlert(onFinished = { reported += scheduler.now })
        scheduler.advanceBy(2_000L)

        playback.stopAlert()
        scheduler.advanceBy(300_000L)

        assertThat(reported).isEmpty()
        assertThat(sound.playing).isNull()
        assertThat(haptics.vibrating).isNull()
    }

    @Test
    fun theTickFollowsTheRingerModeAndTheVibrationSetting() = runTest {
        settings.update { it.copy(vibration = false) }
        val playback = playback(settings.settings)

        playback.playPreAlert()

        assertThat(sound.plays(AlertSound.TICK)).containsExactly(0L)
        assertThat(haptics.events).isEmpty()
    }

    @Test
    fun alarmTestenPlaysTheTickThenTheAlertWithTheCurrentSettings() = runTest {
        settings.update { it.copy(alertSound = "glocke", alertLength = AlertLength.SHORT) }
        val tester: AlertTester = playback(settings.settings)

        tester.testAlert()
        scheduler.advanceBy(300_000L)

        assertThat(sound.events.filter { it.startsWith("play") })
            .containsExactly("play TICK @ 0", "play GLOCKE @ 1500").inOrder()
        assertThat(haptics.events.filter { it.startsWith("play") })
            .containsExactly("play PRE_ALERT @ 0", "play ALERT @ 1500").inOrder()
    }

    @Test
    fun alarmTestenInSilentModeIsVisualOnly() = runTest {
        environment.conditions = AlertConditions(RingerMode.SILENT)
        val tester: AlertTester = playback(settings.settings)

        tester.testAlert()
        scheduler.advanceBy(300_000L)

        assertThat(sound.events).isEmpty()
        assertThat(haptics.events).isEmpty()
    }

    @Test
    fun stoppingTheTestLeavesARealAlertPlaying() = runTest {
        val playback = playback(settings.settings)
        val reported = mutableListOf<Long>()
        playback.startAlert(onFinished = { reported += scheduler.now })

        playback.testAlert()
        playback.stopTestAlert()
        scheduler.advanceBy(300_000L)

        assertThat(sound.plays(AlertSound.TICK)).isEmpty()
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).hasSize(4)
        assertThat(reported).containsExactly(14_000L)
    }

    @Test
    fun unreadableSettingsPlayTheDefaultAlert() = runTest {
        val broken: Flow<UserSettings> = flow { throw IOException("settings file corrupt (test)") }
        val playback = playback(broken)
        val reported = mutableListOf<Long>()

        playback.startAlert(onFinished = { reported += scheduler.now })
        scheduler.advanceBy(300_000L)

        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactly(0L, 3_500L, 7_000L, 10_500L).inOrder()
        assertThat(haptics.plays(Haptic.ALERT)).hasSize(4)
        assertThat(reported).containsExactly(14_000L)
    }

    @Test
    fun anUnknownToneKeyPlaysTheKlangschale() = runTest {
        settings.update { it.copy(alertSound = "gong", alertLength = AlertLength.SHORT) }
        val playback = playback(settings.settings)

        playback.startAlert(onFinished = {})

        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactly(0L)
    }

    @Test
    fun eachAlertTickAndTestLogsTheRingerModeAndTheOutputsItUsed() = runTest {
        settings.update { it.copy(alertSound = "glocke", alertLength = AlertLength.LONG, vibration = false) }
        val playback = playback(settings.settings)

        playback.playPreAlert()
        scheduler.advanceBy(5_000L)
        environment.conditions = AlertConditions(RingerMode.NORMAL, inCall = true)
        playback.startAlert(onFinished = {})
        playback.stopAlert()
        environment.conditions = AlertConditions(RingerMode.SILENT)
        playback.testAlert()

        val played = diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>()
        assertThat(played.map { it.cue }).containsExactly(AlertCue.PRE_ALERT, AlertCue.ALERT, AlertCue.TEST).inOrder()
        assertThat(played.map { it.conditions }).containsExactly(
            AlertConditions(RingerMode.NORMAL),
            AlertConditions(RingerMode.NORMAL, inCall = true),
            AlertConditions(RingerMode.SILENT),
        ).inOrder()
        assertThat(played.map { it.outputs }).containsExactly(
            AlertOutputs(sound = true, vibration = false),
            AlertOutputs(sound = false, vibration = true),
            AlertOutputs.NONE,
        ).inOrder()
        assertThat(played.map { it.tone }).containsExactly("tick", "glocke", "glocke").inOrder()
        assertThat(played.map { it.length }).containsExactly(AlertLength.LONG, AlertLength.LONG, AlertLength.LONG)
        assertThat(played.first().at).isEqualTo(clock.reading())
    }

    @Test
    fun aDiagnosticsPortThatThrowsNeverStopsTheAlert() = runTest {
        val playback = playback(settings.settings, diagnostics = { error("log broken (test)") })
        val reported = mutableListOf<Long>()

        playback.startAlert(onFinished = { reported += scheduler.now })
        scheduler.advanceBy(300_000L)

        assertThat(sound.plays(AlertSound.KLANGSCHALE)).hasSize(4)
        assertThat(reported).containsExactly(14_000L)
    }

    /** The playback on the test thread: its commands run as soon as they are sent. */
    private fun TestScope.playback(
        settings: Flow<UserSettings>,
        diagnostics: TimerDiagnosticsPort = this@AlertPlaybackTest.diagnostics,
    ) = AlertPlayback(
        environment = environment,
        sound = sound,
        haptics = haptics,
        scheduler = scheduler,
        settings = settings,
        scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        diagnostics = diagnostics,
        clock = clock::reading,
    )
}
