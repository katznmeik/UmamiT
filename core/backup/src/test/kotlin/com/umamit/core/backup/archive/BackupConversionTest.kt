package com.umamit.core.backup.archive

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.datastore.StoredSettings
import com.umamit.core.model.LowStockReminderInterval
import com.umamit.core.model.StepKind
import com.umamit.core.model.UserSettings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** `data.json` and `settings.json` of older versions (05 §9, D-130 item 23) and the strict decode, pure. */
class BackupConversionTest {
    private val stepTastes = listOf(
        "tasteUmami",
        "tasteSweetness",
        "tasteBitterness",
        "tasteAstringency",
        "tasteBody",
        "tasteAroma",
        "tastingNote",
    )
    private val otherTastes = listOf("tasteRoast", "tasteAcidity", "tasteFreshness", "tasteSpice")

    private fun refusal(block: () -> Any): BackupRefusal? = try {
        block()
        null
    } catch (refused: BackupRefusedException) {
        refused.refusal
    }

    private fun convert(tree: JsonObject, version: Int = UmamiDatabase.VERSION) = BackupConversion.data(tree, version)

    /** The current file with [key] of the first row of [table] set to [value] (removed for null). */
    private fun row(table: String, key: String, value: JsonElement?) =
        dataTree().withRow(table, 0) { it.with(key, value) }

    /** [tree] as a file of [version]: every key a later schema added removed. */
    private fun downgrade(tree: JsonObject, version: Int): JsonObject {
        val removed = mutableMapOf<String, MutableSet<String>>()
        fun remove(table: String, keys: List<String>) = removed.getOrPut(table) { mutableSetOf() }.addAll(keys)
        if (version < 2) listOf("brewSteps", "sessionSteps").forEach { remove(it, listOf("preAlertSeconds")) }
        if (version < 5) remove("teas", listOf("lowStockWarning"))
        if (version < 7) remove("sessionSteps", stepTastes)
        if (version < 8) remove("sessions", listOf("brewingRating"))
        if (version < 9) {
            remove("teas", listOf("tasteSet"))
            remove("sessionSteps", otherTastes)
        }
        var out = tree
        removed.forEach { (table, keys) ->
            val rows = (out.getValue(table) as JsonArray).map { JsonObject((it as JsonObject) - keys) }
            out = out.with(table, JsonArray(rows))
        }
        if (version < 3) out = out.with("catalogBookmarks", null)
        if (version < 6) out = out.with("sessionDescriptors", null)
        return out
    }

    @Test
    fun `a current file decodes as written`() {
        assertThat(convert(dataTree())).isEqualTo(validData())
    }

    @Test
    fun `every older schema version converts to the current one`() {
        (1 until UmamiDatabase.VERSION).forEach { version ->
            val data = convert(downgrade(dataTree(), version), version)
            assertWithMessage("v$version teas").that(data.teas.map { it.tasteSet }).containsExactly(null, null)
            if (version < 5) assertWithMessage("v$version warning").that(data.teas.all { it.lowStockWarning }).isTrue()
            if (version < 3) assertWithMessage("v$version bookmarks").that(data.catalogBookmarks).isEmpty()
            if (version < 6) assertWithMessage("v$version descriptors").that(data.sessionDescriptors).isEmpty()
            if (version < 2) {
                assertWithMessage("v$version pre-alerts").that(data.brewSteps.map { it.preAlertSeconds })
                    .containsExactly(null, null)
            }
            val brewing = if (version <
                8
            ) {
                data.sessions.map { it.rating }
            } else {
                validData().sessions.map { it.brewingRating }
            }
            assertWithMessage("v$version Zubereitung").that(data.sessions.map { it.brewingRating }).isEqualTo(brewing)
            BackupValidator.check(data)
        }
    }

    @Test
    fun `v7 moves a session's tastes to its first poured infusion`() {
        val data = convert(downgrade(dataTree(), 6), 6)
        val session = data.sessions.first { it.id == Ids.COMPLETED }
        assertThat(listOf(session.tasteUmami, session.tasteAroma)).containsExactly(null, null)
        val first = data.sessionSteps.first { it.id == "ss-1" }
        assertThat(listOf(first.tasteUmami, first.tasteSweetness, first.tasteAroma)).containsExactly(5, 4, 0).inOrder()
        assertThat(first.tastingNote).isNull()
        assertThat(data.sessionSteps.first { it.id == "ss-2" }.tasteUmami).isNull()
    }

