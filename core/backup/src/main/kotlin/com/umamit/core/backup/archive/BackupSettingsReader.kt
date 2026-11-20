package com.umamit.core.backup.archive

import com.umamit.core.datastore.StoredSettings
import com.umamit.core.model.AlertLength
import com.umamit.core.model.CollectionSegment
import com.umamit.core.model.CollectionSort
import com.umamit.core.model.LowStockReminderInterval
import com.umamit.core.model.MonthSpan
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsRankingBy
import com.umamit.core.model.StatsTab
import com.umamit.core.model.ThemeMode
import com.umamit.core.model.UserSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `settings.json` of a backup (05 §6, §8): validated and brought to the current settings `schemaVersion`.
 *
 * - A device-bound key ([BackupFormat.DEVICE_BOUND_SETTINGS_KEYS]) is damage: the writer never writes one, and the
 *   import never replaces one (D-130 item 22).
 * - Its `schemaVersion` is the manifest's `settingsSchemaVersion`; the strict decode refuses an unknown key or a wrong
 *   type. A missing key takes its default, as in the DataStore (05 §6).
 * - An older version is converted by the DataStore's own steps (`SchemaV2Migration`, `SchemaV3Migration` in
 *   `core:datastore`, repeated here because they are internal there): v2 turns a stored 10 g *Knapp ab* into 15 g,
 *   v3 turns `lowStockReminder` into its interval.
 * - Stricter than the DataStore's read, which falls back to defaults: an unknown enum name, a pre-alert, volume or
 *   reminder time out of 05 §6's range, or a month that is not `yyyy-MM` is damage, since the replace writes the
 *   values as they are and the timer reads them (rule 8).
 */
internal object BackupSettingsReader {
    private const val SCHEMA_VERSION = "schemaVersion"
    private const val OLD_DEFAULT_LOW_STOCK_GRAMS = 10.0
    private const val MAX_LOW_STOCK_GRAMS = 500.0
    private const val PRE_ALERT_STEP = 5
    private const val MAX_PRE_ALERT_SECONDS = 30

    /** The settings the replace writes: every key but the device-bound ones, at the current version. */
    fun read(tree: JsonObject, settingsSchemaVersion: Int): JsonObject {
        ensure(tree.keys.none { it in BackupFormat.DEVICE_BOUND_SETTINGS_KEYS })
        val version = (tree[SCHEMA_VERSION] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
        ensure(version == settingsSchemaVersion)
        val stored = BackupJson.decodeStrict(StoredSettings.serializer(), tree)
        val current = upgrade(stored)
        check(current)
        return settingsJson(current)
    }

    private fun upgrade(stored: StoredSettings): StoredSettings {
        var settings = stored
        if (settings.schemaVersion < 2) {
            settings = settings.copy(
                schemaVersion = 2,
                lowStockGrams = if (settings.lowStockGrams == OLD_DEFAULT_LOW_STOCK_GRAMS) {
                    UserSettings.DEFAULT_LOW_STOCK_GRAMS
                } else {
                    settings.lowStockGrams
                },
            )
        }
        if (settings.schemaVersion < UserSettings.CURRENT_SCHEMA_VERSION) {
            settings = settings.copy(
                schemaVersion = UserSettings.CURRENT_SCHEMA_VERSION,
                lowStockReminderInterval = if (settings.lowStockReminder) {
                    LowStockReminderInterval.DAILY.name
                } else {
                    LowStockReminderInterval.OFF.name
                },
            )
        }
        return settings
    }

    private fun check(settings: StoredSettings) {
        known<ThemeMode>(settings.theme)
        known<AlertLength>(settings.alertLength)
        known<LowStockReminderInterval>(settings.lowStockReminderInterval)
        known<CollectionSort>(settings.collectionSort)
        known<CollectionSegment>(settings.collectionSegment)
        known<StatsRange>(settings.statsRange)
        known<StatsTab>(settings.statsTab)
        known<StatsRankingBy>(settings.statsRankingBy)
        ensure(settings.alertSound.isNotBlank() && settings.preAlertSound.isNotBlank())
        ensure(
            settings.alertVolumePercent in
                UserSettings.MIN_ALERT_VOLUME_PERCENT..UserSettings.MAX_ALERT_VOLUME_PERCENT &&
                settings.alertVolumePercent % UserSettings.ALERT_VOLUME_STEP_PERCENT == 0,
        )
        ensure(preAlert(settings.preAlertSeconds))
        ensure(settings.quickTimerPreAlert?.let(::preAlert) ?: true)
        ensure(settings.lowStockGrams.isFinite() && settings.lowStockGrams in 0.0..MAX_LOW_STOCK_GRAMS)
        ensure(settings.lowStockReminderTime in 0..UserSettings.LAST_MINUTE_OF_DAY)
        listOfNotNull(settings.statsRangeFrom, settings.statsRangeTo).forEach { ensure(MonthSpan.parse(it) != null) }
    }

    /** 0 *Aus* or 5–30 s in 5 s steps (SET-10, TMR-22). */
    private fun preAlert(seconds: Int) =
        seconds == 0 || (seconds in PRE_ALERT_STEP..MAX_PRE_ALERT_SECONDS && seconds % PRE_ALERT_STEP == 0)

    private inline fun <reified E : Enum<E>> known(name: String) = ensure(enumValues<E>().any { it.name == name })
}
