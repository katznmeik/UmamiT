package com.umamit.core.timer.diagnostics

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.AlertLength
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeClock
import com.umamit.core.timer.alert.AlertConditions
import com.umamit.core.timer.alert.AlertCue
import com.umamit.core.timer.alert.AlertOutputs
import com.umamit.core.timer.alert.CueStart
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
    fun `a quick timer's handled event and sound start parse back with its slot, the brew's as the brew's`() {
        clock.advanceBy(25)
        val quickTick = TimerDiagnosticFormat.message(
            handled(TimerEvent.PreAlertReached, EventSource.ALARM, TransitionOutcome.Accepted, 25, TimerSlot.QUICK),
        )
        val quickAlert = TimerDiagnosticFormat.message(played(AlertCue.ALERT, lateMs = 25, slot = TimerSlot.QUICK))
        val brewAlert = TimerDiagnosticFormat.message(played(AlertCue.ALERT, lateMs = 25))

        assertThat(FireTime.parse(quickTick)).isEqualTo(
            FireTime(
                kind = TimerAlarmKind.PRE_ALERT,
                source = "ALARM",
                scheduledWall = "10:00:00.000",
                scheduledMonoMs = 5_000_000,
                actualWall = "10:00:00.025",
                actualMonoMs = 5_000_025,
                deltaMs = 25,
                slot = TimerSlot.QUICK,
            ),
        )
        assertThat(FireTime.parsePlayed(quickAlert)?.slot).isEqualTo(TimerSlot.QUICK)
        assertThat(FireTime.parsePlayed(quickAlert)?.kind).isEqualTo(TimerAlarmKind.DEADLINE)
        assertThat(FireTime.parsePlayed(brewAlert)?.slot).isEqualTo(TimerSlot.BREW)
    }

    /**
     * A quick deadline reached while the brew alerts is handled as a hold (04 §9.3, D-098): its line is the
     * quick timer's handled deadline, so a held alert shows in the report as handled on time but sounding late.
     */
    @Test
    fun `the quick timer's deadline held behind the brew's alert parses as its handled deadline`() {
        clock.advanceBy(40)
        val hold = TimerDiagnosticFormat.message(
            TimerDiagnostic.EventHandled(
                at = clock.reading(),
                event = TimerEvent.AlertHold,
                source = EventSource.WAKE_UP,
                outcome = TransitionOutcome.Accepted,
                from = TimerPhase.RUNNING,
                to = TimerPhase.ALERTING,
                scheduled = ScheduledTime(TimerAlarmKind.DEADLINE, clock.elapsed - 40, TimerSlot.QUICK),
            ),
        )
        val ringStart = TimerDiagnosticFormat.message(
            TimerDiagnostic.EventHandled(
                at = clock.reading(),
                event = TimerEvent.AlertRingStart,
                source = EventSource.ARBITRATION,
                outcome = TransitionOutcome.Accepted,
                from = TimerPhase.ALERTING,
                to = TimerPhase.ALERTING,
                scheduled = null,
            ),
        )

        val fire = FireTime.parse(hold)!!

        assertThat(fire.kind).isEqualTo(TimerAlarmKind.DEADLINE)
        assertThat(fire.slot).isEqualTo(TimerSlot.QUICK)
        assertThat(fire.source).isEqualTo("WAKE_UP")
        assertThat(fire.deltaMs).isEqualTo(40)
        assertThat(FireTime.parse(ringStart)).isNull()
    }

    @Test
    fun `a sound start parses back with where its start spent the time (D-092)`() {
        clock.advanceBy(1_178)
        val line = TimerDiagnosticFormat.message(
            played(AlertCue.PRE_ALERT, lateMs = 1_178)
                .copy(start = CueStart(queuedMs = 2, settingsMs = 0, outputsMs = 1_150, soundLoaded = false)),
        )

        val fire = FireTime.parsePlayed(line)!!

        assertThat(fire.deltaMs).isEqualTo(1_178)
        assertThat(fire.scheduledMonoMs).isEqualTo(5_000_000)
        assertThat(fire.start).isEqualTo("queued 2 ms, settings 0 ms, outputs 1150 ms, sound loaded on play")
        assertThat(FireTime.parsePlayed(TimerDiagnosticFormat.message(played(AlertCue.ALERT, lateMs = 5)))?.start)
            .isNull()
    }

    @Test
    fun `the summary names each event's slowest start and where its time went`() {
        val slow = fire(TimerAlarmKind.PRE_ALERT, FireTime.PLAYER, "10:00:50", 50_000, 1_178)
            .copy(start = "queued 2 ms, settings 0 ms, outputs 1150 ms, sound loaded on play")
        val steps = (1..50).map { step(it, preAlertMs = 2, alertMs = 3, alertPlayedMs = if (it == 9) 40L else 20L) }
            .toMutableList()
        steps[0] = steps[0].copy(preAlert = steps[0].preAlert.copy(played = slow))

        val markdown = AccuracyReport(RUN, steps).markdown()

        assertThat(markdown).contains(
            "Slowest pre-alert: step 1, 1178 ms (handled 2 ms, WAKE_UP; " +
                "queued 2 ms, settings 0 ms, outputs 1150 ms, sound loaded on play)",
        )
        assertThat(markdown).contains("Slowest alert: step 9, 40 ms (handled 3 ms, ALARM)")
        assertThat(steps[0].preAlert.summary()).isEqualTo(
            "1178 ms (handled 2 ms, WAKE_UP; queued 2 ms, settings 0 ms, outputs 1150 ms, sound loaded on play)",
        )
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
        val otherTimer = fire(TimerAlarmKind.DEADLINE, FireTime.PLAYER, "10:01:00", 60_000, 3, TimerSlot.QUICK)

        assertThrows(IllegalArgumentException::class.java) { CueTimes(handled, otherStep) }
        assertThrows(IllegalArgumentException::class.java) { CueTimes(handled, tick) }
        assertThrows(IllegalArgumentException::class.java) { CueTimes(handled, handled) }
        assertThrows(IllegalArgumentException::class.java) { CueTimes(handled, otherTimer) }
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
    fun `an alongside run passes with both timers on target, and its summary lists both`() {
        val brew = (1..50).map { step(it, preAlertMs = 10, alertMs = 20) }
        val quick = (1..50).map { quick(it, preAlertMs = 5, alertMs = 15) }

        val report = AccuracyReport(ALONGSIDE, brew, quick)
        val markdown = report.markdown()

        assertThat(report.passed).isTrue()
        assertThat(report.quick?.alert).isEqualTo(DelayStats(50, 15, 15, 15))
        assertThat(markdown).contains("### Timer accuracy · API 36 · alongside")
        assertThat(markdown).contains(
            "50 × 60 s steps with the screen off, pre-alert 10 s (04 §12, TMR-30), each with a quick timer " +
                "alongside (D-098): one of 25 s with pre-alert 10 s from the step's start, " +
                "then one of 300 s across the step's pre-alert and alert.",
        )
        assertThat(markdown).contains("**Result: PASS** · target p95 ≤ 250 ms, max ≤ 1000 ms, for both timers")
        assertThat(
            markdown,
        ).contains("| Timer | Event | Steps | p50 | p95 | max | Handled p95 / max | Fired by | On target |")
        assertThat(
            markdown,
        ).contains("| Brew | Pre-alert | 50 / 50 | 10 ms | 10 ms | 10 ms | 10 ms / 10 ms | WAKE_UP 50 | yes |")
        assertThat(
            markdown,
        ).contains("| Brew | Alert | 50 / 50 | 20 ms | 20 ms | 20 ms | 20 ms / 20 ms | ALARM 50 | yes |")
        assertThat(
            markdown,
        ).contains("| Quick timer | Pre-alert | 50 / 50 | 5 ms | 5 ms | 5 ms | 5 ms / 5 ms | ALARM 50 | yes |")
        assertThat(
            markdown,
        ).contains("| Quick timer | Alert | 50 / 50 | 15 ms | 15 ms | 15 ms | 15 ms / 15 ms | WAKE_UP 50 | yes |")
        assertThat(markdown).contains("Slowest brew alert: step 1, 20 ms (handled 20 ms, ALARM)")
        assertThat(markdown).contains("Slowest quick timer pre-alert: step 1, 5 ms (handled 5 ms, ALARM)")
    }

    @Test
    fun `a late quick timer fails an alongside run although the brew was on time`() {
        val brew = (1..50).map { step(it, preAlertMs = 10, alertMs = 20) }
        val quick = (1..50).map {
            quick(
                it,
                preAlertMs = 5,
                alertMs = 15,
                alertPlayedMs = if (it ==
                    7
                ) {
                    1_200L
                } else {
                    15L
                },
            )
        }

        val report = AccuracyReport(ALONGSIDE, brew, quick)

        assertThat(report.brew.onTarget).isTrue()
        assertThat(report.passed).isFalse()
        assertThat(report.markdown()).contains("**Result: FAIL**")
        assertThat(
            report.markdown(),
        ).contains("| Quick timer | Alert | 50 / 50 | 15 ms | 15 ms | 1200 ms | 15 ms / 15 ms |")
        assertThat(report.markdown()).contains("Slowest quick timer alert: step 7, 1200 ms (handled 15 ms, WAKE_UP)")
    }

    @Test
    fun `an alongside run fails when a quick timer did not complete`() {
        val brew = (1..50).map { step(it, preAlertMs = 1, alertMs = 1) }
        val quick = (1..49).map { quick(it, preAlertMs = 1, alertMs = 1) }

        val report = AccuracyReport(ALONGSIDE, brew, quick)

        assertThat(report.complete).isFalse()
        assertThat(report.passed).isFalse()
        assertThat(report.markdown()).contains("Only 49 of 50 quick timers completed")
        assertThat(AccuracyReport(ALONGSIDE, brew).passed).isFalse()
    }

    @Test
    fun `the alongside CSV names each row's timer, in the order the cues happened`() {
        val csv = AccuracyReport(
            ALONGSIDE.copy(plannedSteps = 1),
            listOf(step(1, preAlertMs = 12, alertMs = 34, alertPlayedMs = 56)),
            listOf(quick(1, preAlertMs = 7, alertMs = 9)),
        ).csv().lines()

        assertThat(csv).containsExactly(
            "step,timer,event,source,scheduled_wall,scheduled_mono_ms," +
                "handled_wall,handled_mono_ms,handled_delta_ms,played_wall,played_mono_ms,played_delta_ms",
            "1,QUICK,PRE_ALERT,ALARM,10:00:15.000,15000,10:00:15.007,15007,7,10:00:15.007,15007,7",
            "1,QUICK,DEADLINE,WAKE_UP,10:00:25.000,25000,10:00:25.009,25009,9,10:00:25.009,25009,9",
            "1,BREW,PRE_ALERT,WAKE_UP,10:00:50.000,50000,10:00:50.012,50012,12,10:00:50.012,50012,12",
            "1,BREW,DEADLINE,ALARM,10:01:00.000,60000,10:01:00.034,60034,34,10:01:00.056,60056,56",
            "",
        ).inOrder()
    }

    @Test
    fun `a report takes each timer's own cues only`() {
        val brew = listOf(step(1, preAlertMs = 1, alertMs = 1))
        val quick = listOf(quick(1, preAlertMs = 1, alertMs = 1))

        assertThrows(IllegalArgumentException::class.java) { AccuracyReport(RUN.copy(plannedSteps = 1), brew, quick) }
        assertThrows(IllegalArgumentException::class.java) { AccuracyReport(ALONGSIDE, quick, quick) }
        assertThrows(IllegalArgumentException::class.java) { AccuracyReport(ALONGSIDE, brew, brew) }
    }

    /** The standard run's summary as every run before v0.3.0 wrote it, so their results stay comparable (#137). */
    @Test
    fun `the standard run's summary is the one of the runs before`() {
        val steps =
            listOf(step(1, preAlertMs = 12, alertMs = 34, alertPlayedMs = 56), step(2, preAlertMs = 8, alertMs = 20))

        val markdown = AccuracyReport(RUN.copy(plannedSteps = 2), steps).markdown()

        assertThat(markdown).isEqualTo(
            listOf(
                "### Timer accuracy · API 36",
                "",
                "2 × 60 s steps with the screen off, pre-alert 10 s (04 §12, TMR-30). " +
                    "Delay = when the sound started − scheduled time, from the diagnostics log (D-060, D-068); " +
                    "the controller's handling of the event is shown for comparison.",
                "",
                "**Result: PASS** · target p95 ≤ 250 ms, max ≤ 1000 ms",
                "",
                "| Event | Steps | p50 | p95 | max | Handled p95 / max | Fired by | On target |",
                "|---|---:|---:|---:|---:|---:|---|---|",
                "| Pre-alert | 2 / 2 | 8 ms | 12 ms | 12 ms | 12 ms / 12 ms | WAKE_UP 2 | yes |",
                "| Alert | 2 / 2 | 20 ms | 56 ms | 56 ms | 34 ms / 34 ms | ALARM 2 | yes |",
                "",
                "Slowest pre-alert: step 1, 12 ms (handled 12 ms, WAKE_UP)",
                "",
                "Slowest alert: step 1, 56 ms (handled 34 ms, ALARM)",
            ).joinToString("\n", postfix = "\n"),
        )
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

    private fun handled(
        event: TimerEvent,
        source: EventSource,
        outcome: TransitionOutcome,
        lateMs: Long,
        slot: TimerSlot = TimerSlot.BREW,
    ) = TimerDiagnostic.EventHandled(
        at = clock.reading(),
        event = event,
        source = source,
        outcome = outcome,
        from = TimerPhase.RUNNING,
        to = TimerPhase.RUNNING,
        scheduled = ScheduledTime(TimerAlarmKind.of(event)!!, clock.elapsed - lateMs, slot),
    )

    private fun played(cue: AlertCue, lateMs: Long?, slot: TimerSlot = TimerSlot.BREW) = TimerDiagnostic.AlertPlayed(
        at = clock.reading(),
        cue = cue,
        conditions = AlertConditions(RingerMode.NORMAL, inCall = false),
        outputs = AlertOutputs(sound = true, vibration = true),
        tone = "klangschale",
        length = AlertLength.NORMAL,
        scheduled = lateMs?.let {
            val kind = if (cue == AlertCue.PRE_ALERT) TimerAlarmKind.PRE_ALERT else TimerAlarmKind.DEADLINE
            ScheduledTime(kind, clock.elapsed - it, slot)
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

    /**
     * The quick timer that ran alongside brew step [step] (D-098): its 25 s with a 10 s pre-alert from the step's
     * start, so its cues come before the brew's, handled [preAlertMs] and [alertMs] late, the sounds then or as given.
     */
    private fun quick(
        step: Int,
        preAlertMs: Long,
        alertMs: Long,
        preAlertPlayedMs: Long = preAlertMs,
        alertPlayedMs: Long = alertMs,
    ) = StepFireTimes(
        step = step,
        preAlert = CueTimes(
            handled = fire(TimerAlarmKind.PRE_ALERT, "ALARM", "10:00:15", 15_000, preAlertMs, TimerSlot.QUICK),
            played = fire(
                TimerAlarmKind.PRE_ALERT,
                FireTime.PLAYER,
                "10:00:15",
                15_000,
                preAlertPlayedMs,
                TimerSlot.QUICK,
            ),
        ),
        alert = CueTimes(
            handled = fire(TimerAlarmKind.DEADLINE, "WAKE_UP", "10:00:25", 25_000, alertMs, TimerSlot.QUICK),
            played = fire(TimerAlarmKind.DEADLINE, FireTime.PLAYER, "10:00:25", 25_000, alertPlayedMs, TimerSlot.QUICK),
        ),
    )

    private fun fire(
        kind: TimerAlarmKind,
        source: String,
        wall: String,
        mono: Long,
        deltaMs: Long,
        slot: TimerSlot = TimerSlot.BREW,
    ) = FireTime(
        kind = kind,
        source = source,
        scheduledWall = "$wall.000",
        scheduledMonoMs = mono,
        actualWall = "$wall.${deltaMs.toString().padStart(3, '0').takeLast(3)}",
        actualMonoMs = mono + deltaMs,
        deltaMs = deltaMs,
        slot = slot,
    )

    private companion object {
        val RUN = AccuracyRun(apiLevel = 36, plannedSteps = 50, stepSeconds = 60, preAlertSeconds = 10)

        /** The `alongside` variant (D-098, #137): a 25 s quick timer from each step's start, then one of 300 s. */
        val ALONGSIDE = RUN.copy(
            alongside = QuickTimerAlongside(seconds = 25, preAlertSeconds = 10, acrossSeconds = 300),
        )
    }
}
