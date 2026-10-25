package com.umamit.core.catalog.parser

/**
 * An amount of leaf for a volume of water (08 §2.5 step 2), ranges at their midpoint.
 *
 * @property waterMl the stated volume; null for a per-litre amount („~10g/L"), which a recipe
 *   brews at a volume of its own (step 4).
 */
internal data class Amount(val leafGrams: Double, val waterMl: Double?) {
    /** Grams per 100 ml: comparable between rows and the prose whatever their volume. */
    val gramsPer100Ml: Double get() = leafGrams / (waterMl ?: LITRE_ML) * PER_100_ML
}

/**
 * [count] tea bags for [waterMl] of water (live sample 2, D-089); [weighed] by the package's
 * grams per bag, unknown without them.
 */
internal data class Bags(val count: Double, val waterMl: Double) {
    fun weighed(gramsPerBag: Double?): Amount? = gramsPerBag?.let { Amount(count * it, waterMl) }
}

internal const val LITRE_ML = 1000.0
private const val PER_100_ML = 100.0
private const val MINUTE_S = 60.0
private const val HOUR_S = 3600.0
private const val BOILING_C = 100.0
private const val VOLUME_GROUP = 4
private const val VOLUME_UNIT_GROUP = 5
private const val BAGS_UNIT_GROUP = 4

/** Reads amounts, temperatures and times from grid cells and prose (08 §2.5 step 2): ranges → midpoint. */
internal object BrewValues {
    /** The first amount per volume in [text]; teaspoons weigh [gramsPerTeaspoon] each. */
    fun amount(text: String, gramsPerTeaspoon: Double): Amount? {
        val match = BrewProse.AMOUNT_PER_VOLUME.find(text) ?: return null
        val (low, high, unit) = match.destructured
        val volume = match.groupValues[VOLUME_GROUP]
        val volumeUnit = match.groupValues[VOLUME_UNIT_GROUP]
        val amount = midpoint(low, high) * if (unit.lowercase().startsWith("t")) gramsPerTeaspoon else 1.0
        val number = volume.toDecimal()
        val water = when {
            volumeUnit.lowercase() == "ml" -> number

            // „/L", „/1L", „pro Liter": per litre.
            number == null || number == 1.0 -> null

            else -> number * LITRE_ML
        }
        return Amount(amount, water).takeIf { water == null || water > 0 }
    }

    /** The first number of tea bags for a volume in [text] („1Stk./500ml", „ein Beutel auf 0,3–0,5 Liter"). */
    fun bags(text: String): Bags? {
        val match = BrewProse.BAGS_PER_VOLUME.find(text) ?: return null
        val (count, low, high) = match.destructured
        val volume = low.toDecimal()?.let { midpoint(low, high) }
        val unit = match.groupValues[BAGS_UNIT_GROUP]
        val water = if (unit.lowercase() == "ml") volume else (volume ?: 1.0) * LITRE_ML
        val bags = count.toDecimal() ?: 1.0
        return water?.takeIf { it > 0 && bags > 0 }?.let { Bags(bags, it) }
    }

    /**
     * The first temperature in [text], °C; boiling water („auf 100 °C aufkochen und auf 70 °C
     * abkühlen lassen") only when no other temperature is named.
     */
    fun temperature(text: String): Double? {
        val all = BrewProse.TEMPERATURE.findAll(text).map { it.destructured.let { (low, high) -> midpoint(low, high) } }
        return all.firstOrNull { it < BOILING_C } ?: all.firstOrNull()
    }

    /** The first time in [text] in seconds; with [hours] false, a time in hours is skipped. */
    fun seconds(text: String, hours: Boolean = true): Double? = BrewProse.TIME.findAll(text).mapNotNull { match ->
        val (low, high, unit) = match.destructured
        val lower = unit.lowercase()
        val factor = when {
            lower.startsWith("st") -> if (hours) HOUR_S else return@mapNotNull null
            lower.startsWith("s") -> 1.0
            else -> MINUTE_S
        }
        midpoint(low, high) * factor
    }.firstOrNull()

    private fun midpoint(low: String, high: String): Double {
        val from = requireNotNull(low.toDecimal())
        val to = high.toDecimal() ?: from
        return (from + to) / 2
    }

    private fun String.toDecimal(): Double? = takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()
}
