package com.umamit.core.backup.recipe

import com.umamit.core.backup.recipe.RecipeFileResult.Refused
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.VesselType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * The one reader of received recipe files (05 §8a, PRF-08 AC5). The file is untrusted, so the checks run in this
 * order and the first that fails decides:
 * 1. more than [RecipeFile.MAX_BYTES], refused unread: not a recipe file;
 * 2. not strict UTF-8 or not JSON, or the top level not an object: damaged;
 * 3. `format` not the string `umamit-recipe`: not a recipe file;
 * 4. `formatVersion` newer than [RecipeFile.FORMAT_VERSION]: newer (before any other field is looked at, since a
 *    newer file may have other fields); not a whole number ≥ 1: damaged;
 * 5. every field ([RecipeFields]): a missing or unknown field, a wrong JSON type, an unknown enum name, a value out
 *    of range or a text too long (never cut): damaged.
 *
 * The shop link never refuses a file: one that fails CAT-17 AC2's check is dropped with the catalog key
 * ([SharedTea.catalogLink]). Nothing is written.
 */
internal object RecipeFileReader {
    private val json = Json
    private val DIGITS = Regex("""\d{1,30}""")
    private const val BOM = '\uFEFF'

    fun read(bytes: ByteArray): RecipeFileResult {
        val tooLarge = bytes.size > RecipeFile.MAX_BYTES
        val root = if (tooLarge) null else parse(bytes)
        val format = (root?.get(Keys.FORMAT) as? JsonPrimitive)?.takeIf { it.isString }?.content
        return when {
            tooLarge -> Refused(RecipeRefusal.NOT_A_RECIPE_FILE)
            root == null -> Refused(RecipeRefusal.DAMAGED)
            format != RecipeFile.FORMAT -> Refused(RecipeRefusal.NOT_A_RECIPE_FILE)
            else -> versioned(root)
        }
    }

    private fun versioned(root: JsonObject): RecipeFileResult {
        val version = (root[Keys.FORMAT_VERSION] as? JsonPrimitive)
            ?.takeIf { !it.isString && DIGITS.matches(it.content) }
            ?.content?.toBigInteger()
        return when {
            version == null -> Refused(RecipeRefusal.DAMAGED)
            version > RecipeFile.FORMAT_VERSION.toBigInteger() -> Refused(RecipeRefusal.NEWER_VERSION)
            version < RecipeFile.FORMAT_VERSION.toBigInteger() -> Refused(RecipeRefusal.DAMAGED)
            else -> fields(root)
        }
    }

    private fun fields(root: JsonObject): RecipeFileResult = try {
        val fields = Fields(root, Keys.ROOT)
        val createdAt = OffsetDateTime.parse(fields.string(Keys.CREATED_AT))
        val tea = RecipeFields.tea(fields.obj(Keys.TEA, Keys.TEA_FIELDS))
        RecipeFileResult.Valid(RecipeFields.recipe(fields.obj(Keys.RECIPE, Keys.RECIPE_FIELDS), tea), createdAt)
    } catch (_: DamagedFileException) {
        Refused(RecipeRefusal.DAMAGED)
    } catch (_: DateTimeParseException) {
        Refused(RecipeRefusal.DAMAGED)
    }

    /** [bytes] as strict UTF-8 (a leading BOM allowed) parsed to an object, or null. */
    private fun parse(bytes: ByteArray): JsonObject? {
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .removePrefix(BOM.toString())
        } catch (_: CharacterCodingException) {
            null
        }
        return try {
            text?.let(json::parseToJsonElement) as? JsonObject
        } catch (_: SerializationException) {
            null
        }
    }
}

/** The field checks of 05 §8a: 05 §3.1–§3.3's ranges, PRF-03's and PRF-04's limits. */
internal object RecipeFields {
    const val MAX_TEA_NAME = 80
    const val MAX_SECONDARY_NAME = 80
    const val MAX_RECIPE_NAME = 40
    const val MAX_STEP_NOTE = 120
    const val MAX_STEPS = 30
    const val MIN_STEP_SECONDS = 5
    const val MAX_STEP_SECONDS = 86_400
    const val MIN_LEAF_GRAMS = 0.1
    const val MAX_LEAF_GRAMS = 100.0
    const val MIN_WATER_ML = 10
    const val MAX_WATER_ML = 3000
    const val MIN_TEMP_C = 5
    const val MAX_TEMP_C = 100
    const val MAX_PRE_ALERT_SECONDS = 30
    private const val UNIT = 5

    fun tea(fields: Fields): SharedTea {
        val (catalogKey, shopUrl) = SharedTea.catalogLink(
            fields.stringOrNull(Keys.CATALOG_KEY),
            fields.stringOrNull(Keys.SHOP_URL),
        )
        return SharedTea(
            name = fields.name(Keys.NAME, MAX_TEA_NAME),
            category = fields.enum<TeaCategory>(Keys.CATEGORY),
            secondaryName = fields.stringOrNull(Keys.SECONDARY_NAME)?.let { text(it, MAX_SECONDARY_NAME, name = true) }
                ?.ifEmpty { null },
            styleKey = SharedTea.knownStyle(fields.stringOrNull(Keys.STYLE_KEY)),
            catalogKey = catalogKey,
            shopUrl = shopUrl,
        )
    }

