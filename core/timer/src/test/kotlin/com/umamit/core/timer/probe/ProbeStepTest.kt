package com.umamit.core.timer.probe

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ProbeStepTest {
    private val step =
        ProbeStep(startElapsedMs = 1_000L, startWallMs = 50_000L, durationMs = 60_000L, preAlertLeadMs = 10_000L)

    @Test
    fun deadlineAndPreAlertFollowTheStartTap() {
        assertThat(step.deadlineElapsedMs).isEqualTo(61_000L)
        assertThat(step.deadlineWallMs).isEqualTo(110_000L)
        assertThat(step.preAlertOffsetMs).isEqualTo(50_000L)
        assertThat(step.preAlertElapsedMs).isEqualTo(51_000L)
    }

    @Test
    fun shortenedTestStepStillHasAPreAlert() {
        val short = step.copy(durationMs = 15_000L)

        assertThat(short.preAlertOffsetMs).isEqualTo(5_000L)
    }

    @Test
    fun noPreAlertWhenStepIsShorterThanLeadPlusFiveSeconds() {
        assertThat(step.copy(durationMs = 14_999L).preAlertOffsetMs).isNull()
        assertThat(step.copy(preAlertLeadMs = 0L).preAlertElapsedMs).isNull()
    }

    @Test
    fun phaseMovesFromRunningToPreAlertToFinished() {
        assertThat(step.phaseAt(1_000L)).isEqualTo(ProbePhase.RUNNING)
        assertThat(step.phaseAt(50_999L)).isEqualTo(ProbePhase.RUNNING)
        assertThat(step.phaseAt(51_000L)).isEqualTo(ProbePhase.PRE_ALERTED)
        assertThat(step.phaseAt(61_000L)).isEqualTo(ProbePhase.FINISHED)
        assertThat(step.copy(preAlertLeadMs = 0L).phaseAt(60_000L)).isEqualTo(ProbePhase.RUNNING)
    }

    @Test
    fun elapsedAndRemainingAreClamped() {
        assertThat(step.elapsedAt(0L)).isEqualTo(0L)
        assertThat(step.elapsedAt(16_000L)).isEqualTo(15_000L)
        assertThat(step.remainingAt(16_000L)).isEqualTo(45_000L)
        assertThat(step.elapsedAt(100_000L)).isEqualTo(60_000L)
        assertThat(step.remainingAt(100_000L)).isEqualTo(0L)
    }

    @Test
    fun invalidStepsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { step.copy(durationMs = 0L) }
        assertThrows(IllegalArgumentException::class.java) { step.copy(preAlertLeadMs = -1L) }
    }
}
