package com.umamit.core.timer

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.umamit.core.timer.service.BrewTimerService
import com.umamit.core.timer.service.ForegroundServiceState

/** Notification channels and IDs of the timer (04 §8.1, §8.2). */
object TimerNotifications {
    /** Live Update / ongoing notification: importance DEFAULT, silent (promotion needs more than MIN). */
    const val CHANNEL_BREW_TIMER = "brew_timer"

    /** Alert with the full-screen intent: importance HIGH, no sound (the AlertPlayer plays it, 04 §9). */
    const val CHANNEL_STEEP_FINISHED = "steep_finished"

    /** „Sitzung gespeichert" and the reboot notice: importance LOW, silent (04 §8.1). */
    const val CHANNEL_SESSIONS = "sessions"

    /**
     * The other active timer, notification [ID_OTHER] (04 §8.1, D-098): importance LOW, silent, never promoted,
     * so the alert stays the app's only notification of importance DEFAULT or higher (D-044).
     */
    const val CHANNEL_TIMER_OTHER = "timer_other"

    /** Live Update; the foreground service notification except while alerting. */
    const val ID_LIVE_UPDATE = 1

    /**
     * „Jetzt abgießen!" alert. While alerting it replaces the Live Update as the
     * foreground service notification, so it is the app's only notification of
     * importance DEFAULT or higher. Android 16 force-groups two such ungrouped
     * notifications of an app and marks them `FLAG_SILENT`, and System UI then
     * suppresses the full-screen intent and the heads-up
     * (`docs/plans/M1-android16-api-notes.md`).
     */
    const val ID_ALERT = 2

    /** „Sitzung gespeichert" (`NotifySessionSaved`, TMR-14), on [CHANNEL_SESSIONS]. */
    const val ID_SESSION_SAVED = 3

    /**
     * The timer's notification while the service does not run: a long running step („Fertig
     * um 14:30", TMR-18) and a demoted session (TMR-14). Never posted while alerting, and
     * cancelled before every start of the service, so the alert stays the app's only
     * notification of importance DEFAULT or higher (D-044). An ID of its own, because the
     * foreground notification 1 is removed by the system asynchronously when the service
     * stops, which could remove a notification 1 posted right after (D-064).
     */
    const val ID_QUIET = 4

    /**
     * The other active timer (04 §8.2, D-098): with a brew session and a quick timer at once, the one whose
     * notification the service does not show, on [CHANNEL_TIMER_OTHER]; posted directly, never by the service.
     */
    const val ID_OTHER = 5

    private const val TAG = "UmamiTTimer"

    /** The notifications posted without the service whose post is posted once more after a burst (#135). */
    private val REPOSTED_IDS = setOf(ID_QUIET, ID_OTHER)

    /** Orders a re-post against a newer post and a cancel of its ID; guards [pendingReposts]. */
    private val repostLock = Any()

    /** The pending re-post per ID; one that is no longer here does nothing when it comes due. */
    private val pendingReposts = mutableMapOf<Int, Runnable>()

    /** Creates (or updates the names of) the timer channels; idempotent. */
    fun ensureChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_BREW_TIMER, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.timer_channel_brew_timer_name))
                    .setDescription(context.getString(R.string.timer_channel_brew_timer_description))
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_STEEP_FINISHED, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.timer_channel_steep_finished_name))
                    .setDescription(context.getString(R.string.timer_channel_steep_finished_description))
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_SESSIONS, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.timer_channel_sessions_name))
                    .setDescription(context.getString(R.string.timer_channel_sessions_description))
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_TIMER_OTHER, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.timer_channel_other_name))
                    .setDescription(context.getString(R.string.timer_channel_other_description))
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .setShowBadge(false)
                    .build(),
            ),
        )
    }

    /**
     * Posts [notification] when notifications are allowed; returns whether it was posted. Each post counts against
     * the app's rate, and Android drops an update posted shortly after another post (#135): the service posts its
     * own once more, and so does this for the quiet notification 4 and notification 5, posted without the service
     * ([scheduleRepost]). The app cannot know whether a post is an update (a killed process leaves 5 behind), so
     * any post of 4 or 5 shortly after another is; the alert 2 never is.
     */
    fun post(context: Context, id: Int, notification: Notification): Boolean = synchronized(repostLock) {
        val mayBeLost = id in REPOSTED_IDS && ForegroundServiceState.postedRecently
        pendingReposts.remove(id)
        notifyIfAllowed(context, id, notification).also { posted ->
            if (posted && mayBeLost) scheduleRepost(context.applicationContext, id, notification)
        }
    }

    /** Removes notification [id]; a pending re-post of it goes too, so an ended timer never comes back. */
    fun cancel(context: Context, id: Int) = synchronized(repostLock) {
        pendingReposts.remove(id)
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun notifyIfAllowed(context: Context, id: Int, notification: Notification): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "Notification $id not posted: POST_NOTIFICATIONS is not granted")
            return false
        }
        NotificationManagerCompat.from(context).notify(id, notification)
        ForegroundServiceState.posted()
        return true
    }

    /**
     * Posts [notification] as [id] once more after `REPOST_DELAY_MS` (#135, as the service does for its own), unless
     * a newer post or a cancel of [id] came first. The copy never alerts.
     */
    private fun scheduleRepost(context: Context, id: Int, notification: Notification) {
        val copy = notification.clone().apply { flags = flags or Notification.FLAG_ONLY_ALERT_ONCE }
        val repost = object : Runnable {
            override fun run() {
                synchronized(repostLock) {
                    if (pendingReposts[id] !== this) return
                    pendingReposts.remove(id)
                    Log.i(TAG, "post($id): re-post")
                    notifyIfAllowed(context, id, copy)
                }
            }
        }
        pendingReposts[id] = repost
        Handler(Looper.getMainLooper()).postDelayed(repost, BrewTimerService.REPOST_DELAY_MS)
    }
}
