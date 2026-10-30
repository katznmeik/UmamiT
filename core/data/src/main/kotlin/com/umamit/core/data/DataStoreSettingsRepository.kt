package com.umamit.core.data

import android.util.Log
import com.umamit.core.common.snap5
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.model.UserSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import javax.inject.Inject

/** [SettingsRepository] on DataStore (05 §6). */
class DataStoreSettingsRepository @Inject constructor(private val source: SettingsDataSource) : SettingsRepository {
    /** Never fails on an unreadable file: the defaults, logged, and the file read again later (D-068). */
    override val settings: Flow<UserSettings> = source.settings.readable { e ->
        Log.w(TAG, "Settings file unreadable; using the defaults until it reads again", e)
    }

    override val seedDataCreated: Flow<Boolean> = source.seedDataCreated

    override suspend fun update(transform: (UserSettings) -> UserSettings) =
        source.update { current -> transform(current).normalized() }

    override suspend fun markSeedDataCreated() = source.markSeedDataCreated()

    override val seedPhotosAttempted: Flow<Boolean> = source.seedPhotosAttempted

    override suspend fun markSeedPhotosAttempted() = source.markSeedPhotosAttempted()

    override val seedVesselsV2Added: Flow<Boolean> = source.seedVesselsV2Added

    override suspend fun markSeedVesselsV2Added() = source.markSeedVesselsV2Added()
}

private const val TAG = "UmamiTSettings"
private const val MAX_PRE_ALERT_SECONDS = 30
private const val MAX_LOW_STOCK_GRAMS = 500

/** The first wait before an unreadable settings file is read again; it doubles up to [MAX_SETTINGS_RETRY_MS]. */
internal const val FIRST_SETTINGS_RETRY_MS = 1_000L
internal const val MAX_SETTINGS_RETRY_MS = 60_000L
private const val MAX_DOUBLINGS = 6

/**
 * What an unreadable settings file reads as (D-066, D-068): the defaults, with the first-start
 * setup counted as done, so the app opens on Start and the timer stays reachable instead of
 * showing the setup on every launch. A genuine first start has no file and reads the stored
 * defaults without an error, so it still gets the setup.
 */
val UNREADABLE_SETTINGS: UserSettings = UserSettings(onboardingCompleted = true)

/**
 * These settings, total (D-068): an [IOException] of the settings file (DataStore corruption, a
 * disk error) is reported to [onFailure] and, before the first value, reads as
 * [UNREADABLE_SETTINGS], so no screen and no timer crashes on it; then the file is read again
 * after 1 s, 2 s, 4 s … up to a minute, so a file that reads again is followed. Any other error
 * still fails the flow.
 */
internal fun Flow<UserSettings>.readable(onFailure: (IOException) -> Unit): Flow<UserSettings> = flow {
    var read = false
    emitAll(
        onEach { read = true }.retryWhen { e, attempt ->
            if (e !is IOException) return@retryWhen false
            onFailure(e)
            if (!read) {
                read = true
                emit(UNREADABLE_SETTINGS)
            }
            delay(settingsRetryDelayMs(attempt))
            true
        },
    )
}

/** The wait after failed read number [attempt] + 1: 1 s, 2 s, 4 s, … up to [MAX_SETTINGS_RETRY_MS]. */
internal fun settingsRetryDelayMs(attempt: Long): Long {
    val doublings = attempt.coerceIn(0, MAX_DOUBLINGS.toLong()).toInt()
    return (FIRST_SETTINGS_RETRY_MS shl doublings).coerceAtMost(MAX_SETTINGS_RETRY_MS)
}

/** Keeps the pre-alert in 0, 5…30 (SET-10) and „Knapp ab" in 0…500 g in steps of 5 (SET-16). */
internal fun UserSettings.normalized(): UserSettings = copy(
    preAlertSeconds = snap5(preAlertSeconds).coerceIn(0, MAX_PRE_ALERT_SECONDS),
    lowStockGrams = snap5(lowStockGrams).coerceIn(0, MAX_LOW_STOCK_GRAMS).toDouble(),
)
