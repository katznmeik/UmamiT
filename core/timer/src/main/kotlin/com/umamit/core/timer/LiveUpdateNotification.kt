package com.umamit.core.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * What the Live Update shows (04 §8.2, §8.3).
 *
 * @property countdownToWallMs wall-clock deadline: when set, the status-bar chip
 *   is the chronometer countdown (RUNNING); otherwise [shortCriticalText] is used.
 * @property segments ProgressStyle segments (lengths in any common unit).
 * @property points ProgressStyle points, positions in the unit of [segments].
 * @property progress progress in the unit of [segments].
 * @property requestPromotion request promotion on Android 16 (a Live Update); false for a
 *   regular ongoing notification, such as a demoted session (TMR-14).
 * @property deleteIntent sent when the user dismisses it (04 §8.3).
 */
data class LiveUpdateContent(
    val title: String,
    val text: String,
    val countdownToWallMs: Long?,
    val shortCriticalText: String?,
    val segments: List<Segment>,
    val points: List<Point>,
    val progress: Int,
    val contentIntent: PendingIntent? = null,
    val actions: List<NotificationCompat.Action> = emptyList(),
    val requestPromotion: Boolean = true,
    val deleteIntent: PendingIntent? = null,
) {
    data class Segment(val length: Int, @param:ColorInt val color: Int)

    data class Point(val position: Int, @param:ColorInt val color: Int)
}

/**
 * Builds the Live Update on the `brew_timer` channel (04 §8.3):
 * - ongoing, silent, with title and a monochrome small icon;
 * - `NotificationCompat.ProgressStyle` with segments and points (framework
 *   `Notification.ProgressStyle` on API 36; a plain progress bar below);
 * - chronometer countdown chip (`setUsesChronometer`, `setChronometerCountDown`,
 *   `setWhen(deadlineWall)`) or a short critical text;
 * - on Android 16 (API 36) always `setRequestPromotedOngoing(true)`, whether or
 *   not the user allows promotion right now
 *   ([TimerNotifications.canPromote]): the system decides when the notification
 *   is posted, and when the user allows promotion later it re-checks the posted
 *   notification at once. Of the two variants (colorized or not) it posts the one
 *   that `Notification.hasPromotableCharacteristics()` accepts on this build.
 *
 * API names and the two Android 16 rule sets: `docs/plans/M1-probe-api-notes.md`.
 */
object LiveUpdateNotification {
    private const val TAG = "UmamiTLiveUpdate"

    fun build(context: Context, content: LiveUpdateContent): Notification =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && content.requestPromotion) {
            promotable(context, content)
        } else {
            builder(context, content, requestPromotion = false, colorized = false).build()
        }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun promotable(context: Context, content: LiveUpdateContent): Notification {
        val plain = builder(context, content, requestPromotion = true, colorized = false).build()
        if (plain.hasPromotableCharacteristics()) return plain
        // Android 16 without the Live Update UI (flag android.app.ui_rich_ongoing off,
        // e.g. the first API 36 release and the framework Robolectric 4.17 ships) only
        // promotes colorized notifications; with it (Android 16 QPR1 and later) only
        // non-colorized ones that carry the request. Use the variant this device accepts.
        val colorized = builder(context, content, requestPromotion = true, colorized = true).build()
        val promotable = colorized.hasPromotableCharacteristics()
        if (promotable) {
            Log.i(TAG, "Promotable only when colorized on this build; using the colorized Live Update")
        } else {
            Log.w(TAG, "Live Update has no promotable characteristics on this build")
        }
        return if (promotable) colorized else plain
    }

    private fun builder(
        context: Context,
        content: LiveUpdateContent,
        requestPromotion: Boolean,
        colorized: Boolean,
    ): NotificationCompat.Builder {
        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(
                content.segments.map { NotificationCompat.ProgressStyle.Segment(it.length).setColor(it.color) },
            )
            .setProgressPoints(
                content.points.map { NotificationCompat.ProgressStyle.Point(it.position).setColor(it.color) },
            )
            .setProgress(content.progress)

        val builder = NotificationCompat.Builder(context, TimerNotifications.CHANNEL_BREW_TIMER)
            .setSmallIcon(R.drawable.ic_stat_umamit)
            .setColor(ContextCompat.getColor(context, R.color.timer_matcha))
            .setColorized(colorized)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(style)
            .setRequestPromotedOngoing(requestPromotion)

        val countdownTo = content.countdownToWallMs
        if (countdownTo != null) {
            builder.setWhen(countdownTo)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
                .setUsesChronometer(false)
            content.shortCriticalText?.let(builder::setShortCriticalText)
        }
        content.contentIntent?.let(builder::setContentIntent)
        content.deleteIntent?.let(builder::setDeleteIntent)
        content.actions.forEach(builder::addAction)
        return builder
    }
}
