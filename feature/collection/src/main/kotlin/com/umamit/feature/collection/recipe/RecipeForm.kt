package com.umamit.feature.collection.recipe

import com.umamit.core.domain.brew.BrewScaling
import com.umamit.core.domain.brew.Ratio
import com.umamit.core.domain.brew.brewMl
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Vessel
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.ui.DurationSteps
import java.io.Serializable
import java.util.Locale

/**
 * One step of Rezept bearbeiten (SCR-12, PRF-04): its [kind], [durationSeconds] (5 s – 24 h), its own temperature
 * [tempC] (null: the recipe's base), a [note] and its Vorwarnung [preAlertSeconds] (null *Automatisch*, 0 *Aus*,
 * else 5–30 s; TMR-23, D-093). [key] tells the steps apart while they are dragged; it is not stored.
 */
data class RecipeStepForm(
    val key: Int,
    val kind: StepKind = StepKind.INFUSION,
    val durationSeconds: Int = DEFAULT_SECONDS,
    val tempC: Int? = null,
    val note: String = "",
    val preAlertSeconds: Int? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
        private const val DEFAULT_SECONDS = 60
    }
}

/**
 * Rezept bearbeiten's fields as typed (SCR-12, PRF-03, PRF-04, D-100): leaf and water as text, so a half-typed
 * value stays as it is. [preAlertSeconds] is the recipe's own Vorwarnung, the base B of its steps' *Automatisch*
 * (null *Wie in Einstellungen*, 0 *Aus*, else 5–30 s). [isDefault] is *Als Standard*. Serializable, so it lives in
 * the `SavedStateHandle`.
 */
