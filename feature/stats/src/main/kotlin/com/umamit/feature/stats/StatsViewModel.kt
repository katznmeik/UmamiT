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
import com.umamit.core.model.MonthSpan
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
import java.time.YearMonth
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
    data class Data(
        val range: StatsRange,
        val tab: StatsTab,
        /** The custom months (*Monate*, R7), kept while [range] is [StatsRange.CUSTOM]; null until chosen. */
        val custom: MonthSpan? = null,
        /** The months the pickers offer: the first session's month … the current one (D-115 A1); null without. */
        val months: MonthSpan? = null,
        /** The keys of the folded sections (`statsFolded`, INS-08, R8). */
        val folded: Set<String> = emptySet(),
    ) : StatsUiState
}

/** A tab's [stats] with the [range] they were computed for: the page takes its range-bound state from here (INS-07). */
data class TabFigures<T>(val range: StatsRange, val stats: T, val custom: MonthSpan? = null) {
    /** What a chart's selection and a list's „Alle anzeigen" reset on: the range and, for *Monate*, its months. */
    val rangeKey: Pair<StatsRange, MonthSpan?> get() = range to custom
}

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

    private data class Choice(
        val range: StatsRange,
        val tab: StatsTab,
        val custom: MonthSpan?,
        val folded: Set<String>,
    )

    private val choice: Flow<Choice> = settings.settings
        .map {
            Choice(
                it.statsRange,
                it.statsTab,
                MonthSpan.of(it.statsRangeFrom?.toString(), it.statsRangeTo?.toString()),
                it.statsFolded,
            )
        }
        .distinctUntilChanged()

    /** The range with the custom months (*Monate*, D-115 Amendment 1); the months only count under CUSTOM. */
    private val range: Flow<Pair<StatsRange, MonthSpan?>> = settings.settings
        .map {
            it.statsRange to
                if (it.statsRange ==
                    StatsRange.CUSTOM
                ) {
                    MonthSpan.of(it.statsRangeFrom?.toString(), it.statsRangeTo?.toString())
                } else {
                    null
                }
        }
        .distinctUntilChanged()

    private val calculator: SharedFlow<StatsCalculator> = dayChanges
        .flatMapLatest { observeStats.calculator() }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** The pickers' months: from the first finished session's month to the current one; null without a session. */
    private val months: Flow<MonthSpan?> = calculator
        .map { calculator ->
            val window = calculator.window(StatsRange.ALL)
            window.periods.firstOrNull()?.let { MonthSpan(it.month, YearMonth.from(window.to)) }
        }
        .distinctUntilChanged()

    val state: StateFlow<StatsUiState> = combine(choice, months) { choice, months ->
        if (months != null) {
            StatsUiState.Data(choice.range, choice.tab, choice.custom, months, choice.folded)
        } else {
            StatsUiState.Empty
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StatsUiState.Loading)

    /** *Überblick*'s figures with their range; null until the first read. A range change replaces them in place. */
    val overview: StateFlow<TabFigures<OverviewStats>?> =
        figures { calculator, range, custom -> calculator.overview(range, custom) }

    /** *Bewertungen*'s figures with their range; null until the first read. */
    val ratings: StateFlow<TabFigures<RatingsStats>?> =
        figures { calculator, range, custom -> calculator.ratings(range, custom) }

    /** *Verbrauch*'s figures with their range; null until the first read. */
    val consumption: StateFlow<TabFigures<ConsumptionStats>?> =
        figures { calculator, range, custom -> calculator.consumption(range, custom) }

    private fun <T : Any> figures(of: (StatsCalculator, StatsRange, MonthSpan?) -> T): StateFlow<TabFigures<T>?> =
        combine(calculator, range) { calculator, (range, custom) ->
            TabFigures(range, of(calculator, range, custom), custom)
        }
            .flowOn(computeDispatcher)
            .map<TabFigures<T>, TabFigures<T>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The selector's choice, remembered across app starts (INS-07). */
    fun selectRange(range: StatsRange) = write { it.copy(statsRange = range) }

    /** *Monate*: the months of the custom range, remembered like the others (INS-07, R7); *Von* never after *Bis*. */
    fun selectCustom(from: YearMonth, to: YearMonth) = write {
        it.copy(statsRange = StatsRange.CUSTOM, statsRangeFrom = minOf(from, to), statsRangeTo = maxOf(from, to))
    }

    /** Folds or opens the section [key] (INS-08, R8), remembered across app starts (`statsFolded`). */
    fun setFolded(key: String, folded: Boolean) = write {
        it.copy(statsFolded = if (folded) it.statsFolded + key else it.statsFolded - key)
    }

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
