package com.umamit.core.timer.service

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * What [BrewTimerServicePort] and [BrewTimerService] share inside the process
 * (D-057): whether the service runs in the foreground and with which
 * notification, how many commands are on their way to it, and whether a stop
 * waits for them. The service is a system-created singleton of the process,
 * so its state is process-wide too; it dies with the process.
 *
 * A stop never reaches a service whose start command is still on its way:
 * stopping a service that was started with `startForegroundService` before it
 * called `startForeground` crashes the app. The service then stops itself once
 * it has shown the last of them.
 *
 * It also keeps whether Android refused the last start, until a newer command
 * or a stop replaces it, so the controller's next turn that may start the
 * service sends one again (D-063); and a short wake lock that bridges from a
 * command to the service's own wake lock.
 */
internal object ForegroundServiceState {
    private val lock = Any()
    private var pendingCommands = 0
    private var stopWanted = false
    private var refused = false
    private var handover: PowerManager.WakeLock? = null

    /** The ID of the service's foreground notification; 0 while it is not in the foreground. */
    @Volatile
    var foregroundId: Int = 0

    /** Logs what the service did (06 §5.6); set by the port, which owns the clocks and the log. */
    @Volatile
    var reporter: ((ServiceReport) -> Unit)? = null

    val isForeground: Boolean get() = foregroundId != 0

    /** Commands are on their way to the service (a start it must answer with `startForeground`). */
    val hasPendingCommands: Boolean get() = synchronized(lock) { pendingCommands > 0 }

    /** A command is about to be sent to the service: it is wanted, not stopped. */
    fun commandSent() = synchronized(lock) {
        pendingCommands++
        stopWanted = false
        refused = false
    }

    /** Android refused a start (`startForegroundService`); the next turn that may start it sends one again. */
    fun startRefused() = synchronized(lock) {
        refused = true
    }

    /** True when a start was refused and no command or stop replaced it since; it is taken. */
    fun takeRefused(): Boolean = synchronized(lock) { refused.also { refused = false } }

    /** Sending the command failed; it never reaches the service. */
    fun commandNotSent() = synchronized(lock) {
        pendingCommands = (pendingCommands - 1).coerceAtLeast(0)
    }

    /** The port wants the service stopped; true when it may stop it now, false when the service will itself. */
    fun stopRequested(): Boolean = synchronized(lock) {
        stopWanted = true
        refused = false
        pendingCommands == 0
    }

    /**
     * Keeps the CPU awake for at most [HANDOVER_MS] while a command travels to the service:
     * an alarm's wake lock ends with its receiver, the service takes its own only on the main
     * thread later (04 §6.3).
     */
    fun holdHandover(context: Context) = synchronized(lock) {
        val wakeLock = handover ?: checkNotNull(context.getSystemService(PowerManager::class.java))
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, HANDOVER_TAG)
            .apply { setReferenceCounted(false) }
            .also { handover = it }
        wakeLock.acquire(HANDOVER_MS)
    }

    /** The service holds its own wake lock (or none is needed): the bridge ends. */
    fun releaseHandover() = synchronized(lock) {
        handover?.takeIf { it.isHeld }?.release()
    }

    /** The service handled a command; true when it must stop itself now (a stop waited for it). */
    fun commandHandled(): Boolean = synchronized(lock) {
        pendingCommands = (pendingCommands - 1).coerceAtLeast(0)
        stopWanted && pendingCommands == 0
    }

    /** Hands [report] to the log; a log that fails never stops the service (rule 8). */
    @Suppress("TooGenericExceptionCaught")
    fun report(report: ServiceReport) {
        try {
            reporter?.invoke(report)
        } catch (e: Exception) {
            Log.w("UmamiTService", "Diagnostics of $report lost", e)
        }
    }

    /** For tests: a process without the service. */
    fun reset() = synchronized(lock) {
        pendingCommands = 0
        stopWanted = false
        foregroundId = 0
        refused = false
        handover?.takeIf { it.isHeld }?.release()
        handover = null
    }

    private const val HANDOVER_TAG = "UmamiT:ServiceHandover"
    private const val HANDOVER_MS = 10_000L
}

/** What [BrewTimerService] reports for the diagnostics log (06 §5.6: FGS start/stop and refusals). */
internal sealed interface ServiceReport {
    /** `startForeground` succeeded with notification [notificationId]. */
    data class InForeground(val notificationId: Int) : ServiceReport

    /**
     * Notification [notificationId], left flagged as a foreground notification by a killed
     * process, was taken over so the foreground switch removes it (D-084).
     */
    data class TookOverStale(val notificationId: Int) : ServiceReport

    /** `startForeground` with notification [notificationId] was refused ([error]). */
    data class Refused(val notificationId: Int, val error: Exception) : ServiceReport

    /** The service was destroyed. */
    data object Stopped : ServiceReport
}
