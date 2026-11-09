package com.umamit.core.timer.diagnostics

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.TimerAlarmKind
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The accuracy run's variant `two-brews` (04 §12, TMR-30; D-108, #202): two overlapping brew sessions, the second's
 * alert held behind the first's until the first's sound has ended (04 §9.3, D-109). Both pre-alerts and the first brew's alerts are gated;
 * the held alert's hold and its release's own delay are reported apart and never gated.
 */
class TwoBrewsAccuracyReportTest {
    @Test
    fun `a two-brews run passes with both brews' pre-alerts and the first's alerts on target, the held alert apart`() {
        val first = (1..50).map { step(it, preAlertMs = 10, alertMs = 20) }
        // Held about 6 s behind the first brew's alert by design (04 §9.3): never counted as lateness.
        val second = (1..50).map { second(it, preAlertMs = 5, heldMs = 6_000L + it, releaseMs = 30) }

        val report = AccuracyReport(TWO_BREWS, first, secondSteps = second)
        val markdown = report.markdown()

        assertThat(report.passed).isTrue()
        assertThat(report.second?.preAlert).isEqualTo(DelayStats(50, 5, 5, 5))
        assertThat(report.second?.held).isEqualTo(DelayStats(50, 6_025, 6_048, 6_050))
        assertThat(report.second?.release).isEqualTo(DelayStats(50, 30, 30, 30))
        assertThat(markdown).contains("### Timer accuracy · API 36 · two brews")
        assertThat(markdown).contains(
            "50 × 60 s steps with the screen off, pre-alert 10 s (04 §12, TMR-30), each with a second brew " +
                "session alongside (D-108): steps of 66 s with pre-alert 10 s started with the first's, so its " +
                "alert comes while the first's sounds and is held until that sound has ended (04 §9.3, D-109).",
        )
        assertThat(markdown).contains(
            "Held alert of brew 2 (04 §9.3): its deadline comes while brew 1's alert sounds, so it is held until " +
                "that sound has ended (D-109), by design, and rings then with its overtime.",
        )
        assertThat(markdown).contains(
            "**Result: PASS** · target p95 ≤ 250 ms, max ≤ 1000 ms, for both brews' pre-alerts and the first " +
                "brew's alerts",
        )
        assertThat(markdown).contains(
            "| Brew 1 | Pre-alert | 50 / 50 | 10 ms | 10 ms | 10 ms | 10 ms / 10 ms | WAKE_UP 50 | yes |",
        )
        assertThat(markdown).contains(
            "| Brew 1 | Alert | 50 / 50 | 20 ms | 20 ms | 20 ms | 20 ms / 20 ms | ALARM 50 | yes |",
        )
        assertThat(markdown).contains(
            "| Brew 2 | Pre-alert | 50 / 50 | 5 ms | 5 ms | 5 ms | 5 ms / 5 ms | ALARM 50 | yes |",
        )
        assertThat(markdown).contains(
            "| Timer | Event | Steps | Held p50 / max | Release → sound p50 | p95 | max | " +
                "Deadline handled p95 / max | Fired by | Within target |",
        )
        assertThat(markdown).contains(
            "| Brew 2 | Alert (held) | 50 / 50 | 6025 ms / 6050 ms | 30 ms | 30 ms | 30 ms | 15 ms / 15 ms | " +
                "WAKE_UP 50 | yes (not gated) |",
        )
        assertThat(markdown).contains("Slowest brew 1 alert: step 1, 20 ms (handled 20 ms, ALARM)")
        assertThat(markdown).contains("Slowest brew 2 pre-alert: step 1, 5 ms (handled 5 ms, ALARM)")
        assertThat(markdown).contains(
            "Slowest brew 2 held alert: step 1, 30 ms after its release, held 6001 ms " +
                "(deadline handled 15 ms, WAKE_UP)",
        )
    }

    @Test
    fun `a slow release of the held alert is reported but never fails a two-brews run`() {
        val first = (1..50).map { step(it, preAlertMs = 10, alertMs = 20) }
        val second = (1..50).map { second(it, preAlertMs = 5, heldMs = 6_000, releaseMs = 1_500) }

        val report = AccuracyReport(TWO_BREWS, first, secondSteps = second)

        assertThat(report.passed).isTrue()
        assertThat(report.markdown()).contains(
            "| 1500 ms | 1500 ms | 1500 ms | 15 ms / 15 ms | WAKE_UP 50 | no (not gated) |",
        )
    }

