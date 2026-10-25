package com.umamit.core.data.catalog

import com.umamit.core.catalog.parser.BrewGridRow
import com.umamit.core.catalog.parser.ShopScale
import com.umamit.core.model.BrewTableCell
import com.umamit.core.model.BrewTableRow
import com.umamit.core.model.TasteScale
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The JSON columns of `catalog_tea` (05 §4.1): `taste_scales_json` (`{"Umami":4,"Bitterstoffe":1}`)
 * and `brew_table_raw` (one object per grid row: `{"kind":"japan","cells":[{"label":"Menge",
 * "text":"~4g/100ml"},…]}`). Reading never throws: an unreadable column reads as empty.
 */
internal object CatalogColumns {
    private const val KIND = "kind"
    private const val CELLS = "cells"
    private const val LABEL = "label"
    private const val TEXT = "text"

    fun tasteScalesJson(scales: List<ShopScale>): String? = scales.takeIf { it.isNotEmpty() }?.let {
        buildJsonObject { it.forEach { scale -> put(scale.label, scale.leaves) } }.toString()
    }

    fun brewTableJson(rows: List<BrewGridRow>): String? = rows.takeIf { it.isNotEmpty() }?.let {
        buildJsonArray {
            it.forEach { row ->
                addJsonObject {
                    put(KIND, row.kind)
                    putJsonArray(CELLS) {
                        row.cells.forEach { cell ->
                            addJsonObject {
                                put(LABEL, cell.label)
                                put(TEXT, cell.text)
                            }
                        }
                    }
                }
            }
        }.toString()
    }

    fun tasteScales(json: String?): List<TasteScale> =
        (parse(json) as? JsonObject).orEmpty().mapNotNull { (label, value) ->
            (value as? JsonPrimitive)?.intOrNull?.let { TasteScale(label, it.coerceIn(0, TasteScale.MAX_LEAVES)) }
        }

    fun brewTable(json: String?): List<BrewTableRow> = (parse(json) as? JsonArray).orEmpty().mapNotNull { row ->
        val fields = row as? JsonObject ?: return@mapNotNull null
        val kind = fields[KIND].text() ?: return@mapNotNull null
        val cells = (fields[CELLS] as? JsonArray).orEmpty().mapNotNull { cell ->
            val cellFields = cell as? JsonObject ?: return@mapNotNull null
            cellFields[TEXT].text()?.let { BrewTableCell(label = cellFields[LABEL].text(), text = it) }
        }
        BrewTableRow(kind, cells)
    }

    private fun parse(json: String?): JsonElement? = json?.let {
        try {
            Json.parseToJsonElement(it)
        } catch (_: SerializationException) {
            null
        }
    }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf {
        it !is JsonNull && it.isString
    }?.content
}
