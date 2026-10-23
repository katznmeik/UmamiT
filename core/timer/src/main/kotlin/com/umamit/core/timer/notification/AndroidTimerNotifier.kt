package com.umamit.core.timer.notification

import android.content.Context
import com.umamit.core.timer.TimerNotifications
import com.umamit.core.timer.TimerStateNotifications
import com.umamit.core.timer.controller.TimerNotifierPort
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.service.BrewTimerServicePort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The controller's notifier (04 §8.2, §8.3; M1-11, D-064):
 *
 * - `UpdateNotification` goes to the [BrewTimerServicePort], the one place that shows the
 *   timer's notifications 1, 2 and the quiet one. While the service runs, the notification
 *   changes only through the service, so the alert is never posted next to the Live Update
 *   (D-044, D-057).
 * - `NotifySessionSaved` posts „Sitzung gespeichert" (ID 3, channel `sessions`, LOW) with the
 *   tea of the session as the timer last showed it (TMR-14: the midnight timeout and
 *   *Beenden & neu starten*).
 */
@Singleton
class AndroidTimerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: BrewTimerServicePort,
    private val notifications: TimerStateNotifications,
) : TimerNotifierPort {
    /** The session the timer showed last and its tea, for „Sitzung gespeichert". */
    @Volatile
    private var lastSession: Pair<String, String?>? = null

    override fun update(state: TimerUiState.Active, fullScreen: Boolean) {
        state.sessionId?.let { lastSession = it to state.teaName }
        service.show(state, fullScreen)
    }

    override fun notifySessionSaved(sessionId: String) {
        val tea = lastSession?.takeIf { it.first == sessionId }?.second
        TimerNotifications.ensureChannels(context)
        TimerNotifications.post(context, TimerNotifications.ID_SESSION_SAVED, notifications.sessionSaved(tea))
    }
}
