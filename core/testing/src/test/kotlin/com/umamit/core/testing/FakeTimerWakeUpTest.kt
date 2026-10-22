package com.umamit.core.testing

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The fake wake-up fires like the process's own timer: at its exact time, as often as it is re-armed. */
class FakeTimerWakeUpTest {
    private val clock = FakeClock(elapsed = 1_000, wall = 50_000)
    private val wakeUp = FakeTimerWakeUp(clock)

    @Test
    fun `fires at exactly its due time and leaves the clock at the end of the advance`() = runTest {
        val firedAt = mutableListOf<Pair<Long, Long>>()
        wakeUp.arm(300) { firedAt += clock.elapsed to clock.wall }

        wakeUp.advanceBy(1_000)

        assertThat(firedAt).containsExactly(1_300L to 50_300L)
        assertThat(clock.elapsed).isEqualTo(2_000)
        assertThat(wakeUp.dueAtElapsedMs).isNull()
    }

    @Test
    fun `a wake-up re-armed from its callback fires again within the same advance`() = runTest {
        val firedAt = mutableListOf<Long>()
        lateinit var tick: suspend () -> Unit
        tick = {
            firedAt += clock.elapsed
            wakeUp.arm(400, tick)
        }
        wakeUp.arm(400, tick)

        wakeUp.advanceBy(1_000)

        assertThat(firedAt).containsExactly(1_400L, 1_800L).inOrder()
        assertThat(wakeUp.dueAtElapsedMs).isEqualTo(2_200)
    }

    @Test
    fun `a cancelled or not yet due wake-up does not fire`() = runTest {
        var fired = 0
        wakeUp.arm(500) { fired++ }
        wakeUp.cancel()
        wakeUp.advanceBy(1_000)
        wakeUp.arm(2_000) { fired++ }

        wakeUp.advanceBy(1_999)

        assertThat(fired).isEqualTo(0)
    }
}
