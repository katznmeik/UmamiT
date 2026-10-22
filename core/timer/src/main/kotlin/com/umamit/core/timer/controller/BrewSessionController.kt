package com.umamit.core.timer.controller

import com.umamit.core.common.ClockReading
import com.umamit.core.data.ActiveBrewStore
import com.umamit.core.data.DatabaseTransactions
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SessionStart
import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerState
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
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
 * 5 °C steps), the recipe's pre-alert [recipePreAlertSeconds] (null: the
 * global setting; 0: off) and the Schritte-Sheet's [preAlert] choice for this
 * session only (SCR-31).
 */
data class BrewStart(
    val snapshot: SessionSnapshot,
    val steps: List<PlannedStep>,
    val recipePreAlertSeconds: Int?,
    val preAlert: PreAlertChoice = PreAlertChoice.Inherit,
) {
    init {
        require(steps.isNotEmpty()) { "a session needs at least one step" }
    }
}

/**
 * The single owner of the timer state (04 §2.1, §4; D-056): every brew
 * session and quick timer goes through it. Each input runs in one turn of one
 * event lock: the clocks are read inside the lock, once per reduce (D-054);
 * the engine's effects then run in the order of D-047 ([TransitionEffects]):
 * the data effects and `Persist` in one database transaction, then
 * `ScheduleAlarms`, then the side effects through the [TimerPorts]. After
 * every event the in-process wake-up is re-armed for the next due time.
 * [state] is the UI state of the screens, the active brew bar and the
 * notifications.
 *
 * Recovery at process start comes with M1-09 part 3 (#46): it restores
 * `active_brew` and sends `recover` as the first turn. Until then the
 * controller starts in Idle.
 */
