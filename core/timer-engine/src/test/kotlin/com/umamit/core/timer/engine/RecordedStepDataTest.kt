package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Recorded step data per 04 §5.1, as stored in the state and sent with RecordStep. */
class RecordedStepDataTest {

    private fun EngineDriver.recordOf(stepIndex: Int): StepRecord {
        val record = (state as TimerState.Active).session.steps[stepIndex].record!!
        assertThat(effects).contains(TimerEffect.RecordStep(SESSION_ID, stepIndex, record))
        return record
    }

    @Test
    fun `a pour right at the deadline records the planned time`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.clocks.advanceSeconds(12)
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)

        driver.send(TimerEvent.Poured)

        assertThat(driver.recordOf(0)).isEqualTo(
            StepRecord(
                plannedSeconds = 60,
                actualSeconds = 60,
                pausedSeconds = 0,
                overtimeSeconds = 0,
                startedAtWallMs = START_WALL_MS + 12_000,
                endedAtWallMs = START_WALL_MS + 72_000,
                skipped = false,
                overtimeAutoAck = false,
            ),
        )
    }

    @Test
    fun `a pour with overtime records the overtime in the actual time`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.clocks.advance(7_400)

        driver.send(TimerEvent.Poured)

        assertThat(driver.recordOf(0)).isEqualTo(
            StepRecord(
                plannedSeconds = 60,
                actualSeconds = 67,
                pausedSeconds = 0,
                overtimeSeconds = 7,
                startedAtWallMs = START_WALL_MS,
                endedAtWallMs = START_WALL_MS + 67_400,
                skipped = false,
            ),
        )
    }

    @Test
    fun `durations round to the nearest second, half up`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.clocks.advance(2_500)

        driver.send(TimerEvent.Poured)

        val record = driver.recordOf(0)
        assertThat(record.overtimeSeconds).isEqualTo(3)
        assertThat(record.actualSeconds).isEqualTo(63)
    }

    @Test
    fun `an early end records the active time and no overtime`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.send(TimerEvent.Start)
        driver.clocks.advance(41_300)

        driver.send(TimerEvent.EndStepNow)

        assertThat(driver.recordOf(0)).isEqualTo(
            StepRecord(
                plannedSeconds = 60,
                actualSeconds = 41,
                pausedSeconds = 0,
                overtimeSeconds = 0,
                startedAtWallMs = START_WALL_MS,
                endedAtWallMs = START_WALL_MS + 41_300,
                skipped = false,
            ),
        )
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

        assertThat(driver.recordOf(0)).isEqualTo(
            StepRecord(
                plannedSeconds = 60,
                actualSeconds = 64,
                pausedSeconds = 75,
                overtimeSeconds = 4,
                startedAtWallMs = START_WALL_MS,
                endedAtWallMs = START_WALL_MS + 139_000,
                skipped = false,
            ),
        )
    }

    @Test
    fun `a step ended early while paused counts the open pause`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(20)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(50)

        driver.send(TimerEvent.EndStepNow)

        assertThat(driver.recordOf(0)).isEqualTo(
            StepRecord(
                plannedSeconds = 60,
                actualSeconds = 20,
                pausedSeconds = 50,
                overtimeSeconds = 0,
                startedAtWallMs = START_WALL_MS,
                endedAtWallMs = START_WALL_MS + 70_000,
                skipped = false,
            ),
        )
    }

    @Test
    fun `a skipped step records the planned time, no times and the skipped flag`() {
        val driver = sessionWith(rinse(10), infusion(60))

        driver.send(TimerEvent.SkipStep)

        assertThat(driver.recordOf(0)).isEqualTo(
            StepRecord(
                plannedSeconds = 10,
                actualSeconds = 0,
                pausedSeconds = 0,
                overtimeSeconds = 0,
                startedAtWallMs = null,
                endedAtWallMs = null,
                skipped = true,
            ),
        )
    }
}
