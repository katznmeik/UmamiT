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
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import com.umamit.core.timer.controller.TimerServicePort
import com.umamit.core.timer.controller.TimerUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The controller's service port (04 §4, §6.3; D-053, D-057): starts, updates
 * and stops [BrewTimerService] with the notification of the state and the
 * wake lock it needs. Runs inside the controller's event lock and never
 * blocks: the service handles each command later on the main thread.
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
 *   in-process wake-up can switch it to the alert at the deadline.
 * - The wake lock covers a running step and the longest alert (Lang, 2 min)
 *   plus 30 s (04 §6.3); a waiting state releases it.
 */
@Singleton
class BrewTimerServicePort @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clocks: TimerClocks,
    private val diagnostics: TimerDiagnosticsPort,
    private val notifications: TimerStateNotifications,
) : TimerServicePort {
    init {
        ForegroundServiceState.reporter = { report ->
            val now = clocks.read()
            diagnostics.record(
                when (report) {
                    is ServiceReport.InForeground -> TimerDiagnostic.ServiceInForeground(now, report.notificationId)

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

    override fun startForeground(state: TimerUiState.Active, fullScreen: Boolean) =
        start(state, fullScreen, again = false)

    override fun retryRefusedStart(state: TimerUiState.Active?, fullScreen: Boolean) {
        if (ForegroundServiceState.takeRefused() && state != null) start(state, fullScreen, again = true)
    }

    /** [again]: a refused start sent once more, whose notification must not alert a second time. */
    private fun start(state: TimerUiState.Active, fullScreen: Boolean, again: Boolean) {
        val now = clocks.read()
        val id = notifications.idFor(state)
        val notification = notifications.build(state, fullScreen, now.elapsedMs)
        if (again) notification.flags = notification.flags or Notification.FLAG_ONLY_ALERT_ONCE
        val wakeLockMs = wakeLockMs(state, now)
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
            TimerNotifications.ensureChannels(context)
            TimerNotifications.cancel(context, BrewTimerService.otherIdThan(id))
            TimerNotifications.post(context, id, notification)
            diagnostics.record(TimerDiagnostic.ServiceStartRefused(now, id, e, again))
        }
    }

    override fun stopForeground() {
        if (ForegroundServiceState.stopRequested()) {
            // The next start goes with startForegroundService, even before onDestroy has run.
            ForegroundServiceState.foregroundId = 0
            context.stopService(Intent(context, BrewTimerService::class.java))
        }
        // Notifications 1 and 2 also go when they were posted without the service (04 §4, D-053).
        TimerNotifications.cancel(context, TimerNotifications.ID_LIVE_UPDATE)
        TimerNotifications.cancel(context, TimerNotifications.ID_ALERT)
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

        /** 04 §6.3: running: remaining + the longest alert + 30 s; alerting: the alert + 30 s; else none. */
        fun wakeLockMs(state: TimerUiState.Active, now: ClockReading): Long = when (state.phase) {
            TimerPhase.RUNNING -> (checkNotNull(state.deadlineElapsedMs) - now.elapsedMs).coerceAtLeast(0) +
                ALERT_MAX_MS + WAKE_LOCK_MARGIN_MS

            TimerPhase.ALERTING -> ALERT_MAX_MS + WAKE_LOCK_MARGIN_MS

            TimerPhase.READY, TimerPhase.PAUSED, TimerPhase.FINISHED -> 0L
        }
    }
}
