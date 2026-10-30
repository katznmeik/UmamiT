package com.umamit.feature.settings

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.SettingsRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * The timer settings (SCR-25): the M1 settings on DataStore (SET-09…11, 14, 17, 25, 26, 27), the
 * reliability checks (SET-24) and *Alarm testen* through the production player (SET-31, D-059).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val tester: AlertTester,
    private val reliability: TimerReliability,
    private val preview: TonePreview,
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

    /** *Anhören*: [toneKey] once, at the stored *Lautstärke* (SET-27). */
    fun previewTone(toneKey: String) = preview.play(toneKey, volumePercent())

    /**
     * *Anhören* in the *Vorwarnton* picker (SET-26): the sound stored under [preAlertKey] once, at the stored
     * *Lautstärke* (SET-27).
     */
    fun previewPreAlertSound(preAlertKey: String) = preview.playPreAlert(preAlertKey, volumePercent())

    private fun volumePercent(): Int = (current.value ?: UserSettings()).alertVolumePercent

    fun stopPreview() = preview.stop()

    private val failed = MutableStateFlow(false)

    /** The last write failed (disk full, I/O error): the screen says so; the next write that works clears it. */
    val saveFailed: StateFlow<Boolean> = failed.asStateFlow()

    /**
     * A row's change, written at once (SET-09…11, 14, 17, 25, 26). A failed write never throws into
     * the app (a crash would take a running timer's process down): it is logged and shown.
     */
    fun change(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            failed.value = try {
                settings.update(transform)
                false
            } catch (e: IOException) {
                Log.w(TAG, "Settings not saved", e)
                true
            }
        }
    }

    override fun onCleared() {
        stopTest()
        stopPreview()
    }

    private companion object {
        const val TAG = "UmamiTSettings"
    }
}
