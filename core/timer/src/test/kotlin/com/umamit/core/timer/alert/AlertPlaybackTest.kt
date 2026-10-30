package com.umamit.core.timer.alert

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.AlertLength
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.timer.controller.ScheduledTime
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
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

    // „Ting (leise)", the tick of M1, unless a test chooses another Vorwarnton (SET-26).
    private val settings = FakeSettingsRepository(UserSettings(preAlertSound = "ting"))
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
    fun oneFailedSettingsReadDoesNotFreezeTheSettingsOfLaterAlerts() = runTest {
        var reads = 0
        val flaky: Flow<UserSettings> = flow {
            if (reads++ == 0) throw IOException("settings file unreadable once (test)")
            emitAll(settings.settings)
        }
        val playback = playback(flaky)
        playback.startAlert(onFinished = {})
        scheduler.advanceBy(300_000L)
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).hasSize(4)

        // The file is read again after the retry delay and then followed (D-068, SET-09, SET-11, SET-14).
        testScheduler.advanceTimeBy(AlertPlayback.FIRST_RETRY_MS + 1)
        settings.update { it.copy(alertSound = "glockenspiel", alertLength = AlertLength.SHORT, vibration = false) }
        val hapticsBefore = haptics.plays(Haptic.ALERT).size
        playback.startAlert(onFinished = {})
        scheduler.advanceBy(300_000L)

        assertThat(sound.plays(AlertSound.GLOCKENSPIEL)).containsExactly(300_000L)
        assertThat(haptics.plays(Haptic.ALERT)).hasSize(hapticsBefore)
    }

    @Test
    fun whileATimerIsActiveTheTickAndTheStoredToneStayLoaded() = runTest {
        settings.update { it.copy(alertSound = "glocke") }
        val playback = playback(settings.settings)

        playback.keepReady(true)
        assertThat(sound.wanted).containsExactly(AlertSound.TICK, AlertSound.GLOCKE).inOrder()

        playback.playPreAlert()
        playback.startAlert(onFinished = {})
        scheduler.advanceBy(300_000L)
        assertThat(sound.plays(AlertSound.TICK)).hasSize(1)
        assertThat(sound.plays(AlertSound.GLOCKE)).isNotEmpty()
        assertThat(sound.loadedOnPlay).isEmpty()

        playback.keepReady(false)
        assertThat(sound.wanted).isEmpty()
    }

    @Test
    fun aToneChangedInTheSettingsIsLoadedWithTheNextCall() = runTest {
        val playback = playback(settings.settings)
        playback.keepReady(true)

        settings.update { it.copy(alertSound = "holzblock") }
        playback.keepReady(true)

        assertThat(sound.wanted).containsExactly(AlertSound.TICK, AlertSound.HOLZBLOCK).inOrder()
    }

    @Test
    fun theChosenPreAlertSoundIsTheOneLoadedAheadAndPlayed() = runTest {
        // SET-26, D-092: the Vorwarnton replaces the tick in the loading ahead and in the pre-alert.
        settings.update { it.copy(preAlertSound = "klangstab", alertSound = "glocke") }
        val playback = playback(settings.settings)

        playback.keepReady(true)
        assertThat(sound.wanted).containsExactly(AlertSound.KLANGSTAB, AlertSound.GLOCKE).inOrder()

        playback.playPreAlert()
        assertThat(sound.plays(AlertSound.KLANGSTAB)).containsExactly(0L)
        assertThat(sound.plays(AlertSound.TICK)).isEmpty()
        assertThat(sound.loadedOnPlay).isEmpty()
        assertThat(diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>().single().start?.soundLoaded).isTrue()
    }

    @Test
    fun aPreAlertSoundChangedWhileATimerIsActiveIsLoadedAtOnceAndTheOldOneReleased() = runTest {
        settings.update { it.copy(preAlertSound = "zimbel") }
        val playback = playback(settings.settings)
        playback.keepReady(true)
        assertThat(sound.wanted).containsExactly(AlertSound.ZIMBEL, AlertSound.KLANGSCHALE).inOrder()

        // Changed in the settings while the session runs: no turn of the controller comes in between.
        settings.update { it.copy(preAlertSound = "tiefe_schale") }
        assertThat(sound.wanted).containsExactly(AlertSound.TIEFE_SCHALE, AlertSound.KLANGSCHALE).inOrder()

        playback.playPreAlert()
        assertThat(sound.plays(AlertSound.TIEFE_SCHALE)).containsExactly(0L)
        assertThat(sound.loadedOnPlay).isEmpty()
    }

    @Test
    fun aToneChangedWhileATimerIsActiveIsLoadedAtOnce() = runTest {
        val playback = playback(settings.settings)
        playback.keepReady(true)

        settings.update { it.copy(alertSound = "glockenspiel") }

        assertThat(sound.wanted).containsExactly(AlertSound.TICK, AlertSound.GLOCKENSPIEL).inOrder()
    }

    @Test
    fun whileNoTimerIsActiveAChangedSoundIsNotLoaded() = runTest {
        val playback = playback(settings.settings)
        playback.keepReady(true)
        playback.keepReady(false)
        val prepares = sound.prepares.toList()

        settings.update { it.copy(preAlertSound = "gloeckchen", alertSound = "sanft") }
        // Other settings changes never load anything either.
        settings.update { it.copy(keepScreenOn = false) }

        assertThat(sound.wanted).isEmpty()
        assertThat(sound.prepares).isEqualTo(prepares)
    }

    @Test
    fun alarmTestenPlaysTheChosenPreAlertSoundToItsEndThenTheAlert() = runTest {
        settings.update { it.copy(preAlertSound = "gloeckchen", alertSound = "sanft", alertLength = AlertLength.SHORT) }
        val tester: AlertTester = playback(settings.settings)

        tester.testAlert()
        scheduler.advanceBy(300_000L)

        // Glöckchen 3.0 s, then the pause of 0.7 s (SET-31).
        assertThat(sound.events.filter { it.startsWith("play") })
            .containsExactly("play GLOECKCHEN @ 0", "play SANFT @ 3700").inOrder()
        assertThat(diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>().map { it.tone }).containsExactly("sanft")
    }

    @Test
    fun aPreAlertLogsTheKeyOfTheSoundItPlayed() = runTest {
        settings.update { it.copy(preAlertSound = "tiefe_schale") }
        val playback = playback(settings.settings)

        playback.playPreAlert()

        assertThat(diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>().map { it.tone }).containsExactly("tiefe_schale")
    }

    @Test
    fun anUnknownPreAlertSoundKeyPlaysTheDefault() = runTest {
        settings.update { it.copy(preAlertSound = "gong") }
        val playback = playback(settings.settings)

        playback.playPreAlert()

        assertThat(sound.plays(PreAlertSound.DEFAULT.sound)).containsExactly(0L)
    }

    @Test
    fun loadingTheSoundsNeverWaitsForTheSettingsFile() = runTest {
        val playback = playback(flow { awaitCancellation() })

        playback.keepReady(true)

        // No 300 ms wait (SETTINGS_WAIT_MS) as a cue has: the defaults' sounds are loaded at once.
        assertThat(sound.prepares).containsExactly("prepare [${PreAlertSound.DEFAULT.sound}, KLANGSCHALE] @ 0")
        assertThat(currentTime).isEqualTo(0L)
    }

    @Test
    fun aCueLogsWhereItsStartSpentTheTimeAndWhetherItsSoundWasLoaded() = runTest {
        val slow = SlowSoundOutput(sound, clock, playMs = 40)
        val playback = playback(settings.settings, sound = slow, queued = true)
        playback.keepReady(true)
        runCurrent()

        playback.playPreAlert(ScheduledTime(TimerAlarmKind.PRE_ALERT, clock.elapsed))
        clock.advanceBy(7)
        runCurrent()
        playback.keepReady(false)
        runCurrent()
        playback.startAlert(ScheduledTime(TimerAlarmKind.DEADLINE, clock.elapsed), onFinished = {})
        runCurrent()
        environment.conditions = AlertConditions(RingerMode.VIBRATE)
        playback.playPreAlert(ScheduledTime(TimerAlarmKind.PRE_ALERT, clock.elapsed))
        runCurrent()
        playback.testAlert()
        runCurrent()

        val played = diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>()
        assertThat(played.map { it.start }).containsExactly(
            CueStart(queuedMs = 7, settingsMs = 0, outputsMs = 40, soundLoaded = true),
            CueStart(queuedMs = 0, settingsMs = 0, outputsMs = 40, soundLoaded = false),
            CueStart(queuedMs = 0, settingsMs = 0, outputsMs = 0, soundLoaded = null),
            null,
        ).inOrder()
        assertThat(played.first().scheduled?.lateMs(played.first().at)).isEqualTo(47L)
    }

    @Test
    fun theLautstaerkeIsReadAtTheStartOfEachCueSoAChangeDuringATimerAppliesToTheNextCue() = runTest {
        // SET-27, 04 §9.1 (D-098): the gain is read with the other settings at the start of every cue.
        settings.update { it.copy(alertVolumePercent = 100) }
        val playback = playback(settings.settings)
        playback.keepReady(true)
        val prepares = sound.prepares.toList()

        playback.startAlert(onFinished = {})
        scheduler.advanceBy(2_000L)
        settings.update { it.copy(alertVolumePercent = 40) }
        scheduler.advanceBy(300_000L)
        playback.playPreAlert()

        assertThat(sound.gains).containsExactly(
            "KLANGSCHALE 1.0 @ 0",
            "KLANGSCHALE 1.0 @ 3500",
            "KLANGSCHALE 1.0 @ 7000",
            "KLANGSCHALE 1.0 @ 10500",
            "TICK 0.4 @ 302000",
        ).inOrder()
        // A new Lautstärke loads nothing: the sounds stay loaded ahead, and the tick started without a load (D-092).
        assertThat(sound.prepares).isEqualTo(prepares)
        assertThat(sound.loadedOnPlay).isEmpty()
        assertThat(diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>().map { it.volumePercent })
            .containsExactly(100, 40)
            .inOrder()
    }

    @Test
    fun alarmTestenPlaysAtTheStoredLautstaerke() = runTest {
        settings.update { it.copy(alertSound = "glocke", alertLength = AlertLength.SHORT, alertVolumePercent = 20) }
        val tester: AlertTester = playback(settings.settings)

        tester.testAlert()
        scheduler.advanceBy(300_000L)

        assertThat(sound.gains).containsExactly("TICK 0.2 @ 0", "GLOCKE 0.2 @ 1500").inOrder()
        assertThat(diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>().single().volumePercent).isEqualTo(20)
    }

    @Test
    fun atAlarmVolumeZeroTheCueStillRunsAndItsLogSaysSo() = runTest {
        // TMR-31, E18 (D-098): the outputs run as the matrix allows; the volume is read for the log only.
        environment.alarmVolumeOff = true
        settings.update { it.copy(alertLength = AlertLength.SHORT) }
        val playback = playback(settings.settings)

        playback.startAlert(onFinished = {})
        environment.alarmVolumeOff = false
        playback.playPreAlert()

        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactly(0L)
        assertThat(haptics.plays(Haptic.ALERT)).containsExactly(0L)
        assertThat(diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>().map { it.alarmVolumeOff })
            .containsExactly(true, false)
            .inOrder()
    }

    @Test
    fun aStoredLautstaerkeBelowTenPercentStillPlaysAtTenPercent() = runTest {
        // Rule 8: the app itself never silences a cue, whatever the file holds (05 §6).
        settings.update { it.copy(alertVolumePercent = 0) }
        val playback = playback(settings.settings)

        playback.playPreAlert()

        assertThat(sound.gains).containsExactly("TICK 0.1 @ 0")
    }

    @Test
    fun theSettingsRetryBacksOffUpToAMinute() {
        assertThat((0L..7L).map { AlertPlayback.retryDelayMs(it) })
            .containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L).inOrder()
    }

    @Test
    fun aStepsAlertAndTickLogTheirScheduledTimeForTheDelayToTheSound() = runTest {
        val playback = playback(settings.settings)
        val due = ScheduledTime(TimerAlarmKind.DEADLINE, clock.elapsed - 140)

        playback.playPreAlert(ScheduledTime(TimerAlarmKind.PRE_ALERT, clock.elapsed - 9))
        playback.startAlert(due, onFinished = {})
        playback.testAlert()

        val played = diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>()
        assertThat(played.map { it.scheduled?.lateMs(it.at) }).containsExactly(9L, 140L, null).inOrder()
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

    /**
     * The playback on the test thread: its commands run as soon as they are sent, or, when
     * [queued], only when the test lets the scheduler run (`runCurrent`), as on the alert thread.
     */
    private fun TestScope.playback(
        settings: Flow<UserSettings>,
        diagnostics: TimerDiagnosticsPort = this@AlertPlaybackTest.diagnostics,
        sound: AlertSoundOutput = this@AlertPlaybackTest.sound,
        queued: Boolean = false,
    ) = AlertPlayback(
        environment = environment,
        sound = sound,
        haptics = haptics,
        scheduler = scheduler,
        settings = settings,
        scope = CoroutineScope(
            backgroundScope.coroutineContext +
                if (queued) StandardTestDispatcher(testScheduler) else UnconfinedTestDispatcher(testScheduler),
        ),
        diagnostics = diagnostics,
        clock = clock::reading,
    )
}

/** [sound], whose every play takes [playMs] on [clock], as a slow device does. */
private class SlowSoundOutput(
    private val sound: FakeSoundOutput,
    private val clock: FakeClock,
    private val playMs: Long,
) : AlertSoundOutput by sound {
    override fun play(sound: AlertSound, gain: Float) {
        clock.advanceBy(playMs)
        this.sound.play(sound, gain)
    }
}
