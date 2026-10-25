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

internal const val LITRE_ML = 1000.0
private const val PER_100_ML = 100.0
private const val MINUTE_S = 60.0
private const val HOUR_S = 3600.0
private const val VOLUME_GROUP = 4
private const val VOLUME_UNIT_GROUP = 5

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

    /** The first temperature in [text], °C. */
    fun temperature(text: String): Double? =
        BrewProse.TEMPERATURE.find(text)?.destructured?.let { (low, high) -> midpoint(low, high) }

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
