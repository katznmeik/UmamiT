package com.umamit.feature.settings.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.diagnostics.DiagnosticsLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The state of *Diagnose* (SCR-27): the log read again after every write
 * while the screen is shown, and the header for *Kopieren*.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(log: DiagnosticsLog, @ApplicationContext context: Context) :
    ViewModel() {
    private val header = headerOf(context)

    val state: StateFlow<DiagnosticsUiState> = log.revision
        .mapLatest { DiagnosticsUiState(entries = log.entries(), header = header) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DiagnosticsUiState(header = header))

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** „UmamiT 0.1.0-rc.1 (10001) · Android 16 (API 36) · Samsung SM-S931B". */
        fun headerOf(context: Context): String {
            val version = try {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                "${info.versionName} (${info.longVersionCode})"
            } catch (_: PackageManager.NameNotFoundException) {
                "?"
            }
            val device = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
            return "UmamiT $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · $device"
        }
    }
}
