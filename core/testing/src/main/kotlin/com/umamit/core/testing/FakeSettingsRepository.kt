package com.umamit.core.testing

import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [SettingsRepository] that starts with the spec defaults (05 §6) and no seed marker. */
class FakeSettingsRepository(initial: UserSettings = UserSettings(), seeded: Boolean = false) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    private val seedMarker = MutableStateFlow(seeded)

    override val settings: Flow<UserSettings> = state

    override val seedDataCreated: Flow<Boolean> = seedMarker

    override suspend fun update(transform: (UserSettings) -> UserSettings) = state.update(transform)

    override suspend fun markSeedDataCreated() {
        seedMarker.value = true
    }
}
