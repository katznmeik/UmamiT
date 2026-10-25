package com.umamit.core.timer.diagnostics

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.AlertLength
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeClock
import com.umamit.core.timer.alert.AlertConditions
import com.umamit.core.timer.alert.AlertCue
import com.umamit.core.timer.alert.AlertOutputs
import com.umamit.core.timer.alert.RingerMode
import com.umamit.core.timer.controller.EventSource
import com.umamit.core.timer.controller.ScheduledTime
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The accuracy workflow's reading of the diagnostics log and its verdict (04 §12, TMR-30, #52):
 * the log lines of the pre-alert and the alert, handled and started (D-068), parse back into
 * fire times, and the report's nearest-rank distribution of the sound starts, verdict, CSV and
 * Markdown.
 */
class AccuracyReportTest {
    private val clock = FakeClock()

    @Test
    fun `an accepted time-based event's log line parses back into its fire time`() {
        clock.advanceBy(80)
        val line = TimerDiagnosticFormat.message(
            handled(TimerEvent.DeadlineReached, EventSource.WAKE_UP, TransitionOutcome.Accepted, lateMs = 80),
        )

        val fire = FireTime.parse(line)

        assertThat(fire).isEqualTo(
            FireTime(
                kind = TimerAlarmKind.DEADLINE,
                source = "WAKE_UP",
                scheduledWall = "10:00:00.000",
                scheduledMonoMs = 5_000_000,
                actualWall = "10:00:00.080",
                actualMonoMs = 5_000_080,
                deltaMs = 80,
            ),
        )
    }

    @Test
    fun `the pre-alert from an alarm parses with its kind and source`() {
        clock.advanceBy(3)
        val line = TimerDiagnosticFormat.message(
            handled(TimerEvent.PreAlertReached, EventSource.ALARM, TransitionOutcome.Accepted, lateMs = 3),
        )

        val fire = FireTime.parse(line)!!

        assertThat(fire.kind).isEqualTo(TimerAlarmKind.PRE_ALERT)
        assertThat(fire.source).isEqualTo("ALARM")
        assertThat(fire.deltaMs).isEqualTo(3)
    }

    @Test
    fun `an ignored event, a user event and other entries are no fire times`() {
        val ignored = TimerDiagnosticFormat.message(
            handled(TimerEvent.DeadlineReached, EventSource.ALARM, TransitionOutcome.Ignored, lateMs = 0),
        )
        val pause = TimerDiagnosticFormat.message(
            TimerDiagnostic.EventHandled(
                clock.reading(),
                TimerEvent.Pause,
                EventSource.APP,
                TransitionOutcome.Accepted,
                TimerPhase.RUNNING,
                TimerPhase.PAUSED,
                scheduled = null,
            ),
        )
        val alarm = TimerDiagnosticFormat.message(
            TimerDiagnostic.AlarmReceived(
                clock.reading(),
                TimerAlarmKind.DEADLINE,
                ScheduledTime(TimerAlarmKind.DEADLINE, clock.elapsed),
            ),
        )

        assertThat(FireTime.parse(ignored)).isNull()
        assertThat(FireTime.parse(pause)).isNull()
        assertThat(FireTime.parse(alarm)).isNull()
        assertThat(FireTime.parse("")).isNull()
        assertThat(FireTime.parse(TimerDiagnosticFormat.message(played(AlertCue.ALERT, lateMs = 5)))).isNull()
    }

    @Test
    fun `the start of the alert's sound parses back with its delay from the deadline`() {
        clock.advanceBy(140)
        val line = TimerDiagnosticFormat.message(played(AlertCue.ALERT, lateMs = 140))

        val fire = FireTime.parsePlayed(line)

        assertThat(fire).isEqualTo(
            FireTime(
                kind = TimerAlarmKind.DEADLINE,
                source = FireTime.PLAYER,
                scheduledWall = "10:00:00.000",
                scheduledMonoMs = 5_000_000,
                actualWall = "10:00:00.140",
                actualMonoMs = 5_000_140,
                deltaMs = 140,
            ),
        )
    }

    @Test
    fun `the tick's start parses as the pre-alert, a test or an event line does not`() {
        clock.advanceBy(9)
        val tick = TimerDiagnosticFormat.message(played(AlertCue.PRE_ALERT, lateMs = 9))
        val test = TimerDiagnosticFormat.message(played(AlertCue.TEST, lateMs = null))
        val event = TimerDiagnosticFormat.message(
            handled(TimerEvent.DeadlineReached, EventSource.ALARM, TransitionOutcome.Accepted, lateMs = 0),
        )

        assertThat(FireTime.parsePlayed(tick)?.kind).isEqualTo(TimerAlarmKind.PRE_ALERT)
        assertThat(FireTime.parsePlayed(tick)?.deltaMs).isEqualTo(9)
        assertThat(FireTime.parsePlayed(test)).isNull()
        assertThat(FireTime.parsePlayed(event)).isNull()
        assertThat(FireTime.parsePlayed("")).isNull()
    }

