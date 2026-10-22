package com.umamit.core.data

import com.umamit.core.common.snap5
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** [SettingsRepository] on DataStore (05 §6). */
class DataStoreSettingsRepository @Inject constructor(private val source: SettingsDataSource) : SettingsRepository {
    override val settings: Flow<UserSettings> = source.settings

    override val seedDataCreated: Flow<Boolean> = source.seedDataCreated

    override suspend fun update(transform: (UserSettings) -> UserSettings) =
        source.update { current -> transform(current).normalized() }

    override suspend fun markSeedDataCreated() = source.markSeedDataCreated()

    override val seedPhotosAttempted: Flow<Boolean> = source.seedPhotosAttempted

    override suspend fun markSeedPhotosAttempted() = source.markSeedPhotosAttempted()
}

private const val MAX_PRE_ALERT_SECONDS = 30

/** Keeps the pre-alert in 0, 5…30 (SET-10). */
internal fun UserSettings.normalized(): UserSettings =
    copy(preAlertSeconds = snap5(preAlertSeconds).coerceIn(0, MAX_PRE_ALERT_SECONDS))
