package com.umamit.core.timer

import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The default settings (05 §6), in memory: the test's step sets its own pre-alert. */
class DefaultSettings : SettingsRepository {
    override val settings: Flow<UserSettings> = MutableStateFlow(UserSettings())
    override val seedDataCreated: Flow<Boolean> = MutableStateFlow(true)
    override val seedPhotosAttempted: Flow<Boolean> = MutableStateFlow(true)
    override val seedVesselsV2Added: Flow<Boolean> = MutableStateFlow(true)

    override suspend fun update(transform: (UserSettings) -> UserSettings) = Unit

    override suspend fun markSeedDataCreated() = Unit

    override suspend fun markSeedPhotosAttempted() = Unit

    override suspend fun markSeedVesselsV2Added() = Unit
}
