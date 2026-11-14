package com.umamit.feature.stats

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.today
import com.umamit.core.data.ObserveStats
import com.umamit.core.domain.stats.StatsSession
import com.umamit.core.model.MonthSpan
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsTab
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.YearMonth

/** SCR-18's view model (INS-07, INS-08, D-115 Q15, Q19) on fakes; the figures come through the real calculator. */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    private val clock = FakeClock()
    private val repository = FakeStatsRepository()
    private val settings = FakeSettingsRepository(UserSettings())

    /** Main, and the dispatcher the figures are computed on: no thread hops, so nothing outlives a test. */
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private val created = mutableListOf<StatsViewModel>()

    private fun viewModel() =
        StatsViewModel(ObserveStats(repository, clock, dispatcher), settings, clock, dispatcher).also {
            created +=
                it
        }

    private fun session(id: String, daysAgo: Long) = StatsSession(
        id = id,
        teaId = "t1",
        teaName = "Sencha Hisui",
        teaType = "Sencha",
        profileId = null,
        profileName = null,
        startedAtMs = clock.wall - Duration.ofDays(daysAgo).toMillis(),
        zoneId = clock.zone.id,
    )

    private fun sessions(vararg list: StatsSession) {
        repository.input.value = repository.input.value.copy(sessions = list.toList())
    }

    /** Collects [flow]'s owner like a screen would, until the test ends. */
    private fun TestScope.keepAlive(vm: StatsViewModel): Job = vm.state.onEach { }.launchIn(backgroundScope)

    private suspend fun StatsViewModel.settled(): StatsUiState = state.first { it != StatsUiState.Loading }

    @Test
    fun `nothing is shown until the first read, so the empty state never flashes`() = runTest {
        val vm = viewModel()
        assertThat(vm.state.value).isEqualTo(StatsUiState.Loading)
    }

    @Test
    fun `the first start shows 90 T and Ueberblick`() = runTest {
        sessions(session("a", daysAgo = 1))
        val vm = viewModel()
        keepAlive(vm)

        val state = vm.settled() as StatsUiState.Data
        assertThat(state.range).isEqualTo(StatsRange.DAYS_90)
        assertThat(state.tab).isEqualTo(StatsTab.OVERVIEW)
        assertThat(state.custom).isNull()
        assertThat(state.folded).isEmpty()
    }

    @Test
    fun `no finished session at all is the empty state, the first one turns it into data`() = runTest {
        val vm = viewModel()
        keepAlive(vm)
        assertThat(vm.settled()).isEqualTo(StatsUiState.Empty)

        sessions(session("a", daysAgo = 0))

        assertThat(vm.state.first { it != StatsUiState.Empty }).isInstanceOf(StatsUiState.Data::class.java)
    }

    @Test
    fun `a range with nothing in it is still data, not the empty state`() = runTest {
        sessions(session("old", daysAgo = 200))
        val vm = viewModel()
        keepAlive(vm)

        assertThat(vm.settled()).isInstanceOf(StatsUiState.Data::class.java)
        assertThat(vm.overview.first { it != null }!!.stats.sessionCount).isEqualTo(0)
    }

    @Test
    fun `a change of range and of tab is written and restored by the next view model`() = runTest {
        sessions(session("a", daysAgo = 1))
        val vm = viewModel()
        keepAlive(vm)
        vm.settled()

        vm.selectRange(StatsRange.MONTHS_12)
        vm.selectTab(StatsTab.CONSUMPTION)

        assertThat(settings.settings.first().statsRange).isEqualTo(StatsRange.MONTHS_12)
        assertThat(settings.settings.first().statsTab).isEqualTo(StatsTab.CONSUMPTION)
        assertThat(vm.state.value.choice()).isEqualTo(StatsRange.MONTHS_12 to StatsTab.CONSUMPTION)

        val restored = viewModel()
        keepAlive(restored)
        assertThat(restored.settled().choice()).isEqualTo(StatsRange.MONTHS_12 to StatsTab.CONSUMPTION)
    }

    private fun StatsUiState.choice() = (this as StatsUiState.Data).let { it.range to it.tab }

    @Test
    fun `the pickers offer the first session's month up to the current month`() = runTest {
        sessions(session("a", daysAgo = 100))
        val vm = viewModel()
        keepAlive(vm)

        val months = (vm.settled() as StatsUiState.Data).months!!

        assertThat(months.from).isEqualTo(YearMonth.from(clock.today().minusDays(100)))
        assertThat(months.to).isEqualTo(YearMonth.from(clock.today()))
    }

    @Test
    fun `Monate is written with its months, Von before Bis, and restored by the next view model`() = runTest {
        sessions(session("a", daysAgo = 200))
        val vm = viewModel()
        keepAlive(vm)
        vm.settled()

        // Bis before Von: the earlier month is Von.
        vm.selectCustom(from = YearMonth.of(2026, 9), to = YearMonth.of(2026, 3))

        val stored = settings.settings.first()
        assertThat(stored.statsRange).isEqualTo(StatsRange.CUSTOM)
        assertThat(stored.statsRangeFrom).isEqualTo(YearMonth.of(2026, 3))
        assertThat(stored.statsRangeTo).isEqualTo(YearMonth.of(2026, 9))
        val restored = viewModel()
        keepAlive(restored)
        val state = restored.settled() as StatsUiState.Data
        assertThat(state.range).isEqualTo(StatsRange.CUSTOM)
        assertThat(state.custom).isEqualTo(MonthSpan(YearMonth.of(2026, 3), YearMonth.of(2026, 9)))
    }

    @Test
    fun `a folded section is written and restored, an opened one is dropped from the set`() = runTest {
        sessions(session("a", daysAgo = 1))
        val vm = viewModel()
        keepAlive(vm)
        vm.settled()

        vm.setFolded("overview.sessions", true)
        vm.setFolded("ratings.top", true)
        vm.setFolded("overview.sessions", false)

        assertThat(settings.settings.first().statsFolded).containsExactly("ratings.top")
        val restored = viewModel()
        keepAlive(restored)
        assertThat((restored.settled() as StatsUiState.Data).folded).containsExactly("ratings.top")
    }

    @Test
    fun `a range change recomputes the figures`() = runTest {
        sessions(session("recent", daysAgo = 3), session("older", daysAgo = 60))
        val vm = viewModel()
        keepAlive(vm)
        vm.overview.onEach { }.launchIn(backgroundScope)
        assertThat(vm.overview.first { it != null }!!.stats.sessionCount).isEqualTo(2)

        vm.selectRange(StatsRange.DAYS_30)

        // The range arrives with the figures it belongs to, never ahead of them.
        val after = vm.overview.first { it?.stats?.sessionCount == 1 }
        assertThat(after!!.range).isEqualTo(StatsRange.DAYS_30)
    }

    @Test
    fun `the three tabs share one calculator read`() = runTest {
        sessions(session("a", daysAgo = 1))
        val vm = viewModel()
        keepAlive(vm)
        vm.overview.onEach { }.launchIn(backgroundScope)
        vm.ratings.onEach { }.launchIn(backgroundScope)
        vm.consumption.onEach { }.launchIn(backgroundScope)

        assertThat(vm.overview.first { it != null }).isNotNull()
        assertThat(vm.ratings.first { it != null }).isNotNull()
        assertThat(vm.consumption.first { it != null }).isNotNull()
        assertThat(repository.input.subscriptionCount.value).isEqualTo(1)
    }

    @Test
    fun `a failed write keeps the stored choice`() = runTest {
        sessions(session("a", daysAgo = 1))
        val vm = viewModel()
        keepAlive(vm)
        vm.settled()
        settings.failUpdates = true

        vm.selectRange(StatsRange.ALL)

        assertThat(vm.state.value.choice()).isEqualTo(StatsRange.DAYS_90 to StatsTab.OVERVIEW)
    }

    @Test
    fun `a day change is picked up on resume`() = runTest {
        // Tomorrow's session is not counted today (INS-01): no data yet.
        sessions(session("tomorrow", daysAgo = -1))
        val vm = viewModel()
        keepAlive(vm)
        assertThat(vm.settled()).isEqualTo(StatsUiState.Empty)

        clock.advanceBy(Duration.ofDays(1).toMillis())
        vm.onResume()

        assertThat(vm.state.first { it != StatsUiState.Empty }).isInstanceOf(StatsUiState.Data::class.java)
    }
}
