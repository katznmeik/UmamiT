package com.umamit.core.timer

import android.util.Log
import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.controller.TimerNotifierPort
import com.umamit.core.timer.controller.TimerUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The port whose Android adapter comes with a later ticket (D-057): the notifier (#47). In the test
 * APK they only write to logcat, so the alert notification the test sees is the service's
 * own foreground notification. A notifier must not post notification 2 itself while the
 * service shows notification 1, or Android 16 would group the two and silence the alert
 * (D-044). A ticket that brings its adapter uses it in [TimerTestApplication].
 */
class LogcatTimerPorts : TimerNotifierPort {
    override fun update(state: TimerUiState.Active, fullScreen: Boolean) =
        log("notification ${state.phase} $fullScreen")

    override fun notifySessionSaved(sessionId: String) = log("session saved $sessionId")

    override fun notifyTimerResumed(state: TimerUiState.Active) = log("timer resumed ${state.phase}")

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "UmamiTTimerTest"
    }
}

/** The default settings (05 §6), in memory: the test's step sets its own pre-alert. */
class DefaultSettings : SettingsRepository {
    override val settings: Flow<UserSettings> = MutableStateFlow(UserSettings())
    override val seedDataCreated: Flow<Boolean> = MutableStateFlow(true)
    override val seedPhotosAttempted: Flow<Boolean> = MutableStateFlow(true)

    override suspend fun update(transform: (UserSettings) -> UserSettings) = Unit

    override suspend fun markSeedDataCreated() = Unit

    override suspend fun markSeedPhotosAttempted() = Unit
}
