package com.umamit.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The 2-requests-per-second limit (CLAUDE.md rule 7) on a virtual clock. */
class RequestRateLimiterTest {
    private var nowNanos = 0L
    private val sleeps = mutableListOf<Long>()
    private val limiter = RequestRateLimiter(
        minIntervalNanos = HALF_SECOND,
        nanoTime = { nowNanos },
        sleepNanos = {
            sleeps += it
            nowNanos += it
        },
    )

    @Test
    fun firstRequestStartsAtOnce() {
        limiter.acquire()

        assertThat(sleeps).isEmpty()
    }

    @Test
    fun backToBackRequestsStartHalfASecondApart() {
        repeat(5) { limiter.acquire() }

        assertThat(sleeps).containsExactly(HALF_SECOND, HALF_SECOND, HALF_SECOND, HALF_SECOND)
        assertThat(nowNanos).isEqualTo(4 * HALF_SECOND)
    }

    @Test
    fun aRequestAfterAShortPauseWaitsOnlyForTheRest() {
        limiter.acquire()
        nowNanos += 300_000_000L
        limiter.acquire()
        nowNanos += 700_000_000L
        limiter.acquire()

        assertThat(sleeps).containsExactly(200_000_000L)
    }

    private companion object {
        const val HALF_SECOND = 500_000_000L
    }
}
