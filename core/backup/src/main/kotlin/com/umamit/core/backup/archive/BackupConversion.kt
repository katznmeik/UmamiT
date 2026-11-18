package com.umamit.core.backup.archive

import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.StepKind
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `data.json` of any `schemaVersion` 1…[UmamiDatabase.VERSION] as rows of the current schema (05 §8 „Versions",
 * D-130 item 23). A column a later schema added takes its migration's value (05 §9):
 *
 * | Since | Rows | Value |
 * |---|---|---|
 * | v2 | `brewSteps`, `sessionSteps`: `preAlertSeconds` | null (*Automatisch*) |
 * | v3 | `catalogBookmarks` | an empty array |
 * | v5 | `teas`: `lowStockWarning` | true |
 * | v6 | `sessionDescriptors` | an empty array |
 * | v7 | `sessionSteps`: the six tastes and `tastingNote` | null, then the session's six move to one step |
 * | v8 | `sessions`: `brewingRating` | the row's `rating` |
 * | v9 | `teas`: `tasteSet`; `sessionSteps`: `tasteRoast`, `tasteAcidity`, `tasteFreshness`, `tasteSpice` | null |
 *
 * A key of a column the file's schema did not have yet is damage (it was not written by that app). After the
 * conversion every row must have every key of its type, nulls included, as the writer writes them: a row type's
 * defaults never fill a missing key. Then the strict decode of [BackupFormat.json] (unknown keys and enum names fail).
 */
internal object BackupConversion {
    private class Added(
        val since: Int,
        val table: String,
        val keys: List<String>,
        val value: (JsonObject) -> JsonElement,
    )

    private const val TEAS = "teas"
    private const val BREW_STEPS = "brewSteps"
    private const val SESSIONS = "sessions"
    private const val SESSION_STEPS = "sessionSteps"
    private const val TASTE_ON_STEPS = 7

    private val NULL: (JsonObject) -> JsonElement = { JsonNull }

    private val ADDED_COLUMNS = listOf(
        Added(2, BREW_STEPS, listOf("preAlertSeconds"), NULL),
        Added(2, SESSION_STEPS, listOf("preAlertSeconds"), NULL),
        Added(5, TEAS, listOf("lowStockWarning")) { JsonPrimitive(true) },
        Added(
            TASTE_ON_STEPS,
            SESSION_STEPS,
            listOf(
                "tasteUmami",
                "tasteSweetness",
                "tasteBitterness",
                "tasteAstringency",
                "tasteBody",
                "tasteAroma",
                "tastingNote",
            ),
            NULL,
        ),
        Added(8, SESSIONS, listOf("brewingRating")) { it["rating"] ?: JsonNull },
        Added(9, TEAS, listOf("tasteSet"), NULL),
        Added(9, SESSION_STEPS, listOf("tasteRoast", "tasteAcidity", "tasteFreshness", "tasteSpice"), NULL),
    )

    private val ADDED_TABLES = mapOf("catalogBookmarks" to 3, "sessionDescriptors" to 6)

    /** Every array of [BackupData] with its row type. */
    private val TABLES: Map<String, KSerializer<*>> = mapOf(
        TEAS to BackupTea.serializer(),
        "brewProfiles" to BackupBrewProfile.serializer(),
        BREW_STEPS to BackupBrewStep.serializer(),
        "vessels" to BackupVessel.serializer(),
        SESSIONS to BackupSession.serializer(),
        SESSION_STEPS to BackupSessionStep.serializer(),
        "customDescriptors" to BackupCustomDescriptor.serializer(),
        "sessionDescriptors" to BackupSessionDescriptor.serializer(),
        "inventoryEvents" to BackupInventoryEvent.serializer(),
        "photos" to BackupPhoto.serializer(),
        "catalogBookmarks" to BackupCatalogBookmark.serializer(),
    )

    fun data(tree: JsonObject, schemaVersion: Int): BackupData {
        val converted = upgrade(tree, schemaVersion)
        ensure(converted.keys == TABLES.keys)
        TABLES.forEach { (table, serializer) ->
            val keys = serializer.descriptor.let { d -> (0 until d.elementsCount).map(d::getElementName).toSet() }
            rows(converted, table).forEach { ensure(it.keys == keys) }
        }
        val data = BackupJson.decodeStrict(BackupData.serializer(), converted)
        return if (schemaVersion < TASTE_ON_STEPS) data.withSessionTasteOnSteps() else data
    }

    private fun upgrade(tree: JsonObject, schemaVersion: Int): JsonObject {
        if (schemaVersion >= UmamiDatabase.VERSION) return tree
        val out = tree.toMutableMap()
        ADDED_TABLES.forEach { (table, since) ->
            if (schemaVersion < since) {
                ensure(table !in out)
                out[table] = JsonArray(emptyList())
            }
        }
        ADDED_COLUMNS.filter { schemaVersion < it.since }.forEach { added ->
            out[added.table] = JsonArray(
                rows(JsonObject(out), added.table).map { row ->
                    ensure(added.keys.none { it in row })
                    JsonObject(row + added.keys.associateWith { added.value(row) })
                },
            )
        }
        return JsonObject(out)
    }

    /** The objects of array [table]; refused when it is missing, not an array, or holds anything but objects. */
    private fun rows(tree: JsonObject, table: String): List<JsonObject> {
        val array = tree[table] as? JsonArray ?: refuse(BackupRefusal.DAMAGED)
        return array.map { it as? JsonObject ?: refuse(BackupRefusal.DAMAGED) }
    }
}

/**
 * 05 §9 v7 (`MIGRATION_6_7`): a session's six tastes move to one of its steps, the first ended step that is neither
 * skipped nor *Spülen* by position, else its first step that is not *Spülen*, else its first step; then the session's
 * six are null. A session without values, or without any step, keeps them.
 */
internal fun BackupData.withSessionTasteOnSteps(): BackupData {
    val stepsBySession = sessionSteps.groupBy { it.sessionId }
    val moved = mutableMapOf<String, BackupSessionStep>()
    val converted = sessions.map { session ->
        val steps = stepsBySession[session.id].orEmpty().sortedBy { it.position }
        val tasted = listOf(
            session.tasteUmami,
            session.tasteSweetness,
            session.tasteBitterness,
            session.tasteAstringency,
            session.tasteBody,
            session.tasteAroma,
        ).any { it != null }
        if (!tasted || steps.isEmpty()) {
            session
        } else {
            val target = steps.firstOrNull { it.actualSeconds != null && !it.skipped && it.kind != StepKind.RINSE }
                ?: steps.firstOrNull { it.kind != StepKind.RINSE }
                ?: steps.first()
            moved[target.id] = target.copy(
                tasteUmami = session.tasteUmami,
                tasteSweetness = session.tasteSweetness,
                tasteBitterness = session.tasteBitterness,
                tasteAstringency = session.tasteAstringency,
                tasteBody = session.tasteBody,
                tasteAroma = session.tasteAroma,
            )
            session.copy(
                tasteUmami = null,
                tasteSweetness = null,
                tasteBitterness = null,
                tasteAstringency = null,
                tasteBody = null,
                tasteAroma = null,
            )
        }
    }
    return copy(sessions = converted, sessionSteps = sessionSteps.map { moved[it.id] ?: it })
}
