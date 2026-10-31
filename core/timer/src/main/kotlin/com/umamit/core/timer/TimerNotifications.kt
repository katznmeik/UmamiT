package com.umamit.core.timer

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
     * the app's rate (#135): the service posts an update that follows one shortly once more.
     */
    fun post(context: Context, id: Int, notification: Notification): Boolean {
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

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }
}