@Suppress("TooManyFunctions") // one function per editing rule of PRF-03 and PRF-04
data class RecipeForm(
    val name: String = "",
    val method: BrewMethod = BrewMethod.KYUSU,
    val vesselId: String? = null,
    val leaf: String = "",
    val water: String = "",
    val baseTempC: Int = DEFAULT_TEMP_C,
    val preAlertSeconds: Int? = null,
    val steps: List<RecipeStepForm> = listOf(RecipeStepForm(key = 0)),
    val notes: String = "",
    val isDefault: Boolean = false,
) : Serializable {
    /** The leaf in grams, or null while it is not 0,1–100 g in 0,1 g. */
    val leafGrams: Double? get() = parseLeaf(leaf)

    /** The water in ml, or null while it is not 10–3000 ml in 5 ml steps. */
    val waterMl: Int? get() = parseWater(water)

    /** Leaf per 100 ml („1,6 g / 100 ml", PRF-03 AC1), or null while leaf or water is not valid. */
    val ratioPer100Ml: Double?
        get() {
            val grams = leafGrams ?: return null
            val ml = waterMl ?: return null
            return BrewScaling.per100Ml(grams, ml)
        }

    /** What *Speichern* refuses (PRF-03, PRF-04, 05 §3.2), shown at the field. */
    fun errors(): Set<RecipeFieldError> = buildSet {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) add(RecipeFieldError.NameEmpty)
        if (trimmed.length > MAX_NAME) add(RecipeFieldError.NameTooLong)
        if (leafGrams == null) add(RecipeFieldError.Leaf)
        if (waterMl == null) add(RecipeFieldError.Water)
        if (steps.none { it.kind != StepKind.RINSE }) add(RecipeFieldError.OnlyRinse)
    }

    /**
     * A vessel chosen (WARE-03, WARE-04, as the setup): the water becomes the volume it brews and the leaf follows
     * at the current ratio; with a leaf or water that isn't valid yet only the water is set. [vessel] null is
     * *Kein Gefäß*: leaf and water stay.
     */
    fun withVessel(vessel: Vessel?): RecipeForm {
        if (vessel == null) return copy(vesselId = null)
        val ml = vessel.brewMl().coerceIn(BrewScaling.MIN_WATER_ML, BrewScaling.MAX_WATER_ML)
        val grams = leafGrams
        val current = waterMl
        val scaled = if (grams != null && current != null) BrewScaling.leafFor(Ratio(grams, current), ml) else null
        return copy(
            vesselId = vessel.id,
            water = ml.toString(),
            leaf = scaled?.let(::leafText) ?: leaf,
        )
    }

    /** The base temperature one 5 °C step warmer or cooler, within 5–100 °C (PRF-03, GEN-12). */
    fun withBaseStep(up: Boolean): RecipeForm = copy(baseTempC = tempStep(baseTempC, up))

    /** The temperature step [index] shows: its own, else the base. */
    fun shownTempC(index: Int): Int = steps[index].tempC ?: baseTempC

    /** Step [index] one 5 °C step warmer or cooler; it then has its own temperature. */
    fun withStepTemp(index: Int, up: Boolean): RecipeForm =
        withStep(index) { it.copy(tempC = tempStep(shownTempC(index), up)) }

    /** Step [index]'s duration one stepper increment longer or shorter (PRF-04). */
    fun withStepDuration(index: Int, up: Boolean): RecipeForm =
        withStep(index) { it.copy(durationSeconds = DurationSteps.stepped(it.durationSeconds, up)) }

    /** „Dauer wählen" (PRF-04): step [index] takes [seconds], bounded and rounded to the increment. */
    fun withStepDurationSet(index: Int, seconds: Int): RecipeForm =
        withStep(index) { it.copy(durationSeconds = DurationSteps.rounded(seconds)) }

    /** Step [index] becomes a [kind]; the last step that brews can't become *Spülen* ([canBeRinse]). */
    fun withStepKind(index: Int, kind: StepKind): RecipeForm =
        if (kind == StepKind.RINSE && !canBeRinse(index)) this else withStep(index) { it.copy(kind = kind) }

    fun withStepNote(index: Int, note: String): RecipeForm =
        withStep(index) { it.copy(note = note.take(MAX_STEP_NOTE)) }

    fun withStepPreAlert(index: Int, seconds: Int?): RecipeForm = withStep(index) { it.copy(preAlertSeconds = seconds) }

    /** Step [index] may be *Spülen*: another step brews (PRF-04: at least one step that is not a rinse). */
    fun canBeRinse(index: Int): Boolean = steps.withIndex().any { (i, s) -> i != index && s.kind != StepKind.RINSE }

    /** Step [index] can go: a rinse, or not the last step that brews (PRF-04). */
    fun canDelete(index: Int): Boolean = steps[index].kind == StepKind.RINSE || canBeRinse(index)

    fun withoutStep(index: Int): RecipeForm =
        if (canDelete(index)) copy(steps = steps.filterIndexed { i, _ -> i != index }) else this

    /** Up to [MAX_STEPS] steps (PRF-03). */
    val canAddStep: Boolean get() = steps.size < MAX_STEPS

    /**
     * *Aufguss hinzufügen*: the timer's *Weiterer Aufguss* default (TMR-10) after the last step, its Vorwarnung
     * *Automatisch* (D-100).
     */
    fun withAddedStep(): RecipeForm {
        if (!canAddStep) return this
        val added = BrewTimerEngine.defaultAddedStep(steps.map { it.planned(baseTempC) })
        val key = (steps.maxOfOrNull { it.key } ?: -1) + 1
        val tempC = added.tempC?.takeIf { it != baseTempC }
        return copy(steps = steps + RecipeStepForm(key, added.kind, added.plannedSeconds, tempC))
    }

    /** Moves step [from] to the place [to] (drag, or TalkBack's move actions). */
    fun withStepMoved(from: Int, to: Int): RecipeForm {
        if (from !in steps.indices || to !in steps.indices || from == to) return this
        val moved = steps.toMutableList().apply { add(to, removeAt(from)) }
        return copy(steps = moved)
    }

    /** The form without the steps' keys, which aren't stored: what „Änderungen verwerfen?" compares. */
    internal fun content(): RecipeForm = copy(steps = steps.map { it.copy(key = 0) })

    /**
     * [base] with this form's values; call only when [errors] is empty. A step at the base temperature keeps none of
     * its own (as the setup's *Im Rezept speichern*). Edited in the app, the recipe's values are the user's.
     */
    internal fun applyTo(base: Recipe): Recipe = base.copy(
        name = name.trim(),
        method = method,
        vesselId = vesselId,
        leafGrams = checkNotNull(leafGrams),
        waterMl = checkNotNull(waterMl),
        baseTempC = baseTempC,
        preAlertSeconds = preAlertSeconds,
        steps = steps.map {
            RecipeStep(
                kind = it.kind,
                durationSeconds = it.durationSeconds,
                tempC = it.tempC?.takeIf { t -> t != baseTempC },
                note = it.note.trim().ifEmpty { null },
                preAlertSeconds = it.preAlertSeconds,
            )
        },
        notes = notes.trim().ifEmpty { null },
        isDefault = base.isDefault || isDefault,
        source = RecipeSource.USER,
    )

    private fun withStep(index: Int, transform: (RecipeStepForm) -> RecipeStepForm): RecipeForm {
        if (index !in steps.indices) return this
        return copy(steps = steps.mapIndexed { i, s -> if (i == index) transform(s) else s })
    }

    companion object {
        private const val serialVersionUID = 1L

        /** A recipe's name has 1–40 characters (PRF-03). */
        const val MAX_NAME = 40

        /** A recipe's notes (PRF-03) and a step's note (05 §3.3). */
        const val MAX_NOTES = 500
        const val MAX_STEP_NOTE = 120

        /** 1–30 steps (PRF-03). */
        const val MAX_STEPS = 30

        /** Base and step temperatures: 5–100 °C in 5 °C steps (PRF-03, GEN-12). */
        const val MIN_TEMP_C = 5
        const val MAX_TEMP_C = 100
        private const val TEMP_UNIT = 5
        private const val DEFAULT_TEMP_C = 70

        /** At or below this, the temperature reads „kalt" (PRF-03). */
        const val COLD_UP_TO_C = 10

        private const val WATER_UNIT = 5
        private val LEAF = Regex("""\d{1,3}(?:[.,]\d)?""")

        /** The fields of [recipe]. */
        fun of(recipe: Recipe): RecipeForm = RecipeForm(
            name = recipe.name,
            method = recipe.method,
            vesselId = recipe.vesselId,
            leaf = leafText(recipe.leafGrams),
            water = recipe.waterMl.toString(),
            baseTempC = recipe.baseTempC,
            preAlertSeconds = recipe.preAlertSeconds,
            steps = recipe.steps.mapIndexed { i, s ->
                RecipeStepForm(i, s.kind, s.durationSeconds, s.tempC, s.note.orEmpty(), s.preAlertSeconds)
            },
            notes = recipe.notes.orEmpty(),
            isDefault = recipe.isDefault,
        )

        /** „4,5" → 4.5 g within 0,1–100 g; null otherwise. */
        fun parseLeaf(text: String): Double? = text.trim().takeIf(LEAF::matches)
            ?.replace(',', '.')?.toDouble()
            ?.takeIf { it >= BrewScaling.MIN_LEAF_GRAMS && it <= BrewScaling.MAX_LEAF_GRAMS }

        /** „250" → 250 ml within 10–3000 ml in 5 ml steps; null otherwise. */
        fun parseWater(text: String): Int? = text.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.toIntOrNull()
            ?.takeIf { it in BrewScaling.MIN_WATER_ML..BrewScaling.MAX_WATER_ML && it % WATER_UNIT == 0 }

        /** 4.5 → „4,5" (German decimal comma, 05 §7). */
        fun leafText(grams: Double): String = "%.1f".format(Locale.GERMAN, grams)

        private fun tempStep(tempC: Int, up: Boolean): Int {
            val next = if (up) {
                Math.floorDiv(tempC, TEMP_UNIT) * TEMP_UNIT + TEMP_UNIT
            } else {
                Math.floorDiv(tempC - 1, TEMP_UNIT) * TEMP_UNIT
            }
            return next.coerceIn(MIN_TEMP_C, MAX_TEMP_C)
        }
    }
}

/** The step as the timer engine plans it, for the Vorwarnung's lead and *Weiterer Aufguss*. */
internal fun RecipeStepForm.planned(baseTempC: Int) =
    PlannedStep(kind, durationSeconds, tempC ?: baseTempC, preAlertSeconds)

/** A field *Speichern* refuses, shown at the field. */
enum class RecipeFieldError {
    NameEmpty,
    NameTooLong,
    Leaf,
    Water,

    /** Every step is *Spülen* (PRF-04). */
    OnlyRinse,
}
