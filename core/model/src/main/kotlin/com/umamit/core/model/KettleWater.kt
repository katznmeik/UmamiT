package com.umamit.core.model

/**
 * What the water of a step is (BRW-10): heated, or cold for a cold or ice brew (≤ 10 °C). In
 * `core:model` so the setup, the timer screen and the notifications share one rule (D-068).
 */
enum class KettleWater {
    /** „Wasserkocher auf 65 °C". */
    HOT,

    /** „Kaltes Wasser" (cold brew). */
    COLD,

    /** „Eiswürfel" (ice brew). */
    ICE,

    ;

    companion object {
        /** At most this temperature the hint names cold water or ice instead of the kettle (BRW-10). */
        const val COLD_MAX_CELSIUS = 10

        /** The water at [temperatureCelsius], with ice for an [iceBrew]. */
        fun of(temperatureCelsius: Int, iceBrew: Boolean): KettleWater = when {
            temperatureCelsius > COLD_MAX_CELSIUS -> HOT
            iceBrew -> ICE
            else -> COLD
        }
    }
}
