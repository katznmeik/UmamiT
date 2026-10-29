package com.umamit.core.testing

import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerUiStep
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake [BrewStarter] with the controller's observable rules (GEN-03, TMR-21): a
 * start while a timer is active is refused, otherwise the session waits in
 * READY with the request's steps; *Beenden & neu starten* replaces the active
 * one. [starts] records every accepted request in order, [quickStarts] every
 * accepted quick timer's duration; a quick timer runs at once.
 */
class FakeBrewStarter(initial: TimerUiState = TimerUiState.Idle) : BrewStarter {
    override val state = MutableStateFlow(initial)
    val starts = mutableListOf<BrewStart>()
    val quickStarts = mutableListOf<Int>()

    /** How many times *Beenden & neu starten* replaced an active timer. */
    var replaced = 0
        private set

    override suspend fun startSession(request: BrewStart): TransitionOutcome {
        val active = state.value as? TimerUiState.Active
        if (active != null) return TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(active.kind))
        accept(request)
        return TransitionOutcome.Accepted
    }

    override suspend fun finishActiveAndStart(request: BrewStart): TransitionOutcome {
        if (state.value is TimerUiState.Active) replaced++
        accept(request)
        return TransitionOutcome.Accepted
    }

    override suspend fun startQuickTimer(durationSeconds: Int): TransitionOutcome {
        val active = state.value as? TimerUiState.Active
        return when {
            active != null -> TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(active.kind))

            durationSeconds !in QUICK_MIN_SECONDS..QUICK_MAX_SECONDS ->
                TransitionOutcome.Rejected(RejectionReason.InvalidQuickTimer)

            else -> acceptQuick(durationSeconds)
        }
    }

    override suspend fun finishActiveAndStartQuickTimer(durationSeconds: Int): TransitionOutcome {
        if (state.value is TimerUiState.Active) replaced++
        state.value = TimerUiState.Idle
        return startQuickTimer(durationSeconds)
    }

    private fun acceptQuick(durationSeconds: Int): TransitionOutcome {
        quickStarts += durationSeconds
        state.value = quickRunningState(durationSeconds)
        return TransitionOutcome.Accepted
    }

    private fun accept(request: BrewStart) {
        starts += request
        state.value = readyState(request, sessionId = "session-${starts.size}")
    }

    companion object {
        private const val QUICK_MIN_SECONDS = 5
        private const val QUICK_MAX_SECONDS = 86_400

        /** A quick timer of [durationSeconds] just started (it runs at once); no deadline, as the fake has no clock. */
        fun quickRunningState(durationSeconds: Int) = TimerUiState.Active(
            kind = SessionKind.Quick,
            teaName = null,
            phase = TimerPhase.RUNNING,
            stepIndex = 0,
            steps = listOf(TimerUiStep(StepKind.INFUSION, durationSeconds, null, StepStatus.STARTED)),
            deadlineElapsedMs = durationSeconds * 1_000L,
            deadlineWallMs = durationSeconds * 1_000L,
            pausedRemainingMs = null,
            preAlertLeadSeconds = null,
            preAlertAtElapsedMs = null,
            preAlertPlayed = false,
            longStep = false,
            kettleTempC = null,
            demoted = false,
            finishWouldDiscard = false,
            preAlertBaseSeconds = PreAlertSettings.DEFAULT_GLOBAL_SECONDS,
        )

        /** The controller's state right after [request] started as [sessionId]. */
        fun readyState(request: BrewStart, sessionId: String) = TimerUiState.Active(
            kind = SessionKind.Brew(sessionId),
            teaName = request.snapshot.teaName,
            phase = TimerPhase.READY,
            stepIndex = 0,
            steps = request.steps.map {
                TimerUiStep(it.kind, it.plannedSeconds, it.tempC, StepStatus.PENDING, it.preAlertSeconds)
            },
            deadlineElapsedMs = null,
            deadlineWallMs = null,
            pausedRemainingMs = null,
            preAlertLeadSeconds = null,
            preAlertAtElapsedMs = null,
            preAlertPlayed = false,
            longStep = false,
            kettleTempC = request.steps.first().tempC,
            demoted = false,
            finishWouldDiscard = true,
            preAlertBaseSeconds = request.recipePreAlertSeconds ?: PreAlertSettings.DEFAULT_GLOBAL_SECONDS,
        )
    }
}
