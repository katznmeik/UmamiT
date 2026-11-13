package com.umamit.core.testing

import com.umamit.core.data.StatsRepository
import com.umamit.core.domain.stats.StatsInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [StatsRepository]: tests set [input] (no sessions, teas or purchases at first). */
class FakeStatsRepository(initial: StatsInput = EMPTY) : StatsRepository {
    val input = MutableStateFlow(initial)

    override fun observeInput(): Flow<StatsInput> = input

    companion object {
        val EMPTY = StatsInput(
            sessions = emptyList(),
            teas = emptyList(),
            inventoryEvents = emptyList(),
            knownDescriptorKeys = emptySet(),
        )
    }
}
