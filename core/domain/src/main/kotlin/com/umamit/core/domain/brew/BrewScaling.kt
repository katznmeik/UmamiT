package com.umamit.core.domain.brew

import com.umamit.core.model.Vessel
import java.math.BigDecimal
import java.math.RoundingMode

/** A leaf-to-water ratio („4,0 g / 100 ml", PRF-03 AC1): [leafGrams] per [waterMl]. */
data class Ratio(val leafGrams: Double, val waterMl: Int) {
    init {
        require(leafGrams > 0.0 && waterMl > 0) { "a ratio needs leaf and water, was $leafGrams g / $waterMl ml" }
    }
}

/**
 * Ratio scaling of the setup (BRW-03, WARE-03) with the rounding of 05 §7:
 * leaf to 0,1 g and water to 5 ml, both half up, within the recipe bounds of
 * 05 §3.2 (leaf 0,1–100 g, water 10–3000 ml). Times and temperatures never
 * scale. Exact decimals, so 2,45 g rounds to 2,5 g and not to the binary 2,4.
 * The setup (SCR-04) and the recipe editor (SCR-12, PRF-03) share it (D-100).
 */
object BrewScaling {
    const val MIN_LEAF_GRAMS = 0.1
    const val MAX_LEAF_GRAMS = 100.0
    const val MIN_WATER_ML = 10
    const val MAX_WATER_ML = 3000

    private val LEAF_UNIT = BigDecimal("0.1")
    private const val WATER_UNIT = 5

    /** The leaf for [waterMl] at [ratio], to 0,1 g. */
    fun leafFor(ratio: Ratio, waterMl: Int): Double {
        val exact = ratio.leafGrams.toBigDecimal() * waterMl.toBigDecimal()
        return roundLeaf(exact.divide(ratio.waterMl.toBigDecimal(), EXACT_SCALE, RoundingMode.HALF_UP))
    }

    /** The water for [leafGrams] at [ratio], to 5 ml. */
    fun waterFor(ratio: Ratio, leafGrams: Double): Int {
        val exact = leafGrams.toBigDecimal() * ratio.waterMl.toBigDecimal()
        val units = exact.divide(ratio.leafGrams.toBigDecimal() * WATER_UNIT.toBigDecimal(), 0, RoundingMode.HALF_UP)
        return (units.toInt() * WATER_UNIT).coerceIn(MIN_WATER_ML, MAX_WATER_ML)
    }

    /** The leaf stepper: ± 0,1 g within the bounds. */
    fun stepLeaf(leafGrams: Double, up: Boolean): Double {
        val step = if (up) LEAF_UNIT else LEAF_UNIT.negate()
        return roundLeaf(leafGrams.toBigDecimal() + step)
    }

    /** The water stepper: to the next multiple of 5 ml up or down, within the bounds. */
    fun stepWater(waterMl: Int, up: Boolean): Int {
        val next = if (up) {
            Math.floorDiv(waterMl, WATER_UNIT) * WATER_UNIT + WATER_UNIT
        } else {
            Math.floorDiv(waterMl - 1, WATER_UNIT) * WATER_UNIT
        }
        return next.coerceIn(MIN_WATER_ML, MAX_WATER_ML)
    }

    /** Leaf per 100 ml, to 0,1 g half up: the ratio „1,6 g / 100 ml" (PRF-03 AC1). */
    fun per100Ml(leafGrams: Double, waterMl: Int): Double = (leafGrams.toBigDecimal() * HUNDRED)
        .divide(waterMl.toBigDecimal(), 1, RoundingMode.HALF_UP)
        .toDouble()

    private fun roundLeaf(grams: BigDecimal): Double =
        grams.setScale(1, RoundingMode.HALF_UP).toDouble().coerceIn(MIN_LEAF_GRAMS, MAX_LEAF_GRAMS)

    private const val EXACT_SCALE = 6
    private val HUNDRED = BigDecimal(100)
}

/** The volume a vessel brews (WARE-04): its usable volume, else its capacity. */
fun Vessel.brewMl(): Int = usableMl ?: capacityMl
