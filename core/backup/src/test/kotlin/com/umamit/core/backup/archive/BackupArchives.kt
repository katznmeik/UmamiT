package com.umamit.core.backup.archive

import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.dao.BackupRows
import com.umamit.core.database.entity.BrewProfileEntity
import com.umamit.core.database.entity.BrewStepEntity
import com.umamit.core.database.entity.CatalogBookmarkEntity
import com.umamit.core.database.entity.SessionDescriptorEntity
import com.umamit.core.database.entity.VesselEntity
import com.umamit.core.datastore.StoredSettings
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.VesselMaterial
import com.umamit.core.model.VesselType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Synthetic backup files for the reader's tests (D-016): the fixture's rows of BackupFixture.kt as `data.json`, and a
// ZIP writer with full control over names, order, methods and the manifest, so every refusal can be built.

/** A valid `data.json`: every table has rows, covers point at the owners' photos. */
fun validRows() = BackupRows(
    teas = listOf(
        tea(Ids.TEA, "Eigener Tee").copy(coverPhotoId = Ids.PHOTO_OWN),
        tea(Ids.TEA_SHOP, "Shop-Tee", catalogKey = "kontor-123").copy(coverPhotoId = Ids.PHOTO_SHOP),
    ),
    brewProfiles = listOf(readProfile()),
    brewSteps = listOf(
        BrewStepEntity("step-1", Ids.PROFILE, 0, StepKind.INFUSION, 60, 60, null, T0, T0, null),
        BrewStepEntity("step-2", Ids.PROFILE, 1, StepKind.INFUSION, 20, 65, "kurz", T0, T0, 5),
    ),
    vessels = listOf(readVessel()),
    sessions = listOf(
        session(Ids.COMPLETED, SessionStatus.COMPLETED, T0 + 10_000),
        session(Ids.MANUAL, SessionStatus.MANUAL, T0 + 20_000).copy(brewingRating = null),
    ),
    sessionSteps = listOf(
        sessionStep("ss-1", Ids.COMPLETED, 0),
        sessionStep("ss-2", Ids.COMPLETED, 1),
        sessionStep("ss-3", Ids.MANUAL, 0),
    ),
    sessionDescriptors = listOf(SessionDescriptorEntity(Ids.COMPLETED, "marine.nori", T0)),
    inventoryEvents = listOf(
        event("event-initial", sessionId = null, type = InventoryEventType.INITIAL, delta = 100.0),
        event("event-brew", sessionId = Ids.COMPLETED, type = InventoryEventType.BREW, delta = -4.0),
    ),
    photos = listOf(
        photo(Ids.PHOTO_OWN, PhotoOrigin.OWN, teaId = Ids.TEA),
        photo(Ids.PHOTO_SESSION, PhotoOrigin.OWN, sessionId = Ids.COMPLETED),
        photo(Ids.PHOTO_SHOP, PhotoOrigin.CATALOG_COPY, teaId = Ids.TEA_SHOP),
        photo(Ids.PHOTO_VESSEL, PhotoOrigin.OWN, vesselId = Ids.VESSEL),
    ),
    catalogBookmarks = listOf(CatalogBookmarkEntity("kontor-456", T0)),
)

private fun readProfile() = BrewProfileEntity(
    id = Ids.PROFILE,
    teaId = Ids.TEA,
    name = "Kyusu",
    method = BrewMethod.KYUSU,
    leafGrams = 4.0,
    waterMl = 200,
    baseTempC = 60,
    vesselId = Ids.VESSEL,
    preAlertSeconds = null,
    isDefault = true,
    position = 0,
    source = RecipeSource.USER,
    notes = null,
    createdAt = T0,
    updatedAt = T0,
)

private fun readVessel() = VesselEntity(
    id = Ids.VESSEL,
    name = "Kyusu 250 ml",
    type = VesselType.KYUSU,
    capacityMl = 250,
    usableMl = 200,
    material = VesselMaterial.UNGLAZED_CLAY,
    notes = null,
    isFavorite = false,
    isArchived = false,
    coverPhotoId = Ids.PHOTO_VESSEL,
    createdAt = T0,
    updatedAt = T0,
)

fun validData(): BackupData = validRows().toBackup()

