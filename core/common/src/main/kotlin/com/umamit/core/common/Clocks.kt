package com.umamit.core.common

import java.time.ZoneId

/**
 * Monotonic clock for all countdown arithmetic (04 §2, §5): on Android
 * `SystemClock.elapsedRealtime()`, which keeps counting in deep sleep and is
 * not affected by changes of the wall clock or the time zone (E1).
 *
 * Injected into callers so tests can drive virtual time (04 §2.6).
 */
fun interface MonotonicClock {
    /** Milliseconds since an arbitrary fixed point (boot on Android). */
    fun elapsedMs(): Long
}

/**
 * Wall clock and time zone for journal timestamps, notification times,
 * reboot recovery and local-date rules such as the midnight timeout (04 §5).
 * Never used for countdown arithmetic.
 */
interface WallClock {
    /** Milliseconds since the Unix epoch. */
    fun nowMs(): Long

    /** The device's current time zone. */
    fun zone(): ZoneId
}

/** The device's wall clock and time zone (`System.currentTimeMillis()`, the default zone). */
object SystemWallClock : WallClock {
    override fun nowMs(): Long = System.currentTimeMillis()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/**
 * Both clocks read at one moment: the `now` of the timer engine's reducer
 * (04 §4). [elapsedMs] drives all durations; [wallMs] and [zone] only label
 * them.
 */
data class ClockReading(val elapsedMs: Long, val wallMs: Long, val zone: ZoneId)

/** Reads [monotonic] and [wall] into one [ClockReading]. */
fun readClocks(monotonic: MonotonicClock, wall: WallClock): ClockReading =
    ClockReading(elapsedMs = monotonic.elapsedMs(), wallMs = wall.nowMs(), zone = wall.zone())
