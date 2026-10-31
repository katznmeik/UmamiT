package com.umamit.core.timer.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import com.umamit.core.timer.TimerNotifications

/**
 * The timer's `specialUse` foreground service (04 §6.1, §6.3; D-053, D-057).
 * It keeps the process in the foreground while the timer needs it (short
 * steps, waiting states, the alert), shows the notification it is sent as
 * its foreground notification and holds the partial wake lock for the time it
 * is sent. It has no timer logic: [BrewTimerServicePort] decides what to show
 * from the controller's state. The alert player (`AlertPlayback`, D-059)
 * plays while it keeps the process in the foreground and holds the wake lock.
 *
 * - The alert (ID 2) replaces the Live Update (ID 1) as the foreground
 *   notification, and a Live Update posted without the service is cancelled,
 *   so Android 16 never force-groups the two and silences the alert (D-044).
 *   Starting in the foreground, the service first takes over the other one when
 *   a killed process left it flagged as a foreground notification, so the
 *   switch removes it (D-084).
 * - A command sent while the service is already in the foreground only
 *   updates a notification of the same ID; a new ID takes `startForeground`.
 * - If `startForeground` is refused ([ForegroundServiceStartNotAllowedException]),
 *   the notification is posted without the service (04 §6.3): the service
 *   leaves the foreground first, so an alert is still the only notification,
 *   and the refusal is logged.
 * - An update of the same ID right after `startForeground` is posted once
 *   more shortly after ([REPOST_DELAY_MS]), so the start's queued post cannot
 *   outlive it (D-071).
 * - Each foreground start and the end of the service are logged too (06 §5.6).
 */