    @Test
    fun `v7's target is a poured infusion, else a non-rinse, else the first step`() {
        val session = validData().sessions[0].copy(id = "s", tasteUmami = 3)
        fun step(id: String, position: Int, kind: StepKind, ended: Boolean, skipped: Boolean = false) =
            validData().sessionSteps[0].copy(
                id = id,
                sessionId = "s",
                position = position,
                kind = kind,
                actualSeconds = if (ended) 30 else null,
                skipped = skipped,
                tasteUmami = null,
            )
        fun target(vararg steps: BackupSessionStep) =
            BackupData(sessions = listOf(session), sessionSteps = steps.toList())
                .withSessionTasteOnSteps().sessionSteps.single { it.tasteUmami == 3 }.id
        val poured = target(
            step("rinse", 0, StepKind.RINSE, ended = true),
            step("skipped", 1, StepKind.INFUSION, ended = true, skipped = true),
            step("poured", 2, StepKind.INFUSION, ended = true),
        )
        assertThat(poured).isEqualTo("poured")
        val pending =
            target(step("rinse", 0, StepKind.RINSE, ended = true), step("pending", 1, StepKind.INFUSION, false))
        assertThat(pending).isEqualTo("pending")
        val rinse = target(step("rinse-2", 1, StepKind.RINSE, ended = false), step("rinse-1", 0, StepKind.RINSE, true))
        assertThat(rinse).isEqualTo("rinse-1")
        val alone = BackupData(sessions = listOf(session)).withSessionTasteOnSteps()
        assertThat(alone.sessions.single().tasteUmami).isEqualTo(3) // no step row: the session keeps its values
        val untasted = BackupData(
            sessions = listOf(
                session.copy(
                    tasteUmami = null,
                    tasteSweetness = null,
                    tasteBitterness = null,
                    tasteAstringency = null,
                    tasteBody = null,
                    tasteAroma = null,
                ),
            ),
            sessionSteps = listOf(step("a", 0, StepKind.INFUSION, true)),
        )
        assertThat(untasted.withSessionTasteOnSteps()).isEqualTo(untasted)
    }

    @Test
    fun `a column the file's schema did not have, a missing key or table and bad JSON types are damaged`() {
        val nan = dataTree().toBytes().decodeToString().replaceFirst("\"leafGrams\":4.0", "\"leafGrams\":NaN")
        val cases: Map<String, () -> Any> = mapOf(
            "v8 with a tasteSet" to {
                convert(downgrade(dataTree(), 8).withRow("teas", 0) { it.with("tasteSet", JsonNull) }, 8)
            },
            "v2 with bookmarks" to {
                convert(downgrade(dataTree(), 2).with("catalogBookmarks", JsonArray(emptyList())), 2)
            },
            "a missing nullable key" to { convert(row("teas", "notes", null)) },
            "a missing defaulted key" to { convert(row("sessions", "brewingRating", null)) },
            "a missing table" to { convert(dataTree().with("photos", null)) },
            "an unknown table" to { convert(dataTree().with("tags", JsonArray(emptyList()))) },
            "a table that is no array" to { convert(dataTree().with("teas", JsonObject(emptyMap()))) },
            "a string for a boolean" to { convert(row("teas", "isOrganic", JsonPrimitive("ja"))) },
            "a fraction for an int" to { convert(row("vessels", "capacityMl", JsonPrimitive(2.5))) },
            "null for a required key" to { convert(row("teas", "name", JsonNull)) },
            "an unknown enum" to { convert(row("sessionSteps", "kind", JsonPrimitive("POUR"))) },
            "a quoted number" to { convert(row("vessels", "capacityMl", JsonPrimitive("250"))) },
            "a quoted boolean" to { convert(row("teas", "isOrganic", JsonPrimitive("true"))) },
            "a quoted fraction" to { convert(row("brewProfiles", "leafGrams", JsonPrimitive("4.0"))) },
            "an unquoted enum" to { convert(row("teas", "category", JsonPrimitive(1))) },
            "NaN" to { convert(BackupFormat.json.parseToJsonElement(nan) as JsonObject) },
        )
        cases.forEach { (why, block) -> assertWithMessage(why).that(refusal(block)).isEqualTo(BackupRefusal.DAMAGED) }
    }

