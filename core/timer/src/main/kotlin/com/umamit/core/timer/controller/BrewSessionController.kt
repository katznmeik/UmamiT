package com.umamit.core.timer.controller

import android.util.Log
import com.umamit.core.common.ClockReading
import com.umamit.core.data.ActiveBrewStore
import com.umamit.core.data.DatabaseTransactions
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SessionStart
import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.TimerSlot
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.engine.AlertArbitration
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerState
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What *Brühen starten* hands the controller (TMR-01, BRW-02): the session's
 * [snapshot] of tea, recipe and vessel, the planned [steps] (temperatures in
 * 5 °C steps), each with its own pre-alert for this session only (TMR-23,
 * SCR-31, D-093), and the recipe's pre-alert [recipePreAlertSeconds], the base B
 * of the automatic rule (null: the global setting; 0: off).
 */
data class BrewStart(val snapshot: SessionSnapshot, val steps: List<PlannedStep>, val recipePreAlertSeconds: Int?) {
    init {
        require(steps.isNotEmpty()) { "a session needs at least one step" }
    }
}

/**
 * The single owner of the timer state (04 §2.1, §4; D-056): every brew
 * session and quick timer goes through it. It runs two slots (04 §3.3,
 * D-098), the brew slot for brew sessions and the quick slot for the quick
 * timer, each with its own engine state, alarms, alert watchdog and stored
 * row ([ControllerSlots], [TimerSlotStore]): a brew session and a quick timer
 * run side by side (TMR-21, TMR-22). One alert player serves both, arbitrated
 * after every event ([AlertArbitration], [TransitionEffects]): the brew's
 * alert first, a quick alert due meanwhile held until the brew's is
 * acknowledged, never two sounds at once (04 §9.3). Each input runs in one turn of one
 * event lock: the clocks are read inside the lock, once per reduce (D-054);
 * the engine's effects then run in the order of D-047 ([TransitionEffects]):
 * the data effects and `Persist` in one database transaction, then
 * `ScheduleAlarms`, then the side effects through the [TimerPorts]. After
 * every event and at the end of every turn the in-process wake-up is re-armed
 * for the next due time of either slot, and each slot's alert watchdog follows
 * its playing alert (D-063) or guards its running step's deadline (D-068).
 * [state] is the UI state of the screens, the active brew bar and the
 * notifications.
 *
 * The first turn of every process recovers the persisted state before its
 * own input (04 §7.2, D-057): a process an alarm started after the old one was
 * killed (E3) handles that alarm on the recovered step. The app's process
 * start, a reboot and an update ask for that turn with [recover] (D-063).
 */