    fun recipe(fields: Fields, tea: SharedTea): SharedRecipe {
        val steps = fields.array(Keys.STEPS)
        check(steps.size in 1..MAX_STEPS)
        val parsed = steps.map { step(Fields(it as? JsonObject ?: damaged(), Keys.STEP_FIELDS)) }
        check(parsed.any { it.kind != StepKind.RINSE })
        return SharedRecipe(
            tea = tea,
            name = fields.name(Keys.NAME, MAX_RECIPE_NAME),
            method = fields.enum<BrewMethod>(Keys.METHOD),
            leafGrams = leaf(fields.number(Keys.LEAF_GRAMS)),
            waterMl = fields.int(Keys.WATER_ML).also { check(it in MIN_WATER_ML..MAX_WATER_ML && it % UNIT == 0) },
            baseTempC = temp(fields.int(Keys.BASE_TEMP_C)),
            steps = parsed,
            vesselType = fields.stringOrNull(Keys.VESSEL_TYPE)?.let { enumByName<VesselType>(it) },
            preAlertSeconds = fields.intOrNull(Keys.PRE_ALERT_SECONDS)?.let(::preAlert),
        )
    }

    private fun step(fields: Fields): RecipeStep = RecipeStep(
        kind = fields.enum<StepKind>(Keys.KIND),
        durationSeconds = fields.int(Keys.DURATION_SECONDS)
            .also { check(it in MIN_STEP_SECONDS..MAX_STEP_SECONDS && it % UNIT == 0) },
        tempC = fields.intOrNull(Keys.TEMP_C)?.let(::temp),
        note = fields.stringOrNull(Keys.NOTE)?.let { text(it, MAX_STEP_NOTE, name = false) }?.ifEmpty { null },
        preAlertSeconds = fields.intOrNull(Keys.PRE_ALERT_SECONDS)?.let(::preAlert),
    )

    /** 0,1–100 g with at most one decimal (PRF-03), from the number as written. */
    private fun leaf(number: String): Double {
        val exact = number.toBigDecimalOrNull() ?: damaged()
        check(exact.stripTrailingZeros().scale() <= 1)
        val grams = exact.toDouble()
        check(grams in MIN_LEAF_GRAMS..MAX_LEAF_GRAMS)
        return grams
    }

    /** 5–100 °C in 5 °C steps (GEN-12). */
    private fun temp(tempC: Int): Int = tempC.also { check(it in MIN_TEMP_C..MAX_TEMP_C && it % UNIT == 0) }

    /** 0 (*Aus*) or 5–30 s in 5 s steps (TMR-23). */
    private fun preAlert(seconds: Int): Int =
        seconds.also { check(it == 0 || (it in UNIT..MAX_PRE_ALERT_SECONDS && it % UNIT == 0)) }

    /** [value] trimmed, within [max] characters (refused, never cut); a [name] has no control characters. */
    fun text(value: String, max: Int, name: Boolean): String {
        val trimmed = value.trim()
        check(trimmed.length <= max)
        if (name) check(trimmed.none(Char::isISOControl))
        return trimmed
    }

    private fun Fields.name(key: String, max: Int): String = text(string(key), max, name = true).ifEmpty { damaged() }

    private inline fun <reified T : Enum<T>> Fields.enum(key: String): T = enumByName(string(key))

    private inline fun <reified T : Enum<T>> enumByName(name: String): T =
        enumValues<T>().firstOrNull { it.name == name } ?: damaged()

    /** Like Kotlin's `check`, but a failed check marks the file damaged. */
    private fun check(condition: Boolean) {
        if (!condition) damaged()
    }
}

/**
 * The fields of one JSON object of the file, read strictly: a field not in [allowed] makes the file damaged, a
 * required field must be there and not null, numbers must be JSON numbers and texts JSON strings.
 */
internal class Fields(private val obj: JsonObject, allowed: Set<String>) {
    init {
        if (!allowed.containsAll(obj.keys)) damaged()
    }

    fun string(key: String): String = stringOrNull(key) ?: damaged()

    fun stringOrNull(key: String): String? = primitiveOrNull(key)?.let { if (it.isString) it.content else damaged() }

    fun int(key: String): Int = intOrNull(key) ?: damaged()

    fun intOrNull(key: String): Int? = numberOrNull(key)?.let { INT.matchEntire(it)?.value?.toIntOrNull() ?: damaged() }

    /** A JSON number's text as written („4.0"). */
    fun number(key: String): String = numberOrNull(key) ?: damaged()

    fun obj(key: String, allowed: Set<String>): Fields = Fields(obj[key] as? JsonObject ?: damaged(), allowed)

    fun array(key: String): JsonArray = obj[key] as? JsonArray ?: damaged()

    private fun numberOrNull(key: String): String? =
        primitiveOrNull(key)?.let { if (it.isString) damaged() else it.content }

    /** The primitive at [key]; null when absent or JSON null; damaged for an object or array. */
    private fun primitiveOrNull(key: String): JsonPrimitive? = when (val element = obj[key]) {
        null -> null
        is JsonPrimitive -> element.takeUnless { it is JsonNull }
        else -> damaged()
    }

    private companion object {
        /** A whole JSON number of at most nine digits (no fraction, no exponent), so it fits an Int. */
        val INT = Regex("""-?\d{1,9}""")
    }
}

/** Marks the file being read as damaged; caught once, in [RecipeFileReader]. */
internal class DamagedFileException : RuntimeException("damaged recipe file")

internal fun damaged(): Nothing = throw DamagedFileException()
