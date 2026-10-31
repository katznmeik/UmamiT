package com.umamit.core.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.umamit.core.model.AlertLength
import com.umamit.core.model.ThemeMode
import com.umamit.core.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
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
    /**
     * A file without the key predates the versioning, so it reads as 1; [SchemaV2Migration] moves every
     * file of version 1 to [UserSettings.CURRENT_SCHEMA_VERSION] once, a fresh install's defaults included.
     */
    val schemaVersion: Int = 1,
    val onboardingCompleted: Boolean = false,
    val theme: String = ThemeMode.SYSTEM.name,
    val dynamicColor: Boolean = false,
    val alertSound: String = UserSettings.DEFAULT_ALERT_SOUND,
    val preAlertSound: String = UserSettings.DEFAULT_PRE_ALERT_SOUND,
    /** SET-27 (v0.3.0, D-098); a file of v0.2.2 has no key and reads 100 %, so no settings migration. */
    val alertVolumePercent: Int = UserSettings.MAX_ALERT_VOLUME_PERCENT,
    /** SET-32 (v0.3.0, D-099); a file of v0.3.0-rc.1 or older has no key and reads on, so no settings migration. */
    val ringUnderDnd: Boolean = true,
    val preAlertSeconds: Int = 10,
    val vibration: Boolean = true,
    val alertLength: String = AlertLength.NORMAL.name,
    val deductStock: Boolean = true,
    val lowStockGrams: Double = UserSettings.DEFAULT_LOW_STOCK_GRAMS,
    val lowStockHidden: Set<String> = emptySet(),
    /** SET-28, SET-29 and the reminder's last date (INV-11, v0.3.0, D-098); a file of v0.2.2 reads the defaults. */
    val lowStockReminder: Boolean = true,
    val lowStockReminderTime: Int = UserSettings.DEFAULT_LOW_STOCK_REMINDER_TIME,
    val lowStockReminderLastDate: String? = null,
    val keepScreenOn: Boolean = true,
    val dimWhileSteeping: Boolean = true,
    val dismissedHints: Set<String> = emptySet(),
    val catalogDownloadPhotos: Boolean = true,
    /** TMR-22 (v0.2.2, D-097): SCR-07's last *Vorwarnung*; null *Automatisch* (also for a file of v0.2.1). */
    val quickTimerPreAlert: Int? = null,
    /** CAT-10 (v0.3.0, D-098): the catalog's folded groups; a file of v0.2.2 has no key and reads ∅, no migration. */
    val catalogFolded: Set<String> = emptySet(),
    /** Set once the first-start seed data exists (M1-07); never reset. */
    val seedDataCreated: Boolean = false,
    /** Set once the one-time seed photo download has ended in any way (ONB-06); never reset. */
    val seedPhotosAttempted: Boolean = false,
    /**
     * The parts of the seed teas' catalog link that are done (CAT-15, D-090), as
     * `<tea id>/description` and `<tea id>/cover`; each is added once and never removed.
     */
    val seedTeaLinksDone: Set<String> = emptySet(),
    /** Set once the seven seed vessels of v0.2.1 exist (WARE-06, D-093); never reset. */
    val seedVesselsV2Added: Boolean = false,
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

/**
 * Settings `schemaVersion` 1 → 2 (v0.2.2, SET-16, D-097): SET-16 had no settings row before, so a stored
 * 10,0 g is the old default, never the user's choice, and becomes the new 15,0 g; any other value stays.
 * DataStore runs it before the first read, and only while the file is at version 1, so exactly once.
 */
internal object SchemaV2Migration : DataMigration<StoredSettings> {
    private const val OLD_DEFAULT_LOW_STOCK_GRAMS = 10.0

    override suspend fun shouldMigrate(currentData: StoredSettings): Boolean = currentData.schemaVersion < 2

    override suspend fun migrate(currentData: StoredSettings): StoredSettings = currentData.copy(
        schemaVersion = 2,
        lowStockGrams = if (currentData.lowStockGrams == OLD_DEFAULT_LOW_STOCK_GRAMS) {
            UserSettings.DEFAULT_LOW_STOCK_GRAMS
        } else {
            currentData.lowStockGrams
        },
    )

