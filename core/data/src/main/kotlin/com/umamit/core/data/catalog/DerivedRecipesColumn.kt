package com.umamit.core.data.catalog

import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * `catalog_tea.derived_recipes_json` (05 §4.1): the derived recipes (CAT-04) in the shape of
 * `brew_profile` and its steps, the default first: `[{"name":"Kyusu","method":"KYUSU",
 * "leaf_grams":4.0,"water_ml":200,"base_temp_c":65,"source":"CATALOG_TABLE","is_default":true,
 * "steps":[{"kind":"INFUSION","duration_seconds":60,"temp_c":null},…]},…]`. Reading never
 * throws: an unreadable column reads as no recipe, an unreadable recipe is left out.
 */
internal object DerivedRecipesColumn {
    private const val NAME = "name"
    private const val METHOD = "method"
    private const val LEAF = "leaf_grams"
    private const val WATER = "water_ml"
    private const val TEMP = "base_temp_c"
    private const val SOURCE = "source"
    private const val DEFAULT = "is_default"
    private const val STEPS = "steps"
    private const val KIND = "kind"
    private const val DURATION = "duration_seconds"
    private const val STEP_TEMP = "temp_c"

    fun json(recipes: List<RecipeTemplate>): String? = recipes.takeIf { it.isNotEmpty() }?.let {
        buildJsonArray {
            it.forEach { recipe ->
                addJsonObject {
                    put(NAME, recipe.name)
                    put(METHOD, recipe.method.name)
                    put(LEAF, recipe.leafGrams)
                    put(WATER, recipe.waterMl)
                    put(TEMP, recipe.baseTempC)
                    put(SOURCE, recipe.source.name)
                    put(DEFAULT, recipe.isDefault)
                    putJsonArray(STEPS) {
                        recipe.steps.forEach { step ->
                            addJsonObject {
                                put(KIND, step.kind.name)
                                put(DURATION, step.durationSeconds)
                                put(STEP_TEMP, step.tempC)
                            }
                        }
                    }
                }
            }
        }.toString()
    }

    fun recipes(json: String?): List<RecipeTemplate> = (parse(json) as? JsonArray).orEmpty().mapNotNull { element ->
        val fields = element as? JsonObject ?: return@mapNotNull null
        val steps = (fields[STEPS] as? JsonArray).orEmpty().map { step(it) ?: return@mapNotNull null }
        RecipeTemplate(
            name = fields.string(NAME) ?: return@mapNotNull null,
            method = fields.enum<BrewMethod>(METHOD) ?: return@mapNotNull null,
            leafGrams = fields.primitive(LEAF)?.doubleOrNull ?: return@mapNotNull null,
            waterMl = fields.primitive(WATER)?.intOrNull ?: return@mapNotNull null,
            baseTempC = fields.primitive(TEMP)?.intOrNull ?: return@mapNotNull null,
            steps = steps.ifEmpty { return@mapNotNull null },
            source = fields.enum<RecipeSource>(SOURCE) ?: RecipeSource.CATALOG_TABLE,
            isDefault = fields.primitive(DEFAULT)?.booleanOrNull ?: false,
        )
    }

    private fun step(element: JsonElement): RecipeStep? {
        val fields = element as? JsonObject
        val kind = fields?.enum<StepKind>(KIND)
        val seconds = fields?.primitive(DURATION)?.intOrNull?.takeIf { it > 0 }
        return if (kind != null && seconds != null) {
            RecipeStep(kind, seconds, fields.primitive(STEP_TEMP)?.intOrNull)
        } else {
            null
        }
    }

    private fun parse(json: String?): JsonElement? = json?.let {
        try {
            Json.parseToJsonElement(it)
        } catch (_: SerializationException) {
            null
        }
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content

    private inline fun <reified E : Enum<E>> JsonObject.enum(key: String): E? =
        string(key)?.let { name -> enumValues<E>().firstOrNull { it.name == name } }
}