    @Test
    fun `percentiles are nearest-rank over the absolute delays`() {
        val stats = DelayStats.of((1L..100L).toList().shuffled() + listOf(-1L))!!

        assertThat(stats.count).isEqualTo(101)
        // ceil(0.5 × 101) = 51st and ceil(0.95 × 101) = 96th of 1, 1, 2, 3, …, 100.
        assertThat(stats.p50Ms).isEqualTo(50)
        assertThat(stats.p95Ms).isEqualTo(95)
        assertThat(stats.maxMs).isEqualTo(100)
        assertThat(DelayStats.of(emptyList())).isNull()
        assertThat(DelayStats.of(listOf(7L))).isEqualTo(DelayStats(1, 7, 7, 7))
    }

    @Test
    fun `the target is p95 at most 250 ms and max at most 1 s, both inclusive`() {
        assertThat(DelayStats(50, 10, 250, 1_000).onTarget).isTrue()
        assertThat(DelayStats(50, 10, 251, 300).onTarget).isFalse()
        assertThat(DelayStats(50, 10, 20, 1_001).onTarget).isFalse()
    }

    @Test
    fun `fifty steps on time pass`() {
        val report = AccuracyReport(RUN, (1..50).map { step(it, preAlertMs = it.toLong(), alertMs = 2L * it) })

        assertThat(report.complete).isTrue()
        assertThat(report.preAlert).isEqualTo(DelayStats(50, 25, 48, 50))
        assertThat(report.alert).isEqualTo(DelayStats(50, 50, 96, 100))
        assertThat(report.passed).isTrue()
    }

    @Test
    fun `the gate measures the start of the sound, not the handled event (D-068)`() {
        // Handled within 5 ms, but the sound started 1.5 s late (a slow persist or player queue).
        val steps = (1..50).map { step(it, preAlertMs = 5, alertMs = 5, alertPlayedMs = 1_500) }

        val report = AccuracyReport(RUN, steps)

        assertThat(report.alertHandled).isEqualTo(DelayStats(50, 5, 5, 5))
        assertThat(report.alert).isEqualTo(DelayStats(50, 1_500, 1_500, 1_500))
        assertThat(report.passed).isFalse()
        assertThat(report.markdown()).contains("| Alert | 50 / 50 | 1500 ms | 1500 ms | 1500 ms | 5 ms / 5 ms |")
    }

    @Test
    fun `a sound start is paired only with its own cue`() {
        val handled = fire(TimerAlarmKind.DEADLINE, "ALARM", "10:01:00", 60_000, 3)
        val otherStep = fire(TimerAlarmKind.DEADLINE, FireTime.PLAYER, "10:02:10", 130_000, 3)
        val tick = fire(TimerAlarmKind.PRE_ALERT, FireTime.PLAYER, "10:01:00", 60_000, 3)

        assertThrows(IllegalArgumentException::class.java) { CueTimes(handled, otherStep) }
        assertThrows(IllegalArgumentException::class.java) { CueTimes(handled, tick) }
        assertThrows(IllegalArgumentException::class.java) { CueTimes(handled, handled) }
    }

    @Test
    fun `one alert over a second fails the run`() {
        val steps = (1..50).map { step(it, preAlertMs = 5, alertMs = if (it == 17) 1_200L else 5L) }

        val report = AccuracyReport(RUN, steps)

        assertThat(report.alert!!.maxMs).isEqualTo(1_200)
        assertThat(report.passed).isFalse()
    }

    @Test
    fun `a slow pre-alert p95 fails the run`() {
        val steps = (1..50).map { step(it, preAlertMs = if (it <= 3) 400L else 10L, alertMs = 5) }

        assertThat(AccuracyReport(RUN, steps).passed).isFalse()
    }

    @Test
    fun `a run that stopped early fails even when its steps were on time`() {
        val report = AccuracyReport(RUN, (1..49).map { step(it, preAlertMs = 1, alertMs = 1) })

        assertThat(report.complete).isFalse()
        assertThat(report.passed).isFalse()
        assertThat(report.markdown()).contains("Only 49 of 50 steps completed")
        assertThat(AccuracyReport(RUN, emptyList()).passed).isFalse()
    }

