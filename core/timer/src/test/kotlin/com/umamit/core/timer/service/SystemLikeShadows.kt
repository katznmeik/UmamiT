package com.umamit.core.timer.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
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
 */
@Implements(NotificationManager::class)
class SystemLikeNotificationManager : ShadowNotificationManager() {
    /** Per ID: the IDs that were showing when it was posted while not showing itself. */
    val showingWhenFirstPosted = mutableMapOf<Int, Set<Int>>()

    /** The notifications whose full-screen intent System UI launched, in order: added ones that carry one. */
    val fullScreenLaunches = mutableListOf<Notification>()

    /** Called with each notification posted (by the app or the system), as it is posted. */
    var onPost: ((id: Int, notification: Notification) -> Unit)? = null

    @Implementation
    override fun notify(id: Int, notification: Notification) = notify(null, id, notification)

    @Implementation
    override fun notify(tag: String?, id: Int, notification: Notification) {
        val old = getNotification(tag, id)
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
        fun of(manager: NotificationManager): SystemLikeNotificationManager = Shadow.extract(manager)
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
