package com.umamit.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.WallClock
import com.umamit.core.common.today
import com.umamit.core.data.ObserveStats
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.StatsDispatcher
import com.umamit.core.domain.stats.ConsumptionStats
import com.umamit.core.domain.stats.OverviewStats
import com.umamit.core.domain.stats.RatingsStats
import com.umamit.core.domain.stats.StatsCalculator
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsTab
import com.umamit.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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

/** A tab's [stats] with the [range] they were computed for: the page takes its range-bound state from here (INS-07). */
data class TabFigures<T>(val range: StatsRange, val stats: T)

/**
 * Statistik (SCR-18; INS-07, INS-08): the remembered range and tab (`statsRange`, `statsTab`; 90 T and *Überblick*
 * on the first start, D-115 Q19) and the figures of each tab for the range.
 *
 * One calculator flow ([ObserveStats.calculator]: one read of the data per change) is shared by the screen state and
 * the three tabs; each tab combines it with the range on [computeDispatcher] and runs only while it is collected. The
 * calculator reads today when it is built, so [onResume] builds a new one once the day has changed (the screen is back
 * after midnight).
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel @Inject constructor(
    private val observeStats: ObserveStats,
    private val settings: SettingsRepository,
    private val clock: WallClock,
    @StatsDispatcher private val computeDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private var day: LocalDate = clock.today()

    /** Counts the re-reads for a new day; the shared calculator flow restarts on a change. */
    private val dayChanges = MutableStateFlow(0)

    private val choice: Flow<Pair<StatsRange, StatsTab>> = settings.settings
        .map { it.statsRange to it.statsTab }
        .distinctUntilChanged()

    private val range: Flow<StatsRange> = choice.map { it.first }.distinctUntilChanged()

    private val calculator: SharedFlow<StatsCalculator> = dayChanges
        .flatMapLatest { observeStats.calculator() }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    private val hasSessions: Flow<Boolean> = calculator.map { it.hasSessions }.distinctUntilChanged()

    val state: StateFlow<StatsUiState> = combine(choice, hasSessions) { (range, tab), any ->
        if (any) StatsUiState.Data(range, tab) else StatsUiState.Empty
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StatsUiState.Loading)

    /** *Überblick*'s figures with their range; null until the first read. A range change replaces them in place. */
    val overview: StateFlow<TabFigures<OverviewStats>?> = figures { calculator, range -> calculator.overview(range) }

    /** *Bewertungen*'s figures with their range; null until the first read. */
    val ratings: StateFlow<TabFigures<RatingsStats>?> = figures { calculator, range -> calculator.ratings(range) }

    /** *Verbrauch*'s figures with their range; null until the first read. */
    val consumption: StateFlow<TabFigures<ConsumptionStats>?> =
        figures { calculator, range -> calculator.consumption(range) }

    private fun <T : Any> figures(of: (StatsCalculator, StatsRange) -> T): StateFlow<TabFigures<T>?> =
        combine(calculator, range) { calculator, range -> TabFigures(range, of(calculator, range)) }
            .flowOn(computeDispatcher)
            .map<TabFigures<T>, TabFigures<T>?> { it }
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
