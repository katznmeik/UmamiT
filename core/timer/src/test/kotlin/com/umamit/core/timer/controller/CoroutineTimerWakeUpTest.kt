package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The production in-process wake-up (04 §4, §6.1) on virtual time; Robolectric for `android.util.Log`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoroutineTimerWakeUpTest {

    @Test
    fun `fires once after its delay`() = runTest {
        val wakeUp = CoroutineTimerWakeUp(backgroundScope)
        var fired = 0

        wakeUp.arm(1_000) { fired++ }
        advanceTimeBy(999)
        runCurrent()
        assertThat(fired).isEqualTo(0)
        advanceTimeBy(1)
        runCurrent()
        advanceTimeBy(10_000)

        assertThat(fired).isEqualTo(1)
    }

    @Test
    fun `arming again replaces the armed wake-up, and cancel disarms it`() = runTest {
        val wakeUp = CoroutineTimerWakeUp(backgroundScope)
        val fired = mutableListOf<String>()

        wakeUp.arm(1_000) { fired += "first" }
        wakeUp.arm(2_000) { fired += "second" }
        advanceTimeBy(5_000)
        wakeUp.arm(1_000) { fired += "cancelled" }
        wakeUp.cancel()
        advanceTimeBy(5_000)

        assertThat(fired).containsExactly("second")
    }

    @Test
    fun `a callback that re-arms or cancels from inside is not cut short`() = runTest {
        val wakeUp = CoroutineTimerWakeUp(backgroundScope)
        val done = mutableListOf<String>()

        wakeUp.arm(1_000) {
            // The controller re-arms after every event of the turn the wake-up started.
            wakeUp.arm(500) { done += "next" }
            delay(100) // e.g. the transition's database write
            wakeUp.cancel()
            wakeUp.arm(500) { done += "rearmed" }
            delay(100)
            done += "first"
        }
        advanceTimeBy(10_000)

        assertThat(done).containsExactly("first", "rearmed").inOrder()
    }

    @Test
    fun `a callback that throws does not stop later wake-ups`() = runTest {
        val wakeUp = CoroutineTimerWakeUp(backgroundScope)
        var fired = 0

        wakeUp.arm(100) { error("boom (test)") }
        advanceTimeBy(1_000)
        wakeUp.arm(100) { fired++ }
        advanceTimeBy(1_000)

        assertThat(fired).isEqualTo(1)
    }
}
