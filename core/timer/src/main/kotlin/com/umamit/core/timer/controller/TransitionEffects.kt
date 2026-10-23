package com.umamit.core.timer.controller

import android.util.Log
import com.umamit.core.common.ClockReading
import com.umamit.core.data.ActiveBrewStore
import com.umamit.core.data.DatabaseTransactions
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SessionStart
import com.umamit.core.timer.engine.BrewTimerEngine
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

    /** The current turn sent the service a command (`StartForeground`, `StopForeground`). */
    private var serviceCommanded = false

    /** An alert plays: `StartAlert` started it and nothing ended it yet (the alert watchdog follows it). */
    val alertPlaying: Boolean get() = playingAlert != null

    /** True while the alert of the alerting [state] plays. */
    fun isPlaying(state: TimerState.Alerting): Boolean = playingAlert == stepAlertOf(state)

    /** A new turn of the controller's event lock begins. */
    fun beginTurn() {
        serviceCommanded = false
    }

    /**
     * At the end of a turn that may start the service from the background (an exact alarm, a
     * notification action, a tap; 04 §6.2, §6.3): a start Android refused earlier goes again,
     * with the notification of [timer], the state now (D-057, D-063). Not after a turn that
     * sent the service a command itself: that command replaced the refused one, or was refused
     * just now. A state that needs no service drops it. The alert keeps its full-screen intent
     * while it plays.
     */
    fun retryRefusedStart(timer: TimerState, now: ClockReading) {
        if (serviceCommanded) return
        val ui = (state.value as? TimerUiState.Active)?.takeIf { BrewTimerEngine.needsService(timer) }
        val fullScreen = timer is TimerState.Alerting && isPlaying(timer)
        attempt { ports.service.retryRefusedStart(ui, fullScreen) }
            ?.let { record(TimerDiagnostic.EffectFailed(now, TimerEffect.StartForeground, it)) }
    }

    /**
     * Runs the effects of the accepted [transition] at [now]. [start] is the
     * request of a `CreateSession`; [teaName] the session's tea for the UI, [iceBrew] whether it is an ice brew.
     * [savedSheetAsNotification]: a `ShowSavedSheet` is executed as
     * `NotifySessionSaved` (*Beenden & neu starten*, D-054).
     */
    suspend fun run(
        transition: TimerTransition,
        now: ClockReading,
        start: SessionStart?,
        teaName: String?,
        iceBrew: Boolean = false,
        savedSheetAsNotification: Boolean = false,
    ) {
        val persistAt = transition.effects.indexOf(TimerEffect.Persist)
        persist(transition.effects.take(persistAt), transition.state, start, now)
        val ui = uiStateOf(transition.state, teaName, now, iceBrew)
        mutableState.value = ui
        val fullScreen = TimerEffect.StartAlert in transition.effects
        transition.effects.drop(persistAt + 1)
            .map { if (savedSheetAsNotification && it is TimerEffect.ShowSavedSheet) it.asNotification() else it }
            .forEach { effect ->
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
     * „Dein …-Timer läuft weiter" (04 §7.3) for the session that survived a reboot, after its
     * recovery; not while alerting, where the alert is the notice (D-044).
     */
    fun notifyResumedAfterReboot() {
        val active = (state.value as? TimerUiState.Active)?.takeUnless { it.alerting } ?: return
        attempt { ports.notifier.notifyTimerResumed(active) }?.let { Log.w(TAG, "reboot notice not posted", it) }
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

            TimerEffect.StartForeground -> {
                serviceCommanded = true
                ports.service.startForeground(ui as TimerUiState.Active, fullScreen)
            }

            TimerEffect.StopForeground -> {
                serviceCommanded = true
                ports.service.stopForeground()
            }

            TimerEffect.UpdateNotification -> ports.notifier.update(ui as TimerUiState.Active, fullScreen)

            is TimerEffect.ShowSavedSheet -> ports.savedSheet.showSavedSheet(effect.sessionId)

            is TimerEffect.NotifySessionSaved -> ports.notifier.notifySessionSaved(effect.sessionId)

            else -> error("not a side effect: $effect")
        }
    }

    /**
     * `StartAlert` (04 §4, D-048): ensures the alert of the alerting step
     * plays; an alert already playing for the same deadline is not restarted.
     */
    private fun startAlert(state: TimerState) {
        val alert = stepAlertOf(state as TimerState.Alerting)
        if (alert == playingAlert) return
        ports.alert.startAlert(alert)
        playingAlert = alert
    }
}

private const val TAG = "UmamiTTimer"

/** The alert of the alerting step of [state]: one per deadline (D-048). */
private fun stepAlertOf(state: TimerState.Alerting) =
    StepAlert(state.session.kind, state.stepIndex, state.deadlineWallMs)

/** The saved sheet of a finish as the notification „Sitzung gespeichert" (D-054). */
private fun TimerEffect.ShowSavedSheet.asNotification() = TimerEffect.NotifySessionSaved(sessionId)

/**
 * Runs [block] and returns what it threw, or null. A storage or adapter error
 * of any kind must not stop the alert (rule 8, 04 §4), so every exception is
 * caught here and logged by the caller; cancellation still propagates.
 */
@Suppress("TooGenericExceptionCaught") // any failure of a port or of storage is logged, never fatal (rule 8)
internal inline fun attempt(block: () -> Unit): Exception? = try {
    block()
    null
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    e
}
