package com.umamit.core.testing

import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException

/** In-memory [SettingsRepository] that starts with the spec defaults (05 §6) and no seed markers. */
class FakeSettingsRepository(
    initial: UserSettings = UserSettings(),
    seeded: Boolean = false,
    seedPhotosAttempted: Boolean = false,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    private val seedMarker = MutableStateFlow(seeded)
    private val seedPhotoMarker = MutableStateFlow(seedPhotosAttempted)

    override val settings: Flow<UserSettings> = state

    override val seedDataCreated: Flow<Boolean> = seedMarker

    /** While set, every [update] fails like a full disk (DataStore's `IOException`) and changes nothing. */
    var failUpdates: Boolean = false

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        if (failUpdates) throw IOException("synthetic write failure")
        state.update(transform)
    }

    override suspend fun markSeedDataCreated() {
        seedMarker.value = true
    }

    override val seedPhotosAttempted: Flow<Boolean> = seedPhotoMarker

    override suspend fun markSeedPhotosAttempted() {
        seedPhotoMarker.value = true
    }
}