    @Test
    fun `older settings are converted by the DataStore's steps`() {
        fun read(settings: StoredSettings) = BackupSettingsReader.read(settingsJson(settings), settings.schemaVersion)
        fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
        val v1 = read(StoredSettings(schemaVersion = 1, lowStockGrams = 10.0, lowStockReminder = false))
        assertThat(v1.text("schemaVersion")).isEqualTo(UserSettings.CURRENT_SCHEMA_VERSION.toString())
        assertThat(v1.text("lowStockGrams")).isEqualTo("15.0")
        assertThat(v1.text("lowStockReminderInterval")).isEqualTo(LowStockReminderInterval.OFF.name)
        val v2 = read(StoredSettings(schemaVersion = 2, lowStockGrams = 10.0, lowStockReminder = true))
        assertThat(v2.text("lowStockGrams")).isEqualTo("10.0")
        assertThat(v2.text("lowStockReminderInterval")).isEqualTo(LowStockReminderInterval.DAILY.name)
        assertThat(read(validStoredSettings())).isEqualTo(settingsTree())
        assertThat(BackupSettingsReader.read(JsonObject(mapOf("schemaVersion" to JsonPrimitive(3))), 3))
            .isEqualTo(settingsJson(StoredSettings(schemaVersion = 3))) // a missing key reads its default
    }

    @Test
    fun `the backup reminder switch is a backed-up setting and a file without it reads on`() {
        // SET-20 (05 §6, §8, D-130 Q11): not device-bound, so written, read and replaced like the other user settings.
        val off = BackupSettingsReader.read(settingsJson(validStoredSettings().copy(backupReminderEnabled = false)), 3)
        assertThat(off.getValue("backupReminderEnabled").jsonPrimitive.content).isEqualTo("false")
        val older = BackupSettingsReader.read(
            settingsJson(validStoredSettings()).with("backupReminderEnabled", null),
            3,
        )
        assertThat(older.getValue("backupReminderEnabled").jsonPrimitive.content).isEqualTo("true")
        assertWithMessage("wrong type").that(
            refusal {
                BackupSettingsReader.read(
                    settingsJson(validStoredSettings()).with("backupReminderEnabled", JsonPrimitive("on")),
                    3,
                )
            },
        ).isEqualTo(BackupRefusal.DAMAGED)
    }

    @Test
    fun `settings out of range or device-bound are damaged`() {
        val good = validStoredSettings()
        val cases = mapOf(
            "theme" to settingsJson(good.copy(theme = "PINK")),
            "alert length" to settingsJson(good.copy(alertLength = "ENDLESS")),
            "interval" to settingsJson(good.copy(lowStockReminderInterval = "HOURLY")),
            "sort" to settingsJson(good.copy(collectionSort = "PRICE")),
            "segment" to settingsJson(good.copy(collectionSegment = "WISHLIST")),
            "range" to settingsJson(good.copy(statsRange = "DAYS_7")),
            "tab" to settingsJson(good.copy(statsTab = "MAP")),
            "ranking" to settingsJson(good.copy(statsRankingBy = "PRICE")),
            "sound" to settingsJson(good.copy(alertSound = "")),
            "volume 0" to settingsJson(good.copy(alertVolumePercent = 0)),
            "volume 55" to settingsJson(good.copy(alertVolumePercent = 55)),
            "pre-alert 7" to settingsJson(good.copy(preAlertSeconds = 7)),
            "pre-alert 40" to settingsJson(good.copy(preAlertSeconds = 40)),
            "quick pre-alert 2" to settingsJson(good.copy(quickTimerPreAlert = 2)),
            "low stock 501" to settingsJson(good.copy(lowStockGrams = 501.0)),
            "reminder 1440" to settingsJson(good.copy(lowStockReminderTime = 1_440)),
            "month" to settingsJson(good.copy(statsRangeFrom = "2026-13")),
            "version not the manifest's" to settingsJson(good.copy(schemaVersion = 2)),
            "no version" to settingsJson(good).with("schemaVersion", null),
            "version as text" to settingsJson(good).with("schemaVersion", JsonPrimitive("3")),
            "unknown key" to settingsJson(good).with("cupSizeMl", JsonPrimitive(150)),
            "wrong type" to settingsJson(good).with("vibration", JsonPrimitive(3)),
            "a quoted boolean" to settingsJson(good).with("vibration", JsonPrimitive("true")),
        ) + BackupFormat.DEVICE_BOUND_SETTINGS_KEYS.associate { key ->
            "device-bound $key" to settingsJson(good).with(key, JsonNull)
        }
        cases.forEach { (why, tree) ->
            assertWithMessage(why).that(refusal { BackupSettingsReader.read(tree, 3) }).isEqualTo(BackupRefusal.DAMAGED)
        }
    }
}
