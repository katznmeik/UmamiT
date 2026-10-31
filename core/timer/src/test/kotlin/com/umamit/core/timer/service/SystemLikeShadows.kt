package com.umamit.core.timer.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.os.SystemClock
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowService

/**
 * Robolectric's notification manager with three rules of AOSP's NotificationManagerService
 * that Robolectric leaves out (D-084):
 *
 * - An app's cancel never removes a notification flagged `FLAG_FOREGROUND_SERVICE`
 *   (`cancelNotificationWithTag`: `mustNotHaveFlags` for callers other than the system).
 * - An app's update of such a notification keeps the flag (`PostNotificationRunnable`: "don't
 *   lose the foreground service state").
 * - The system ([systemCancel]) removes it.
 *
 * It also records, for each notification ID, which IDs were showing when it was first posted,
 * so a test can check that the alert never appeared next to notification 1 (D-044), and which
 * notifications took the screen: System UI launches a full-screen intent only for a notification
 * that is added, never for an update of one it already shows (`HeadsUpCoordinator`'s
 * `onEntryAdded` makes the full-screen decision; `onEntryUpdated` only decides about a heads-up
 * again), so [fullScreenLaunches] lists the new posts that carry one (D-098, #135).
 *
 * With [limitUpdateRate] it also sheds updates as AOSP does (`checkDisqualifyingFeatures`, #135): an update of a
 * notification that is showing is dropped without a word to the app while the package's enqueue rate is above 5 per
 * second (`mMaxPackageEnqueueRate`); every post that is let through counts, a new one, an update and the service's
 * `startForeground` post alike (`NotificationUsageStats.registerEnqueuedByAppAndTrimmed`), as an exponentially
 * weighted moving average of the time between posts (`RateEstimator`, alpha 0.7, on `SystemClock.elapsedRealtime`).
 * The rate is the package's: it carries over from an earlier process.
 */
@Implements(NotificationManager::class)
class SystemLikeNotificationManager : ShadowNotificationManager() {
    /** Per ID: the IDs that were showing when it was posted while not showing itself. */
    val showingWhenFirstPosted = mutableMapOf<Int, Set<Int>>()

    /** The notifications whose full-screen intent System UI launched, in order: added ones that carry one. */
    val fullScreenLaunches = mutableListOf<Notification>()

    /** Called with each notification posted (by the app or the system), as it is posted. */
    var onPost: ((id: Int, notification: Notification) -> Unit)? = null

    /** AOSP's rate limit on updates (above); off unless a test turns it on. */
    var limitUpdateRate = false

    /** The updates the rate limit dropped, in order: ID and notification. */
    val shed = mutableListOf<Pair<Int, Notification>>()

    private val enqueueRate = EnqueueRate()

    @Implementation
    override fun notify(id: Int, notification: Notification) = notify(null, id, notification)

    @Implementation
    override fun notify(tag: String?, id: Int, notification: Notification) {
        val old = getNotification(tag, id)
        val now = SystemClock.elapsedRealtime()
        if (limitUpdateRate && old != null && enqueueRate.rateAt(now) > MAX_ENQUEUE_RATE) {
            shed += id to notification
            return
        }
        enqueueRate.update(now)
        if (old == null) {
            showingWhenFirstPosted[id] = activeNotifications.map { it.id }.toSet()
            if (notification.fullScreenIntent != null) fullScreenLaunches += notification
        } else {
            notification.flags = notification.flags or (old.flags and Notification.FLAG_FOREGROUND_SERVICE)
        }
        onPost?.invoke(id, notification)
        super.notify(tag, id, notification)
    }

    @Implementation
    override fun cancel(id: Int) = cancel(null, id)

    @Implementation
    override fun cancel(tag: String?, id: Int) {
        val flags = getNotification(tag, id)?.flags ?: return
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        super.cancel(tag, id)
    }

    /** A cancel by the system (ActivityManager's `ServiceRecord.cancelNotification`): not refused. */
    fun systemCancel(id: Int) = super.cancel(null, id)

    companion object {
        /** AOSP's `mMaxPackageEnqueueRate` (`DEFAULT_MAX_NOTIFICATION_ENQUEUE_RATE`): posts per second. */
        const val MAX_ENQUEUE_RATE = 5.0

        fun of(manager: NotificationManager): SystemLikeNotificationManager = Shadow.extract(manager)
    }
}

/** AOSP's `RateEstimator`: an exponentially weighted moving average of the time between events, in seconds. */
private class EnqueueRate {
    private var lastEventMs: Long? = null

    /** AOSP's generous start for a package it knows nothing about. */
    private var interarrivalS = 1000.0

    fun update(nowMs: Long) {
        if (lastEventMs != null) interarrivalS = estimateAt(nowMs)
        lastEventMs = nowMs
    }

    /** The rate if there were an event now; 0 before the first. */
    fun rateAt(nowMs: Long): Double = if (lastEventMs == null) 0.0 else 1.0 / estimateAt(nowMs)

    private fun estimateAt(nowMs: Long): Double {
        val dt = maxOf((nowMs - checkNotNull(lastEventMs)) / 1000.0, MINIMUM_DT_S)
        return ALPHA * interarrivalS + (1 - ALPHA) * dt
    }

    private companion object {
        const val ALPHA = 0.7
        const val MINIMUM_DT_S = 0.0005
    }
}

/**
 * Robolectric's service with two rules of AOSP's ActiveServices that Robolectric leaves out
 * (D-084): when `startForeground` switches to another ID, and on `STOP_FOREGROUND_REMOVE`, the
 * system cancels the service's previous foreground notification
 * (`setServiceForegroundInnerLocked` → `cancelForegroundNotificationLocked`). The system's
 * posting and cancelling are asynchronous in AOSP but keep their order (both go through the
 * ActivityManager's handler); here they happen at once.
 *
 * [refuseStartForegroundWith]: the next `startForeground` with that ID throws
 * [ForegroundServiceStartNotAllowedException], as a re-checked start in the background can.
 */
@Implements(Service::class)
class SystemLikeService : ShadowService() {
    private var foregroundId = 0

    @Implementation
    override fun startForeground(id: Int, notification: Notification) {
        if (refusedIds.remove(id)) throw ForegroundServiceStartNotAllowedException("refused in the test: $id")
        if (foregroundId != 0 && foregroundId != id) notifications().systemCancel(foregroundId)
        foregroundId = id
        super.startForeground(id, notification)
    }

    @Implementation
    override fun stopForeground(removeNotification: Boolean) {
        if (removeNotification && foregroundId != 0) notifications().systemCancel(foregroundId)
        foregroundId = 0
        super.stopForeground(removeNotification)
    }

    private fun notifications() = SystemLikeNotificationManager.of(
        RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java),
    )

    companion object {
        private val refusedIds = mutableSetOf<Int>()

        fun refuseStartForegroundWith(id: Int) {
            refusedIds += id
        }

        fun reset() = refusedIds.clear()
    }
}
