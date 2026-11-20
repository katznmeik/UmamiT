package com.umamit.feature.onboarding

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.catalog.CatalogRefreshRunner
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.timer.reliability.ReliabilityCheck
import com.umamit.core.timer.reliability.ReliabilityChecks
import com.umamit.core.timer.reliability.ReliabilityStatus
import com.umamit.core.timer.reliability.TimerReliability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * The first-start setup (SCR-01): reads the reliability checks (ONB-02), loads the catalog on *Katalog
 * jetzt laden* (ONB-03) and sets `onboardingCompleted` at the end (ONB-04). It creates no data and
 * sends nothing on its own (ONB-06, D-135).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    reliability: TimerReliability,
    private val settings: SettingsRepository,
    private val catalog: CatalogRefreshRunner,
) : ViewModel() {

    private val checks = ReliabilityChecks(reliability)

    /** The checks as last read; [refresh] reads them again on every resume. */
    val status: StateFlow<ReliabilityStatus> = checks.status

    private var finishing = false

    fun refresh() = checks.refresh()

    /** A row's action: the check's system pages, for the screen to start. */
    fun open(check: ReliabilityCheck): List<Intent> = checks.open(check)

    private val catalogStarted = MutableStateFlow(false)

    /** The catalog page (ONB-03): null until *Katalog jetzt laden*, then the refresh's progress and end. */
    val catalogLoad: StateFlow<CatalogRefreshState?> = combine(catalogStarted, catalog.state) { started, state ->
        state.takeIf { started }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * *Katalog jetzt laden*: the refresh starts in its own service and goes on after the setup
     * (CAT-07 AC1); only this tap starts it (AC7).
     */
    fun loadCatalog() {
        catalogStarted.value = true
        catalog.start()
    }

    private val failed = MutableStateFlow(false)

    /** The marker could not be written: the page says so, and *Weiter* tries again. */
    val saveFailed: StateFlow<Boolean> = failed.asStateFlow()

    /**
     * *Später* or *Weiter* on the last page: marks the setup done (ONB-04), then [onFinish]; a second
     * tap while it writes is ignored. A failed write (disk full) never throws into the app: the setup
     * stays open with a note, and the next tap tries again. A catalog load goes on.
     */
    fun finish(onFinish: () -> Unit) {
        if (finishing) return
        finishing = true
        viewModelScope.launch {
            try {
                settings.update { it.copy(onboardingCompleted = true) }
                failed.value = false
                onFinish()
            } catch (e: IOException) {
                Log.w(TAG, "Setup marker not saved", e)
                failed.value = true
                finishing = false
            }
        }
    }

    private companion object {
        const val TAG = "UmamiTSetup"
    }
}
