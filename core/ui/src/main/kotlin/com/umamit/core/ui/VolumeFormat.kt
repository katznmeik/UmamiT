package com.umamit.core.ui

/**
 * The shared German volume format (INV-08, INS-01, INS-05, D-115 Amendment 2), for the tea detail and the
 * Statistik. Pure integer maths, so no float edge decides a half.
 */
object VolumeFormat {
    private const val TEN = 10L
    private const val TEN_HALF = 5L
    private const val ML_PER_LITER = 1_000L
    private const val TENTH_LITER = 100L
    private const val TENTH_LITER_HALF = 50L
    private const val TENTHS_PER_LITER = 10L

    /**
     * [ml] as „750 ml" or „12,4 l": below 1 l the nearest 10 ml (never 0 for a non-zero volume), from 1 l litres to
     * one decimal, half up. The litre threshold is decided on the rounded value, so 995 ml reads „1,0 l".
     */
    fun volume(ml: Long): String = parts(ml).let { "${it.first} ${it.second}" }

    /** [volume] split into the number („12,4") and the unit („l" / „ml"), for a numeral with a smaller unit. */
    fun parts(ml: Long): Pair<String, String> {
        val tens = (ml + TEN_HALF) / TEN * TEN
        return when {
            ml <= 0L -> "0" to "ml"

            tens < ML_PER_LITER -> maxOf(tens, TEN).toString() to "ml"

            else -> {
                val tenths = (ml + TENTH_LITER_HALF) / TENTH_LITER
                "${tenths / TENTHS_PER_LITER},${tenths % TENTHS_PER_LITER}" to "l"
            }
        }
    }
}
