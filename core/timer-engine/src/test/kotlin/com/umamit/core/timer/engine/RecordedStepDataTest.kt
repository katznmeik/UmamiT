package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Recorded step data per 04 §5.1, as stored in the state and sent with RecordStep. */
class RecordedStepDataTest {

    /** The record of step [stepIndex], checked to be the one the last transition sent. */
    private fun EngineDriver.recorded(stepIndex: Int): StepRecord {
        val record = recordOf(stepIndex)
        assertThat(effects).contains(TimerEffect.RecordStep(SESSION_ID, stepIndex, record))
        return record
    }

    private fun record(
        actual: Int,
        paused: Int = 0,
        overtime: Int = 0,
        startedAt: Long = START_WALL_MS,
        endedAt: Long,
        planned: Int = 60,
    ) = StepRecord(
        plannedSeconds = planned,
        actualSeconds = actual,
        pausedSeconds = paused,
        overtimeSeconds = overtime,
        startedAtWallMs = startedAt,
        endedAtWallMs = endedAt,
        skipped = false,
        overtimeAutoAck = false,
    )

    @Test
    fun `a pour right at the deadline records the planned time`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.clocks.advanceSeconds(12)
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)

        driver.send(TimerEvent.Poured)

        assertThat(driver.recorded(0))
            .isEqualTo(record(actual = 60, startedAt = START_WALL_MS + 12_000, endedAt = START_WALL_MS + 72_000))
    }

    @Test
    fun `a pour with overtime records the overtime in the actual time`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.clocks.advance(7_400)

        driver.send(TimerEvent.Poured)

        assertThat(driver.recorded(0)).isEqualTo(record(actual = 67, overtime = 7, endedAt = START_WALL_MS + 67_400))
    }

    @Test
    fun `durations round to the nearest second, half up`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.clocks.advance(2_500)

        driver.send(TimerEvent.Poured)

        val record = driver.recorded(0)
        assertThat(record.overtimeSeconds).isEqualTo(3)
        assertThat(record.actualSeconds).isEqualTo(63)
    }

    @Test
    fun `an early end records the active time and no overtime`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.send(TimerEvent.Start)
        driver.clocks.advance(41_300)

        driver.send(TimerEvent.EndStepNow)

        assertThat(driver.recorded(0)).isEqualTo(record(actual = 41, endedAt = START_WALL_MS + 41_300))
    }

    @Test
    fun `ending a running step at or after the deadline, before the deadline event, counts as poured`() {
        val ends = listOf(TimerEvent.EndStepNow, TimerEvent.FinishSession)
        val cases = listOf(0L to record(actual = 60, endedAt = START_WALL_MS + 60_000)) +
            listOf(300L to record(actual = 60, endedAt = START_WALL_MS + 60_300)) +
            listOf(2_600L to record(actual = 63, overtime = 3, endedAt = START_WALL_MS + 62_600))
        ends.forEach { end ->
            cases.forEach { (afterDeadlineMs, expected) ->
                val driver = sessionWith(infusion(60), infusion(20)).apply { send(TimerEvent.Start) }
                driver.advanceTo(driver.deadline + afterDeadlineMs)

                val effects = driver.send(end)

                val sent = effects.filterIsInstance<TimerEffect.RecordStep>().single().record
                assertWithMessage("$end at deadline + $afterDeadlineMs ms").that(sent).isEqualTo(expected)
                assertWithMessage("$end: no alert").that(effects).doesNotContain(TimerEffect.StartAlert)
            }
        }
    }

    @Test
    fun `a paused step records the paused time separately and excludes it from the actual time`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(20)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(45)
        driver.send(TimerEvent.Resume)
        driver.clocks.advanceSeconds(15)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(30)
        driver.send(TimerEvent.Resume)
        driver.clocks.advanceSeconds(25)
        driver.send(TimerEvent.DeadlineReached)
        driver.clocks.advanceSeconds(4)

        driver.send(TimerEvent.Poured)

        assertThat(driver.recorded(0))
            .isEqualTo(record(actual = 64, paused = 75, overtime = 4, endedAt = START_WALL_MS + 139_000))
    }

    @Test
    fun `a step ended early while paused counts the open pause`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(20)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(50)

        driver.send(TimerEvent.EndStepNow)

        assertThat(driver.recorded(0)).isEqualTo(record(actual = 20, paused = 50, endedAt = START_WALL_MS + 70_000))
    }

    @Test
    fun `a skipped step records the planned time, no times and the skipped flag`() {
        val driver = sessionWith(rinse(10), infusion(60))

        driver.send(TimerEvent.SkipStep)

        assertThat(driver.recorded(0)).isEqualTo(
            StepRecord(
                plannedSeconds = 10,
                actualSeconds = 0,
                pausedSeconds = 0,
                overtimeSeconds = 0,
                startedAtWallMs = null,
                endedAtWallMs = null,
                skipped = true,
                overtimeAutoAck = false,
            ),
        )
    }
}
