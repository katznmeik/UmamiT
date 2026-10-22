package com.umamit.core.data

/** Wall-clock time for `created_at` / `updated_at` (epoch ms UTC, 05 §1.4). */
fun interface TimeSource {
    fun nowMillis(): Long
}

/**
 * Snaps a temperature to a multiple of 5 °C, half up (GEN-12, 05 §7: 52,5 → 55).
 * TODO(#28): switch to core:common's `snap5` once it lands.
 */
internal fun snapTo5(celsius: Double): Int = SNAP_STEP * kotlin.math.floor(celsius / SNAP_STEP + HALF).toInt()

internal fun snapTo5(celsius: Int): Int = snapTo5(celsius.toDouble())

private const val SNAP_STEP = 5
private const val HALF = 0.5
