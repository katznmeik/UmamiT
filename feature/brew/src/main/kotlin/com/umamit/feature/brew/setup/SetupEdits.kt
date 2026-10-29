package com.umamit.feature.brew.setup

import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Vessel
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertChoice

/**
 * The values of the setup for this session (BRW-02): the recipe they started
 * from, vessel, water, leaf, the locked [ratio] (BRW-03), method, base
 * temperature and steps, each with its own pre-alert (TMR-23, D-093). Pure:
 * every change is a new value.
 */
internal data class SetupEdits(
    val recipeId: String?,
    val vesselId: String?,
    val waterMl: Int,
    val leafGrams: Double,
    val ratio: Ratio,
    val locked: Boolean,
    val method: BrewMethod,
    val baseTempC: Int,
    val steps: List<PlannedStep>,
) {
    /**
     * What the Schritte-Sheet's single selector and the *Vorwarnung* row show until #92: the first step's
     * choice (the sheet writes one value into every step; D-093).
     */
    val preAlert: PreAlertChoice get() = PreAlertChoice.of(steps.firstOrNull()?.preAlertSeconds)

    /** A vessel sets the water to the volume it brews and, with the lock, rescales the leaf (WARE-03). */
    fun withVessel(vessel: Vessel): SetupEdits = withWater(vessel.brewMl()).copy(vesselId = vessel.id)

    fun withWater(waterMl: Int): SetupEdits {
        val water = waterMl.coerceIn(BrewScaling.MIN_WATER_ML, BrewScaling.MAX_WATER_ML)
        return if (locked) {
            copy(waterMl = water, leafGrams = BrewScaling.leafFor(ratio, water))
        } else {
            copy(waterMl = water, ratio = Ratio(leafGrams, water))
        }
    }

    fun withLeaf(leafGrams: Double): SetupEdits = if (locked) {
        copy(leafGrams = leafGrams, waterMl = BrewScaling.waterFor(ratio, leafGrams))
    } else {
        copy(leafGrams = leafGrams, ratio = Ratio(leafGrams, waterMl))
    }

    /** Locking takes the current values as the ratio. */
    fun withLock(locked: Boolean): SetupEdits = copy(locked = locked, ratio = Ratio(leafGrams, waterMl))

    /** The session's steps with their pre-alerts, each with a temperature (the base where none is given). */
    fun withSteps(steps: List<PlannedStep>): SetupEdits =
        copy(steps = steps.map { it.copy(tempC = it.tempC ?: baseTempC) })

    /** The pre-alert the *Vorwarnung* row shows (04 §5): the steps' explicit value, else the recipe's, else global. */
    fun effectivePreAlertSeconds(recipe: Recipe?, globalSeconds: Int): Int = when (val preAlert = preAlert) {
        PreAlertChoice.Inherit -> recipe?.preAlertSeconds ?: globalSeconds
        PreAlertChoice.Off -> 0
        is PreAlertChoice.Seconds -> preAlert.seconds
    }

    /** Whether these values differ from [recipe], so *Im Rezept speichern* is offered (BRW-02). */
    fun differsFrom(recipe: Recipe): Boolean = this != of(recipe).copy(locked = locked, ratio = ratio)

    /**
     * The recipe with these values (*Im Rezept speichern*): the steps with their pre-alerts (D-093); a step at the
     * base temperature keeps none of its own. The recipe's pre-alert base B stays.
     */
    fun appliedTo(recipe: Recipe): Recipe = recipe.copy(
        vesselId = vesselId,
        waterMl = waterMl,
        leafGrams = leafGrams,
        steps = steps.map { step ->
            RecipeStep(
                step.kind,
                step.plannedSeconds,
                step.tempC.takeIf { it != baseTempC },
                preAlertSeconds = step.preAlertSeconds,
            )
        },
    )

    companion object {
        /** The values of [recipe], with the ratio locked. */
        fun of(recipe: Recipe): SetupEdits = SetupEdits(
            recipeId = recipe.id,
            vesselId = recipe.vesselId,
            waterMl = recipe.waterMl,
            leafGrams = recipe.leafGrams,
            ratio = Ratio(recipe.leafGrams, recipe.waterMl),
            locked = true,
            method = recipe.method,
            baseTempC = recipe.baseTempC,
            steps = recipe.steps.map {
                PlannedStep(it.kind, it.durationSeconds, it.tempC ?: recipe.baseTempC, it.preAlertSeconds)
            },
        )

        /** A tea without any recipe: one minute at 70 °C, 5,0 g in 250 ml (the Kyusu ratio of 08 §2.5a). */
        fun default(): SetupEdits = SetupEdits(
            recipeId = null,
            vesselId = null,
            waterMl = DEFAULT_WATER_ML,
            leafGrams = DEFAULT_LEAF_GRAMS,
            ratio = Ratio(DEFAULT_LEAF_GRAMS, DEFAULT_WATER_ML),
            locked = true,
            method = BrewMethod.KYUSU,
            baseTempC = DEFAULT_TEMP_C,
            steps = listOf(PlannedStep(StepKind.INFUSION, DEFAULT_SECONDS, DEFAULT_TEMP_C)),
        )

        private const val DEFAULT_WATER_ML = 250
        private const val DEFAULT_LEAF_GRAMS = 5.0
        private const val DEFAULT_TEMP_C = 70
        private const val DEFAULT_SECONDS = 60

        // --- Kept across process death (GEN-07) as one string in the SavedStateHandle.
        private const val FIELD = "|"
        private const val STEP = ";"
        private const val PART = ":"
        private const val NONE = ""

        fun encode(e: SetupEdits): String = listOf(
            e.recipeId ?: NONE,
            e.vesselId ?: NONE,
            e.waterMl,
            e.leafGrams,
            e.ratio.leafGrams,
            e.ratio.waterMl,
            e.locked,
            e.method.name,
            e.baseTempC,
            e.steps.joinToString(STEP) {
                listOf(it.kind.name, it.plannedSeconds, it.tempC ?: NONE, it.preAlertSeconds ?: NONE).joinToString(PART)
            },
        ).joinToString(FIELD)

        /** The edits of [encoded], or null when it is missing or unreadable (the setup then starts over). */
        @Suppress("MagicNumber")
        fun decode(encoded: String?): SetupEdits? = runCatching {
            val f = checkNotNull(encoded).split(FIELD)
            SetupEdits(
                recipeId = f[0].ifEmpty { null },
                vesselId = f[1].ifEmpty { null },
                waterMl = f[2].toInt(),
                leafGrams = f[3].toDouble(),
                ratio = Ratio(f[4].toDouble(), f[5].toInt()),
                locked = f[6].toBooleanStrict(),
                method = BrewMethod.valueOf(f[7]),
                baseTempC = f[8].toInt(),
                steps = f[9].split(STEP).map { s ->
                    val p = s.split(PART)
                    PlannedStep(
                        StepKind.valueOf(p[0]),
                        p[1].toInt(),
                        p[2].ifEmpty { null }?.toInt(),
                        p[3].ifEmpty { null }?.toInt(),
                    )
                },
            )
        }.getOrNull()
    }
}

/** The volume a vessel brews (WARE-04): its usable volume, else its capacity. */
internal fun Vessel.brewMl(): Int = usableMl ?: capacityMl