@Singleton
class BrewSessionController @Inject constructor(
    private val clocks: TimerClocks,
    sessions: SessionRepository,
    activeBrew: ActiveBrewStore,
    transactions: DatabaseTransactions,
    private val settings: SettingsRepository,
    private val ports: TimerPorts,
) {
    private val lock = Mutex()
    private val effects = TransitionEffects(sessions, activeBrew, transactions, ports)

    /** The engine state; read and written only inside [lock]. */
    private var timer: TimerState = TimerState.Idle

    /** The tea of the active session, for the UI and the notifications; read and written inside [lock]. */
    private var teaName: String? = null

    /** The timer as the UI shows it; updated on every accepted transition, after it is persisted. */
    val state: StateFlow<TimerUiState> = effects.state

    /**
     * *Brühen starten* (TMR-01): starts a brew session waiting in READY. The
     * pre-alert inherits the recipe's value, else the global setting, frozen
     * now (04 §5). Returns `Rejected(ActiveTimerExists)` while a session or
     * quick timer is active (TMR-21); then nothing is written.
     */
    suspend fun startSession(request: BrewStart): TransitionOutcome {
        val global = settings.current().preAlertSeconds
        val preAlert = PreAlertSettings.inherited(request.recipePreAlertSeconds, global).with(request.preAlert)
        val event = TimerEvent.StartSession(
            sessionId = UUID.randomUUID().toString(),
            steps = request.steps,
            preAlert = preAlert,
            longStepsOnly = BrewTimerEngine.longStepsOnly(request.snapshot.method),
        )
        return inTurn {
            val now = clocks.read()
            val start = SessionStart(
                id = event.sessionId,
                snapshot = request.snapshot,
                steps = request.steps,
                preAlertSeconds = preAlert.effectiveSeconds,
                startedAtMs = now.wallMs,
                zoneId = now.zone.id,
            )
            handle(event, now, EventSource.APP, start, request.snapshot.teaName)
        }
    }

    /**
     * *Schnelltimer* (TMR-22, 04 §10): one infusion of [durationSeconds] with the
     * global pre-alert, waiting in READY; nothing is recorded. Returns
     * `Rejected(ActiveTimerExists)` while a timer is active (TMR-21) and
     * `Rejected(InvalidQuickTimer)` for a duration outside 5 s – 24 h.
     */
    suspend fun startQuickTimer(durationSeconds: Int): TransitionOutcome {
        val event = TimerEvent.StartQuickTimer(durationSeconds, settings.current().preAlertSeconds)
        return inTurn { handle(event, clocks.read(), EventSource.APP) }
    }

    /**
     * A user event from the app or a notification ([source]): every event of
     * 04 §3.2 and §10 except the starts ([startSession], [startQuickTimer]) and
     * the events the controller produces itself (`recover`, `alertCompleted`,
     * the time-based events), which throw [IllegalArgumentException]. Returns
     * the engine's outcome; a rejected or ignored event changes nothing (E5).
     */
    suspend fun send(event: TimerEvent, source: EventSource = EventSource.APP): TransitionOutcome {
        require(event in USER_EVENTS || event is TimerEvent.EditSteps) { "not a user event: $event" }
        return inTurn { handle(event, clocks.read(), source) }
    }

    /**
     * The alarm of [kind] arrived (04 §6.2; from the alarm receiver): every
     * time-based event due now is sent, not only the kind's own, so a late or
     * early alarm is harmless (04 §3.2 guards, §4).
     */
    suspend fun onAlarm(kind: TimerAlarmKind) {
        inTurn {
            effects.record(TimerDiagnostic.AlarmReceived(clocks.read(), kind, scheduled(timer, kind)))
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
            handle(TimerEvent.AlertCompleted, now, EventSource.ALERT_PLAYER)
        } else {
            TransitionOutcome.Ignored
        }
    }

    /** The in-process wake-up fired. */
    private suspend fun wakeUp() = inTurn { handleDue(EventSource.WAKE_UP) }

    /**
     * One turn of the event lock (04 §4). Once it holds the lock, the turn
     * runs to its end even if the caller is cancelled (a screen that closes
     * right after *Abgegossen*): a transition half applied would leave the
     * alert playing or the stored state behind (rule 8).
     */
    private suspend fun <T> inTurn(block: suspend () -> T): T =
        lock.withLock { withContext(NonCancellable) { block() } }

    /**
     * Every time-based event due now, in the engine's order (04 §4); one made
     * obsolete by an earlier one is ignored.
     */
    private suspend fun handleDue(source: EventSource) {
        val due = BrewTimerEngine.dueEvents(timer, clocks.read())
        due.forEach { handle(it, clocks.read(), source) }
        // Nothing was due (a stale alarm, a wake-up that drifted): wait for the rest.
        if (due.isEmpty()) rearmWakeUp()
    }

    /** One event in the current lock turn at [now]; the wake-up is re-armed after it, accepted or not. */
    private suspend fun handle(
        event: TimerEvent,
        now: ClockReading,
        source: EventSource,
        start: SessionStart? = null,
        startTeaName: String? = null,
    ): TransitionOutcome {
        val before = timer
        val transition = BrewTimerEngine.reduce(before, event, now)
        effects.record(
            TimerDiagnostic.EventHandled(
                at = now,
                event = event,
                source = source,
                outcome = transition.outcome,
                from = (before as? TimerState.Active)?.phase,
                to = (transition.state as? TimerState.Active)?.phase,
                scheduled = TimerAlarmKind.of(event)?.let { scheduled(before, it) },
            ),
        )
        if (transition.outcome == TransitionOutcome.Accepted) {
            timer = transition.state
            teaName = if (transition.state is TimerState.Active) startTeaName ?: teaName else null
            effects.run(transition, now, start, teaName)
        }
        rearmWakeUp()
        return transition.outcome
    }

    /**
     * Arms the wake-up for the earliest due time of the current state, or
     * disarms it when nothing is due; only from a state of the current boot
     * (04 §4: after `recover`).
     */
    private fun rearmWakeUp() {
        val now = clocks.read()
        val active = (timer as? TimerState.Active)?.takeIf { it.session.bootCount == now.bootCount }
        val delay = nextDueDelayMs(active?.let { BrewTimerEngine.dueTimes(it) } ?: DueTimes.NONE, now)
        if (delay == null) ports.wakeUp.cancel() else ports.wakeUp.arm(delay) { wakeUp() }
    }

    private companion object {
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

/** When the alarm of [kind] is due in [state]; null when it is not due there. */
private fun scheduled(state: TimerState, kind: TimerAlarmKind): ScheduledTime? =
    kind.timeIn(BrewTimerEngine.dueTimes(state))?.let { ScheduledTime(kind, it) }

/** The settings; an unreadable settings file counts as the defaults (D-054). */
private suspend fun SettingsRepository.current(): UserSettings = settings
    .catch { e -> if (e is IOException) emit(UserSettings()) else throw e }
    .first()

/** These settings with the session's own pre-alert [choice] (*Wie Einstellung*, *Aus*, N s; D-048). */
private fun PreAlertSettings.with(choice: PreAlertChoice): PreAlertSettings = copy(
    sessionOverrideSeconds = when (choice) {
        PreAlertChoice.Inherit -> null
        PreAlertChoice.Off -> 0
        is PreAlertChoice.Seconds -> choice.seconds
    },
)