class BrewTimerService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** [SystemClock.uptimeMillis] of the last successful `startForeground`; null before the first. */
    private var lastForegroundStartAt: Long? = null

    /** The pending re-post of a same-ID update ([scheduleRepost]); at most one. Main thread only. */
    private var pendingRepost: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        // A foreground notification on a channel that does not exist crashes the app (fresh
        // install, cleared data); creating the channels is idempotent.
        TimerNotifications.ensureChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = intent?.let(::notificationOf)
        val id = intent?.let(::notificationIdOf) ?: 0
        if (notification != null && id != 0) {
            show(id, notification, foregroundRequired = isForegroundRequired(intent))
            holdWakeLock(wakeLockMsOf(intent))
            ForegroundServiceState.releaseHandover()
        } else {
            Log.w(TAG, "Command without a notification: ${intent?.action}")
        }
        // The stop names this start: Android ignores it when a newer start is already queued
        // (stopSelf() would stop anyway and crash a pending startForegroundService).
        if (ForegroundServiceState.commandHandled() && stopSelfResult(startId)) {
            cancelRepost()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            ForegroundServiceState.foregroundId = 0
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelRepost()
        releaseWakeLock()
        ForegroundServiceState.foregroundId = 0
        ForegroundServiceState.report(ServiceReport.Stopped)
        super.onDestroy()
    }

    private fun show(id: Int, notification: Notification, foregroundRequired: Boolean) {
        // Only one of notifications 1 and 2 at a time (D-044, 04 §8.2). Android refuses an app's
        // cancel of a notification flagged as a foreground notification; for the service's own,
        // the switch of startForeground removes it.
        TimerNotifications.cancel(this, otherIdThan(id))
        // A pending re-post holds older content; this command is newer.
        cancelRepost()
        val shownNow = ForegroundServiceState.foregroundId == id
        if (shownNow && startsAlert(notification)) {
            // An alert that starts while another alert is the foreground notification 2 (the brew's taking over a
            // ringing quick alert, a quick alert released when the brew session ends during its alert; D-098):
            // System UI launches a full-screen intent only for a notification it adds, never for an update, so
            // the earlier alert goes first and the new one is posted as a new notification.
            Log.i(TAG, "show($id): a new alert, posted as a new notification")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            ForegroundServiceState.foregroundId = 0
        } else if (!foregroundRequired && shownNow) {
            Log.i(TAG, "show($id): notify, foregroundRequired=false")
            TimerNotifications.post(this, id, notification)
            val sinceStart = lastForegroundStartAt?.let { SystemClock.uptimeMillis() - it }
            if (sinceStart != null && sinceStart < REPOST_WINDOW_MS) scheduleRepost(id, notification)
            return
        } else if (!ForegroundServiceState.isForeground) {
            takeOverStale(otherIdThan(id))
        }
        try {
            Log.i(TAG, "show($id): startForeground, foregroundRequired=$foregroundRequired")
            ServiceCompat.startForeground(this, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            lastForegroundStartAt = SystemClock.uptimeMillis()
            ForegroundServiceState.foregroundId = id
            ForegroundServiceState.report(ServiceReport.InForeground(id))
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "startForeground($id) refused; posting it without the service", e)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            ForegroundServiceState.foregroundId = 0
            TimerNotifications.cancel(this, otherIdThan(id))
            TimerNotifications.post(this, id, notification)
            ForegroundServiceState.report(ServiceReport.Refused(id, e))
        }
    }

    /**
     * Takes over notification [id] when a killed process left it flagged as a foreground
     * notification that no service owns (D-084): `startForeground` with a copy makes it this
     * service's foreground notification, so the switch to the new ID right after removes it, as
     * the system, before the new notification is posted (D-044). AOSP leaves such a notification
     * when the process dies while it posts an update of its foreground notification: the
     * system's cancel for the dead service can come first, and the update, already flagged,
     * is posted after it. The app's cancel never removes it.
     *
     * Only while the service is not in the foreground (its first start, or one after a refusal):
     * in the foreground, the other ID is its own foreground notification or an ordinary one. The
     * copy never alerts again and opens no screen. Any failure is logged, and the normal start
     * follows (rule 8).
     */
    @Suppress("TooGenericExceptionCaught") // the takeover is an extra; nothing it throws may stop the start
    private fun takeOverStale(id: Int) {
        try {
            val stale = checkNotNull(getSystemService(NotificationManager::class.java)).activeNotifications
                .firstOrNull { it.id == id && it.tag == null && it.notification.flags and FOREGROUND != 0 }
                ?: return
            val copy = stale.notification.clone().apply {
                flags = flags or Notification.FLAG_ONLY_ALERT_ONCE
                fullScreenIntent = null
            }
            Log.w(TAG, "show: taking over the stale foreground notification $id")
            ServiceCompat.startForeground(this, id, copy, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            ForegroundServiceState.report(ServiceReport.TookOverStale(id))
        } catch (e: Exception) {
            Log.w(TAG, "Taking over notification $id failed; starting without it", e)
        }
    }

    /**
     * Posts [notification] as [id] once more after [REPOST_DELAY_MS], if [id] is then still the
     * foreground notification (D-071). The copy has [Notification.FLAG_ONLY_ALERT_ONCE], so it
     * never rings or vibrates again.
     */
    private fun scheduleRepost(id: Int, notification: Notification) {
        val copy = notification.clone().apply { flags = flags or Notification.FLAG_ONLY_ALERT_ONCE }
        val repost = Runnable {
            pendingRepost = null
            if (ForegroundServiceState.foregroundId == id) {
                Log.i(TAG, "show($id): re-post")
                TimerNotifications.post(this, id, copy)
            }
        }
        pendingRepost = repost
        mainHandler.postDelayed(repost, REPOST_DELAY_MS)
    }

    private fun cancelRepost() {
        pendingRepost?.let(mainHandler::removeCallbacks)
        pendingRepost = null
    }

    /** Holds the partial wake lock for [timeoutMs] from now (04 §6.3), or releases it for 0. */
    private fun holdWakeLock(timeoutMs: Long) {
        if (timeoutMs <= 0) {
            releaseWakeLock()
            return
        }
        val lock = wakeLock ?: checkNotNull(getSystemService(PowerManager::class.java))
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        lock.acquire(timeoutMs)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    internal companion object {
        private const val TAG = "UmamiTService"
        private const val FOREGROUND = Notification.FLAG_FOREGROUND_SERVICE
        private const val WAKE_LOCK_TAG = "UmamiT:BrewTimer"
        private const val ACTION_SHOW = "com.umamit.core.timer.action.SHOW"
        private const val EXTRA_ID = "com.umamit.core.timer.extra.NOTIFICATION_ID"
        private const val EXTRA_NOTIFICATION = "com.umamit.core.timer.extra.NOTIFICATION"
        private const val EXTRA_WAKE_LOCK_MS = "com.umamit.core.timer.extra.WAKE_LOCK_MS"
        private const val EXTRA_FOREGROUND_REQUIRED = "com.umamit.core.timer.extra.FOREGROUND_REQUIRED"

        /**
         * How long after a `startForeground` a same-ID update is posted once more (D-071).
         * AOSP posts the notification of `startForeground` asynchronously
         * (`ServiceRecord.postNotification` queues it on the ActivityManager's handler), while
         * the service's own `notify` is a direct call into the NotificationManager. An update
         * right after the start can arrive first, and the start's late post then overwrites it:
         * up to 15 s while running (the refresh repairs it), for good in a waiting state.
         */
        const val REPOST_WINDOW_MS = 1_000L

        /** How long after such an update its re-post follows: long enough for the queued post to land. */
        const val REPOST_DELAY_MS = 500L

        /**
         * The command to show [notification] as notification [id] and hold the wake
         * lock for [wakeLockMs]. [foregroundRequired]: it is sent with
         * `startForegroundService`, so the service must call `startForeground`.
         */
        fun command(
            context: Context,
            id: Int,
            notification: Notification,
            wakeLockMs: Long,
            foregroundRequired: Boolean,
        ): Intent = Intent(context, BrewTimerService::class.java)
            .setAction(ACTION_SHOW)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_NOTIFICATION, notification)
            .putExtra(EXTRA_WAKE_LOCK_MS, wakeLockMs)
            .putExtra(EXTRA_FOREGROUND_REQUIRED, foregroundRequired)

        fun notificationIdOf(command: Intent): Int = command.getIntExtra(EXTRA_ID, 0)

        fun notificationOf(command: Intent): Notification? =
            command.getParcelableExtra(EXTRA_NOTIFICATION, Notification::class.java)

        fun wakeLockMsOf(command: Intent): Long = command.getLongExtra(EXTRA_WAKE_LOCK_MS, 0)

        fun isForegroundRequired(command: Intent): Boolean = command.getBooleanExtra(EXTRA_FOREGROUND_REQUIRED, false)

        /**
         * [notification] starts an alert: its full-screen intent is to take the screen now (D-057). An alert shown
         * again (an update in the same alert, a re-sent start) has none or must not alert again
         * ([Notification.FLAG_ONLY_ALERT_ONCE]).
         */
        fun startsAlert(notification: Notification): Boolean = notification.fullScreenIntent != null &&
            notification.flags and Notification.FLAG_ONLY_ALERT_ONCE == 0

        fun otherIdThan(id: Int): Int =
            if (id == TimerNotifications.ID_ALERT) TimerNotifications.ID_LIVE_UPDATE else TimerNotifications.ID_ALERT
    }
}
