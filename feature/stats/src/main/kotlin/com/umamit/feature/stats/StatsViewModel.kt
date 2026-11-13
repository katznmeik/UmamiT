package com.umamit.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.WallClock
import com.umamit.core.common.today
import com.umamit.core.data.ObserveStats
import com.umamit.core.data.SettingsRepository
import com.umamit.core.domain.stats.ConsumptionStats
import com.umamit.core.domain.stats.OverviewStats
import com.umamit.core.domain.stats.RatingsStats
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsTab
import com.umamit.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

/**
 * What the Statistik screen (SCR-18) shows.
 *
 * Nothing is drawn while [Loading], so the empty state never flashes before the first read (INS-08).
 */
sealed interface StatsUiState {
    /** The settings and the first read of the sessions are not in yet. */
    data object Loading : StatsUiState

    /** No finished session at all: A9 and no selector or tabs (INS-08, D-115 Q15). */
    data object Empty : StatsUiState

    /** At least one finished session: the selector, the tabs and the [tab]'s content for [range] (INS-07). */
    data class Data(val range: StatsRange, val tab: StatsTab) : StatsUiState
}

/**
 * Statistik (SCR-18; INS-07, INS-08): the remembered range and tab (`statsRange`, `statsTab`; 90 T and *Überblick*
 * on the first start, D-115 Q19) and the figures of each tab for the range through [ObserveStats].
 *
 * The three tab flows run only while their tab is collected. [ObserveStats] reads today at each emission, so
 * [onResume] starts them again once the day has changed (the screen is back after midnight).
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel @Inject constructor(
    private val observeStats: ObserveStats,
    private val settings: SettingsRepository,
    private val clock: WallClock,
) : ViewModel() {
    private var day: LocalDate = clock.today()

    /** Counts the re-reads for a new day; every figure flow restarts on a change. */
    private val dayChanges = MutableStateFlow(0)

    private val choice: Flow<Pair<StatsRange, StatsTab>> = settings.settings
        .map { it.statsRange to it.statsTab }
        .distinctUntilChanged()

    private val hasSessions: Flow<Boolean> = dayChanges
        .flatMapLatest { observeStats.calculator() }
        .map { it.hasSessions }
        .distinctUntilChanged()

    val state: StateFlow<StatsUiState> = combine(choice, hasSessions) { (range, tab), any ->
        if (any) StatsUiState.Data(range, tab) else StatsUiState.Empty
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StatsUiState.Loading)

    /** *Überblick*'s figures for the range; null until the first read. A range change replaces them in place. */
    val overview: StateFlow<OverviewStats?> = figures { observeStats.overview(it) }

    /** *Bewertungen*'s figures for the range; null until the first read. */
    val ratings: StateFlow<RatingsStats?> = figures { observeStats.ratings(it) }

    /** *Verbrauch*'s figures for the range; null until the first read. */
    val consumption: StateFlow<ConsumptionStats?> = figures { observeStats.consumption(it) }

    private fun <T : Any> figures(of: (StatsRange) -> Flow<T>): StateFlow<T?> = combine(
        choice.map { it.first }.distinctUntilChanged(),
        dayChanges,
    ) { range, _ -> range }
        .flatMapLatest { of(it) }
        .map<T, T?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The selector's choice, remembered across app starts (INS-07). */
    fun selectRange(range: StatsRange) = write { it.copy(statsRange = range) }

    /** The tab shown, remembered across app starts (INS-07). */
    fun selectTab(tab: StatsTab) = write { it.copy(statsTab = tab) }

    /** The screen is back in front: a new day makes „heute" and so every window move (INS-07). */
    fun onResume() {
        val now = clock.today()
        if (now != day) {
            day = now
            dayChanges.update { it + 1 }
        }
    }

    private fun write(change: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            try {
                settings.update(change)
            } catch (_: IOException) {
                // The choice is a convenience: the screen keeps showing the one stored before.
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
