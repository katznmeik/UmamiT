package com.umamit.core.timer.service

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
 */
internal object ForegroundServiceState {
    private val lock = Any()
    private var pendingCommands = 0
    private var stopWanted = false

    /** The ID of the service's foreground notification; 0 while it is not in the foreground. */
    @Volatile
    var foregroundId: Int = 0

    /** Logs a start refused inside the service (notification ID, error); set by the port. */
    @Volatile
    var reporter: ((notificationId: Int, error: Exception) -> Unit)? = null

    val isForeground: Boolean get() = foregroundId != 0

    /** A command is about to be sent to the service: it is wanted, not stopped. */
    fun commandSent() = synchronized(lock) {
        pendingCommands++
        stopWanted = false
    }

    /** Sending the command failed; it never reaches the service. */
    fun commandNotSent() = synchronized(lock) {
        pendingCommands = (pendingCommands - 1).coerceAtLeast(0)
    }

    /** The port wants the service stopped; true when it may stop it now, false when the service will itself. */
    fun stopRequested(): Boolean = synchronized(lock) {
        stopWanted = true
        pendingCommands == 0
    }

    /** The service handled a command; true when it must stop itself now (a stop waited for it). */
    fun commandHandled(): Boolean = synchronized(lock) {
        pendingCommands = (pendingCommands - 1).coerceAtLeast(0)
        stopWanted && pendingCommands == 0
    }

    fun report(notificationId: Int, error: Exception) {
        reporter?.invoke(notificationId, error)
    }

    /** For tests: a process without the service. */
    fun reset() = synchronized(lock) {
        pendingCommands = 0
        stopWanted = false
        foregroundId = 0
    }
}