/** Settings with some values away from their defaults. */
fun validStoredSettings() = StoredSettings(
    schemaVersion = 3,
    theme = "DARK",
    preAlertSeconds = 15,
    alertVolumePercent = 70,
    lowStockGrams = 25.0,
    quickTimerPreAlert = 0,
    lowStockReminderInterval = "WEEKLY",
    statsRange = "CUSTOM",
    statsRangeFrom = "2026-01",
    statsRangeTo = "2026-10",
    catalogFolded = setOf("green/-"),
)

fun JsonElement.toBytes() = BackupFormat.json.encodeToString(JsonElement.serializer(), this).encodeToByteArray()

fun dataTree(data: BackupData = validData()): JsonObject =
    BackupFormat.json.encodeToJsonElement(BackupData.serializer(), data).jsonObject

fun settingsTree(settings: StoredSettings = validStoredSettings()): JsonObject = settingsJson(settings)

/** [this] with [key] set to [value] (or removed for null). */
fun JsonObject.with(key: String, value: JsonElement?): JsonObject =
    JsonObject(if (value == null) this - key else this + (key to value))

/** [this] with row [index] of array [table] changed by [edit]. */
fun JsonObject.withRow(table: String, index: Int, edit: (JsonObject) -> JsonObject): JsonObject {
    val rows = getValue(table) as JsonArray
    return with(table, JsonArray(rows.mapIndexed { i, row -> if (i == index) edit(row.jsonObject) else row }))
}

/** One ZIP entry as the test writes it; with [zeros], that many zero bytes stream in instead of [bytes] (a bomb). */
class TestEntry(val name: String, val bytes: ByteArray, val stored: Boolean = false, val zeros: Long = 0)

/** A ZIP of [entries] in this order; stored entries with their CRC, the others deflated. */
fun zip(entries: List<TestEntry>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { entry ->
            val zipEntry = ZipEntry(entry.name)
            if (entry.stored) {
                zipEntry.method = ZipEntry.STORED
                zipEntry.size = entry.bytes.size.toLong()
                zipEntry.compressedSize = entry.bytes.size.toLong()
                zipEntry.crc = CRC32().apply { update(entry.bytes) }.value
            }
            zip.putNextEntry(zipEntry)
            if (entry.zeros > 0) {
                val chunk = ByteArray(1 shl 20)
                repeat((entry.zeros / chunk.size).toInt()) { zip.write(chunk) }
            } else {
                zip.write(entry.bytes)
            }
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/**
 * The entries of a backup: the photos (stored), [data], [settings], [extra] and the manifest last, which counts the
 * arrays of [data] and holds the SHA-256 of every other entry; [manifest] edits it before it is written.
 */
fun backupEntries(
    photos: Map<String, ByteArray>,
    data: JsonObject = dataTree(),
    settings: JsonObject = settingsTree(),
    extra: List<TestEntry> = emptyList(),
    schemaVersion: Int = UmamiDatabase.VERSION,
    manifest: (JsonObject) -> JsonObject = { it },
): List<TestEntry> {
    val entries = photos.map { (id, bytes) -> TestEntry(BackupFormat.photoEntry(id), bytes, stored = true) } +
        TestEntry(BackupFormat.DATA, data.toBytes()) +
        TestEntry(BackupFormat.SETTINGS, settings.toBytes()) +
        extra
    val counts = JsonObject(
        data.mapValues { (_, rows) -> JsonPrimitive((rows as? JsonArray)?.size ?: 0) },
    )
    val tree = JsonObject(
        mapOf(
            "format" to JsonPrimitive(BackupFormat.FORMAT_ID),
            "formatVersion" to JsonPrimitive(BackupFormat.FORMAT_VERSION),
            "createdAt" to JsonPrimitive(T0),
            "zoneId" to JsonPrimitive("Europe/Berlin"),
            "appVersionName" to JsonPrimitive("0.9.0"),
            "appVersionCode" to JsonPrimitive(90_099),
            "schemaVersion" to JsonPrimitive(schemaVersion),
            "settingsSchemaVersion" to (settings["schemaVersion"] ?: JsonPrimitive(3)),
            "counts" to counts,
            "checksums" to JsonObject(entries.associate { it.name to JsonPrimitive(sha256(it.bytes)) }),
        ),
    )
    return entries + TestEntry(BackupFormat.MANIFEST, manifest(tree).toBytes())
}
