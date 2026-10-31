package com.umamit.core.testing

import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerUiStep
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Fake [BrewStarter] with the controller's observable rules (GEN-03, TMR-21, TMR-22, D-098): one brew session
 * and one quick timer may be active at once; a start while a timer of its own kind is active is refused,
 * otherwise the session waits in READY with the request's steps and the quick timer runs at once, in front;
 * *Beenden & neu starten* replaces the active one of its own kind only. [state] is the timer in front (a test
 * sets it to place one timer), [other] the one beside it. [starts] records every accepted request in order,
 * [quickStarts] every accepted quick timer's duration and [quickPreAlerts] its *Vorwarnung* choice (null
 * *Automatisch*, 0 *Aus*, else 5–30 s; D-097).
 */
class FakeBrewStarter(initial: TimerUiState = TimerUiState.Idle) : BrewStarter {
    override val state = MutableStateFlow(initial)

    /** The timer of the other slot, beside the one in front ([state]). */
    val other = MutableStateFlow<TimerUiState>(TimerUiState.Idle)
    val starts = mutableListOf<BrewStart>()
    val quickStarts = mutableListOf<Int>()
    val quickPreAlerts = mutableListOf<Int?>()

    /** How many times *Beenden & neu starten* replaced an active timer. */
    var replaced = 0
        private set

    override fun activeIn(slot: TimerSlot): TimerUiState.Active? = activeIn(slot, state.value, other.value)

    override fun observeActive(slot: TimerSlot): Flow<TimerUiState.Active?> =
        combine(state, other) { front, beside -> activeIn(slot, front, beside) }.distinctUntilChanged()

    private fun activeIn(slot: TimerSlot, vararg timers: TimerUiState): TimerUiState.Active? =
        timers.filterIsInstance<TimerUiState.Active>().firstOrNull { it.slot == slot }

    override suspend fun startSession(request: BrewStart): TransitionOutcome {
        val active = activeIn(TimerSlot.BREW)
        if (active != null) return TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(active.kind))
        accept(request)
        return TransitionOutcome.Accepted
    }

    override suspend fun finishActiveAndStart(request: BrewStart): TransitionOutcome {
        if (end(TimerSlot.BREW)) replaced++
        accept(request)
        return TransitionOutcome.Accepted
    }

    override suspend fun startQuickTimer(durationSeconds: Int, preAlertSeconds: Int?): TransitionOutcome {
        val active = activeIn(TimerSlot.QUICK)
        return when {
            active != null -> TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(active.kind))

            durationSeconds !in QUICK_MIN_SECONDS..QUICK_MAX_SECONDS ||
                (preAlertSeconds != null && preAlertSeconds != 0 && preAlertSeconds !in QUICK_PRE_ALERT_SECONDS) ->
                TransitionOutcome.Rejected(RejectionReason.InvalidQuickTimer)

            else -> acceptQuick(durationSeconds, preAlertSeconds)
        }
    }

    override suspend fun finishActiveAndStartQuickTimer(
        durationSeconds: Int,
        preAlertSeconds: Int?,
    ): TransitionOutcome {
        if (end(TimerSlot.QUICK)) replaced++
        return startQuickTimer(durationSeconds, preAlertSeconds)
    }

    private fun acceptQuick(durationSeconds: Int, preAlertSeconds: Int?): TransitionOutcome {
        quickStarts += durationSeconds
        quickPreAlerts += preAlertSeconds
        inFront(quickRunningState(durationSeconds))
        return TransitionOutcome.Accepted
    }

    private fun accept(request: BrewStart) {
        starts += request
        inFront(readyState(request, sessionId = "session-${starts.size}"))
    }

    /** [timer] starts in front; the active timer of the other slot moves beside it. */
    private fun inFront(timer: TimerUiState.Active) {
        other.value = activeIn(timer.slot.other)
            ?: TimerUiState.Idle
        state.value = timer
    }

    /** Ends the active timer of [slot]; true when there was one. The other one comes to the front. */
    private fun end(slot: TimerSlot): Boolean = when {
        (state.value as? TimerUiState.Active)?.slot == slot -> {
            state.value = other.value
            other.value = TimerUiState.Idle
            true
        }

        (other.value as? TimerUiState.Active)?.slot == slot -> {
            other.value = TimerUiState.Idle
            true
        }

        else -> false
    }

    companion object {
        private const val QUICK_MIN_SECONDS = 5
        private const val QUICK_MAX_SECONDS = 86_400
        private val QUICK_PRE_ALERT_SECONDS = 5..30

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
