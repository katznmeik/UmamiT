package com.umamit.core.testing

import com.umamit.core.common.ClockReading
import com.umamit.core.common.MonotonicClock
import com.umamit.core.common.WallClock
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A virtual monotonic clock with a boot count and a virtual wall clock with a
 * zone (04 §2.6, §5). [advanceBy] moves both together, as real time does;
 * setting [wall] or [zone] (the user changes the clock, E1) or [reboot] (E2)
 * changes one of them.
 */
class FakeClock(
    var elapsed: Long = 5_000_000L,
    var wall: Long = DEFAULT_WALL_MS,
    var zone: ZoneId = BERLIN,
    var boot: Int = 1,
) : MonotonicClock,
    WallClock {
    override fun elapsedMs(): Long = elapsed

    override fun bootCount(): Int = boot

    override fun nowMs(): Long = wall

    override fun zone(): ZoneId = zone

    /** Both clocks at this moment. */
    fun reading(): ClockReading = ClockReading(elapsed, wall, zone, boot)

    /** Real time passes: both clocks move forward by [ms]. */
    fun advanceBy(ms: Long) {
        require(ms >= 0) { "time only moves forward, was $ms" }
        elapsed += ms
        wall += ms
    }

    /** The phone was off for [offMs] and has been up for [upMs] since: a new boot with a new elapsed clock. */
    fun reboot(offMs: Long, upMs: Long) {
        boot += 1
        wall += offMs + upMs
        elapsed = upMs
    }

    companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

        /** 2026-10-22 10:00 in Berlin. */
        val DEFAULT_WALL_MS: Long = ZonedDateTime.of(2026, 10, 22, 10, 0, 0, 0, BERLIN).toInstant().toEpochMilli()
    }
}
