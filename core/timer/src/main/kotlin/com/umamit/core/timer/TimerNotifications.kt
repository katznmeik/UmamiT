package com.umamit.core.timer

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Notification channels and IDs of the timer (04 §8.1, §8.2). */
object TimerNotifications {
    /** Live Update / ongoing notification: importance DEFAULT, silent (promotion needs more than MIN). */
    const val CHANNEL_BREW_TIMER = "brew_timer"

    /** Alert with the full-screen intent: importance HIGH, no sound (the AlertPlayer plays it, 04 §9). */
    const val CHANNEL_STEEP_FINISHED = "steep_finished"

    /** Live Update; the foreground service notification except while alerting. */
    const val ID_LIVE_UPDATE = 1

    /**
     * „Jetzt abgießen!" alert. While alerting it replaces the Live Update as the
     * foreground service notification, so it is the app's only notification of
     * importance DEFAULT or higher. Android 16 force-groups two such ungrouped
     * notifications of an app and marks them `FLAG_SILENT`, and System UI then
     * suppresses the full-screen intent and the heads-up
     * (`docs/plans/M1-probe-api-notes.md`).
     */
    const val ID_ALERT = 2

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
            ),
        )
    }

    /**
     * Whether the user currently allows promoted ongoing notifications (Live
     * Updates, 04 §8.3); for the status row and the reliability banner. The Live
     * Update requests promotion on Android 16 regardless, so it is promoted as
     * soon as the user allows it. Always false below Android 16 (API 36), where
     * the notification stays a normal ongoing one.
     */
    fun canPromote(context: Context): Boolean = NotificationManagerCompat.from(context).canPostPromotedNotifications()

    /** Whether full-screen intents are allowed (04 §8.2); otherwise the alert is a heads-up only. */
    fun canUseFullScreenIntent(context: Context): Boolean =
        NotificationManagerCompat.from(context).canUseFullScreenIntent()

    /** Posts [notification] when notifications are allowed; returns whether it was posted. */
    fun post(context: Context, id: Int, notification: Notification): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "Notification $id not posted: POST_NOTIFICATIONS is not granted")
            return false
        }
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }
}
