package com.umamit.core.data.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.umamit.core.data.R

/**
 * The backup reminder's notification (BAK-06, 06 §5.4, D-130 Q11): „Zeit für eine Sicherung" with „23 neue
 * Sessions seit der letzten Sicherung." (never saved: „Du hast noch keine Sicherung erstellt."), a tap opens SCR-26.
 *
 * **Apart from the timer (rule 8):** the channel `reminders` („Erinnerungen") at importance LOW, which keeps it out of
 * the alert's section (D-044); no sound, no vibration, no heads-up, no full-screen intent; its own ID [ID].
 */
object BackupReminderNotifications {
    const val CHANNEL_ID = "reminders"

    /** The reminder (06 §5.4); the catalog has 10 and 11, the low-stock reminder 12, the timer 1–5. */
    const val ID = 13

    private const val REQUEST_OPEN = 13

    /** Creates the channel; idempotent. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.data_reminders_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.data_reminders_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** The reminder for [sessions] since the last export; [neverSaved] without one. */
    fun notification(context: Context, sessions: Int, neverSaved: Boolean): Notification {
        val text = if (neverSaved) {
            context.getString(R.string.data_backup_reminder_text_never)
        } else {
            context.getString(R.string.data_backup_reminder_text, sessions)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            // The app's sprig, as the catalog's notifications show it.
            .setSmallIcon(R.drawable.ic_stat_catalog)
            .setContentTitle(context.getString(R.string.data_backup_reminder_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openBackup(context))
            .build()
    }

    /** Posts the reminder as notification [ID], replacing an older one. */
    fun post(context: Context, sessions: Int, neverSaved: Boolean) {
        ensureChannel(context)
        context.getSystemService(NotificationManager::class.java)
            ?.notify(ID, notification(context, sessions, neverSaved))
    }

    /** Removes the reminder, if shown: switched off. */
    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }

    /** A tap opens SCR-26 (03 §2.4); the app's root lets an alert finish first (rule 8). */
    private fun openBackup(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        OpenBackupIntents.openBackup(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
