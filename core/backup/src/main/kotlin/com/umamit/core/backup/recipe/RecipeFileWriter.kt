package com.umamit.core.backup.recipe

import com.umamit.core.model.RecipeStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Writes the JSON of 05 §8a, readable in a text editor (D-130 Q2). The tea's optional fields are left out when
 * absent; `catalogKey` and `shopUrl` only for a catalog tea. The recipe's own optional fields are written as null.
 */
internal object RecipeFileWriter {
    private val json = Json { prettyPrint = true }

    fun write(recipe: SharedRecipe, createdAt: OffsetDateTime): ByteArray {
        val root = buildJsonObject {
            put(Keys.FORMAT, RecipeFile.FORMAT)
            put(Keys.FORMAT_VERSION, RecipeFile.FORMAT_VERSION)
            put(
                Keys.CREATED_AT,
                createdAt.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            )
            put(Keys.TEA, tea(recipe.tea))
            put(Keys.RECIPE, recipe(recipe))
        }
        return json.encodeToString(JsonObject.serializer(), root).toByteArray(Charsets.UTF_8)
    }

    private fun tea(tea: SharedTea): JsonObject = buildJsonObject {
        put(Keys.NAME, tea.name)
        tea.secondaryName?.let { put(Keys.SECONDARY_NAME, it) }
        put(Keys.CATEGORY, tea.category.name)
        tea.styleKey?.let { put(Keys.STYLE_KEY, it) }
        tea.catalogKey?.let { put(Keys.CATALOG_KEY, it) }
        tea.shopUrl?.let { put(Keys.SHOP_URL, it) }
    }

    private fun recipe(recipe: SharedRecipe): JsonObject = buildJsonObject {
        put(Keys.NAME, recipe.name)
        put(Keys.METHOD, recipe.method.name)
        put(Keys.LEAF_GRAMS, recipe.leafGrams)
        put(Keys.WATER_ML, recipe.waterMl)
        put(Keys.BASE_TEMP_C, recipe.baseTempC)
        putOrNull(Keys.VESSEL_TYPE, recipe.vesselType?.name)
        putOrNull(Keys.PRE_ALERT_SECONDS, recipe.preAlertSeconds)
        put(Keys.STEPS, buildJsonArray { recipe.steps.forEach { add(step(it)) } })
    }

    private fun step(step: RecipeStep): JsonObject = buildJsonObject {
        put(Keys.KIND, step.kind.name)
        put(Keys.DURATION_SECONDS, step.durationSeconds)
        putOrNull(Keys.TEMP_C, step.tempC)
        putOrNull(Keys.PRE_ALERT_SECONDS, step.preAlertSeconds)
        put(Keys.NOTE, step.note.orEmpty())
    }

    private fun JsonObjectBuilder.putOrNull(key: String, value: String?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun JsonObjectBuilder.putOrNull(key: String, value: Int?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }
}

/** The field names of 05 §8a. */
internal object Keys {
    const val FORMAT = "format"
    const val FORMAT_VERSION = "formatVersion"
    const val CREATED_AT = "createdAt"
    const val TEA = "tea"
    const val RECIPE = "recipe"
    const val NAME = "name"
    const val SECONDARY_NAME = "secondaryName"
    const val CATEGORY = "category"
    const val STYLE_KEY = "styleKey"
    const val CATALOG_KEY = "catalogKey"
    const val SHOP_URL = "shopUrl"
    const val METHOD = "method"
    const val LEAF_GRAMS = "leafGrams"
    const val WATER_ML = "waterMl"
    const val BASE_TEMP_C = "baseTempC"
    const val VESSEL_TYPE = "vesselType"
    const val PRE_ALERT_SECONDS = "preAlertSeconds"
    const val STEPS = "steps"
    const val KIND = "kind"
    const val DURATION_SECONDS = "durationSeconds"
    const val TEMP_C = "tempC"
    const val NOTE = "note"

    val ROOT = setOf(FORMAT, FORMAT_VERSION, CREATED_AT, TEA, RECIPE)
    val TEA_FIELDS = setOf(NAME, SECONDARY_NAME, CATEGORY, STYLE_KEY, CATALOG_KEY, SHOP_URL)
    val RECIPE_FIELDS = setOf(NAME, METHOD, LEAF_GRAMS, WATER_ML, BASE_TEMP_C, VESSEL_TYPE, PRE_ALERT_SECONDS, STEPS)
    val STEP_FIELDS = setOf(KIND, DURATION_SECONDS, TEMP_C, PRE_ALERT_SECONDS, NOTE)
}
