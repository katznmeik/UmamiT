package com.umamit.feature.stats

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.ObserveStats
import com.umamit.core.domain.stats.StatsSession
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsTab
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

/** SCR-18's view model (INS-07, INS-08, D-115 Q15, Q19) on fakes; the figures come through the real calculator. */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    private val clock = FakeClock()
    private val repository = FakeStatsRepository()
    private val settings = FakeSettingsRepository(UserSettings())

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = StatsViewModel(ObserveStats(repository, clock), settings, clock)

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

        assertThat(vm.settled()).isEqualTo(StatsUiState.Data(StatsRange.DAYS_90, StatsTab.OVERVIEW))
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
        assertThat(vm.overview.first { it != null }!!.sessionCount).isEqualTo(0)
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
        assertThat(vm.state.value).isEqualTo(StatsUiState.Data(StatsRange.MONTHS_12, StatsTab.CONSUMPTION))

        val restored = viewModel()
        keepAlive(restored)
        assertThat(restored.settled()).isEqualTo(StatsUiState.Data(StatsRange.MONTHS_12, StatsTab.CONSUMPTION))
    }

    @Test
    fun `a range change recomputes the figures`() = runTest {
        sessions(session("recent", daysAgo = 3), session("older", daysAgo = 60))
        val vm = viewModel()
        keepAlive(vm)
        vm.overview.onEach { }.launchIn(backgroundScope)
        assertThat(vm.overview.first { it != null }!!.sessionCount).isEqualTo(2)

        vm.selectRange(StatsRange.DAYS_30)

        assertThat(vm.overview.first { it?.sessionCount == 1 }).isNotNull()
    }

    @Test
    fun `a failed write keeps the stored choice`() = runTest {
        sessions(session("a", daysAgo = 1))
        val vm = viewModel()
        keepAlive(vm)
        vm.settled()
        settings.failUpdates = true

        vm.selectRange(StatsRange.ALL)

        assertThat(vm.state.value).isEqualTo(StatsUiState.Data(StatsRange.DAYS_90, StatsTab.OVERVIEW))
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
