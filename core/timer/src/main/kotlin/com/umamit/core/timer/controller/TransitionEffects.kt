package com.umamit.core.timer.controller

import android.util.Log
import com.umamit.core.common.ClockReading
import com.umamit.core.data.DatabaseTransactions
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SessionStart
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.TimerEffect
import com.umamit.core.timer.engine.TimerState
import com.umamit.core.timer.engine.TimerTransition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

/**
 * Executes the effects of accepted transitions for the [BrewSessionController]
 * (04 §4, §7.1; D-047, D-054), always inside the controller's event lock:
 *
 * 1. the data effects and `Persist` of the transition's slot in one database
 *    transaction; if it fails, the failure is logged and `Persist` alone is
 *    retried once;
 * 2. the new UI state for the [screens];
 * 3. `ScheduleAlarms` of the slot and the side effects through the [ports],
 *    each on its own, so a port that throws is logged and the next effect
 *    still runs.
 *
 * Nothing here throws on a storage or port error: rule 8 lets nothing keep
 * `StartAlert` from running. Both slots share the one alert player ([player],
 * 04 §9.3) and the service ([roles], 04 §8.2), D-098.
 */
internal class TransitionEffects(
    private val sessions: SessionRepository,
    private val store: TimerSlotStore,
    private val transactions: DatabaseTransactions,
    private val ports: TimerPorts,
) {
    /** What the screens see of both slots. */
    val screens = SlotScreens()

    /** The one alert player of both slots. */
    val player =
        SharedAlertPlayer(ports.alert) { now, effect, error ->
            record(TimerDiagnostic.EffectFailed(now, effect, error))
        }

    /** Which notification each active timer has. */
    val roles = NotificationRoles(ports.service, ports.notifier, screens)

    /** The timer in front of the screens ([SlotScreens.state]). */
    val state: StateFlow<TimerUiState> get() = screens.state

    /** A new turn of the controller's event lock begins. */
    fun beginTurn() {
        roles.serviceCommanded = false
    }

    /**
     * At the end of a turn that may start the service from the background (an exact alarm, a
     * notification action, a tap; 04 §6.2, §6.3): a start Android refused earlier goes again,
     * with the notification of [timer], the lead slot's state now (D-057, D-063). Not after a
     * turn that sent the service a command itself: that command replaced the refused one, or was
     * refused just now. While neither timer needs the service ([serviceNeeded]) it is dropped. The
     * alert keeps its full-screen intent while it plays.
     */
    fun retryRefusedStart(timer: TimerState, serviceNeeded: Boolean, now: ClockReading) {
        if (roles.serviceCommanded) return
        val ui = roles.lead?.let { screens[it] }?.takeIf { serviceNeeded }
        val fullScreen = timer is TimerState.Alerting && player.isPlaying(timer)
        attempt { ports.service.retryRefusedStart(ui, fullScreen) }
            ?.let { record(TimerDiagnostic.EffectFailed(now, TimerEffect.StartForeground, it)) }
    }

    /**
     * Runs the effects of the accepted [transition] of [slot] at [now]. [start] is the
     * request of a `CreateSession`; [details] what the UI shows of the session's row (null: the quick timer).
     * [savedSheetAsNotification]: a `ShowSavedSheet` is executed as
     * `NotifySessionSaved` (*Beenden & neu starten*, D-054). [scheduled]: when the time-based
     * event of the transition was due, which the pre-alert tick passes to the player's log (D-068).
     */
    suspend fun run(
        slot: TimerSlot,
        transition: TimerTransition,
        now: ClockReading,
        start: SessionStart?,
        details: SessionDetails?,
        savedSheetAsNotification: Boolean = false,
        scheduled: ScheduledTime? = null,
    ) {
        val persistAt = transition.effects.indexOf(TimerEffect.Persist)
        persist(slot, transition.effects.take(persistAt), transition.state, start, now)
        val ui = uiStateOf(transition.state, details, now)
        screens.show(slot, ui)
        val turn = Turn(slot, transition, ui, scheduled?.takeIf { it.kind == TimerAlarmKind.PRE_ALERT }, now)
        val notifications = roles.begin(slot)
        transition.effects.drop(persistAt + 1)
            .map { if (savedSheetAsNotification && it is TimerEffect.ShowSavedSheet) it.asNotification() else it }
            .forEach { effect ->
                attempt { executeSide(turn, notifications, effect) }
                    ?.let { record(TimerDiagnostic.EffectFailed(now, effect, it)) }
            }
        attempt { notifications.finish() }
            ?.let { record(TimerDiagnostic.EffectFailed(now, TimerEffect.UpdateNotification, it)) }
    }

    /** One accepted transition of [slot] whose side effects run. */
    private class Turn(
        val slot: TimerSlot,
        val transition: TimerTransition,
        val ui: TimerUiState,
        val preAlertDue: ScheduledTime?,
        val now: ClockReading,
    )

    /**
     * The player reported the end of [alert]: true when it is the playing
     * alert, which then no longer plays; false for any other alert (stopped,
     * or an earlier step's), which is logged and must change nothing.
     */
    fun alertEnded(alert: StepAlert, now: ClockReading): Boolean = player.alertEnded(alert).also { playing ->
        if (!playing) record(TimerDiagnostic.StaleAlertCompletion(now, alert))
    }

    /**
     * „Dein …-Timer läuft weiter" (04 §7.3) for the timer of [slot] that survived a reboot, after its
     * recovery; not while alerting, where the alert is the notice (D-044).
     */
    fun notifyResumedAfterReboot(slot: TimerSlot) {
        val active = screens[slot]?.takeUnless { it.alerting } ?: return
        attempt { ports.notifier.notifyTimerResumed(active) }?.let { Log.w(TAG, "reboot notice not posted", it) }
    }

    /** Writes [entry] to the diagnostics log; a log that fails never stops the timer. */
    fun record(entry: TimerDiagnostic) {
        attempt { ports.diagnostics.record(entry) }?.let { Log.w(TAG, "diagnostics entry lost: $entry", it) }
    }

    /**
     * The [data] effects and `Persist` of [state] in [slot] in one transaction (04 §7.1).
     * If it fails, the failure is logged and `Persist` alone is retried once;
     * if that fails too, the previous row stays until a later transition
     * persists again (D-054). The store's write also brings the step rows to
     * the plan, so a retried persist still records an ended step (D-052).
     */
    private suspend fun persist(
        slot: TimerSlot,
        data: List<TimerEffect>,
        state: TimerState,
        start: SessionStart?,
        now: ClockReading,
    ) {
        val snapshot = BrewTimerEngine.snapshot(state)
        val failure = attempt {
            transactions.inTransaction {
                data.forEach { executeData(it, start) }
                store.persist(slot, snapshot)
            }
        } ?: return
        record(TimerDiagnostic.PersistFailed(now, retry = false, error = failure))
        attempt { store.persist(slot, snapshot) }
            ?.let { record(TimerDiagnostic.PersistFailed(now, retry = true, error = it)) }
    }

    private suspend fun executeData(effect: TimerEffect, start: SessionStart?) {
        when (effect) {
            is TimerEffect.CreateSession -> sessions.startSession(checkNotNull(start))

            is TimerEffect.ReplacePendingSteps ->
                sessions.replacePendingSteps(effect.sessionId, effect.fromIndex, effect.steps)

            is TimerEffect.RecordStep -> sessions.recordStep(effect.sessionId, effect.stepIndex, effect.record)

            is TimerEffect.CompleteSession -> sessions.completeSession(effect.sessionId, effect.autoFinished)

            is TimerEffect.DiscardSession -> sessions.discardSession(effect.sessionId)

            else -> error("not a data effect: $effect")
        }
    }

    /**
     * One side [effect] of the [turn]'s transition. The alert's notification is full screen only when the
     * alert starts in the same transition (D-057). The cues go through the shared [player]; the service and
     * the notification follow the slot's role ([notifications]) while the other slot has a timer.
     */
    private fun executeSide(turn: Turn, notifications: NotificationRoles.Transition, effect: TimerEffect) {
        val fullScreen = TimerEffect.StartAlert in turn.transition.effects
        when (effect) {
            is TimerEffect.ScheduleAlarms -> ports.alarms.reconcile(turn.slot, effect.due)

            TimerEffect.PlayPreAlert -> player.preAlert(
                turn.slot,
                (turn.transition.state as TimerState.Running).deadlineElapsedMs,
                turn.preAlertDue,
                turn.now,
            )

            TimerEffect.StartAlert -> player.startAlert(turn.slot, turn.transition.state as TimerState.Alerting)

            TimerEffect.StopAlert -> player.stopAlert(turn.slot, turn.now)

            TimerEffect.StartForeground -> notifications.startForeground(turn.ui as TimerUiState.Active, fullScreen)

            TimerEffect.StopForeground -> notifications.stopForeground()

            TimerEffect.UpdateNotification -> notifications.update(turn.ui as TimerUiState.Active, fullScreen)

            is TimerEffect.ShowSavedSheet -> ports.savedSheet.showSavedSheet(effect.sessionId)

            is TimerEffect.NotifySessionSaved -> ports.notifier.notifySessionSaved(effect.sessionId)

            else -> error("not a side effect: $effect")
        }
    }
}

private const val TAG = "UmamiTTimer"

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