    override suspend fun cleanUp() = Unit
}

/**
 * The settings DataStore on the file of [produceFile], with the settings migrations (05 §6). The app
 * and the tests open it here, so both run the same migrations.
 */
fun settingsDataStore(
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File,
): DataStore<StoredSettings> = DataStoreFactory.create(
    serializer = SettingsSerializer,
    migrations = listOf(SchemaV2Migration),
    scope = scope,
    produceFile = produceFile,
)

/** The settings of 05 §6 (M1 subset) and the seed marker, backed by DataStore. */
class SettingsDataSource @Inject constructor(private val dataStore: DataStore<StoredSettings>) {
    val settings: Flow<UserSettings> = dataStore.data.map { it.toModel() }

    val seedDataCreated: Flow<Boolean> = dataStore.data.map { it.seedDataCreated }

    val seedPhotosAttempted: Flow<Boolean> = dataStore.data.map { it.seedPhotosAttempted }

    val seedTeaLinksDone: Flow<Set<String>> = dataStore.data.map { it.seedTeaLinksDone }

    val seedVesselsV2Added: Flow<Boolean> = dataStore.data.map { it.seedVesselsV2Added }

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

    suspend fun markSeedVesselsV2Added() {
        dataStore.updateData { it.copy(seedVesselsV2Added = true) }
    }

    /** Adds [parts] to [seedTeaLinksDone] (CAT-15, D-090). */
    suspend fun markSeedTeaLinksDone(parts: Set<String>) {
        dataStore.updateData { it.copy(seedTeaLinksDone = it.seedTeaLinksDone + parts) }
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
    preAlertSound = preAlertSound,
    alertVolumePercent = alertVolumePercent,
    ringUnderDnd = ringUnderDnd,
    preAlertSeconds = preAlertSeconds,
    vibration = vibration,
    alertLength = enumOrDefault(alertLength, AlertLength.NORMAL),
    deductStock = deductStock,
    lowStockGrams = lowStockGrams,
    lowStockHidden = lowStockHidden,
    lowStockReminder = lowStockReminder,
    lowStockReminderTime = lowStockReminderTime,
    lowStockReminderLastDate = lowStockReminderLastDate,
    keepScreenOn = keepScreenOn,
    dimWhileSteeping = dimWhileSteeping,
    dismissedHints = dismissedHints,
    catalogDownloadPhotos = catalogDownloadPhotos,
    quickTimerPreAlert = quickTimerPreAlert,
    catalogFolded = catalogFolded,
)

private fun UserSettings.toStored(markersOf: StoredSettings) = StoredSettings(
    schemaVersion = schemaVersion,
    onboardingCompleted = onboardingCompleted,
    theme = theme.name,
    dynamicColor = dynamicColor,
    alertSound = alertSound,
    preAlertSound = preAlertSound,
    alertVolumePercent = alertVolumePercent,
    ringUnderDnd = ringUnderDnd,
    preAlertSeconds = preAlertSeconds,
    vibration = vibration,
    alertLength = alertLength.name,
    deductStock = deductStock,
    lowStockGrams = lowStockGrams,
    lowStockHidden = lowStockHidden,
    lowStockReminder = lowStockReminder,
    lowStockReminderTime = lowStockReminderTime,
    lowStockReminderLastDate = lowStockReminderLastDate,
    keepScreenOn = keepScreenOn,
    dimWhileSteeping = dimWhileSteeping,
    dismissedHints = dismissedHints,
    catalogDownloadPhotos = catalogDownloadPhotos,
    quickTimerPreAlert = quickTimerPreAlert,
    catalogFolded = catalogFolded,
    seedDataCreated = markersOf.seedDataCreated,
    seedPhotosAttempted = markersOf.seedPhotosAttempted,
    seedTeaLinksDone = markersOf.seedTeaLinksDone,
    seedVesselsV2Added = markersOf.seedVesselsV2Added,
)
