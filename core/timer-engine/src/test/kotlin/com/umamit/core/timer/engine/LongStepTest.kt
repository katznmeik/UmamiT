package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.BrewMethod
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.StartAlert
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopForeground
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification
import org.junit.Test

/**
 * Long steps (TMR-18, 04 §1, §6.1): ≥ 10 min, or any step of a cold or ice
 * brew. They run without the foreground service and without a pre-alert, and
 * show their wall-clock end („Fertig um 14:30").
 */
class LongStepTest {

    private fun coldBrew(vararg steps: PlannedStep) = EngineDriver().apply {
        send(TimerEvent.StartSession(SESSION_ID, steps.toList(), PRE_ALERT, longStepsOnly = true))
    }

    @Test
    fun `a step of 10 min or more and every step of a cold or ice brew is long`() {
        // (session, long)
        val cases = listOf(
            "9:59 infusion" to sessionWith(infusion(599)) to false,
            "10:00 infusion" to sessionWith(infusion(600)) to true,
            "3 h infusion" to sessionWith(infusion(3 * 3600)) to true,
            "1 min cold brew" to coldBrew(infusion(60)) to true,
        )
        cases.forEach { (named, long) ->
            val (name, driver) = named
            assertWithMessage(name).that(BrewTimerEngine.isLongStep(driver.state)).isEqualTo(long)
        }
        assertThat(BrewTimerEngine.isLongStep(TimerState.Idle)).isFalse()
        assertThat(BrewTimerEngine.isLongStep(sessionWith(infusion(60)).apply { send(TimerEvent.SkipStep) }.state))
            .isFalse()
    }

    @Test
    fun `cold and ice brew have long steps only`() {
        val methods = BrewMethod.entries.filter(BrewTimerEngine::longStepsOnly)

        assertThat(methods).containsExactly(BrewMethod.COLD_BREW, BrewMethod.ICE_BREW)
    }

    @Test
    fun `a long step starts without the service and without a pre-alert`() {
        val driver = sessionWith(infusion(600))

        val effects = driver.send(TimerEvent.Start)

        val running = driver.state as TimerState.Running
        assertThat(running.preAlertAtElapsedMs).isNull()
        assertThat(effects)
            .containsExactly(Persist, deadlineAlarm(running.deadlineElapsedMs), StopForeground, UpdateNotification)
            .inOrder()
    }

    @Test
    fun `a short step starts with the service ensured`() {
        val driver = sessionWith(infusion(599))

        val effects = driver.send(TimerEvent.Start)

        assertThat(effects).contains(StartForeground)
        assertThat(effects).doesNotContain(StopForeground)
    }

    @Test
    fun `a long step shows its wall-clock end, also after the wall clock was changed`() {
        val driver = sessionWith(infusion(3 * 3600)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(600)

        assertThat(BrewTimerEngine.longStepEndWallMs(driver.state, driver.clocks.now()))
            .isEqualTo(START_WALL_MS + 3 * 3_600_000L)

        driver.clocks.wall -= 3_600_000L
        assertThat(BrewTimerEngine.longStepEndWallMs(driver.state, driver.clocks.now()))
            .isEqualTo(START_WALL_MS + 2 * 3_600_000L)
    }

    @Test
    fun `only a running long step has a wall-clock end`() {
        val short = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        val ready = sessionWith(infusion(600))
        val paused = sessionWith(infusion(600)).apply {
            send(TimerEvent.Start)
            send(TimerEvent.Pause)
        }
        listOf(short, ready, paused, EngineDriver()).forEach { driver ->
            assertWithMessage(
                "${driver.state}",
            ).that(BrewTimerEngine.longStepEndWallMs(driver.state, driver.clocks.now()))
                .isNull()
        }
    }

    @Test
    fun `pausing a long step brings the service back, resuming stops it again`() {
        val driver = sessionWith(infusion(1200)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(60)

        assertThat(driver.send(TimerEvent.Pause)).contains(StartForeground)
        assertThat(driver.send(TimerEvent.Resume)).contains(StopForeground)
        assertThat(driver.send(TimerEvent.EndStepNow)).contains(StartForeground)
    }

    @Test
    fun `the deadline of a long step restarts the service before the alert`() {
        val driver = coldBrew(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(60)

        val effects = driver.send(TimerEvent.DeadlineReached)

        assertThat(effects.indexOf(StartForeground)).isLessThan(effects.indexOf(StartAlert))
    }

    @Test
    fun `editing the plan during a long step does not start the service`() {
        val driver = sessionWith(infusion(1200), infusion(60)).apply { send(TimerEvent.Start) }

        val effects = driver.send(TimerEvent.AddStep)

        assertThat(effects).doesNotContain(StartForeground)
    }

    @Test
    fun `a cold brew session survives the persistence round trip`() {
        val state = coldBrew(infusion(60)).apply { send(TimerEvent.Start) }.state

        val snapshot = BrewTimerEngine.snapshot(state)!!

        assertThat(snapshot.longStepsOnly).isTrue()
        assertThat(BrewTimerEngine.restore(snapshot)).isEqualTo(state)
    }
}
