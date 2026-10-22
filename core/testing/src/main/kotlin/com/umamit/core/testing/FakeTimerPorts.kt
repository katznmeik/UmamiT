package com.umamit.core.testing

import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.SavedSheetPort
import com.umamit.core.timer.controller.StepAlert
import com.umamit.core.timer.controller.TimerAlarmPort
import com.umamit.core.timer.controller.TimerAlertPort
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import com.umamit.core.timer.controller.TimerNotifierPort
import com.umamit.core.timer.controller.TimerServicePort
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerWakeUpPort
import com.umamit.core.timer.engine.DueTimes

// Fakes of the BrewSessionController's ports (04 §4, spec #40 seam 2). Each keeps the
// state its Android adapter would leave behind (armed alarms, a running service, the
// posted notification, a playing alert) and appends every call to a shared
// TimerPortLog, so a test can check the order of the calls across ports.

/** One call of the controller on a port, in a [TimerPortLog]. Tests may add their own kinds. */
interface PortCall

/** The calls of several fakes in the order they happened. */
class TimerPortLog {
    private val recorded = mutableListOf<PortCall>()

    val calls: List<PortCall> get() = synchronized(recorded) { recorded.toList() }

    fun record(call: PortCall) {
        synchronized(recorded) { recorded += call }
    }

    /** Forgets the calls so far, e.g. after a test's setup. */
    fun clear() {
        synchronized(recorded) { recorded.clear() }
    }
}

/** `ScheduleAlarms` with the full due set. */
data class AlarmsReconciled(val due: DueTimes) : PortCall

data class ServiceStarted(val phase: TimerPhase) : PortCall

data object ServiceStopped : PortCall

data class NotificationShown(val phase: TimerPhase, val fullScreen: Boolean) : PortCall

data class SessionSavedNotified(val sessionId: String) : PortCall

data object PreAlertPlayed : PortCall

data class AlertStarted(val alert: StepAlert) : PortCall

data object AlertStopped : PortCall

data class SavedSheetShown(val sessionId: String) : PortCall

/** Fake [TimerAlarmPort]: [armed] is the due set of the last reconcile, as the alarm manager would hold it. */
class FakeTimerAlarms(private val log: TimerPortLog = TimerPortLog()) : TimerAlarmPort {
    var armed: DueTimes = DueTimes.NONE
        private set

    override fun reconcile(due: DueTimes) {
        armed = due
        log.record(AlarmsReconciled(due))
    }
}

/** Fake [TimerServicePort]: [running] while started and not stopped; [foreground] is its notification's state. */
class FakeTimerService(private val log: TimerPortLog = TimerPortLog()) : TimerServicePort {
    var running = false
        private set
    var foreground: TimerUiState.Active? = null
        private set

    /** Makes every port call throw, like an adapter bug, to check that nothing after it is skipped. */
    var failing = false

    override fun startForeground(state: TimerUiState.Active) {
        check(!failing) { "service start failed (test)" }
        running = true
        foreground = state
        log.record(ServiceStarted(state.phase))
    }

    override fun stopForeground() {
        check(!failing) { "service stop failed (test)" }
        running = false
        foreground = null
        log.record(ServiceStopped)
    }
}

/** Fake [TimerNotifierPort]: [shown] is the last posted timer notification's state; [sessionsSaved] notification 3. */
class FakeTimerNotifier(private val log: TimerPortLog = TimerPortLog()) : TimerNotifierPort {
    var shown: TimerUiState.Active? = null
        private set
    val sessionsSaved = mutableListOf<String>()

    override fun update(state: TimerUiState.Active, fullScreen: Boolean) {
        shown = state
        log.record(NotificationShown(state.phase, fullScreen))
    }

    override fun notifySessionSaved(sessionId: String) {
        sessionsSaved += sessionId
        log.record(SessionSavedNotified(sessionId))
    }
}

/**
 * Fake [TimerAlertPort]: [playing] is the alert that plays until it is stopped or the test
 * reports its end through the controller; [started] every alert ever started, [ticks] the
 * pre-alert ticks.
 */
class FakeTimerAlertPlayer(private val log: TimerPortLog = TimerPortLog()) : TimerAlertPort {
    var playing: StepAlert? = null
        private set
    val started = mutableListOf<StepAlert>()
    var ticks = 0
        private set

    override fun playPreAlert() {
        ticks++
        log.record(PreAlertPlayed)
    }

    override fun startAlert(alert: StepAlert) {
        playing = alert
        started += alert
        log.record(AlertStarted(alert))
    }

    override fun stopAlert() {
        playing = null
        log.record(AlertStopped)
    }
}

/** Fake [TimerDiagnosticsPort]: the [entries] in order. */
class FakeTimerDiagnostics : TimerDiagnosticsPort {
    private val recorded = mutableListOf<TimerDiagnostic>()

    val entries: List<TimerDiagnostic> get() = synchronized(recorded) { recorded.toList() }

    override fun record(entry: TimerDiagnostic) {
        synchronized(recorded) { recorded += entry }
    }

    /** The entries of type [T]. */
    inline fun <reified T : TimerDiagnostic> entriesOf(): List<T> = entries.filterIsInstance<T>()
}

/** Fake [SavedSheetPort]: the sessions whose „Gespeichert" sheet was shown, in order. */
class FakeSavedSheet(private val log: TimerPortLog = TimerPortLog()) : SavedSheetPort {
    val shown = mutableListOf<String>()

    override fun showSavedSheet(sessionId: String) {
        shown += sessionId
        log.record(SavedSheetShown(sessionId))
    }
}

/**
 * Fake [TimerWakeUpPort] on [clock]: an armed wake-up is due at the elapsed time of its
 * arming plus its delay. [advanceBy] lets time pass and fires the wake-up whenever it comes
 * due, at exactly its time, as the process's own timer would.
 */
class FakeTimerWakeUp(private val clock: FakeClock) : TimerWakeUpPort {
    private var onDue: (suspend () -> Unit)? = null

    /** When the armed wake-up fires, on the elapsed clock; null when none is armed. */
    var dueAtElapsedMs: Long? = null
        private set

    override fun arm(delayMs: Long, onDue: suspend () -> Unit) {
        require(delayMs >= 0) { "a wake-up delay is never negative, was $delayMs" }
        dueAtElapsedMs = clock.elapsed + delayMs
        this.onDue = onDue
    }

    override fun cancel() {
        dueAtElapsedMs = null
        onDue = null
    }

    /** Moves [clock] forward by [ms], firing the wake-up each time it comes due on the way. */
    suspend fun advanceBy(ms: Long) {
        val target = clock.elapsed + ms
        repeat(MAX_FIRES_PER_ADVANCE) {
            val dueAt = dueAtElapsedMs?.takeIf { it <= target } ?: return clock.advanceBy(target - clock.elapsed)
            clock.advanceBy((dueAt - clock.elapsed).coerceAtLeast(0))
            val action = checkNotNull(onDue)
            cancel()
            action()
        }
        error("the wake-up fired $MAX_FIRES_PER_ADVANCE times without time passing: it spins")
    }

    private companion object {
        const val MAX_FIRES_PER_ADVANCE = 1_000
    }
}