@Singleton
class BrewSessionController @Inject constructor(
    private val clocks: TimerClocks,
    sessions: SessionRepository,
    activeBrew: ActiveBrewStore,
    transactions: DatabaseTransactions,
    private val settings: SettingsRepository,
    private val ports: TimerPorts,
) : TimerInputs {
    private val lock = Mutex()
    private val store: TimerSlotStore = ActiveBrewSlotStore(activeBrew)
    private val effects = TransitionEffects(sessions, store, transactions, ports)
    private val persistedTimer = PersistedTimer(store, sessions, ports, effects)
    private val recovery = ProcessRecovery()
    private val sideBySide = SideBySide()

    /** The brew and the quick slot; read and written only inside [lock]. */
    private val slots = ControllerSlots(ports.alarms)

    /**
     * The timer the screens show (04 §3.3); updated on every accepted transition, after it is persisted: the
     * ringing alert's (the brew's first), else the timer the user started or used last in the app, else the
     * other active one.
     */
    val state: StateFlow<TimerUiState> = effects.state

    /** The UI state of each slot's timer (the brew session's, the quick timer's), Idle while it has none. */
    val slotStates: Map<TimerSlot, StateFlow<TimerUiState>> = TimerSlot.entries.associateWith { effects.screens.of(it) }

    private val mutableRecovered = MutableStateFlow(false)

    /**
     * The first recovery of this process has run (04 §7.2; GEN-07, D-068): until then [state] is
     * `Idle` only because nothing was read yet, so a screen must not take it for "no session"
     * (the timer route restored after process death waits for it). Set after the recovered state
     * is published in [state]; also when the stored timer could not be read, so no screen waits
     * for a broken database.
     */
    val recovered: StateFlow<Boolean> = mutableRecovered.asStateFlow()

    /**
     * *Brühen starten* (TMR-01): starts a brew session waiting in READY, in the brew slot, alongside a quick
     * timer (D-098). The pre-alert inherits the recipe's value, else the global setting, frozen now (04 §5).
     * Returns `Rejected(ActiveTimerExists)` while another brew session is active (TMR-21); then nothing is
     * written.
     */
    suspend fun startSession(request: BrewStart): TransitionOutcome {
        val start = PendingStart(request, settings.current().preAlertSeconds)
        return inTurn(serviceStartAllowed = true) { start.handle() }
    }

    /**
     * *Beenden & neu starten* (TMR-21, D-054): the answer to „Erst die … -Sitzung beenden?".
     * In one turn, so nothing gets in between, the active brew session ends as by *Sitzung beenden*
     * (saved, or discarded without a completed infusion: the dialog was the confirmation), and
     * [request] starts. A quick timer is never touched (D-098). The finish's „Gespeichert" sheet
     * becomes the notification „Sitzung gespeichert" (TMR-14): the new session's timer is in front.
     * Returns the start's outcome.
     */
    suspend fun finishActiveAndStart(request: BrewStart): TransitionOutcome {
        val start = PendingStart(request, settings.current().preAlertSeconds)
        return inTurn(serviceStartAllowed = true) {
            sideBySide.endActive(TimerSlot.BREW)
            start.handle()
        }
    }

    /** A brew start whose pre-alert base B is frozen (04 §5); it reads the clocks when its turn handles it. */
    private inner class PendingStart(private val request: BrewStart, globalPreAlertSeconds: Int) {
        private val preAlert = PreAlertSettings.inherited(request.recipePreAlertSeconds, globalPreAlertSeconds)
        private val event = TimerEvent.StartSession(
            sessionId = UUID.randomUUID().toString(),
            steps = request.steps,
            preAlert = preAlert,
            longStepsOnly = BrewTimerEngine.longStepsOnly(request.snapshot.method),
        )

        /** Inside a turn: the start in the brew slot at the current clocks. */
        suspend fun handle(): TransitionOutcome {
            val now = clocks.read()
            val start = SessionStart(
                id = event.sessionId,
                snapshot = request.snapshot,
                steps = request.steps,
                preAlertSeconds = preAlert.inheritedSeconds,
                startedAtMs = now.wallMs,
                zoneId = now.zone.id,
            )
            return sideBySide.start(TimerSlot.BREW, event, now, start)
        }
    }

    /**
     * *Schnelltimer* (TMR-22, 04 §10): one infusion of [durationSeconds] with
     * SCR-07's *Vorwarnung* [preAlertSeconds] (D-097: null *Automatisch* with
     * B = the global pre-alert, read now; 0 *Aus*; else 5–30 s as set), waiting
     * in READY in the quick slot, alongside a brew session (D-098); nothing is
     * recorded. Returns `Rejected(ActiveTimerExists)` while another quick timer
     * is active and `Rejected(InvalidQuickTimer)` for a duration outside
     * 5 s – 24 h or a choice other than 0 or 5–30 s. With [run], its step starts
     * in the same turn (SCR-07's *Start*). With [finishActive] it is „Schnelltimer
     * beenden und neu starten?" (D-054, D-065, D-098): in the same turn the
     * active quick timer first ends (*Abbrechen*, or *Fertig* while alerting);
     * the brew is never touched.
     */
    suspend fun startQuickTimer(
        durationSeconds: Int,
        preAlertSeconds: Int? = null,
        run: Boolean = false,
        finishActive: Boolean = false,
    ): TransitionOutcome {
        val event = TimerEvent.StartQuickTimer(durationSeconds, settings.current().preAlertSeconds, preAlertSeconds)
        return inTurn(serviceStartAllowed = true) {
            if (finishActive) sideBySide.endActive(TimerSlot.QUICK)
            val outcome = sideBySide.start(TimerSlot.QUICK, event, clocks.read())
            // SCR-07's *Start* (D-065): READY and running in one turn, so no input lands between them and a
            // cancelled caller never leaves a waiting quick timer behind.
            if (run && outcome == TransitionOutcome.Accepted) {
                handle(TimerSlot.QUICK, TimerEvent.Start, clocks.read(), EventSource.APP)
            } else {
                outcome
            }
        }
    }

    /**
     * A user event of [slot]'s timer from the app or a notification ([source]): every event of
     * 04 §3.2 and §10 except the starts ([startSession], [startQuickTimer]) and
     * the events the controller produces itself (`recover`, `alertCompleted`,
     * the time-based events, the arbitration's), which throw [IllegalArgumentException].
     * It acts on that slot only (04 §3.3): *Sitzung beenden* never touches the quick
     * timer, *Abbrechen* never the brew. Returns the engine's outcome; a rejected or
     * ignored event changes nothing (E5). An event from the app puts its timer in
     * front of the screens ([state]).
     */
    override suspend fun send(slot: TimerSlot, event: TimerEvent, source: EventSource): TransitionOutcome {
        require(event in USER_EVENTS || event is TimerEvent.EditSteps) { "not a user event: $event" }
        return inTurn(serviceStartAllowed = true) {
            handle(slot, event, clocks.read(), source).also {
                if (it == TransitionOutcome.Accepted && source == EventSource.APP) effects.screens.focus(slot)
            }
        }
    }

    /**
     * The alarm of [kind] in [slot] arrived (04 §6.2; from the alarm receiver):
     * every time-based event due now in either slot is sent, not only the kind's
     * own, so a late or early alarm is harmless (04 §3.2 guards, §4), and an
     * alarm v0.2.2 set for a quick timer under the brew slot's codes still
     * delivers its event (D-098).
     */
    override suspend fun onAlarm(slot: TimerSlot, kind: TimerAlarmKind) {
        inTurn(serviceStartAllowed = true) {
            val now = clocks.read()
            val inSlot = slots[slot]
            // An alarm that started the process was due in the state it recovered (06 §5.6).
            val scheduled = if (kind == TimerAlarmKind.ALERT_WATCHDOG) {
                inSlot.watchdog.scheduled
            } else {
                scheduled(inSlot.timer, kind, slot) ?: inSlot.restoredForAlarmLog?.let { scheduled(it, kind, slot) }
            }
            slots.forEach { it.restoredForAlarmLog = null }
            effects.record(TimerDiagnostic.AlarmReceived(now, kind, scheduled, slot))
            // The persisted timer could not be read: the alarm comes again, in case it was the only one.
            if (recovery.retrying) recovery.retryAlarm(slot, kind, now)
            handleDue(EventSource.ALARM)
        }
    }

    /**
     * The alert player finished the cycle of [alert] (SET-14): the alert
     * counts as played, so recovery does not replay it (D-047). A report for
     * an alert that no longer plays (stopped, or an earlier step's) is ignored.
     */
    suspend fun alertCompleted(alert: StepAlert): TransitionOutcome = inTurn {
        val now = clocks.read()
        if (effects.alertEnded(alert, now)) {
            handle(slotOf(alert.kind), TimerEvent.AlertCompleted, now, EventSource.ALERT_PLAYER)
        } else {
            TransitionOutcome.Ignored
        }
    }

    /**
     * Recovery without an input of its own (04 §7.2, §7.3, §7.5): the app's process started,
     * the phone booted, or the app was updated. The first turn of the process recovers the
     * persisted timer; in a process that already did, nothing changes.
     */
    override suspend fun recover(trigger: RecoveryTrigger) {
        inTurn(
            onLocked = {
                effects.record(
                    TimerDiagnostic.RecoveryRequested(clocks.read(), trigger, alreadyRecovered = recovery.done),
                )
            },
        ) { }
    }

    /**
     * One turn of the event lock (04 §4). Once it holds the lock, the turn
     * runs to its end even if the caller is cancelled (a screen that closes
     * right after *Abgegossen*): a transition half applied would leave the
     * alert playing or the stored state behind (rule 8). The first turn of
     * the process recovers the persisted state before its own input ([block]).
     * [onLocked] runs first, before that recovery. [serviceStartAllowed]: the input may
     * start the service from the background (an exact alarm, a notification
     * action, a tap), so a start Android refused earlier goes again at the end
     * (D-063); the in-process wake-up and the alert player's report may not.
     */
    private suspend fun <T> inTurn(
        onLocked: () -> Unit = {},
        serviceStartAllowed: Boolean = false,
        block: suspend () -> T,
    ): T = lock.withLock {
        withContext(NonCancellable) {
            effects.beginTurn()
            onLocked()
            recovery.once()
            block().also {
                // A pre-alert that waited for the other slot's sound plays once it ended (04 §5, §9.3).
                effects.player.releasePreAlerts(clocks.read()) {
                    (slots[it].timer as? TimerState.Running)?.deadlineElapsedMs
                }
                if (serviceStartAllowed) {
                    effects.retryRefusedStart(
                        effects.roles.lead?.let { slots[it].timer } ?: TimerState.Idle,
                        serviceNeeded = slots.any { BrewTimerEngine.needsService(it.timer) },
                        now = clocks.read(),
                    )
                }
                rearmWakeUp()
                // Until the stored timer is read, nothing is known to play: a watchdog set again stays.
                if (recovery.done) {
                    val now = clocks.read()
                    slots.forEach {
                        it.watchdog.follow(effects.player.alertPlayingIn == it.id, deadlineGuardAt(it.timer, now), now)
                    }
                }
                // After the turn's own cues: while a timer is active, the sounds of its next tick and
                // alert stay loaded, so they start without loading (D-092). A port that throws is
                // logged; each cue then loads its own sound.
                attempt { ports.alert.keepReady(slots.any { it.active != null }) }
                    ?.let { Log.w(TAG, "alert sounds not kept ready", it) }
            }
        }
    }

    /**
     * Recovery of this process (04 §4, §7.2; D-048, D-057, D-063); used only
     * inside [lock].
     */
    private inner class ProcessRecovery {
        /** This process has read the persisted state and sent `recover`. */
        var done = false
            private set

        /** How soon the next try comes while reads fail ([RecoveryBackoff]); alarm and wake-up alike. */
        var retryDelayMs = 0L
            private set

        /** The reads failed for so long that this process stopped its retries; a later input reads again. */
        private var retriesStopped = false

        /** The persisted state is unread and a retry comes: an alarm set again, the in-process wake-up. */
        val retrying: Boolean get() = !done && !retriesStopped

        /** Since when (elapsed) the reads of the persisted state have failed; null while none failed. */
        private var failingSinceMs: Long? = null

        /**
         * In the first turn of the process: the persisted state of each slot is restored and
         * `recover` applied once, the brew slot first (04 §7.2, D-098), so its alarms, service,
         * notification and an unplayed alert come back; then the alarms of every slot without a
         * timer are cancelled (a quick timer of v0.2.2 set its alarms under the brew slot's codes,
         * 04 §6.2), and the sessions a device restore left in progress are ended (E11). A read
         * that fails is tried again in a later turn, less and less often ([RecoveryBackoff]).
         */
        suspend fun once() {
            if (done) return
            val persisted = persistedTimer.read(clocks.read())
            if (persisted == null) {
                failed()
                // The screens do not wait for a database that cannot be read: Idle counts as known.
                mutableRecovered.value = true
                return
            }
            done = true
            val now = clocks.read()
            persisted.forEach { (slot, restored) -> recoverSlot(slots[slot], restored, now) }
            persisted.filterValues { it.state !is TimerState.Active }.keys.forEach { idle ->
                attempt { ports.alarms.reconcile(idle, DueTimes.NONE) }
                    ?.let { Log.w(TAG, "alarms of the idle $idle slot not cancelled", it) }
            }
            // The killed process's notification 5 stays posted: it goes unless a timer is shown there now (D-098).
            attempt { effects.roles.dropStaleOther() }?.let { Log.w(TAG, "stale notification 5 not removed", it) }
            // After the recovered state is published: the screens may now trust Idle.
            mutableRecovered.value = true
            // After the timer and its alert: the sessions a device restore (E11) left in progress.
            val restoredBrew = persisted[TimerSlot.BREW]?.state as? TimerState.Active
            persistedTimer.endOrphans((restoredBrew?.session?.kind as? SessionKind.Brew)?.sessionId, now)
        }

        /** Restores [restored] into [slot] and sends it `recover` (04 §7.2); a slot without a timer stays idle. */
        private suspend fun recoverSlot(slot: ControllerSlot, restored: PersistedTimer.Restored, now: ClockReading) {
            val active = restored.state as? TimerState.Active ?: return
            slot.timer = active
            slot.restoredForAlarmLog = active
            handle(slot.id, TimerEvent.Recover, now, EventSource.RECOVERY, startDetails = restored.details)
            val rebooted = active.session.bootCount != now.bootCount
            effects.record(
                TimerDiagnostic.Recovered(
                    at = now,
                    from = active.phase,
                    to = slot.active?.phase,
                    rebooted = rebooted,
                    decision = recoveryDecision(active, slot.timer, effects.player::isPlaying),
                    service = BrewTimerEngine.needsService(slot.timer),
                ),
            )
            if (rebooted) effects.notifyResumedAfterReboot(slot.id)
        }

        /**
         * Sets the alarm of [kind] in [slot] again [retryDelayMs] after [now]: its turn could not
         * read the persisted timer, and it may have been the step's only alarm (D-057).
         */
        fun retryAlarm(slot: TimerSlot, kind: TimerAlarmKind, now: ClockReading) {
            val at = retryDelayMs + when (kind.clock) {
                AlarmClock.ELAPSED -> now.elapsedMs
                AlarmClock.WALL -> now.wallMs
            }
            attempt { ports.alarms.retry(slot, kind, at) }
                ?.let { Log.e(TAG, "alarm $kind of the $slot slot could not be set again", it) }
        }

        /**
         * No read of this turn succeeded (D-057): the next try comes after the
         * [RecoveryBackoff] delay for how long the reads have failed. After 11 min the retries
         * stop, so a database that stays broken does not wake the phone forever (D-063). Only
         * the retries: the stored timer may well be valid (a cold brew hours from its
         * deadline), so its row, its alarms and its session stay. Its own alarms, a tap or the
         * next process start read again, each with its three tries.
         */
        private fun failed() {
            if (retriesStopped) return
            val now = clocks.read()
            val failingFor = now.elapsedMs - (failingSinceMs ?: now.elapsedMs.also { failingSinceMs = it })
            val delay = RecoveryBackoff.nextDelayMs(failingFor)
            if (delay != null) {
                retryDelayMs = delay
                return
            }
            retriesStopped = true
            effects.record(TimerDiagnostic.RecoveryRetriesStopped(now, failingFor))
        }
    }

    /**
     * The two slots side by side (04 §3.3, §9.3, TMR-21, TMR-22; D-098), used only inside [lock]: a brew
     * session starts in the brew slot and a quick timer in the quick slot, each while the other slot runs;
     * only a second start in the same slot is refused (`ActiveTimerExists`, by the engine), and *Beenden &
     * neu starten* ends the timer of that slot only. After every accepted event the alert arbitration
     * settles the slots.
     */
    private inner class SideBySide {
        /**
         * *Beenden & neu starten* in [slot] (TMR-21, D-054, D-098): its timer ends as by *Sitzung beenden*,
         * *Abgegossen* or *Abbrechen* ([endingEventOf]); the other slot's timer is never touched.
         */
        suspend fun endActive(slot: TimerSlot) {
            slots[slot].active?.let {
                handle(slot, endingEventOf(it), clocks.read(), EventSource.APP, savedSheetAsNotification = true)
            }
        }

        /**
         * A start ([TimerEvent.StartSession], [TimerEvent.StartQuickTimer]) in [slot] at [now] from the app;
         * an accepted one puts its timer in front of the screens.
         */
        suspend fun start(
            slot: TimerSlot,
            event: TimerEvent,
            now: ClockReading,
            start: SessionStart? = null,
        ): TransitionOutcome = handle(slot, event, now, EventSource.APP, start).also {
            if (it == TransitionOutcome.Accepted) effects.screens.focus(slot)
        }

        /**
         * The alert arbitration after an accepted event (04 §9.3, D-098): a quick alert that still rings while the
         * brew alerts is held (preemption), a held one rings once the brew no longer alerts (release), in the
         * same turn.
         */
        suspend fun settle() {
            val event = AlertArbitration.settle(slots.brew, slots.quick) ?: return
            handle(TimerSlot.QUICK, event, clocks.read(), EventSource.ARBITRATION)
        }
    }

    /**
     * Every time-based event due now in either slot, the brew slot first, each slot's in the
     * engine's order (04 §4); one made obsolete by an earlier one is ignored.
     */
    private suspend fun handleDue(source: EventSource) {
        var anyDue = false
        slots.forEach { slot ->
            val due = BrewTimerEngine.dueEvents(slot.timer, clocks.read())
            due.forEach { handle(slot.id, it, clocks.read(), source) }
            anyDue = anyDue || due.isNotEmpty()
        }
        // Nothing was due (a stale alarm, a wake-up that drifted): wait for the rest.
        if (!anyDue) rearmWakeUp()
    }

    /**
     * One event of [slot] in the current lock turn at [now]; the wake-up is re-armed after it, accepted or
     * not. [savedSheetAsNotification]: the transition's saved sheet is posted as „Sitzung gespeichert"
     * instead (D-054). The quick slot gets [event] as the arbitration maps it while the brew alerts (its
     * deadline holds, its recovery recovers held); after an accepted event the slots are settled (04 §9.3).
     */
    private suspend fun handle(
        slot: TimerSlot,
        event: TimerEvent,
        now: ClockReading,
        source: EventSource,
        start: SessionStart? = null,
        startDetails: SessionDetails? = start?.snapshot?.let(SessionDetails::of),
        savedSheetAsNotification: Boolean = false,
    ): TransitionOutcome {
        val inSlot = slots[slot]
        val before = inSlot.timer
        val due = TimerAlarmKind.of(event)?.let { scheduled(before, it, slot) }
        val sent = if (slot == TimerSlot.QUICK) AlertArbitration.quickEvent(event, slots.brew) else event
        val transition = BrewTimerEngine.reduce(before, sent, now)
        effects.record(
            TimerDiagnostic.EventHandled(
                at = now,
                event = sent,
                source = source,
                outcome = transition.outcome,
                from = (before as? TimerState.Active)?.phase,
                to = (transition.state as? TimerState.Active)?.phase,
                scheduled = due,
            ),
        )
        if (transition.outcome == TransitionOutcome.Accepted) {
            if (source != EventSource.RECOVERY) inSlot.restoredForAlarmLog = null
            inSlot.timer = transition.state
            val active = transition.state is TimerState.Active
            inSlot.details = if (active) startDetails ?: inSlot.details else null
            effects.run(slot, transition, now, start, inSlot.details, savedSheetAsNotification, scheduled = due)
            sideBySide.settle()
        }
        rearmWakeUp()
        return transition.outcome
    }

    /**
     * Arms the wake-up for the earliest due time of either slot's current state, or
     * disarms it when nothing is due; only from a state of the current boot
     * (04 §4: after `recover`). While the persisted state could not be read,
     * it comes back to read it again, with the backoff (D-057, D-063).
     */
    private fun rearmWakeUp() {
        val now = clocks.read()
        val delay = when {
            recovery.done -> slots.mapNotNull { slot ->
                slot.active?.takeIf { it.session.bootCount == now.bootCount }
                    ?.let { nextDueDelayMs(BrewTimerEngine.dueTimes(it), now) }
            }.plus(
                // A pre-alert waits for the other slot's pre-alert to ring out (04 §9.3).
                listOfNotNull(
                    effects.player.nextPreAlertReleaseAtElapsedMs?.let {
                        (it - now.elapsedMs).coerceAtLeast(0)
                    },
                ),
            ).minOrNull()

            recovery.retrying -> recovery.retryDelayMs

            else -> null
        }
        if (delay == null) {
            ports.wakeUp.cancel()
        } else {
            ports.wakeUp.arm(delay) { inTurn { handleDue(EventSource.WAKE_UP) } }
        }
    }

    private companion object {
        const val TAG = "UmamiTTimer"

        /** The events a user sends from the app or a notification (04 §3.2, §10). */
        val USER_EVENTS: Set<TimerEvent> = setOf(
            TimerEvent.Start,
            TimerEvent.Pause,
            TimerEvent.Resume,
            TimerEvent.Poured,
            TimerEvent.EndStepNow,
            TimerEvent.SkipStep,
            TimerEvent.FinishSession,
            TimerEvent.Discard,
            TimerEvent.Cancel,
            TimerEvent.AddStep,
        )
    }
}

