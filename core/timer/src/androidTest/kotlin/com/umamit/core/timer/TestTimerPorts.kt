package com.umamit.core.timer

import android.util.Log
import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.controller.SavedSheetPort
import com.umamit.core.timer.controller.StepAlert
import com.umamit.core.timer.controller.TimerAlertPort
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import com.umamit.core.timer.controller.TimerNotifierPort
import com.umamit.core.timer.controller.TimerUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The ports whose Android adapters come with later tickets (D-057): the diagnostics log
 * (#43), the alert player (#44), the notifier (#47) and the saved sheet (M1-16). In the test
 * APK they only write to logcat, so the alert notification the test sees is the service's
 * own foreground notification. A notifier must not post notification 2 itself while the
 * service shows notification 1, or Android 16 would group the two and silence the alert
 * (D-044). A ticket that brings its adapter uses it in [TimerTestApplication].
 */
class LogcatTimerPorts :
    TimerNotifierPort,
    TimerAlertPort,
    TimerDiagnosticsPort,
    SavedSheetPort {
    override fun update(state: TimerUiState.Active, fullScreen: Boolean) =
        log("notification ${state.phase} $fullScreen")

    override fun notifySessionSaved(sessionId: String) = log("session saved $sessionId")

    override fun playPreAlert() = log("pre-alert tick")

    override fun startAlert(alert: StepAlert) = log("alert $alert")

    override fun stopAlert() = log("alert stopped")

    override fun record(entry: TimerDiagnostic) = log("diagnostics $entry")

    override fun showSavedSheet(sessionId: String) = log("saved sheet $sessionId")

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
