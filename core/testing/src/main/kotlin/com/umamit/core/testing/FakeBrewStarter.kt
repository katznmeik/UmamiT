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
 * Fake [BrewStarter] with the controller's observable admission of v0.5.0 (GEN-03, TMR-21, TMR-22, D-108): any two
 * timers may be active at once, two brew sessions (`BREW`, then `BREW_2`) or a brew session and the quick timer. A
 * start of a tea with an active session is refused as `ActiveTimerExists` naming that session, a second quick timer
 * as `ActiveTimerExists(Quick)`, and any other start while two timers are active as `TwoTimersActive` with the active
 * slots; otherwise the session waits in READY with the request's steps and the quick timer runs at once, in front.
 * *Beenden & neu starten* ends the timer of the slot given (only while it holds the session given, if one is), then
 * starts as above. [state] is the timer in front (a
 * test sets it to place one timer), [other] the one beside it. [starts] records every accepted request in order,
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

    /** The tea of each session this fake started, by session id: a start of that tea finds its session (D-108). */
    private val teaOf = mutableMapOf<String, String?>()

    /** How many times *Beenden & neu starten* replaced an active timer. */
    var replaced = 0
        private set

    override fun activeIn(slot: TimerSlot): TimerUiState.Active? = activeIn(slot, state.value, other.value)

    override fun observeActive(slot: TimerSlot): Flow<TimerUiState.Active?> =
        combine(state, other) { front, beside -> activeIn(slot, front, beside) }.distinctUntilChanged()

    private fun activeIn(slot: TimerSlot, vararg timers: TimerUiState): TimerUiState.Active? =
        timers.filterIsInstance<TimerUiState.Active>().firstOrNull { it.slot == slot }

    override suspend fun startSession(request: BrewStart): TransitionOutcome {
        val teaId = request.snapshot.teaId
        val same = active.firstOrNull { teaId != null && it.sessionId?.let(teaOf::get) == teaId }
        return when {
            same != null -> TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(same.kind))
            active.size >= MAX_ACTIVE -> twoTimersActive
            else -> accept(request, BREW_SLOTS.first { activeIn(it) == null })
        }
    }

    override suspend fun finishActiveAndStart(
        request: BrewStart,
        ending: TimerSlot,
        endingSession: String?,
    ): TransitionOutcome {
        if (end(ending, endingSession)) replaced++
        return startSession(request)
    }

    override suspend fun startQuickTimer(durationSeconds: Int, preAlertSeconds: Int?): TransitionOutcome {
        val quick = activeIn(TimerSlot.QUICK)
        return when {
            quick != null -> TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(quick.kind))

            durationSeconds !in QUICK_MIN_SECONDS..QUICK_MAX_SECONDS ||
                (preAlertSeconds != null && preAlertSeconds != 0 && preAlertSeconds !in QUICK_PRE_ALERT_SECONDS) ->
                TransitionOutcome.Rejected(RejectionReason.InvalidQuickTimer)

            active.size >= MAX_ACTIVE -> twoTimersActive

            else -> acceptQuick(durationSeconds, preAlertSeconds)
        }
    }

    override suspend fun finishActiveAndStartQuickTimer(
        durationSeconds: Int,
        preAlertSeconds: Int?,
        ending: TimerSlot,
        endingSession: String?,
    ): TransitionOutcome {
        if (end(ending, endingSession)) replaced++
        return startQuickTimer(durationSeconds, preAlertSeconds)
    }

    /** The active timers in the slot order. */
    private val active: List<TimerUiState.Active> get() = TimerSlot.entries.mapNotNull(::activeIn)

    private val twoTimersActive
        get() = TransitionOutcome.Rejected(RejectionReason.TwoTimersActive(active.map { it.slot }))

    private fun acceptQuick(durationSeconds: Int, preAlertSeconds: Int?): TransitionOutcome {
        quickStarts += durationSeconds
        quickPreAlerts += preAlertSeconds
        inFront(quickRunningState(durationSeconds))
        return TransitionOutcome.Accepted
    }

    private fun accept(request: BrewStart, slot: TimerSlot): TransitionOutcome {
        starts += request
        val sessionId = "session-${starts.size}"
        teaOf[sessionId] = request.snapshot.teaId
        inFront(readyState(request, sessionId, slot))
        return TransitionOutcome.Accepted
    }

    /** [timer] starts in front; the active timer of the other slot moves beside it. */
    private fun inFront(timer: TimerUiState.Active) {
        other.value = listOf(state.value, other.value).filterIsInstance<TimerUiState.Active>()
            .firstOrNull { it.slot != timer.slot }
            ?: TimerUiState.Idle
        state.value = timer
    }

    /**
     * Ends the active timer of [slot], while it is [sessionId] when that is given; true when there was one. The other
     * one comes to the front.
     */
    private fun end(slot: TimerSlot, sessionId: String?): Boolean = when {
        sessionId != null && activeIn(slot)?.sessionId != sessionId -> false

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
        private const val MAX_ACTIVE = 2
        private val BREW_SLOTS = listOf(TimerSlot.BREW, TimerSlot.BREW_2)

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

        /** The controller's state right after [request] started as [sessionId] in [slot] (`BREW` or `BREW_2`). */
        fun readyState(request: BrewStart, sessionId: String, slot: TimerSlot = TimerSlot.BREW) = TimerUiState.Active(
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
            inSlot = slot.takeIf { it == TimerSlot.BREW_2 },
        )
    }
}
