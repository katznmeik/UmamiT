package com.umamit.feature.home

import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.LowStockRepository
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SeedData
import com.umamit.core.model.LowStockCard
import com.umamit.core.model.LowStockTea
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.testing.lowStockCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Start's writes that fail on the disk (rule 8): the app, and a running timer's service in its process, lives
 * on. On Robolectric, so the failure is really logged (`android.util.Log`) rather than stubbed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeViewModelWriteFailureTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val clock = object : MonotonicClock {
        override fun elapsedMs() = 5_000L

        override fun bootCount() = 1
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an Ausblenden that cannot be stored never crashes the app or its timer (INV-10, rule 8)`() = runTest {
        SeedData.teas.forEach { collection.teas.saveTea(it) }
        val card = lowStockCard(grams = 14.0)
        val failing = object : LowStockRepository {
            override fun observeCard(): Flow<LowStockCard?> = flowOf(card)

            override suspend fun hide(card: LowStockCard) = throw IOException("synthetic: disk full")

            override suspend fun reminderTeas(): List<LowStockTea> = emptyList()
        }
        val vm = HomeViewModel(
            collection.teas,
            sessions.sessions,
            FakeBrewStarter(),
            clock,
            PhotoFiles(folder.newFolder("photos")),
            FakeTimerReliability(),
            failing,
        )
        assertThat(vm.state.value.lowStock).isEqualTo(card)

        vm.hideLowStock()

        // Not hidden: the card stays for another try.
        assertThat(vm.state.value.loading).isFalse()
        assertThat(vm.state.value.lowStock).isEqualTo(card)
    }
}
