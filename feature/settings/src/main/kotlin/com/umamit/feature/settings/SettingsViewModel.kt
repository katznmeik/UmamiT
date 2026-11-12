package com.umamit.feature.settings

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.ThemeMode
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.alert.AlertTester
import com.umamit.core.timer.alert.TonePreview
import com.umamit.core.timer.reliability.ReliabilityCheck
import com.umamit.core.timer.reliability.ReliabilityStatus
import com.umamit.core.timer.reliability.TimerReliability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject

/**
 * The timer settings (SCR-25): the M1 settings on DataStore (SET-09…11, 14, 17, 25, 26, 27, 32), the
 * reliability checks (SET-24) and *Alarm testen* through the production player (SET-31, D-059).
 */
@HiltViewModel
@Suppress("TooManyFunctions") // one function per row action of SCR-25, the checks, the tests and the *Design* hand-over
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val tester: AlertTester,
    private val reliability: TimerReliability,
    private val preview: TonePreview,
    private val nightMode: AppNightMode,
) : ViewModel() {

    /** The stored settings; null until the file is read. */
    val current: StateFlow<UserSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val checks = MutableStateFlow(reliability.status())

    /** The checks as last read; [refresh] reads them again on every resume. */
    val status: StateFlow<ReliabilityStatus> = checks.asStateFlow()

    fun refresh() {
        checks.value = reliability.status()
    }

    /** A row's *Beheben*: the check's pages, for the screen to start. */
    fun open(check: ReliabilityCheck): List<Intent> = reliability.settingsIntents(check)

    /** *Alarm testen* and the tone's *Anhören*: the tick, then the alert, with the stored settings. */
    fun testAlert() = tester.testAlert()

    /** The screen closes: a running test stops; a real alert plays on (D-059). */
    fun stopTest() = tester.stopTestAlert()

    /**
     * *Anhören*: [toneKey] once, at the stored *Lautstärke* (SET-27) and, under Do Not Disturb, as the stored
     * *Bei „Nicht stören" klingeln* says (SET-32, D-099).
     */
    fun previewTone(toneKey: String) = stored().let { preview.play(toneKey, it.alertVolumePercent, it.ringUnderDnd) }

    /**
     * *Anhören* in the *Vorwarnton* picker (SET-26): the sound stored under [preAlertKey] once, at the stored
     * *Lautstärke* (SET-27) and *Bei „Nicht stören" klingeln* (SET-32).
     */
    fun previewPreAlertSound(preAlertKey: String) =
        stored().let { preview.playPreAlert(preAlertKey, it.alertVolumePercent, it.ringUnderDnd) }

    private fun stored(): UserSettings = current.value ?: UserSettings()

    fun stopPreview() = preview.stop()

    private val failed = MutableStateFlow(false)

    /** The last write failed (disk full, I/O error): the screen says so; the next write that works clears it. */
    val saveFailed: StateFlow<Boolean> = failed.asStateFlow()

    /**
     * A row's change, written at once (SET-09…11, 14, 17, 25, 26). A failed write never throws into
     * the app (a crash would take a running timer's process down): it is logged and shown.
     */
    fun change(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { failed.value = !settings.tryUpdate(transform) }
    }

    /** *Design* writes and the hand-over run one after another, so a hand-over reads the pick before it. */
    private val themeLock = Mutex()

    /**
     * SET-05 *Design* (D-114): a pick is stored at once, and the app's theme follows the stored value at once. It is
     * not handed to the system here: [handOverTheme] does that when the dialog closes (PO, motion review M21), so
     * the activity is re-created once, after the dialog is gone.
     */
    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            failed.value = !themeLock.withLock { settings.tryUpdate { it.copy(theme = mode) } }
        }
    }

    /**
     * Hands the stored *Design* to the system ([AppNightMode]) when it differs from the one last handed over, so the
     * next start's starting window shows it too: when the *Design* dialog closes (*Fertig*, back, a tap outside),
     * and whenever the settings screen shows with no alert playing (a pick whose dialog an alert closed, a theme
     * a backup restore brought, m3). After any pick still being written. [alerting] is read on the main thread
     * right before the hand-over, in the same frame: the activity is never re-created under an alert (rule 8). An
     * unreadable settings file hands nothing over.
     */
    fun handOverTheme(alerting: () -> Boolean) {
        viewModelScope.launch {
            val stored = themeLock.withLock { settings.tryRead()?.theme } ?: return@launch
            if (nightMode.handedOver() != stored && !alerting()) nightMode.apply(stored)
        }
    }

    override fun onCleared() {
        stopTest()
        stopPreview()
    }
}

/** [SettingsRepository.update] that never throws into the app: a failed write is logged and reads false. */
private suspend fun SettingsRepository.tryUpdate(transform: (UserSettings) -> UserSettings): Boolean = try {
    update(transform)
    true
} catch (e: IOException) {
    Log.w(TAG, "Settings not saved", e)
    false
}

/** The stored settings, or null when the file cannot be read (logged). */
private suspend fun SettingsRepository.tryRead(): UserSettings? = try {
    settings.first()
} catch (e: IOException) {
    Log.w(TAG, "Settings not read", e)
    null
}

private const val TAG = "UmamiTSettings"
