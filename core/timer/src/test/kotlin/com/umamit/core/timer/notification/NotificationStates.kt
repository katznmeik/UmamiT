package com.umamit.core.timer.notification

import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeClock
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerUiStep
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.StepStatus

/**
 * Timer states as the notifications see them (04 §8.2): a „Gyokuro" session of three steps
 * (a 4 s rinse, a 60 s and a 30 s infusion at 60 °C) on step 2, at [clock].
 */
class NotificationStates(private val clock: FakeClock) {
    private val plan = listOf(
        TimerUiStep(StepKind.RINSE, 4, 60, StepStatus.DONE),
        TimerUiStep(StepKind.INFUSION, 60, 60, StepStatus.PENDING),
        TimerUiStep(StepKind.INFUSION, 30, 60, StepStatus.PENDING),
    )

    fun ready() = base(TimerPhase.READY)

    /** Running with [remainingMs] of the 60 s step left. */
    fun running(remainingMs: Long = 45_000, preAlertPlayed: Boolean = false) = base(TimerPhase.RUNNING).copy(
        steps = plan.withCurrent(StepStatus.STARTED),
        deadlineElapsedMs = clock.elapsed + remainingMs,
        deadlineWallMs = clock.wall + remainingMs,
        preAlertAtElapsedMs = if (preAlertPlayed) null else clock.elapsed + remainingMs - 10_000,
        preAlertPlayed = preAlertPlayed,
    )

    fun paused(remainingMs: Long = 11_200) = base(TimerPhase.PAUSED).copy(
        steps = plan.withCurrent(StepStatus.STARTED),
        pausedRemainingMs = remainingMs,
    )

    fun alerting() = base(TimerPhase.ALERTING).copy(
        steps = plan.withCurrent(StepStatus.STARTED),
        deadlineElapsedMs = clock.elapsed - 3_000,
        deadlineWallMs = clock.wall - 3_000,
    )

    fun finished() = base(TimerPhase.FINISHED).copy(
        stepIndex = 3,
        steps = plan.map { it.copy(status = StepStatus.DONE) },
        preAlertLeadSeconds = null,
        kettleTempC = null,
    )

    /** A cold brew's single long step (≥ 10 min, TMR-18), running with [remainingMs] left. */
    fun longStep(remainingMs: Long) = base(TimerPhase.RUNNING).copy(
        stepIndex = 0,
        steps = listOf(TimerUiStep(StepKind.INFUSION, 8 * 3_600, null, StepStatus.STARTED)),
        deadlineElapsedMs = clock.elapsed + remainingMs,
        deadlineWallMs = clock.wall + remainingMs,
        preAlertLeadSeconds = null,
        longStep = true,
        kettleTempC = null,
    )

    /** The quick timer („Schnelltimer", 04 §10) of 90 s in [phase]. */
    fun quick(phase: TimerPhase) = TimerUiState.Active(
        kind = SessionKind.Quick,
        teaName = null,
        phase = phase,
        stepIndex = 0,
        steps = listOf(
            TimerUiStep(
                StepKind.INFUSION,
                90,
                null,
                if (phase == TimerPhase.READY) StepStatus.PENDING else StepStatus.STARTED,
            ),
        ),
        deadlineElapsedMs = clock.elapsed.takeIf { phase == TimerPhase.RUNNING || phase == TimerPhase.ALERTING },
        deadlineWallMs = clock.wall.takeIf { phase == TimerPhase.RUNNING || phase == TimerPhase.ALERTING },
        pausedRemainingMs = 30_000L.takeIf { phase == TimerPhase.PAUSED },
        preAlertLeadSeconds = 10,
        preAlertAtElapsedMs = null,
        preAlertPlayed = false,
        longStep = false,
        kettleTempC = null,
        demoted = false,
        finishWouldDiscard = false,
        preAlert = PreAlertChoice.Inherit,
    )

    private fun base(phase: TimerPhase) = TimerUiState.Active(
        kind = SessionKind.Brew("s1"),
        teaName = "Gyokuro",
        phase = phase,
        stepIndex = 1,
        steps = plan,
        deadlineElapsedMs = null,
        deadlineWallMs = null,
        pausedRemainingMs = null,
        preAlertLeadSeconds = 10,
        preAlertAtElapsedMs = null,
        preAlertPlayed = false,
        longStep = false,
        kettleTempC = 60,
        demoted = false,
        finishWouldDiscard = false,
        preAlert = PreAlertChoice.Inherit,
    )

    private fun List<TimerUiStep>.withCurrent(status: StepStatus) =
        mapIndexed { index, step -> if (index == 1) step.copy(status = status) else step }
}
