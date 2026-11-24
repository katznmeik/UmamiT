package com.umamit.core.data

import com.umamit.core.datastore.SettingsDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject

/**
 * The versionCode in which the „Vollbild-Alarm wieder erlauben" sheet was shown or answered (TMR-31, SCR-34, D-136
 * item 13, 05 §6 `fsiPromptedVersionCode`), device-bound (05 §8). Apart from [SettingsRepository], which this
 * one sheet does not widen.
 */
interface FsiPromptRepository {
    /** The stored versionCode; 0 = never prompted. */
    val promptedVersionCode: Flow<Int>

    /** Records that the sheet was shown or answered in [versionCode]. */
    suspend fun markPrompted(versionCode: Int)
}

/** [FsiPromptRepository] on DataStore. An unreadable file reads as no value: the sheet then simply does not show. */
class DataStoreFsiPromptRepository @Inject constructor(private val source: SettingsDataSource) : FsiPromptRepository {
    override val promptedVersionCode: Flow<Int> = source.fsiPromptedVersionCode.catch {
        /* unreadable: no value, no sheet */
    }

    override suspend fun markPrompted(versionCode: Int) = source.markFsiPromptedVersionCode(versionCode)
}
