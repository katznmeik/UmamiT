package com.umamit.core.data.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.umamit.core.data.R
import com.umamit.core.model.LowStockTea
import java.text.NumberFormat
import java.util.Locale

/**
 * The daily low-stock reminder's notification (INV-11, 04 §8.1, 06 §5.4, D-098): „3 Tees werden knapp" with
 * one line per tea („Tanabata · 14 g", „Hisui · leer"), least stock first.
 *
 * **Apart from the timer (rule 8):** its own channel `stock` („Vorrat") at importance LOW, which keeps it out
 * of the alert's section, so the alert stays the app's only notification of importance DEFAULT or higher
 * (D-044); no sound, no vibration, no heads-up, no full-screen intent; its own ID [ID], never one of the
 * timer's.
 */
object LowStockReminderNotifications {
    const val CHANNEL_ID = "stock"

    /** The reminder (06 §5.4); the catalog has 10 and 11, the timer 1–5. */
    const val ID = 12

    private const val REQUEST_OPEN = 12

    /** Creates the channel; idempotent. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.data_stock_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.data_stock_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** The reminder for [teas] (not empty), least stock first. */
    fun notification(context: Context, teas: List<LowStockTea>): Notification {
        require(teas.isNotEmpty()) { "A day without a low tea posts nothing (INV-11 AC2)" }
        val title = if (teas.size == 1) {
            context.getString(R.string.data_stock_reminder_title_one, teas.single().teaName)
        } else {
            context.getString(R.string.data_stock_reminder_title, teas.size)
        }
        val lines = teas.map { line(context, it) }
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach(style::addLine)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            // The app's sprig, as the catalog's notifications show it.
            .setSmallIcon(R.drawable.ic_stat_catalog)
            .setContentTitle(title)
            .setContentText(lines.joinToString(context.getString(R.string.data_stock_reminder_separator)))
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openStart(context))
            .build()
    }

    /** Posts the reminder for [teas] (not empty) as notification [ID], replacing yesterday's. */
    fun post(context: Context, teas: List<LowStockTea>) {
        ensureChannel(context)
        context.getSystemService(NotificationManager::class.java)?.notify(ID, notification(context, teas))
    }

    /** Removes the reminder, if shown: switched off, or a day without a low tea. */
    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }

    /** „Tanabata · 14 g", „Tanabata · 12,5 g", „Hisui · leer" (INV-05, German format to 0,1 g). */
    private fun line(context: Context, tea: LowStockTea): String = if (tea.isEmpty) {
        context.getString(R.string.data_stock_reminder_line_empty, tea.teaName)
    } else {
        context.getString(R.string.data_stock_reminder_line, tea.teaName, grams(tea.remainingGrams))
    }

    private fun grams(grams: Double): String = NumberFormat.getNumberInstance(Locale.GERMANY).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }.format(grams)

    /** A tap opens Start at its root (03 §2.4); the app's root lets an alert finish first (rule 8). */
    private fun openStart(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        OpenStartIntents.openStart(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
