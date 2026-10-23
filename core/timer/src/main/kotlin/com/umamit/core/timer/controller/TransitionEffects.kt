package com.umamit.core.timer.controller

import android.util.Log
import com.umamit.core.common.ClockReading
import com.umamit.core.data.ActiveBrewStore
import com.umamit.core.data.DatabaseTransactions
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SessionStart
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.engine.TimerEffect
import com.umamit.core.timer.engine.TimerState
import com.umamit.core.timer.engine.TimerTransition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Executes the effects of accepted transitions for the [BrewSessionController]
 * (04 §4, §7.1; D-047, D-054), always inside the controller's event lock:
 *
 * 1. the data effects and `Persist` in one database transaction; if it fails,
 *    the failure is logged and `Persist` alone is retried once;
 * 2. the new [state] for the UI;
 * 3. `ScheduleAlarms` and the side effects through the [ports], each on its
 *    own, so a port that throws is logged and the next effect still runs.
 *
 * Nothing here throws on a storage or port error: rule 8 lets nothing keep
 * `StartAlert` from running.
 */
internal class TransitionEffects(
    private val sessions: SessionRepository,
    private val activeBrew: ActiveBrewStore,
    private val transactions: DatabaseTransactions,
    private val ports: TimerPorts,
) {
    private val mutableState = MutableStateFlow<TimerUiState>(TimerUiState.Idle)

    /** The UI state of the last accepted transition, published after its persist. */
    val state: StateFlow<TimerUiState> = mutableState.asStateFlow()

    /** The alert the player plays, between `StartAlert` and its end or `StopAlert`. */
    private var playingAlert: StepAlert? = null

    /**
     * Runs the effects of the accepted [transition] at [now]. [start] is the
     * request of a `CreateSession`; [teaName] the session's tea for the UI, [iceBrew] whether it is an ice brew.
     */
    suspend fun run(
        transition: TimerTransition,
        now: ClockReading,
        start: SessionStart?,
        teaName: String?,
        iceBrew: Boolean = false,
    ) {
        val persistAt = transition.effects.indexOf(TimerEffect.Persist)
        persist(transition.effects.take(persistAt), transition.state, start, now)
        val ui = uiStateOf(transition.state, teaName, now, iceBrew)
        mutableState.value = ui
        val fullScreen = TimerEffect.StartAlert in transition.effects
        transition.effects.drop(persistAt + 1).forEach { effect ->
            attempt { executeSide(effect, transition.state, ui, fullScreen) }
                ?.let { record(TimerDiagnostic.EffectFailed(now, effect, it)) }
        }
    }

    /**
     * The player reported the end of [alert]: true when it is the playing
     * alert, which then no longer plays; false for any other alert (stopped,
     * or an earlier step's), which is logged and must change nothing.
     */
    fun alertEnded(alert: StepAlert, now: ClockReading): Boolean {
        if (alert != playingAlert) {
            record(TimerDiagnostic.StaleAlertCompletion(now, alert))
            return false
        }
        playingAlert = null
        return true
    }

    /**
     * The persisted state could not be restored (04 §7.2): its row is cleared and its alarms
     * cancelled, so nothing runs a wrong timer; each step is logged if it fails.
     */
    suspend fun discardUnreadable(now: ClockReading) {
        attempt { activeBrew.clear() }?.let { record(TimerDiagnostic.PersistFailed(now, retry = false, error = it)) }
        attempt { ports.alarms.reconcile(DueTimes.NONE) }
            ?.let { record(TimerDiagnostic.EffectFailed(now, TimerEffect.ScheduleAlarms(DueTimes.NONE), it)) }
    }

    /**
     * Sets the alarm of [kind] again [RETRY_DELAY_MS] after [now]: its turn could not read the
     * persisted timer, and it may have been the step's only alarm (04 §7.2, D-057).
     */
    fun retryAlarm(kind: TimerAlarmKind, now: ClockReading) {
        val at = RETRY_DELAY_MS + when (kind.clock) {
            AlarmClock.ELAPSED -> now.elapsedMs
            AlarmClock.WALL -> now.wallMs
        }
        attempt { ports.alarms.retry(kind, at) }?.let { Log.e(TAG, "alarm $kind could not be set again", it) }
    }

    /** Writes [entry] to the diagnostics log; a log that fails never stops the timer. */
    fun record(entry: TimerDiagnostic) {
        attempt { ports.diagnostics.record(entry) }?.let { Log.w(TAG, "diagnostics entry lost: $entry", it) }
    }

    /**
     * The [data] effects and `Persist` of [state] in one transaction (04 §7.1).
     * If it fails, the failure is logged and `Persist` alone is retried once;
     * if that fails too, the previous row stays until a later transition
     * persists again (D-054). The store's write also brings the step rows to
     * the plan, so a retried persist still records an ended step (D-052).
     */
    private suspend fun persist(data: List<TimerEffect>, state: TimerState, start: SessionStart?, now: ClockReading) {
        val snapshot = BrewTimerEngine.snapshot(state)
        val failure = attempt {
            transactions.inTransaction {
                data.forEach { executeData(it, state, start) }
                activeBrew.persist(snapshot)
            }
        } ?: return
        record(TimerDiagnostic.PersistFailed(now, retry = false, error = failure))
        attempt { activeBrew.persist(snapshot) }
            ?.let { record(TimerDiagnostic.PersistFailed(now, retry = true, error = it)) }
    }

    private suspend fun executeData(effect: TimerEffect, newState: TimerState, start: SessionStart?) {
        when (effect) {
            is TimerEffect.CreateSession -> sessions.startSession(checkNotNull(start))

            is TimerEffect.ReplacePendingSteps -> sessions.replacePendingSteps(
                effect.sessionId,
                effect.fromIndex,
                effect.steps,
                preAlertSeconds = (newState as TimerState.Active).session.preAlert.effectiveSeconds,
            )

            is TimerEffect.RecordStep -> sessions.recordStep(effect.sessionId, effect.stepIndex, effect.record)

            is TimerEffect.CompleteSession -> sessions.completeSession(effect.sessionId, effect.autoFinished)

            is TimerEffect.DiscardSession -> sessions.discardSession(effect.sessionId)

            else -> error("not a data effect: $effect")
        }
    }

    private fun executeSide(effect: TimerEffect, newState: TimerState, ui: TimerUiState, fullScreen: Boolean) {
        when (effect) {
            is TimerEffect.ScheduleAlarms -> ports.alarms.reconcile(effect.due)

            TimerEffect.PlayPreAlert -> ports.alert.playPreAlert()

            TimerEffect.StartAlert -> startAlert(newState)

            TimerEffect.StopAlert -> {
                playingAlert = null
                ports.alert.stopAlert()
            }

            TimerEffect.StartForeground -> ports.service.startForeground(ui as TimerUiState.Active, fullScreen)

            TimerEffect.StopForeground -> ports.service.stopForeground()

            TimerEffect.UpdateNotification -> ports.notifier.update(ui as TimerUiState.Active, fullScreen)

            is TimerEffect.ShowSavedSheet -> ports.savedSheet.showSavedSheet(effect.sessionId)

            is TimerEffect.NotifySessionSaved -> ports.notifier.notifySessionSaved(effect.sessionId)

            else -> error("not a side effect: $effect")
        }
    }

    companion object {
        /** How soon a turn that could not read the persisted timer comes again (alarm and wake-up). */
        const val RETRY_DELAY_MS = 5_000L
    }

    /**
     * `StartAlert` (04 §4, D-048): ensures the alert of the alerting step
     * plays; an alert already playing for the same deadline is not restarted.
     */
    private fun startAlert(state: TimerState) {
        val alerting = state as TimerState.Alerting
        val alert = StepAlert(alerting.session.kind, alerting.stepIndex, alerting.deadlineWallMs)
        if (alert == playingAlert) return
        ports.alert.startAlert(alert)
        playingAlert = alert
    }
}

private const val TAG = "UmamiTTimer"

/**
 * Runs [block] and returns what it threw, or null. A storage or adapter error
 * of any kind must not stop the alert (rule 8, 04 §4), so every exception is
 * caught here and logged by the caller; cancellation still propagates.
 */
@Suppress("TooGenericExceptionCaught") // any failure of a port or of storage is logged, never fatal (rule 8)
private inline fun attempt(block: () -> Unit): Exception? = try {
    block()
    null
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    e
}
