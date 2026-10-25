package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Display values (04 §5): remaining time rounded up, overtime rounded down, in whole seconds. */
class DisplayTest {

    @Test
    fun `idle shows nothing`() {
        assertThat(EngineDriver().display()).isNull()
    }

    @Test
    fun `ready shows the planned duration of the step`() {
        val driver = sessionWith(infusion(90))

        assertThat(driver.display()).isEqualTo(TimerDisplay(remainingSeconds = 90, overtimeSeconds = 0))
    }

    @Test
    fun `running remaining time rounds up to whole seconds`() {
        val cases = listOf(
            0L to 60L,
            1L to 60L,
            999L to 60L,
            1_000L to 59L,
            58_999L to 2L,
            59_000L to 1L,
            59_001L to 1L,
            59_999L to 1L,
            60_000L to 0L,
        )
        cases.forEach { (elapsedMs, expected) ->
            val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
            driver.clocks.advance(elapsedMs)

            assertWithMessage("after $elapsedMs ms").that(driver.display())
                .isEqualTo(TimerDisplay(remainingSeconds = expected, overtimeSeconds = 0))
        }
    }

    @Test
    fun `a running step past its deadline shows zero until the deadline event arrives`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(62)

        assertThat(driver.display()).isEqualTo(TimerDisplay(remainingSeconds = 0, overtimeSeconds = 0))
    }

    @Test
    fun `remaining time rounds up around the hour boundary (h mm ss)`() {
        // A two-hour cold brew: „1:00:00" stays on screen until exactly 59:59 are left.
        val cases = listOf(
            0L to 7_200L,
            3_599_999L to 3_601L,
            3_600_000L to 3_600L,
            3_600_001L to 3_600L,
            3_601_000L to 3_599L,
            7_199_000L to 1L,
        )
        cases.forEach { (elapsedMs, expected) ->
            val driver = sessionWith(infusion(7_200)).apply { send(TimerEvent.Start) }
            driver.clocks.advance(elapsedMs)

            assertWithMessage("after $elapsedMs ms").that(driver.display()!!.remainingSeconds).isEqualTo(expected)
        }
    }

    @Test
    fun `paused shows the frozen remaining time rounded up`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advance(25_400)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(300)

        assertThat(driver.display()).isEqualTo(TimerDisplay(remainingSeconds = 35, overtimeSeconds = 0))
    }

    @Test
    fun `alerting overtime rounds down to whole seconds`() {
        val cases = listOf(
            0L to 0L,
            999L to 0L,
            1_000L to 1L,
            7_999L to 7L,
            3_599_999L to 3_599L,
            3_600_000L to 3_600L,
        )
        cases.forEach { (overtimeMs, expected) ->
            val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
            driver.clocks.advanceSeconds(60)
            driver.send(TimerEvent.DeadlineReached)
            driver.clocks.advance(overtimeMs)

            assertWithMessage("after $overtimeMs ms overtime").that(driver.display())
                .isEqualTo(TimerDisplay(remainingSeconds = 0, overtimeSeconds = expected))
        }
    }

    @Test
    fun `finished shows zero`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.SkipStep) }

        assertThat(driver.display()).isEqualTo(TimerDisplay(remainingSeconds = 0, overtimeSeconds = 0))
    }
}
