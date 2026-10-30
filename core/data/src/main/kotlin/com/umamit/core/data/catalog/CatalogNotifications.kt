package com.umamit.core.data.catalog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.umamit.core.data.R

/**
 * The catalog refresh's notifications (CAT-07 AC1, 06 §5.3, 04 §8.1/§8.2): the progress while it
 * runs (the foreground service's notification) and the result when it has ended.
 *
 * **Apart from the timer (rule 8, CAT-07 AC8):** their own channel `catalog` at importance LOW,
 * which Android 16 sorts into the silent section, so they are never grouped with (and never
 * silence) the step alert, whose channel is HIGH (D-044); their own IDs from 10 on, never one of
 * the timer's 1–4; no sound, no vibration, no heads-up.
 */
internal object CatalogNotifications {
    const val CHANNEL_ID = "catalog"

    /** The progress, as the foreground service's notification. */
    const val ID_PROGRESS = 10

    /** The result of the last refresh; it stays until tapped, dismissed or seen in the app. */
    const val ID_RESULT = 11

    private const val REQUEST_CANCEL = 10
    private const val REQUEST_OPEN = 11

    /** Creates the channel; idempotent, and needed before a foreground notification is shown on it. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.data_catalog_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.data_catalog_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** „Katalog wird geladen · Seiten: 120 von 1560 · Produkte: 40" with *Abbrechen*. */
    fun progress(context: Context, progress: CatalogRefreshProgress): Notification {
        val starting = progress.pagesTotal == 0
        val text = if (starting) {
            context.getString(R.string.data_catalog_progress_starting)
        } else {
            context.getString(
                R.string.data_catalog_progress,
                progress.pagesDone,
                progress.pagesTotal,
                progress.products,
            )
        }
        return builder(context)
            .setContentTitle(context.getString(R.string.data_catalog_progress_title))
            .setContentText(text)
            .setProgress(progress.pagesTotal, progress.pagesDone, starting)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                0,
                context.getString(R.string.data_catalog_cancel),
                PendingIntent.getService(
                    context,
                    REQUEST_CANCEL,
                    CatalogRefreshService.cancelIntent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
    }

    /** The end of a run; null when there is nothing to tell (the user cancelled it). */
    fun result(context: Context, outcome: CatalogRefreshOutcome): Notification? {
        val (title, text) = when (outcome) {
            CatalogRefreshOutcome.Cancelled -> return null

            is CatalogRefreshOutcome.Success ->
                context.getString(R.string.data_catalog_result_success_title) to summary(context, outcome)

            is CatalogRefreshOutcome.Failure -> {
                val reason = context.getString(outcome.reason())
                // The shop-changed copy (03 §5) says it itself.
                val text = if (outcome is CatalogRefreshOutcome.ShopChanged) {
                    reason
                } else {
                    "$reason ${context.getString(R.string.data_catalog_result_kept)}"
                }
                context.getString(R.string.data_catalog_result_failed_title) to text
            }
        }
        return builder(context)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    /** „274 Grüntees · 12 neu · 5 geändert · 2 nicht mehr im Sortiment · 3 Rezepte prüfen" (CAT-07 AC3). */
    fun summary(context: Context, outcome: CatalogRefreshOutcome.Success): String {
        val resources = context.resources
        return outcome.summary().joinToString(context.getString(R.string.data_catalog_summary_separator)) { part ->
            when (part) {
                is SummaryPart.Teas -> resources.getQuantityString(
                    R.plurals.data_catalog_tea_count,
                    part.count,
                    part.count,
                )

                is SummaryPart.New -> context.getString(R.string.data_catalog_summary_new, part.count)

                is SummaryPart.Changed -> context.getString(R.string.data_catalog_summary_changed, part.count)

                is SummaryPart.Discontinued -> context.getString(R.string.data_catalog_summary_discontinued, part.count)

                is SummaryPart.RecipesToCheck ->
                    resources.getQuantityString(R.plurals.data_catalog_summary_recipes, part.count, part.count)

                SummaryPart.NoChanges -> context.getString(R.string.data_catalog_summary_unchanged)
            }
        }
    }

    fun post(context: Context, id: Int, notification: Notification) {
        context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
    }

    fun cancelResult(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_RESULT)
    }

    private fun builder(context: Context): NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_catalog)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setShowWhen(false)
        .setContentIntent(openRefresh(context))

    /**
     * A tap opens Sammlung › Katalog with the refresh sheet (SCR-22, 03 §2.4, D-097), the same
     * for the progress and the result. Never the timer's intent, and no navigation of its own:
     * the app's root decides when it shows, alerts first (rule 8).
     */
    private fun openRefresh(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        CatalogRefreshIntents.openRefresh(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @StringRes
    private fun CatalogRefreshOutcome.Failure.reason(): Int = when (this) {
        is CatalogRefreshOutcome.Blocked -> R.string.data_catalog_result_blocked
        is CatalogRefreshOutcome.Offline -> R.string.data_catalog_result_offline
        is CatalogRefreshOutcome.ShopUnavailable -> R.string.data_catalog_result_unavailable
        is CatalogRefreshOutcome.ShopChanged -> R.string.data_catalog_result_shop_changed
        is CatalogRefreshOutcome.StorageFailed -> R.string.data_catalog_result_storage
    }
}
