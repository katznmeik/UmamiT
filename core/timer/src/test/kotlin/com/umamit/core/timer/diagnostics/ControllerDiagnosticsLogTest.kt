package com.umamit.core.timer.diagnostics

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.diagnostics.DiagnosticsEntry
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.timer.controller.ControllerHarness
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.brewStart
import com.umamit.core.timer.controller.infusion
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The controller's diagnostics written to the real log (06 §5.6, TMR-30,
 * M1-20): each pre-alert and deadline with its scheduled and actual fire
 * time on both clocks and the difference in ms, read back as the *Diagnose*
 * screen shows it. The fake clock starts at 10:00:00 in Berlin, monotonic
 * 5 000 000 ms.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ControllerDiagnosticsLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val log by lazy {
        DiagnosticsLog(folder.newFolder("diagnostics"), DiagnosticsLog.MAX_BYTES, Dispatchers.IO)
    }
    private val h by lazy { ControllerHarness(diagnosticsPort = LogTimerDiagnostics(log)) }

    @After
    fun tearDown() = h.close()

    @Test
    fun `a pre-alert and a deadline from their alarms are logged with scheduled and actual times`() = runTest {
        h.teaWithStock()
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)

        // The pre-alert (10 s before the end) arrives 80 ms late, the deadline 40 ms late.
        h.clock.advanceBy(50_080)
        h.controller.onAlarm(TimerAlarmKind.PRE_ALERT)
        h.clock.advanceBy(9_960)
        h.controller.onAlarm(TimerAlarmKind.DEADLINE)

        val entries = log.entries()
        assertThat(entries.messageOf("event PreAlertReached")).isEqualTo(
            "event PreAlertReached from ALARM: RUNNING -> RUNNING accepted | " +
                "PRE_ALERT scheduled 10:00:50.000 (mono 5050000) actual 10:00:50.080 (mono 5050080) delta +80 ms",
        )
        assertThat(entries.timeOf("event PreAlertReached")).isEqualTo("2026-10-22 10:00:50.080 +02:00")
        assertThat(entries.messageOf("event DeadlineReached")).isEqualTo(
            "event DeadlineReached from ALARM: RUNNING -> ALERTING accepted | " +
                "DEADLINE scheduled 10:01:00.000 (mono 5060000) actual 10:01:00.040 (mono 5060040) delta +40 ms",
        )
        assertThat(entries.messageOf("alarm DEADLINE")).isEqualTo(
            "alarm DEADLINE received | " +
                "DEADLINE scheduled 10:01:00.000 (mono 5060000) actual 10:01:00.040 (mono 5060040) delta +40 ms",
        )
    }

    @Test
    fun `the in-process wake-up is logged on time, and user events with the clocks they were read at`() = runTest {
        h.teaWithStock()
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)

        h.advanceSeconds(60)

        val entries = log.entries()
        assertThat(entries.messageOf("event Start ")).isEqualTo(
            "event Start from APP: READY -> RUNNING accepted | mono 5000000 boot 1",
        )
        assertThat(entries.messageOf("event DeadlineReached")).isEqualTo(
            "event DeadlineReached from WAKE_UP: RUNNING -> ALERTING accepted | " +
                "DEADLINE scheduled 10:01:00.000 (mono 5060000) actual 10:01:00.000 (mono 5060000) delta +0 ms",
        )
    }

    @Test
    fun `an ignored or refused event is logged with its outcome`() = runTest {
        h.teaWithStock()
        h.controller.startSession(brewStart(infusion(60)))

        h.controller.send(TimerEvent.Pause)
        h.controller.startQuickTimer(60)

        val entries = log.entries()
        assertThat(entries.messageOf("event StartSession")).isEqualTo(
            "event StartSession (1 step) from APP: IDLE -> READY accepted | mono 5000000 boot 1",
        )
        assertThat(entries.messageOf("event Pause")).isEqualTo(
            "event Pause from APP: READY -> READY ignored | mono 5000000 boot 1",
        )
        assertThat(entries.messageOf("event StartQuickTimer")).isEqualTo(
            "event StartQuickTimer (60 s, pre-alert auto, base 10 s, lead 10 s) from APP: " +
                "READY -> READY rejected: ActiveTimerExists | mono 5000000 boot 1",
        )
    }

    /**
     * A quick timer's start records its effective *Vorwarnung* (TMR-22, D-097, #109, #116): SCR-07's choice, the
     * global B and the resulting lead, so a report of a silent pre-alert can be settled from *Diagnose*.
     */
    @Test
    fun `a quick timer's start is logged with its choice, the global Vorwarnung and the resulting lead`() = runTest {
        val starts = listOf(
            Triple(120, null, 10),
            Triple(120, 0, 10),
            Triple(120, 20, 10),
            Triple(20, 20, 10),
            Triple(120, null, 0),
        )
        starts.forEach { (seconds, choice, base) ->
            h.settings.update { it.copy(preAlertSeconds = base) }
            h.controller.startQuickTimer(seconds, choice)
            h.controller.send(TimerEvent.Cancel)
        }

        assertThat(log.entries().map { it.message }.filter { it.startsWith("event StartQuickTimer") }).containsExactly(
            "event StartQuickTimer (120 s, pre-alert auto, base 10 s, lead 10 s) from APP: IDLE -> READY accepted | " +
                "mono 5000000 boot 1",
            "event StartQuickTimer (120 s, pre-alert off, base 10 s, lead none) from APP: IDLE -> READY accepted | " +
                "mono 5000000 boot 1",
            "event StartQuickTimer (120 s, pre-alert 20 s, base 10 s, lead 20 s) from APP: IDLE -> READY accepted | " +
                "mono 5000000 boot 1",
            "event StartQuickTimer (20 s, pre-alert 20 s, base 10 s, lead none) from APP: IDLE -> READY accepted | " +
                "mono 5000000 boot 1",
            "event StartQuickTimer (120 s, pre-alert auto, base 0 s, lead none) from APP: IDLE -> READY accepted | " +
                "mono 5000000 boot 1",
        )
    }

    @Test
    fun `no note text reaches the log`() = runTest {
        h.teas.saveTea(
            Tea(
                id = ControllerHarness.TEA_ID,
                name = ControllerHarness.TEA_NAME,
                category = TeaCategory.GREEN,
                form = TeaForm.LOOSE_LEAF,
                notes = NOTE,
            ),
        )
        h.controller.startSession(brewStart(infusion(30), infusion(20)))
        h.controller.send(TimerEvent.Start)
        h.store.failNext(2)
        h.advanceSeconds(30)
        h.controller.send(TimerEvent.Poured)
        h.controller.send(TimerEvent.FinishSession)

        val text = log.entries().joinToString("\n") { it.line }
        assertThat(text).contains("persist failed")
        assertThat(text).contains("event FinishSession")
        assertThat(text).doesNotContain(NOTE)
    }

    private fun List<DiagnosticsEntry>.entryOf(prefix: String) = single { it.message.startsWith(prefix) }

    private fun List<DiagnosticsEntry>.messageOf(prefix: String) = entryOf(prefix).message

    private fun List<DiagnosticsEntry>.timeOf(prefix: String) = entryOf(prefix).time

    private companion object {
        const val NOTE = "Nur mit Oma trinken"
    }
}
