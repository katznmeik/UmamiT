package com.umamit.core.timer

import android.util.Log
import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.controller.SavedSheetPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The ports whose Android adapters come with later tickets (D-057): the saved sheet (M1-16).
 * In the test APK it only writes to logcat. A ticket that brings its adapter uses it in
 * [TimerTestApplication].
 */
class LogcatTimerPorts : SavedSheetPort {
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
