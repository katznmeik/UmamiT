package com.umamit.feature.brew.timer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The timer's second ticker after deep sleep (A-01, D-117 Q19): its `delay` runs on uptime, which a sleeping phone
 * does not count, so a start after a stop reads the clock again in `ON_START`, and the first frame after unlock shows
 * the second of now, never one from before the sleep.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerNowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Elapsed time the phone slept: the clock (`elapsedRealtime`) moves, the test's uptime does not. */
    private var slept = 0L
    private var start = 0L
    private val clock: () -> Long = { NOW + compose.mainClock.currentTime - start + slept }
    private var drawn = 0L

    private fun show() {
        val state = TimerStates.running(remainingMs = 47_400)
        start = compose.mainClock.currentTime
        compose.setContent {
            val now by rememberTimerNow(state, clock)
            SideEffect { drawn = now }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a start after a stop reads the clock before its first frame`() {
        show()
        assertThat(drawn).isEqualTo(NOW)
        compose.mainClock.autoAdvance = false

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        slept = 10_000
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeByFrame()

        // The ticks' next read (at 0,4 s of uptime) has not come yet: the read is the start's own.
        assertThat(drawn).isAtLeast(NOW + 10_000)
    }

    @Test
    fun `without a stop the clock is read only at the second boundaries`() {
        show()
        compose.mainClock.autoAdvance = false
        slept = 10_000

        compose.mainClock.advanceTimeByFrame()

        assertThat(drawn).isEqualTo(NOW)
    }
}
