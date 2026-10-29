package com.umamit.core.timer.service

import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeClock
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerUiStep
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.StepStatus

/** A one-step session of „Bio Sencha Hisui" in [phase] at [clock]: running with 60 s left, alerting since now. */
fun testState(phase: TimerPhase, clock: FakeClock): TimerUiState.Active {
    val deadlineInMs = when (phase) {
        TimerPhase.RUNNING -> 60_000L
        TimerPhase.ALERTING -> 0L
        else -> null
    }
    return TimerUiState.Active(
        kind = SessionKind.Brew("s1"),
        teaName = "Bio Sencha Hisui",
        phase = phase,
        stepIndex = 0,
        steps = listOf(TimerUiStep(StepKind.INFUSION, 60, 60, StepStatus.STARTED)),
        deadlineElapsedMs = deadlineInMs?.let { clock.elapsed + it },
        deadlineWallMs = deadlineInMs?.let { clock.wall + it },
        pausedRemainingMs = null,
        preAlertLeadSeconds = 10,
        preAlertAtElapsedMs = null,
        preAlertPlayed = false,
        longStep = false,
        kettleTempC = 60,
        demoted = false,
        finishWouldDiscard = false,
        preAlertBaseSeconds = 10,
    )
}