/**
 * Which row of 04 §7.2 recovery applied to [restored], from the state it left ([recovered]);
 * [isPlaying] tells whether the recovered alert plays (it was replayed).
 */
private fun recoveryDecision(
    restored: TimerState.Active,
    recovered: TimerState,
    isPlaying: (TimerState.Alerting) -> Boolean,
): RecoveryDecision = when {
    recovered !is TimerState.Active -> RecoveryDecision.SESSION_ENDED

    recovered is TimerState.Alerting && isPlaying(recovered) -> RecoveryDecision.ALERT_REPLAYED

    recovered is TimerState.Alerting && recovered.held -> RecoveryDecision.ALERT_HELD

    restored is TimerState.WithDeadline && recovered !is TimerState.WithDeadline ->
        RecoveryDecision.OVERTIME_ACKNOWLEDGED

    recovered.session.demoted && !restored.session.demoted -> RecoveryDecision.DEMOTED

    else -> RecoveryDecision.RESTORED
}

/**
 * How long until the earliest time in [due], at [now]: elapsed fields on the
 * elapsed clock, wall fields on the wall clock; 0 when one is already due,
 * null when nothing is due.
 */
private fun nextDueDelayMs(due: DueTimes, now: ClockReading): Long? = TimerAlarmKind.entries.mapNotNull { kind ->
    kind.timeIn(due)?.let { at ->
        when (kind.clock) {
            AlarmClock.ELAPSED -> at - now.elapsedMs
            AlarmClock.WALL -> at - now.wallMs
        }
    }
}.minOrNull()?.coerceAtLeast(0)

