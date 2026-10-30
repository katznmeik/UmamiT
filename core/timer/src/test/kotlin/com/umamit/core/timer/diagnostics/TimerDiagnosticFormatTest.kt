package com.umamit.core.timer.diagnostics

import android.app.ForegroundServiceStartNotAllowedException
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.AlertLength
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeClock
import com.umamit.core.timer.alert.AlertConditions
import com.umamit.core.timer.alert.AlertCue
import com.umamit.core.timer.alert.AlertOutputs
import com.umamit.core.timer.alert.CueStart
import com.umamit.core.timer.alert.DoNotDisturb
import com.umamit.core.timer.alert.RingerMode
import com.umamit.core.timer.controller.RecoveryDecision
import com.umamit.core.timer.controller.RecoveryTrigger
import com.umamit.core.timer.controller.ScheduledTime
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerDiagnostic
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The log lines of the adapters' entries (06 §5.6): the ringer mode at each
 * alert, the service's starts, stops and refusals, the permission states, and
 * an early wall-clock alarm. The clock reads monotonic 5 000 000, boot 1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerDiagnosticFormatTest {
    private val now = FakeClock().reading()

    @Test
    fun `recovery logs what triggered it and what it decided`() {
        val lines = listOf(
            TimerDiagnostic.RecoveryRequested(now, RecoveryTrigger.BOOT_COMPLETED, alreadyRecovered = false),
            TimerDiagnostic.RecoveryRequested(now, RecoveryTrigger.APP_START, alreadyRecovered = true),
            TimerDiagnostic.Recovered(
                now,
                from = TimerPhase.RUNNING,
                to = TimerPhase.ALERTING,
                rebooted = true,
                decision = RecoveryDecision.ALERT_REPLAYED,
                service = true,
            ),
            TimerDiagnostic.Recovered(
                now,
                from = TimerPhase.PAUSED,
                to = null,
                rebooted = false,
                decision = RecoveryDecision.SESSION_ENDED,
                service = false,
            ),
            TimerDiagnostic.OrphanSessionEnded(now, saved = true),
            TimerDiagnostic.RecoveryRetriesStopped(now, failingForMs = 660_500),
        ).map(TimerDiagnosticFormat::message)

        assertThat(lines).containsExactly(
            "recovery requested by BOOT_COMPLETED | mono 5000000 boot 1",
            "recovery requested by APP_START: already done in this process | mono 5000000 boot 1",
            "recovery: stored RUNNING from before a reboot -> ALERTING: alert replayed, with the service | " +
                "mono 5000000 boot 1",
            "recovery: stored PAUSED -> IDLE: session ended, without the service | mono 5000000 boot 1",
            "recovery: a session left in progress without its timer was saved | mono 5000000 boot 1",
            "recovery: retries stopped after 660 s of failed reads; the next input reads again | " +
                "mono 5000000 boot 1",
        ).inOrder()
    }

    @Test
    fun `a refused start sent again and refused once more says so`() {
        val entry = TimerDiagnostic.ServiceStartRefused(
            now,
            notificationId = 1,
            error = ForegroundServiceStartNotAllowedException("background"),
            again = true,
        )

        assertThat(TimerDiagnosticFormat.message(entry))
            .isEqualTo(
                "service: foreground start with notification 1 refused again: " +
                    "ForegroundServiceStartNotAllowedException: background | mono 5000000 boot 1",
            )
    }

    @Test
    fun `an alert shows the ringer mode, call, Do Not Disturb, the alarm volume and the outputs it used`() {
        val entry = TimerDiagnostic.AlertPlayed(
            now,
            AlertCue.ALERT,
            AlertConditions(RingerMode.VIBRATE, inCall = false, dnd = DoNotDisturb.OFF),
            AlertOutputs(sound = false, vibration = true),
            tone = "klangschale",
            length = AlertLength.NORMAL,
            volumePercent = 60,
        )

        assertThat(TimerDiagnosticFormat.message(entry)).isEqualTo(
            "alert ALERT: ringer VIBRATE, in call no, DND off, alarm volume on -> vibration | " +
                "tone klangschale, length NORMAL, gain 60 % | mono 5000000 boot 1",
        )
    }

    @Test
    fun `each Do Not Disturb mode and an alarm volume at 0 are named`() {
        // D-098: how One UI reports the ringer mode under each DND mode is read from these lines on the phone.
        fun message(dnd: DoNotDisturb, alarmVolumeOff: Boolean = false) = TimerDiagnosticFormat.message(
            TimerDiagnostic.AlertPlayed(
                now,
                AlertCue.ALERT,
                AlertConditions(RingerMode.SILENT, dnd = dnd, alarmVolumeOff = alarmVolumeOff),
                AlertOutputs.NONE,
                tone = "klangschale",
                length = AlertLength.NORMAL,
            ),
        )

        assertThat(message(DoNotDisturb.PRIORITY)).contains("DND priority, alarm volume on ->")
        assertThat(message(DoNotDisturb.ALARMS_ONLY)).contains("DND alarms only, alarm volume on ->")
        assertThat(message(DoNotDisturb.TOTAL_SILENCE, alarmVolumeOff = true))
            .contains("DND total silence, alarm volume 0 ->")
    }

    @Test
    fun `a silent pre-alert is visual only`() {
        val entry = TimerDiagnostic.AlertPlayed(
            now,
            AlertCue.PRE_ALERT,
            AlertConditions(RingerMode.SILENT, inCall = true, dnd = DoNotDisturb.TOTAL_SILENCE),
            AlertOutputs.NONE,
            tone = "tick",
            length = AlertLength.SHORT,
        )

        assertThat(TimerDiagnosticFormat.message(entry)).startsWith(
            "alert PRE_ALERT: ringer SILENT, in call yes, DND total silence, alarm volume on -> visual only",
        )
    }

    @Test
    fun `a step's cue shows where its start spent the time before the delay to its sound`() {
        fun entry(start: CueStart) = TimerDiagnostic.AlertPlayed(
            now,
            AlertCue.PRE_ALERT,
            AlertConditions(RingerMode.NORMAL),
            AlertOutputs(sound = true, vibration = true),
            tone = "tick",
            length = AlertLength.NORMAL,
            scheduled = ScheduledTime(TimerAlarmKind.PRE_ALERT, now.elapsedMs - 17),
            start = start,
        )

        assertThat(TimerDiagnosticFormat.message(entry(CueStart(2, 0, 15, soundLoaded = true)))).isEqualTo(
            "alert PRE_ALERT: ringer NORMAL, in call no, DND off, alarm volume on -> sound + vibration | " +
                "tone tick, length NORMAL, gain 100 % | queued 2 ms, settings 0 ms, outputs 15 ms, sound ready | " +
                "PRE_ALERT scheduled 09:59:59.983 (mono 4999983) actual 10:00:00.000 (mono 5000000) delta +17 ms",
        )
        assertThat(TimerDiagnosticFormat.message(entry(CueStart(1, 300, 900, soundLoaded = false))))
            .contains("| queued 1 ms, settings 300 ms, outputs 900 ms, sound loaded on play |")
        assertThat(TimerDiagnosticFormat.message(entry(CueStart(1, 0, 3, soundLoaded = null))))
            .contains("| queued 1 ms, settings 0 ms, outputs 3 ms, no sound |")
    }

    @Test
    fun `the service's start, stop and refusal`() {
        assertThat(TimerDiagnosticFormat.message(TimerDiagnostic.ServiceInForeground(now, 1)))
            .isEqualTo("service: in the foreground with notification 1 | mono 5000000 boot 1")
        assertThat(TimerDiagnosticFormat.message(TimerDiagnostic.ServiceStopped(now)))
            .isEqualTo("service: stopped | mono 5000000 boot 1")
        assertThat(TimerDiagnosticFormat.message(TimerDiagnostic.ServiceTookOverStale(now, 1))).isEqualTo(
            "service: took over the stale foreground notification 1 of a killed process to remove it | " +
                "mono 5000000 boot 1",
        )
        val refused = TimerDiagnostic.ServiceStartRefused(
            now,
            2,
            ForegroundServiceStartNotAllowedException("app is in the background"),
        )
        assertThat(TimerDiagnosticFormat.message(refused)).isEqualTo(
            "service: foreground start with notification 2 refused: " +
                "ForegroundServiceStartNotAllowedException: app is in the background | mono 5000000 boot 1",
        )
    }

    @Test
    fun `the permission states at a process start`() {
        val entry = TimerDiagnostic.PermissionsAtStart(
            now,
            PermissionStates(
                notifications = true,
                promotedNotifications = null,
                fullScreenIntent = false,
                exactAlarms = true,
                batteryUnrestricted = false,
                alarmVolumeOn = false,
            ),
        )

        assertThat(TimerDiagnosticFormat.message(entry)).isEqualTo(
            "process start: notifications yes, live updates n/a, full-screen alert no, exact alarms yes, " +
                "battery unrestricted no, alarm volume 0 | mono 5000000 boot 1",
        )
    }

    @Test
    fun `an alarm on the wall clock that fires early shows a negative delta`() {
        val entry = TimerDiagnostic.AlarmReceived(
            now,
            TimerAlarmKind.IDLE_DEMOTE,
            ScheduledTime(TimerAlarmKind.IDLE_DEMOTE, now.wallMs + 250),
        )

        assertThat(TimerDiagnosticFormat.message(entry)).isEqualTo(
            "alarm IDLE_DEMOTE received | " +
                "IDLE_DEMOTE scheduled 10:00:00.250 (mono 5000250) actual 10:00:00.000 (mono 5000000) delta -250 ms",
        )
    }
}
