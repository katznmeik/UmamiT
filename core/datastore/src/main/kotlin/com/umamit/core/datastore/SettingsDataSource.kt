package com.umamit.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.umamit.core.model.AlertLength
import com.umamit.core.model.ThemeMode
import com.umamit.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * The stored form of the settings (05 §6, M1 subset) plus the seed markers. Every field
 * has its spec default, so a fresh install, a missing key or an older file read the
 * defaults. Enums are stored by name and unknown names fall back to the default.
 */
@Serializable
data class StoredSettings(
    val schemaVersion: Int = 1,
    val onboardingCompleted: Boolean = false,
    val theme: String = ThemeMode.SYSTEM.name,
    val dynamicColor: Boolean = false,
    val alertSound: String = UserSettings.DEFAULT_ALERT_SOUND,
    val preAlertSeconds: Int = 10,
    val vibration: Boolean = true,
    val alertLength: String = AlertLength.NORMAL.name,
    val deductStock: Boolean = true,
    val lowStockGrams: Double = 10.0,
    val keepScreenOn: Boolean = true,
    val dimWhileSteeping: Boolean = true,
    val dismissedHints: Set<String> = emptySet(),
    /** Set once the first-start seed data exists (M1-07); never reset. */
    val seedDataCreated: Boolean = false,
    /** Set once the one-time seed photo download has ended in any way (ONB-06); never reset. */
    val seedPhotosAttempted: Boolean = false,
)

/** JSON serializer for [StoredSettings]; an unreadable file reads as the defaults. */
object SettingsSerializer : Serializer<StoredSettings> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue = StoredSettings()

    override suspend fun readFrom(input: InputStream): StoredSettings = try {
        json.decodeFromString(StoredSettings.serializer(), input.readBytes().decodeToString())
    } catch (_: SerializationException) {
        defaultValue
    } catch (_: IllegalArgumentException) {
        defaultValue
    }

    override suspend fun writeTo(t: StoredSettings, output: OutputStream) {
        output.write(json.encodeToString(StoredSettings.serializer(), t).encodeToByteArray())
    }
}

/** The settings of 05 §6 (M1 subset) and the seed marker, backed by DataStore. */
class SettingsDataSource @Inject constructor(private val dataStore: DataStore<StoredSettings>) {
    val settings: Flow<UserSettings> = dataStore.data.map { it.toModel() }

    val seedDataCreated: Flow<Boolean> = dataStore.data.map { it.seedDataCreated }

    val seedPhotosAttempted: Flow<Boolean> = dataStore.data.map { it.seedPhotosAttempted }

    /** Applies [transform] atomically to the current settings. */
    suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.updateData { stored -> transform(stored.toModel()).toStored(markersOf = stored) }
    }

    suspend fun markSeedDataCreated() {
        dataStore.updateData { it.copy(seedDataCreated = true) }
    }

    suspend fun markSeedPhotosAttempted() {
        dataStore.updateData { it.copy(seedPhotosAttempted = true) }
    }
}

private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

private fun StoredSettings.toModel() = UserSettings(
    schemaVersion = schemaVersion,
    onboardingCompleted = onboardingCompleted,
    theme = enumOrDefault(theme, ThemeMode.SYSTEM),
    dynamicColor = dynamicColor,
    alertSound = alertSound,
    preAlertSeconds = preAlertSeconds,
    vibration = vibration,
    alertLength = enumOrDefault(alertLength, AlertLength.NORMAL),
    deductStock = deductStock,
    lowStockGrams = lowStockGrams,
    keepScreenOn = keepScreenOn,
    dimWhileSteeping = dimWhileSteeping,
    dismissedHints = dismissedHints,
)

private fun UserSettings.toStored(markersOf: StoredSettings) = StoredSettings(
    schemaVersion = schemaVersion,
    onboardingCompleted = onboardingCompleted,
    theme = theme.name,
    dynamicColor = dynamicColor,
    alertSound = alertSound,
    preAlertSeconds = preAlertSeconds,
    vibration = vibration,
    alertLength = alertLength.name,
    deductStock = deductStock,
    lowStockGrams = lowStockGrams,
    keepScreenOn = keepScreenOn,
    dimWhileSteeping = dimWhileSteeping,
    dismissedHints = dismissedHints,
    seedDataCreated = markersOf.seedDataCreated,
    seedPhotosAttempted = markersOf.seedPhotosAttempted,
)
