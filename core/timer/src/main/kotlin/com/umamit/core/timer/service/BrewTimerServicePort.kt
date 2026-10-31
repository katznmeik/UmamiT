package com.umamit.core.timer.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.umamit.core.common.ClockReading
import com.umamit.core.model.AlertLength
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.TimerNotifications
import com.umamit.core.timer.TimerStateNotifications
import com.umamit.core.timer.alert.limitMs
import com.umamit.core.timer.controller.CoroutineTimerWakeUp
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import com.umamit.core.timer.controller.TimerServicePort
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerWakeUpPort
import com.umamit.core.timer.notification.LiveUpdateDismissal
import com.umamit.core.timer.notification.runsWithService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The controller's service port (04 §4, §6.3; D-053, D-057) and the one place that shows the
 * timer's notifications 1 (Live Update), 2 (alert) and the quiet one (04 §8.2; D-064): it
 * starts, updates and stops [BrewTimerService] with the notification of the state and the
 * wake lock it needs, and shows `UpdateNotification` for the notifier ([show]). Runs inside the
 * controller's event lock and never blocks: the service handles each command later on the
 * main thread.
 *
 * - While the service is not in the foreground, a command goes with
 *   `startForegroundService`, which Android allows from the user's tap, a
 *   notification action and an exact alarm (04 §6.2), or while the app is
 *   exempt from battery optimisation. A refusal
 *   ([ForegroundServiceStartNotAllowedException]) is logged and the
 *   notification is posted without the service until the next interaction or
 *   alarm (04 §6.3).
 * - While it runs in the foreground, a command goes with `startService`, which
 *   the running foreground service allows from the background, so the
 *   in-process wake-up can switch it to the alert at the deadline. Every change
 *   of the notification goes through the service then, so notification 2 is never
 *   posted next to the service's notification 1 (D-044, D-057).
 * - The wake lock covers a running step and the longest alert (Lang, 2 min)
 *   plus 30 s (04 §6.3); a waiting state releases it.
 * - A running step's Live Update is refreshed every 15 s (04 §8.3), in order with the
 *   controller's commands; a refresh never follows a newer state.
 */