    @Test
    fun `the CSV has one row per cue in order, with the handled event and the sound start`() {
        val csv = AccuracyReport(RUN, listOf(step(1, preAlertMs = 12, alertMs = 34, alertPlayedMs = 56)))
            .csv().lines()

        assertThat(csv).containsExactly(
            "step,event,source,scheduled_wall,scheduled_mono_ms," +
                "handled_wall,handled_mono_ms,handled_delta_ms,played_wall,played_mono_ms,played_delta_ms",
            "1,PRE_ALERT,WAKE_UP,10:00:50.000,50000,10:00:50.012,50012,12,10:00:50.012,50012,12",
            "1,DEADLINE,ALARM,10:01:00.000,60000,10:01:00.034,60034,34,10:01:00.056,60056,56",
            "",
        ).inOrder()
    }

    @Test
    fun `the Markdown summary shows the verdict, the distribution and the sources`() {
        val markdown = AccuracyReport(RUN, (1..50).map { step(it, preAlertMs = 10, alertMs = 20) }).markdown()

        assertThat(markdown).contains("### Timer accuracy · API 36")
        assertThat(markdown).contains("50 × 60 s steps with the screen off, pre-alert 10 s")
        assertThat(markdown).contains("**Result: PASS** · target p95 ≤ 250 ms, max ≤ 1000 ms")
        assertThat(
            markdown,
        ).contains("| Pre-alert | 50 / 50 | 10 ms | 10 ms | 10 ms | 10 ms / 10 ms | WAKE_UP 50 | yes |")
        assertThat(markdown).contains("| Alert | 50 / 50 | 20 ms | 20 ms | 20 ms | 20 ms / 20 ms | ALARM 50 | yes |")
        assertThat(markdown).contains("Delay = when the sound started − scheduled time")
        assertThat(markdown).doesNotContain("Gate check")
    }

    @Test
    fun `a gate check run says so and fails`() {
        val run = RUN.copy(injectedDelayMs = 1_500)
        val report = AccuracyReport(run, (1..50).map { step(it, preAlertMs = 1_510, alertMs = 1_520) })

        assertThat(report.passed).isFalse()
        assertThat(report.markdown()).contains("**Gate check:** every pre-alert and alert was delayed by 1500 ms")
        assertThat(report.markdown()).contains("**Result: FAIL**")
        assertThat(report.markdown()).contains("| **no** |")
    }

    private fun handled(event: TimerEvent, source: EventSource, outcome: TransitionOutcome, lateMs: Long) =
        TimerDiagnostic.EventHandled(
            at = clock.reading(),
            event = event,
            source = source,
            outcome = outcome,
            from = TimerPhase.RUNNING,
            to = TimerPhase.RUNNING,
            scheduled = ScheduledTime(TimerAlarmKind.of(event)!!, clock.elapsed - lateMs),
        )

    private fun played(cue: AlertCue, lateMs: Long?) = TimerDiagnostic.AlertPlayed(
        at = clock.reading(),
        cue = cue,
        conditions = AlertConditions(RingerMode.NORMAL, inCall = false, dndBlocksAlerts = false),
        outputs = AlertOutputs(sound = true, vibration = true),
        tone = "klangschale",
        length = AlertLength.NORMAL,
        scheduled = lateMs?.let {
            ScheduledTime(
                if (cue ==
                    AlertCue.PRE_ALERT
                ) {
                    TimerAlarmKind.PRE_ALERT
                } else {
                    TimerAlarmKind.DEADLINE
                },
                clock.elapsed - it,
            )
        },
    )

    /** A step whose events were handled [preAlertMs] and [alertMs] late, the sounds started then or as given. */
    private fun step(
        step: Int,
        preAlertMs: Long,
        alertMs: Long,
        preAlertPlayedMs: Long = preAlertMs,
        alertPlayedMs: Long = alertMs,
    ) = StepFireTimes(
        step = step,
        preAlert = CueTimes(
            handled = fire(TimerAlarmKind.PRE_ALERT, "WAKE_UP", "10:00:50", 50_000, preAlertMs),
            played = fire(TimerAlarmKind.PRE_ALERT, FireTime.PLAYER, "10:00:50", 50_000, preAlertPlayedMs),
        ),
        alert = CueTimes(
            handled = fire(TimerAlarmKind.DEADLINE, "ALARM", "10:01:00", 60_000, alertMs),
            played = fire(TimerAlarmKind.DEADLINE, FireTime.PLAYER, "10:01:00", 60_000, alertPlayedMs),
        ),
    )

    private fun fire(kind: TimerAlarmKind, source: String, wall: String, mono: Long, deltaMs: Long) = FireTime(
        kind = kind,
        source = source,
        scheduledWall = "$wall.000",
        scheduledMonoMs = mono,
        actualWall = "$wall.${deltaMs.toString().padStart(3, '0').takeLast(3)}",
        actualMonoMs = mono + deltaMs,
        deltaMs = deltaMs,
    )

    private companion object {
        val RUN = AccuracyRun(apiLevel = 36, plannedSteps = 50, stepSeconds = 60, preAlertSeconds = 10)
    }
}
