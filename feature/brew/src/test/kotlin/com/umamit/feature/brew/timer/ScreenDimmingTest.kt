package com.umamit.feature.brew.timer

import com.google.common.truth.Truth.assertThat
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Test

/**
 * TMR-17, SET-25: a RUNNING step dims after 10 s untouched and is bright again
 * at the pre-alert, the alert, any touch, and in READY/PAUSED/FINISHED. The
 * clock is a plain value the test moves.
 */
class ScreenDimmingTest {
    private var clock = NOW
    private var dimming = ScreenDimming(lastTouchMs = NOW)

    private fun advance(ms: Long) {
        clock += ms
    }

    @Test
    fun `a running step dims after 10 s untouched`() {
        val running = TimerStates.running(remainingMs = 48_000)

        advance(9_999)
        assertThat(dimming.isDimmed(running, enabled = true, nowElapsedMs = clock)).isFalse()
        advance(1)
        assertThat(dimming.isDimmed(running, enabled = true, nowElapsedMs = clock)).isTrue()
    }

    @Test
    fun `a touch brightens the screen and restarts the 10 s`() {
        val running = TimerStates.running(remainingMs = 48_000)
        advance(15_000)
        assertThat(dimming.isDimmed(running, enabled = true, nowElapsedMs = clock)).isTrue()

        dimming = dimming.touched(clock)

        assertThat(dimming.isDimmed(running, enabled = true, nowElapsedMs = clock)).isFalse()
        advance(10_000)
        assertThat(dimming.isDimmed(running, enabled = true, nowElapsedMs = clock)).isTrue()
    }

    @Test
    fun `the pre-alert brightens the screen until the step ends`() {
        val running = TimerStates.running(remainingMs = 48_000)
        advance(37_999)
        assertThat(dimming.isDimmed(running, enabled = true, nowElapsedMs = clock)).isTrue()
        advance(1)
        assertThat(dimming.isDimmed(running, enabled = true, nowElapsedMs = clock)).isFalse()
        advance(9_000)
        assertThat(dimming.isDimmed(running, enabled = true, nowElapsedMs = clock)).isFalse()
    }

    @Test
    fun `the alert and READY, PAUSED, FINISHED are never dimmed`() {
        advance(60_000)
        listOf(TimerStates.alerting(), TimerStates.ready(), TimerStates.paused(), TimerStates.finished()).forEach {
            assertThat(dimming.isDimmed(it, enabled = true, nowElapsedMs = clock)).isFalse()
        }
    }

    @Test
    fun `Beim Ziehen abdunkeln off never dims`() {
        advance(20_000)
        assertThat(dimming.isDimmed(TimerStates.running(), enabled = false, nowElapsedMs = clock)).isFalse()
    }

    @Test
    fun `the next check is due when the 10 s run out, then at the pre-alert`() {
        val running = TimerStates.running(remainingMs = 48_000)

        assertThat(dimming.nextChangeDelayMs(running, enabled = true, nowElapsedMs = clock)).isEqualTo(10_000)
        advance(12_000)
        // Dimmed now; it brightens at the pre-alert, 38 s after the start.
        assertThat(dimming.nextChangeDelayMs(running, enabled = true, nowElapsedMs = clock)).isEqualTo(26_000)
        advance(26_000)
        assertThat(dimming.nextChangeDelayMs(running, enabled = true, nowElapsedMs = clock)).isNull()
        assertThat(dimming.nextChangeDelayMs(TimerStates.paused(), enabled = true, nowElapsedMs = clock)).isNull()
    }
}