@Singleton
class BrewTimerServicePort internal constructor(
    private val context: Context,
    private val clocks: TimerClocks,
    private val diagnostics: TimerDiagnosticsPort,
    private val notifications: TimerStateNotifications,
    private val refresh: TimerWakeUpPort,
) : TimerServicePort {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        clocks: TimerClocks,
        diagnostics: TimerDiagnosticsPort,
        notifications: TimerStateNotifications,
    ) : this(
        context,
        clocks,
        diagnostics,
        notifications,
        CoroutineTimerWakeUp(CoroutineScope(SupervisorJob() + Dispatchers.Default)),
    )

    /**
     * Orders everything that shows notifications 1, 2 and the quiet one: the controller's
     * commands and the refresh. A command is sent while it is held, so the service receives
     * the commands in this order.
     */
    private val lock = Any()

    /** Counts the controller's commands; a refresh armed for an older one is dropped. Guarded by [lock]. */
    private var generation = 0L

    /** The state and full-screen flag last sent or posted, so the same update is not sent twice. Guarded by [lock]. */
    private var lastShown: Pair<TimerUiState.Active, Boolean>? = null

    /** Notification 5, the other active timer (D-098); its wake-lock need adds to the service's. Guarded by [lock]. */
    private var other: TimerUiState.Active? = null

    init {
        ForegroundServiceState.reporter = { report ->
            val now = clocks.read()
            diagnostics.record(
                when (report) {
                    is ServiceReport.InForeground -> TimerDiagnostic.ServiceInForeground(now, report.notificationId)

                    is ServiceReport.TookOverStale -> TimerDiagnostic.ServiceTookOverStale(now, report.notificationId)

                    is ServiceReport.Refused -> TimerDiagnostic.ServiceStartRefused(
                        now,
                        report.notificationId,
                        report.error,
                    )

                    ServiceReport.Stopped -> TimerDiagnostic.ServiceStopped(now)
                },
            )
        }
    }

    override fun startForeground(state: TimerUiState.Active, fullScreen: Boolean) = synchronized(lock) {
        generation++
        // The quiet notification goes before the service shows its own (D-044): it was posted
        // directly, so the cancel takes effect before the service's notification arrives.
        TimerNotifications.cancel(context, TimerNotifications.ID_QUIET)
        send(state, fullScreen, clocks.read())
        armRefresh(state, generation)
    }

    override fun retryRefusedStart(state: TimerUiState.Active?, fullScreen: Boolean) = synchronized(lock) {
        if (!ForegroundServiceState.takeRefused() || state == null) return@synchronized
        generation++
        TimerNotifications.cancel(context, TimerNotifications.ID_QUIET)
        send(state, fullScreen, clocks.read(), again = true)
        armRefresh(state, generation)
    }

    override fun stopForeground() = synchronized(lock) {
        generation++
        lastShown = null
        refresh.cancel()
        if (ForegroundServiceState.stopRequested()) {
            // The next start goes with startForegroundService, even before onDestroy has run.
            ForegroundServiceState.foregroundId = 0
            context.stopService(Intent(context, BrewTimerService::class.java))
        }
        // Notifications 1 and 2 also go when they were posted without the service (04 §4, D-053).
        TIMER_IDS.forEach { TimerNotifications.cancel(context, it) }
    }

    /**
     * `UpdateNotification` (the notifier, 04 §8.2): shows the notification of [state]. While the
     * service runs or starts, it goes through the service, which shows it as its foreground
     * notification, so the ID changes only there (D-057). Otherwise it is posted directly: the
     * quiet notification of a long step or a demoted session, or notification 1 or 2 when the
     * start of the service was refused. A Live Update the user dismissed is not posted again
     * for the same state (04 §8.3).
     */
    fun show(state: TimerUiState.Active, fullScreen: Boolean) = synchronized(lock) {
        generation++
        if (lastShown != state to fullScreen) show(state, fullScreen, clocks.read())
        armRefresh(state, generation)
    }

    private fun show(state: TimerUiState.Active, fullScreen: Boolean, now: ClockReading) {
        if (!state.alerting && LiveUpdateDismissal.isDismissed(state)) return
        val serviceShows = ForegroundServiceState.isForeground || ForegroundServiceState.hasPendingCommands
        // The service runs for [state] when it needs it, or when notification 5's timer does (04 §6.1 two slots,
        // D-098): the timer due next is then the service's Live Update, also a long step that runs without it alone.
        val withService = state.runsWithService || other?.runsWithService == true
        if (withService && serviceShows) send(state, fullScreen, now) else post(state, fullScreen, now)
    }

    /**
     * Notification 5 (04 §8.2, D-098): posts the other active timer's notification [state] on `timer_other`,
     * or removes it (null). A short step running there needs the wake lock as the service's own does (04 §6.1
     * two slots): when its need grows, the service's last notification is sent again with the longer wake lock,
     * without alerting again.
     */
    fun showOther(state: TimerUiState.Active?) = synchronized(lock) {
        val now = clocks.read()
        val before = other
        other = state
        if (state == null) {
            TimerNotifications.cancel(context, TimerNotifications.ID_OTHER)
        } else if (state != before) {
            TimerNotifications.ensureChannels(context)
            TimerNotifications.post(context, TimerNotifications.ID_OTHER, notifications.other(state, now))
        }
        val shown = lastShown
        val grows = state != null && wakeLockMs(state, now) > (before?.let { wakeLockMs(it, now) } ?: 0L)
        if (grows && shown != null && ForegroundServiceState.isForeground) {
            send(shown.first, shown.second, now, again = true)
        }
    }

    /** Posts the notification of [state] without the service; the other timer notifications go first (D-044). */
    private fun post(state: TimerUiState.Active, fullScreen: Boolean, now: ClockReading) {
        lastShown = state to fullScreen
        postAlone(notifications.idFor(state), notifications.build(state, fullScreen, now))
    }

    private fun postAlone(id: Int, notification: Notification) {
        TimerNotifications.ensureChannels(context)
        TIMER_IDS.filter { it != id }.forEach { TimerNotifications.cancel(context, it) }
        TimerNotifications.post(context, id, notification)
    }

    /** The 15 s refresh of a running step's Live Update (04 §8.3), while no newer command came. */
    private fun armRefresh(state: TimerUiState.Active, sent: Long) {
        if (state.phase != TimerPhase.RUNNING) {
            refresh.cancel()
            return
        }
        refresh.arm(REFRESH_MS) {
            synchronized(lock) {
                if (sent == generation) {
                    show(state, fullScreen = false, now = clocks.read())
                    armRefresh(state, sent)
                }
            }
        }
    }

    /**
     * Sends [state] to the service: to the running one, else with a foreground start (04 §6.3).
     * [again]: a refused start sent once more (D-063), whose notification must not alert a
     * second time; refused again, it is logged as such.
     */
    private fun send(state: TimerUiState.Active, fullScreen: Boolean, now: ClockReading, again: Boolean = false) {
        lastShown = state to fullScreen
        val id = notifications.idFor(state, byService = true)
        val notification = notifications.build(state, fullScreen, now)
        if (again) notification.flags = notification.flags or Notification.FLAG_ONLY_ALERT_ONCE
        // The longer of the two timers' needs (04 §6.1 two slots, D-098).
        val wakeLockMs = maxOf(wakeLockMs(state, now), other?.let { wakeLockMs(it, now) } ?: 0L)
        ForegroundServiceState.holdHandover(context)
        if (ForegroundServiceState.isForeground && sendToRunning(id, notification, wakeLockMs)) return
        val command = BrewTimerService.command(context, id, notification, wakeLockMs, foregroundRequired = true)
        ForegroundServiceState.commandSent()
        try {
            ContextCompat.startForegroundService(context, command)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            ForegroundServiceState.commandNotSent()
            ForegroundServiceState.startRefused()
            Log.w(TAG, "Foreground service start refused; posting notification $id without it", e)
            postAlone(id, notification)
            diagnostics.record(TimerDiagnostic.ServiceStartRefused(now, id, e, again))
        }
    }

    /** A command to the running foreground service; false when Android refused it (the service died meanwhile). */
    private fun sendToRunning(id: Int, notification: Notification, wakeLockMs: Long): Boolean {
        ForegroundServiceState.commandSent()
        return try {
            context.startService(
                BrewTimerService.command(context, id, notification, wakeLockMs, foregroundRequired = false),
            )
            true
        } catch (e: IllegalStateException) {
            ForegroundServiceState.commandNotSent()
            Log.w(TAG, "Command to the running service refused; starting it again", e)
            false
        }
    }

    private companion object {
        const val TAG = "UmamiTService"
        val ALERT_MAX_MS: Long = checkNotNull(AlertLength.LONG.limitMs)
        const val WAKE_LOCK_MARGIN_MS = 30_000L

        /** 04 §8.3: the Live Update of a running step is refreshed every 15 s, not every second. */
        const val REFRESH_MS = 15_000L

        /** The timer's notifications of importance DEFAULT or higher: only one is ever shown (D-044). */
        val TIMER_IDS = listOf(
            TimerNotifications.ID_LIVE_UPDATE,
            TimerNotifications.ID_ALERT,
            TimerNotifications.ID_QUIET,
        )

        /**
         * 04 §6.3: a short running step: remaining + the longest alert + 30 s; alerting: the alert + 30 s; else
         * none, also for a long step the service shows for the other timer (04 §6.1).
         */
        fun wakeLockMs(state: TimerUiState.Active, now: ClockReading): Long = when (state.phase) {
            TimerPhase.RUNNING -> if (state.longStep) {
                0L
            } else {
                (checkNotNull(state.deadlineElapsedMs) - now.elapsedMs).coerceAtLeast(0) +
                    ALERT_MAX_MS + WAKE_LOCK_MARGIN_MS
            }

            TimerPhase.ALERTING -> ALERT_MAX_MS + WAKE_LOCK_MARGIN_MS

            TimerPhase.READY, TimerPhase.PAUSED, TimerPhase.FINISHED -> 0L
        }
    }
}