    @Test
    fun `a late second brew's pre-alert fails a two-brews run although the first was on time`() {
        val first = (1..50).map { step(it, preAlertMs = 10, alertMs = 20) }
        val second = (1..50).map {
            second(it, preAlertMs = if (it == 9) 1_100L else 5L, heldMs = 6_000, releaseMs = 30)
        }

        val report = AccuracyReport(TWO_BREWS, first, secondSteps = second)

        assertThat(report.brew.onTarget).isTrue()
        assertThat(report.passed).isFalse()
        assertThat(report.markdown()).contains("**Result: FAIL**")
        assertThat(report.markdown()).contains("| Brew 2 | Pre-alert | 50 / 50 | 5 ms | 5 ms | 1100 ms |")
    }

    @Test
    fun `a late first brew's alert fails a two-brews run`() {
        val first = (1..50).map {
            step(it, preAlertMs = 10, alertMs = 20, alertPlayedMs = if (it == 3) 1_300L else 20L)
        }
        val second = (1..50).map { second(it, preAlertMs = 5, heldMs = 6_000, releaseMs = 30) }

        val report = AccuracyReport(TWO_BREWS, first, secondSteps = second)

        assertThat(report.passed).isFalse()
        assertThat(report.markdown()).contains("| Brew 1 | Alert | 50 / 50 | 20 ms | 20 ms | 1300 ms |")
    }

    @Test
    fun `a two-brews run fails when a second brew's step did not complete`() {
        val first = (1..50).map { step(it, preAlertMs = 1, alertMs = 1) }
        val second = (1..49).map { second(it, preAlertMs = 1, heldMs = 6_000, releaseMs = 1) }

        val report = AccuracyReport(TWO_BREWS, first, secondSteps = second)

        assertThat(report.passed).isFalse()
        assertThat(report.markdown()).contains("Only 49 of 50 second brew steps completed")
        assertThat(AccuracyReport(TWO_BREWS, first).passed).isFalse()
    }

    @Test
    fun `the two-brews CSV names each row's timer and the held alert's hold, in the order the cues happened`() {
        val csv = AccuracyReport(
            TWO_BREWS.copy(plannedSteps = 1),
            listOf(step(1, preAlertMs = 12, alertMs = 34, alertPlayedMs = 56)),
            secondSteps = listOf(second(1, preAlertMs = 7, heldMs = 6_400, releaseMs = 21)),
        ).csv().lines()

        assertThat(csv).containsExactly(
            "step,timer,event,source,scheduled_wall,scheduled_mono_ms," +
                "handled_wall,handled_mono_ms,handled_delta_ms,played_wall,played_mono_ms,played_delta_ms,held_ms",
            "1,BREW,PRE_ALERT,WAKE_UP,10:00:50.000,50000,10:00:50.012,50012,12,10:00:50.012,50012,12,",
            "1,BREW_2,PRE_ALERT,ALARM,10:00:56.000,56000,10:00:56.007,56007,7,10:00:56.007,56007,7,",
            "1,BREW,DEADLINE,ALARM,10:01:00.000,60000,10:01:00.034,60034,34,10:01:00.056,60056,56,",
            "1,BREW_2,DEADLINE,WAKE_UP,10:01:06.000,66000,10:01:06.015,66015,15,10:01:12.421,72421,21,6400",
            "",
        ).inOrder()
    }

    @Test
    fun `a held cue takes a released sound of its own deadline, and a report each brew's own cues`() {
        val hold = fire(TimerAlarmKind.DEADLINE, "ALARM", "10:01:06", 66_000, 15, TimerSlot.BREW_2)
        val unheld = fire(TimerAlarmKind.DEADLINE, FireTime.PLAYER, "10:01:06", 66_000, 15, TimerSlot.BREW_2)
        val first = listOf(step(1, preAlertMs = 1, alertMs = 1))
        val second = listOf(second(1, preAlertMs = 1, heldMs = 6_000, releaseMs = 1))
        val oneStep = TWO_BREWS.copy(plannedSteps = 1)

        assertThrows(IllegalArgumentException::class.java) { HeldCueTimes(hold, unheld) }
        assertThrows(IllegalArgumentException::class.java) {
            HeldCueTimes(hold, unheld.copy(heldMs = 6_000, slot = TimerSlot.QUICK))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccuracyReport(RUN.copy(plannedSteps = 1), first, secondSteps = second)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccuracyReport(oneStep, first, quickSteps = listOf(quick(1, 1, 1)), secondSteps = second)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RUN.copy(alongside = QuickTimerAlongside(25, 10, 300), twoBrews = TWO_BREWS.twoBrews)
        }
    }

    private companion object {
        val RUN = AccuracyRun(apiLevel = 36, plannedSteps = 50, stepSeconds = 60, preAlertSeconds = 10)

        /** The `two-brews` variant (D-108, #202): a second brew session of 66 s steps with a 10 s pre-alert. */
        val TWO_BREWS = RUN.copy(twoBrews = SecondBrew(stepSeconds = 66, preAlertSeconds = 10))
    }
}
