package com.umamit.core.backup.archive

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The `.umamit` backup file, `formatVersion` 1 (05 §8, BAK-01, D-128 Q5, D-130 Q7). A ZIP with these entries and no
 * others; entry order is not significant (the writer puts the photos first and [MANIFEST] last, because the manifest
 * holds the others' checksums):
 *
 * - [MANIFEST] (deflated, UTF-8 JSON): a [BackupManifest]. `format` is [FORMAT_ID]; `checksums` maps the path of
 *   every other entry to the lowercase hex SHA-256 of its bytes (the uncompressed entry), exactly those paths.
 * - [DATA] (deflated, UTF-8 JSON): a [BackupData], one array per personal table of 05 §3 with the columns in
 *   camelCase. Only `COMPLETED` and `MANUAL` sessions and the rows that hang on them; never `active_brew` or an
 *   `IN_PROGRESS` session (D-130 item 27). The catalog is never in it. The rows are closed under their references:
 *   every id a row points to is in the file, except `catalogKey`s (the catalog is re-downloadable).
 * - [SETTINGS] (deflated, UTF-8 JSON): one object, the settings file of 05 §6 with every key except
 *   [DEVICE_BOUND_SETTINGS_KEYS]; defaults are written out, so every key is present.
 * - `photos/<id>.jpg` ([photoEntry], stored, no recompression): the JPEG of each `photos` row in [DATA], own and
 *   catalog copies alike, byte for byte. A photo whose file was missing at the time of the write is left out
 *   together with its row, and a tea's or vessel's `coverPhotoId` that pointed at it is null in [DATA]; so every
 *   row has its file and every file its row.
 *
 * [json] is the format's JSON: the writer's, and the one the reader decodes with (unknown keys and enum names fail).
 */
object BackupFormat {
    const val FORMAT_ID = "umamit-backup"
    const val FORMAT_VERSION = 1

    /** The file name extension; the picker's document is typed `application/octet-stream` (D-130 item 27). */
    const val EXTENSION = "umamit"
    const val MIME_TYPE = "application/octet-stream"

    const val MANIFEST = "manifest.json"
    const val DATA = "data.json"
    const val SETTINGS = "settings.json"
    const val PHOTOS_DIR = "photos/"
    const val PHOTO_SUFFIX = ".jpg"

    /**
     * The settings keys of 05 §6 that stay on the device: neither written to [SETTINGS] nor replaced by an import
     * (05 §8, D-130 item 22). Names that are not settings yet are listed too, so they stay out once they are.
     */
    val DEVICE_BOUND_SETTINGS_KEYS: Set<String> = setOf(
        "onboardingCompleted",
        "seedDataCreated",
        "seedVesselsV2Added",
        "seedPhotosAttempted",
        "seedTeaLinksDone",
        "dismissedHints",
        "lowStockReminderLastDate",
        "backupReminderLastAt",
        "lastBackupAt",
        "safetyCopyAt",
    )

    /** The entry of photo [id]. */
    fun photoEntry(id: String): String = "$PHOTOS_DIR$id$PHOTO_SUFFIX"

    val json: Json = Json {
        encodeDefaults = true
        explicitNulls = true
    }
}

/** `manifest.json` (05 §8). `createdAt` is epoch ms, `zoneId` the device's zone at that moment. */
@Serializable
data class BackupManifest(
    val format: String = BackupFormat.FORMAT_ID,
    val formatVersion: Int = BackupFormat.FORMAT_VERSION,
    val createdAt: Long,
    val zoneId: String,
    val appVersionName: String,
    val appVersionCode: Long,
    /** `umamit.db`'s schema version (9 in v0.9.0). */
    val schemaVersion: Int,
    /** The settings file's `schemaVersion` (05 §6). */
    val settingsSchemaVersion: Int,
    val counts: BackupEntryCounts,
    /** Entry path → lowercase hex SHA-256, for every entry but the manifest. */
    val checksums: Map<String, String>,
)

/** The number of rows of each array of [BackupData]; [photos] is also the number of photo entries. */
@Serializable
data class BackupEntryCounts(
    val teas: Int,
    val brewProfiles: Int,
    val brewSteps: Int,
    val vessels: Int,
    val sessions: Int,
    val sessionSteps: Int,
    val customDescriptors: Int,
    val sessionDescriptors: Int,
    val inventoryEvents: Int,
    val photos: Int,
    val catalogBookmarks: Int,
)

/** The counts of [this] `data.json`. */
fun BackupData.counts() = BackupEntryCounts(
    teas = teas.size,
    brewProfiles = brewProfiles.size,
    brewSteps = brewSteps.size,
    vessels = vessels.size,
    sessions = sessions.size,
    sessionSteps = sessionSteps.size,
    customDescriptors = customDescriptors.size,
    sessionDescriptors = sessionDescriptors.size,
    inventoryEvents = inventoryEvents.size,
    photos = photos.size,
    catalogBookmarks = catalogBookmarks.size,
)
