package com.umamit.core.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.ColorInt
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
 * @property promote request a promoted ongoing notification; pass
 *   [TimerNotifications.canPromote], so it falls back to a normal ongoing one.
 */
data class LiveUpdateContent(
    val title: String,
    val text: String,
    val countdownToWallMs: Long?,
    val shortCriticalText: String?,
    val segments: List<Segment>,
    val points: List<Point>,
    val progress: Int,
    val promote: Boolean,
    val contentIntent: PendingIntent? = null,
    val actions: List<NotificationCompat.Action> = emptyList(),
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
 * - `setRequestPromotedOngoing(true)` when promotion is allowed; on Android 16
 *   the variant (colorized or not) that `Notification.hasPromotableCharacteristics()`
 *   accepts on this build.
 *
 * API names: `docs/plans/M1-probe-api-notes.md`.
 */
object LiveUpdateNotification {
    private const val TAG = "UmamiTLiveUpdate"

    fun build(context: Context, content: LiveUpdateContent): Notification {
        val plain = builder(context, content, colorized = false).build()
        if (!content.promote || Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA ||
            plain.hasPromotableCharacteristics()
        ) {
            return plain
        }
        // Early Android 16 builds only promote colorized notifications (seen in the
        // Android 16 framework that Robolectric 4.17 ships); later builds promote
        // only non-colorized ones. Use the variant this device considers promotable.
        val colorized = builder(context, content, colorized = true).build()
        val promotable = colorized.hasPromotableCharacteristics()
        if (promotable) {
            Log.i(TAG, "Promotable only when colorized on this build; using the colorized Live Update")
        } else {
            Log.w(TAG, "Live Update has no promotable characteristics on this build")
        }
        return if (promotable) colorized else plain
    }

    private fun builder(context: Context, content: LiveUpdateContent, colorized: Boolean): NotificationCompat.Builder {
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
            .setSmallIcon(R.drawable.ic_timer_notification)
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
            .setRequestPromotedOngoing(content.promote)

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
        content.actions.forEach(builder::addAction)
        return builder
    }
}
