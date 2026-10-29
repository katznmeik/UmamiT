package com.umamit.feature.brew.timer

import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerUiStep
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.StepStatus

/**
 * Fake controller states for the timer screen tests (spec #40 seam 4): a
 * three-step Gyokuro session, built per phase at the monotonic time [NOW].
 * Each step's pre-alert lead follows the automatic rule with the global
 * default B = 10 s (TMR-23, D-093): 1:00 → 10 s, 0:20 → 5 s, 0:40 → 10 s.
 */
internal object TimerStates {
    const val NOW = 1_000_000L

    /** The session's pre-alert base B: the global default (SET-10). */
    private const val BASE = PreAlertSettings.DEFAULT_GLOBAL_SECONDS

    /** The lead the engine gives [step] (null: none, e.g. a long step), as the controller publishes it. */
    private fun leadOf(step: TimerUiStep?, longStepsOnly: Boolean = false): Int? = step?.let {
        BrewTimerEngine.stepPreAlertLeadSeconds(
            PlannedStep(it.kind, it.plannedSeconds, it.tempC, it.preAlertSeconds),
            BASE,
            longStepsOnly,
        )
    }

    private val plan = listOf(
        TimerUiStep(StepKind.INFUSION, 60, 55, StepStatus.PENDING),
        TimerUiStep(StepKind.INFUSION, 20, 60, StepStatus.PENDING),
        TimerUiStep(StepKind.INFUSION, 40, 60, StepStatus.PENDING),
    )

    private fun steps(current: Int, currentStatus: StepStatus, plan: List<TimerUiStep>) = plan.mapIndexed { i, step ->
        step.copy(
            status = when {
                i < current -> StepStatus.DONE
                i == current -> currentStatus
                else -> StepStatus.PENDING
            },
        )
    }

    private fun base(phase: TimerPhase, stepIndex: Int, status: StepStatus, plan: List<TimerUiStep> = this.plan) =
        TimerUiState.Active(
            kind = SessionKind.Brew("s1"),
            teaName = "Bio Uji Gyokuro Gokō",
            phase = phase,
            stepIndex = stepIndex,
            steps = steps(stepIndex, status, plan),
            deadlineElapsedMs = null,
            deadlineWallMs = null,
            pausedRemainingMs = null,
            preAlertLeadSeconds = leadOf(plan.getOrNull(stepIndex)),
            preAlertAtElapsedMs = null,
            preAlertPlayed = false,
            longStep = false,
            kettleTempC = plan.getOrNull(stepIndex)?.tempC,
            demoted = false,
            finishWouldDiscard = stepIndex == 0,
            preAlertBaseSeconds = BASE,
        )

    /** READY for [stepIndex] (the first step by default: nothing brewed yet). */
    fun ready(stepIndex: Int = 0) = base(TimerPhase.READY, stepIndex, StepStatus.PENDING)

    /**
     * RUNNING step [stepIndex] (step 1, 60 s, by default) with [remainingMs] left at [NOW]; the pre-alert is due
     * its lead before the end (10 s for step 1, 5 s for the 0:20 of step 2).
     */
    fun running(remainingMs: Long = 48_000, preAlertPlayed: Boolean = false, stepIndex: Int = 0) =
        base(TimerPhase.RUNNING, stepIndex, StepStatus.STARTED).let {
            val deadline = NOW + remainingMs
            it.copy(
                deadlineElapsedMs = deadline,
                deadlineWallMs = 1_700_000_000_000 + remainingMs,
                preAlertAtElapsedMs = it.preAlertLeadSeconds?.takeIf { !preAlertPlayed }?.let { lead ->
                    deadline - lead * 1_000L
                },
                preAlertPlayed = preAlertPlayed,
            )
        }

    /** A long step (cold brew, 3 h) RUNNING with [remainingMs] left; no pre-alert (TMR-18). */
    fun longRunning(remainingMs: Long = 2 * 3_600_000L) = base(
        TimerPhase.RUNNING,
        0,
        StepStatus.STARTED,
        plan = listOf(TimerUiStep(StepKind.INFUSION, 3 * 3_600, 5, StepStatus.PENDING)),
    ).copy(
        deadlineElapsedMs = NOW + remainingMs,
        deadlineWallMs = 1_700_000_000_000 + remainingMs,
        preAlertLeadSeconds = null,
        longStep = true,
    )

    fun paused(remainingMs: Long = 31_400) =
        base(TimerPhase.PAUSED, 0, StepStatus.STARTED).copy(pausedRemainingMs = remainingMs)

    /** ALERTING, [overtimeMs] after the deadline at [NOW]. */
    fun alerting(overtimeMs: Long = 7_000, rinse: Boolean = false, stepIndex: Int = 1) = base(
        TimerPhase.ALERTING,
        stepIndex,
        StepStatus.STARTED,
        plan = if (rinse) listOf(TimerUiStep(StepKind.RINSE, 10, 80, StepStatus.PENDING)) + plan else plan,
    ).copy(
        deadlineElapsedMs = NOW - overtimeMs,
        deadlineWallMs = 1_700_000_000_000 - overtimeMs,
        finishWouldDiscard = false,
    )

    fun finished() = base(TimerPhase.FINISHED, 3, StepStatus.DONE).copy(kettleTempC = null, finishWouldDiscard = false)

    /** The quick timer (SCR-07, 04 §10) of [seconds] in [phase]: no tea, no temperature, one infusion. */
    fun quick(phase: TimerPhase, seconds: Int = 90, remainingMs: Long = 48_000, overtimeMs: Long = 7_000) =
        TimerUiState.Active(
            kind = SessionKind.Quick,
            teaName = null,
            phase = phase,
            stepIndex = 0,
            steps = listOf(
                TimerUiStep(
                    StepKind.INFUSION,
                    seconds,
                    null,
                    if (phase == TimerPhase.READY) StepStatus.PENDING else StepStatus.STARTED,
                ),
            ),
            deadlineElapsedMs = when (phase) {
                TimerPhase.RUNNING -> NOW + remainingMs
                TimerPhase.ALERTING -> NOW - overtimeMs
                else -> null
            },
            deadlineWallMs = when (phase) {
                TimerPhase.RUNNING -> 1_700_000_000_000 + remainingMs
                TimerPhase.ALERTING -> 1_700_000_000_000 - overtimeMs
                else -> null
            },
            pausedRemainingMs = if (phase == TimerPhase.PAUSED) remainingMs else null,
            // The quick timer's automatic rule with the global B (TMR-22): 1:30 → 10 s.
            preAlertLeadSeconds = leadOf(TimerUiStep(StepKind.INFUSION, seconds, null, StepStatus.PENDING)),
            preAlertAtElapsedMs = if (phase == TimerPhase.RUNNING) {
                leadOf(TimerUiStep(StepKind.INFUSION, seconds, null, StepStatus.PENDING))
                    ?.let { NOW + remainingMs - it * 1_000L }
            } else {
                null
            },
            preAlertPlayed = false,
            longStep = false,
            kettleTempC = null,
            demoted = false,
            finishWouldDiscard = false,
            preAlertBaseSeconds = BASE,
        )

    /** A cold step (5 °C) waiting in READY: a cold brew, or an ice brew with [iceBrew]. */
    fun coldReady(iceBrew: Boolean) = base(
        TimerPhase.READY,
        0,
        StepStatus.PENDING,
        plan = listOf(TimerUiStep(StepKind.INFUSION, 2 * 3_600, 5, StepStatus.PENDING)),
    ).copy(longStep = true, longStepsOnly = true, iceBrew = iceBrew)
}
