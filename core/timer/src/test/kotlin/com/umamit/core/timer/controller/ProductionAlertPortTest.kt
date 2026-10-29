package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.AlertLength
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.alert.AlertConditions
import com.umamit.core.timer.alert.AlertPlayback
import com.umamit.core.timer.alert.AlertSound
import com.umamit.core.timer.alert.ControllerAlertPort
import com.umamit.core.timer.alert.FakeEnvironment
import com.umamit.core.timer.alert.FakeHaptics
import com.umamit.core.timer.alert.FakeScheduler
import com.umamit.core.timer.alert.FakeSoundOutput
import com.umamit.core.timer.alert.Haptic
import com.umamit.core.timer.alert.RingerMode
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The controller with the production alert port (M1-10, #44): the real
 * engine, controller and ringer-mode player, with fake device outputs. The
 * player reports the end of each alert once (`AlertCompleted`, D-047), the
 * tick plays at most once per step, and both follow the settings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProductionAlertPortTest {
    private val scheduler = FakeScheduler()
    private val environment = FakeEnvironment(AlertConditions(RingerMode.NORMAL))
    private val sound = FakeSoundOutput(scheduler)
    private val haptics = FakeHaptics(scheduler)

    /** The scope of the end reports, so a test can wait until they are handled. */
    private val reports = CoroutineScope(SupervisorJob())
    private lateinit var playbackScope: CoroutineScope
    private lateinit var h: ControllerHarness

    @After
    fun tearDown() {
        // A report's turn runs to its end even when cancelled; it ends before the database closes (#50).
        runBlocking { reports.coroutineContext.job.cancelAndJoin() }
        if (::h.isInitialized) h.close()
    }

    @Test
    fun `AlertCompleted arrives once per alert and marks it played`() = runTest {
        harness()
        h.controller.startSession(brewStart(infusion(20), infusion(30)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(20)
        // The deadline alarm arrives after the in-process wake-up: no second alert.
        h.controller.onAlarm(TimerAlarmKind.DEADLINE)

        playAlertToTheEnd()

        assertThat(completions()).hasSize(1)
        assertThat(h.store.read()!!.activeBrew.alertPlayed).isTrue()
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactly(0L, 3_500L, 7_000L, 10_500L).inOrder()

        h.controller.send(TimerEvent.Poured)
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(30)
        playAlertToTheEnd()

        assertThat(completions()).hasSize(2)
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).hasSize(8)
        assertThat(h.diagnostics.entriesOf<TimerDiagnostic.StaleAlertCompletion>()).isEmpty()
    }

    @Test
    fun `the first tick and alert of a session start sounds loaded before them`() = runTest {
        harness()
        // The chosen Vorwarnton is the tick's sound (SET-26), loaded ahead like it (D-092).
        h.settings.update { it.copy(alertSound = "sanft", preAlertSound = "klangstab") }
        h.controller.startSession(brewStart(infusion(60)))
        assertThat(sound.wanted).containsExactly(AlertSound.KLANGSTAB, AlertSound.SANFT).inOrder()

        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(60)
        playAlertToTheEnd()

        assertThat(sound.plays(AlertSound.KLANGSTAB)).hasSize(1)
        assertThat(sound.plays(AlertSound.SANFT)).hasSize(5)
        assertThat(sound.loadedOnPlay).isEmpty()
        val started = h.diagnostics.entriesOf<TimerDiagnostic.AlertPlayed>().map { it.start?.soundLoaded }
        assertThat(started).containsExactly(true, true).inOrder()

        h.controller.send(TimerEvent.Poured)
        h.controller.send(TimerEvent.FinishSession)
        assertThat(sound.wanted).isEmpty()
    }

    @Test
    fun `an alert acknowledged before its end is not reported`() = runTest {
        harness()
        h.controller.startSession(brewStart(infusion(20), infusion(30)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(20)
        scheduler.advanceBy(5_000L)

        h.controller.send(TimerEvent.Poured)
        playAlertToTheEnd()

        assertThat(completions()).isEmpty()
        assertThat(sound.playing).isNull()
        assertThat(haptics.vibrating).isNull()
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactly(0L, 3_500L).inOrder()
        assertThat(h.active.phase).isEqualTo(TimerPhase.READY)
    }

    @Test
    fun `the tick is never repeated, however often the pre-alert is delivered`() = runTest {
        harness()
        h.settings.update { it.copy(preAlertSound = "ting") }
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)

        h.advanceSeconds(50)
        h.controller.onAlarm(TimerAlarmKind.PRE_ALERT)
        scheduler.advanceBy(2_000L)
        h.controller.onAlarm(TimerAlarmKind.PRE_ALERT)
        h.advanceSeconds(5)
        h.controller.onAlarm(TimerAlarmKind.PRE_ALERT)
        h.advanceSeconds(5)
        playAlertToTheEnd()

        assertThat(sound.plays(AlertSound.TICK)).hasSize(1)
        assertThat(haptics.plays(Haptic.PRE_ALERT)).hasSize(1)
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).hasSize(4)
        assertThat(completions()).hasSize(1)
    }

    @Test
    fun `the alert plays the stored tone and length and reports after it`() = runTest {
        harness()
        h.settings.update { it.copy(alertSound = "glocke", alertLength = AlertLength.SHORT) }
        h.controller.startSession(brewStart(infusion(20)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(20)

        scheduler.advanceBy(2_999L)
        awaitReports()
        assertThat(completions()).isEmpty()
        scheduler.advanceBy(1L)
        awaitReports()

        assertThat(sound.plays(AlertSound.GLOCKE)).containsExactly(0L)
        assertThat(completions()).hasSize(1)
    }

    @Test
    fun `a silent alert still runs its length and reports its end`() = runTest {
        harness()
        environment.conditions = AlertConditions(RingerMode.SILENT)
        h.controller.startSession(brewStart(infusion(20)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(20)

        playAlertToTheEnd()

        assertThat(sound.events).isEmpty()
        assertThat(haptics.events).isEmpty()
        assertThat(completions()).hasSize(1)
        assertThat(h.store.read()!!.activeBrew.alertPlayed).isTrue()
    }

    /** The harness with the production port (playback on the test thread, end reports in [reports]) and tea „t1". */
    private suspend fun TestScope.harness() {
        playbackScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        h = ControllerHarness(alertPort = {
            ControllerAlertPort(
                playback = AlertPlayback(
                    environment,
                    sound,
                    haptics,
                    scheduler,
                    settings.settings,
                    playbackScope,
                    diagnostics,
                    clock::reading,
                ),
                controller = controllerProvider,
                scope = reports,
            )
        })
        h.teaWithStock()
    }

    /** Lets the player run every cycle of the alert, then waits until its end report is handled. */
    private suspend fun playAlertToTheEnd() {
        scheduler.advanceBy(300_000L)
        awaitReports()
    }

    private suspend fun awaitReports() {
        reports.coroutineContext[Job]!!.children.toList().joinAll()
    }

    private fun completions() = h.diagnostics.entriesOf<TimerDiagnostic.EventHandled>()
        .filter { it.event == TimerEvent.AlertCompleted && it.source == EventSource.ALERT_PLAYER }
}