/**
 * The deadline guard of the step running in [state] (D-068): [AlertWatchdog.GUARD_MS] after its
 * deadline, from a state of the boot of [now]; null when no step runs.
 */
private fun deadlineGuardAt(state: TimerState, now: ClockReading): Long? = (state as? TimerState.Running)
    ?.takeIf { it.session.bootCount == now.bootCount }
    ?.let { it.deadlineElapsedMs + AlertWatchdog.GUARD_MS }

/** When the alarm of [kind] is due in [state], the timer of [slot]; null when it is not due there. */
private fun scheduled(state: TimerState, kind: TimerAlarmKind, slot: TimerSlot): ScheduledTime? =
    kind.timeIn(BrewTimerEngine.dueTimes(state))?.let { ScheduledTime(kind, it, slot) }

/**
 * The user event that ends [state] for *Beenden & neu starten* (D-054): *Sitzung beenden* for a
 * brew session; for the quick timer *Abgegossen* while alerting, else *Abbrechen* (04 §10).
 */
private fun endingEventOf(state: TimerState.Active): TimerEvent = when {
    state.session.kind is SessionKind.Brew -> TimerEvent.FinishSession
    state is TimerState.Alerting -> TimerEvent.Poured
    else -> TimerEvent.Cancel
}

/** The settings; an unreadable settings file counts as the defaults (D-054). */
private suspend fun SettingsRepository.current(): UserSettings = settings
    .catch { e -> if (e is IOException) emit(UserSettings()) else throw e }
    .first()

/**
 * The UI state of [slot]'s timer ([BrewSessionController.slotStates]): the brew session's or the quick timer's,
 * Idle while the slot has none.
 */
fun BrewSessionController.state(slot: TimerSlot): StateFlow<TimerUiState> = slotStates.getValue(slot)

/**
 * A user event ([BrewSessionController.send]) to the slot of the timer in front of the screens
 * ([BrewSessionController.state]), the brew slot while none is: for callers that see one timer.
 */
suspend fun BrewSessionController.send(event: TimerEvent, source: EventSource = EventSource.APP): TransitionOutcome =
    send((state.value as? TimerUiState.Active)?.slot ?: TimerSlot.BREW, event, source)
