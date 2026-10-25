package com.umamit.core.common

import kotlin.math.floor

/**
 * Snaps a temperature in °C to the nearest multiple of 5, half up
 * (GEN-12, 05 §7): `snap5(x) = 5 × floor(x / 5 + 0.5)`, so 52,5 → 55.
 *
 * Every stored and shown temperature is a multiple of 5; imported or legacy
 * values pass through this function at the repository boundary.
 */
fun snap5(celsius: Double): Int = SNAP_STEP * floor(celsius / SNAP_STEP + HALF).toInt()

/** [snap5] for whole degrees. */
fun snap5(celsius: Int): Int = snap5(celsius.toDouble())

private const val SNAP_STEP = 5
private const val HALF = 0.5
