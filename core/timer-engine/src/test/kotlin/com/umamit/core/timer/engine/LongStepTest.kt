package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.BrewMethod
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.PlayPreAlert
import com.umamit.core.timer.engine.TimerEffect.StartAlert
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopForeground
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification
import org.junit.Test

/**
 * Long steps (TMR-18, 04 §1, §6.1): from v0.3.0 any step of 60 min or more,
 * and every step of a cold or ice brew (D-098, #122; until then 10 min). They
 * run without the foreground service and without a pre-alert, and show their
 * wall-clock end („Fertig um 14:30"). A hot step of 10–59 min is a short step.
 */
class LongStepTest {

    private fun coldBrew(vararg steps: PlannedStep) = EngineDriver().apply {
        send(TimerEvent.StartSession(SESSION_ID, steps.toList(), PRE_ALERT, longStepsOnly = true))
    }

    @Test
    fun `a hot step of 60 min or more and every step of a cold or ice brew is long`() {
        // (session, long)
        val cases = listOf(
            "9:59 infusion" to sessionWith(infusion(599)) to false,
            "10:00 infusion" to sessionWith(infusion(600)) to false,
            "59:59 infusion" to sessionWith(infusion(3_599)) to false,
            "60:00 infusion" to sessionWith(infusion(3_600)) to true,
            "3 h infusion" to sessionWith(infusion(3 * 3600)) to true,
            "1 min cold brew" to coldBrew(infusion(60)) to true,
            "5 s cold brew" to coldBrew(infusion(5)) to true,
            "24 h cold brew" to coldBrew(infusion(24 * 3600)) to true,
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
    fun `the planned-step rule the setup and the Schritte-Sheet ask is the same`() {
        // (seconds, cold or ice brew) to long
        val cases = mapOf(
            (599 to false) to false,
            (600 to false) to false,
            (3_599 to false) to false,
            (3_600 to false) to true,
            (86_400 to false) to true,
            (5 to true) to true,
            (600 to true) to true,
            (86_400 to true) to true,
        )
        cases.forEach { (case, long) ->
            val (seconds, coldOrIce) = case
            assertWithMessage("$seconds s, cold or ice: $coldOrIce")
                .that(BrewTimerEngine.isLongStep(infusion(seconds), longStepsOnly = coldOrIce))
                .isEqualTo(long)
        }
    }

    @Test
    fun `every active state needs the service except a demoted session and a running long step`() {
        val short = sessionWith(infusion(60))
        val long = sessionWith(infusion(3_600))
        assertThat(BrewTimerEngine.needsService(short.state)).isTrue()
        assertThat(BrewTimerEngine.needsService(long.state)).isTrue()
        short.send(TimerEvent.Start)
        long.send(TimerEvent.Start)
        assertThat(BrewTimerEngine.needsService(short.state)).isTrue()
        assertThat(BrewTimerEngine.needsService(long.state)).isFalse()
        long.send(TimerEvent.Pause)
        assertThat(BrewTimerEngine.needsService(long.state)).isTrue()
        long.clocks.advance(IDLE_DEMOTE_MS)
        long.send(TimerEvent.IdleDemote)
        assertThat(BrewTimerEngine.needsService(long.state)).isFalse()
        assertThat(BrewTimerEngine.needsService(TimerState.Idle)).isFalse()
    }

    @Test
    fun `cold and ice brew have long steps only`() {
        val methods = BrewMethod.entries.filter(BrewTimerEngine::longStepsOnly)

        assertThat(methods).containsExactly(BrewMethod.COLD_BREW, BrewMethod.ICE_BREW)
    }

    @Test
    fun `a long step starts without the service and without a pre-alert`() {
        val driver = sessionWith(infusion(3_600))

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
    fun `a 15-min hot step counts down with the service and its pre-alert, without a wall-clock end`() {
        val driver = sessionWith(infusion(900))

        val effects = driver.send(TimerEvent.Start)

        val running = driver.state as TimerState.Running
        assertThat(running.preAlertAtElapsedMs).isEqualTo(running.deadlineElapsedMs - 10_000)
        assertThat(effects)
            .containsExactly(Persist, runningAlarms(running.deadlineElapsedMs), StartForeground, UpdateNotification)
            .inOrder()
        assertThat(BrewTimerEngine.needsService(driver.state)).isTrue()
        assertThat(BrewTimerEngine.longStepEndWallMs(driver.state, driver.clocks.now())).isNull()
    }

    @Test
    fun `a 20-min hot step plays its automatic and its explicit pre-alert, a 60-min one has none`() {
        // (step, lead in s or none)
        val cases = listOf(
            infusion(1_200) to 10, // Automatisch: min(B = 10 s, 5 · ⌊1200 / 15⌋)
            infusion(1_200).withPreAlert(30) to 30,
            infusion(3_599).withPreAlert(5) to 5,
            infusion(3_600) to null,
            infusion(3_600).withPreAlert(30) to null,
        )
        cases.forEach { (step, lead) ->
            val driver = sessionWith(step).apply { send(TimerEvent.Start) }
            val deadline = driver.deadline
            val scheduled = BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs
            assertWithMessage("$step: scheduled").that(scheduled).isEqualTo(lead?.let { deadline - it * 1_000L })
            assertWithMessage("$step: lead").that(BrewTimerEngine.preAlertLeadSeconds(driver.state)).isEqualTo(lead)

            driver.advanceTo(deadline - (lead ?: 30) * 1_000L)
            val effects = driver.send(TimerEvent.PreAlertReached)

            assertWithMessage("$step: played").that(PlayPreAlert in effects).isEqualTo(lead != null)
        }
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
        val ready = sessionWith(infusion(3_600))
        val paused = sessionWith(infusion(3_600)).apply {
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
        val driver = sessionWith(infusion(4_800)).apply { send(TimerEvent.Start) }
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
        val driver = sessionWith(infusion(4_800), infusion(60)).apply { send(TimerEvent.Start) }

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

    // --- The update from v0.2.2 (D-098): a hot step of 10–59 min was a long step there.

    /**
     * What v0.2.2 stored for [driver]'s running step, which it ran as a long step (≥ 10 min): the same row
     * without a pre-alert, since a long step had none.
     */
    private fun asV022(driver: EngineDriver): TimerState {
        val snapshot = BrewTimerEngine.snapshot(driver.state)!!
        return BrewTimerEngine.restore(snapshot.copy(activeBrew = snapshot.activeBrew.copy(preAlertElapsedMs = null)))
    }

    @Test
    fun `a 15-min hot step v0_2_2 ran as a long step gets the service and its pre-alert back at recovery`() {
        val starts = mapOf(
            "brew" to sessionWith(infusion(900)),
            "quick timer" to EngineDriver().apply { send(TimerEvent.StartQuickTimer(900, globalPreAlertSeconds = 10)) },
        )
        starts.forEach { (name, started) ->
            started.send(TimerEvent.Start)
            val deadline = started.deadline
            val driver = EngineDriver(started.clocks, asV022(started))
            driver.clocks.advanceSeconds(300) // the update, 5 min into the step

            val effects = driver.send(TimerEvent.Recover)

            val running = driver.state as TimerState.Running
            assertWithMessage("$name: pre-alert").that(running.preAlertAtElapsedMs).isEqualTo(deadline - 10_000)
            assertWithMessage("$name: effects").that(effects)
                .containsExactly(Persist, runningAlarms(deadline), StartForeground, UpdateNotification)
                .inOrder()
            driver.advanceTo(deadline - 10_000)
            assertWithMessage("$name: played").that(driver.send(TimerEvent.PreAlertReached)).contains(PlayPreAlert)
            driver.advanceTo(deadline)
            assertWithMessage("$name: alert").that(driver.send(TimerEvent.DeadlineReached)).contains(StartAlert)
        }
    }

    @Test
    fun `a 15-min hot step recovered after its pre-alert time skips it, never a late tick, and still alerts`() {
        val started = sessionWith(infusion(900)).apply { send(TimerEvent.Start) }
        val deadline = started.deadline
        val driver = EngineDriver(started.clocks, asV022(started))
        driver.advanceTo(deadline - 5_000)

        val effects = driver.send(TimerEvent.Recover)

        assertThat((driver.state as TimerState.Running).preAlertAtElapsedMs).isNull()
        assertThat(effects)
            .containsExactly(Persist, deadlineAlarm(deadline), StartForeground, UpdateNotification)
            .inOrder()
        assertThat(driver.send(TimerEvent.PreAlertReached)).doesNotContain(PlayPreAlert)
        driver.advanceTo(deadline)
        assertThat(driver.send(TimerEvent.DeadlineReached)).contains(StartAlert)
    }

    @Test
    fun `a 15-min hot step v0_2_2 ran gets its pre-alert back also after a reboot`() {
        val started = sessionWith(infusion(900)).apply { send(TimerEvent.Start) }
        val driver = EngineDriver(started.clocks, asV022(started))
        driver.clocks.reboot(offMs = 60_000, upMs = 20_000) // 80 s after the start

        driver.send(TimerEvent.Recover)

        val running = driver.state as TimerState.Running
        assertThat(running.deadlineElapsedMs).isEqualTo(20_000 + 900_000 - 80_000)
        assertThat(running.preAlertAtElapsedMs).isEqualTo(running.deadlineElapsedMs - 10_000)
    }

    @Test
    fun `a 15-min hot step v0_2_2 left paused gets its pre-alert when it resumes`() {
        val paused = sessionWith(infusion(900)).apply {
            send(TimerEvent.Start)
            clocks.advanceSeconds(60)
            send(TimerEvent.Pause)
        }
        val driver = EngineDriver(paused.clocks, BrewTimerEngine.restore(BrewTimerEngine.snapshot(paused.state)))
        driver.send(TimerEvent.Recover)

        driver.send(TimerEvent.Resume)

        assertThat((driver.state as TimerState.Running).preAlertAtElapsedMs).isEqualTo(driver.deadline - 10_000)
    }

    @Test
    fun `a 60-min hot step and a cold brew v0_2_2 ran stay long steps at recovery`() {
        val long = listOf(
            sessionWith(infusion(3_600)),
            EngineDriver().apply {
                send(TimerEvent.StartSession(SESSION_ID, listOf(infusion(900)), PRE_ALERT, longStepsOnly = true))
            },
        )
        long.forEach { started ->
            started.send(TimerEvent.Start)
            val driver = EngineDriver(started.clocks, asV022(started))
            driver.clocks.advanceSeconds(300)

            val effects = driver.send(TimerEvent.Recover)

            assertWithMessage("${driver.state}").that((driver.state as TimerState.Running).preAlertAtElapsedMs)
                .isNull()
            assertWithMessage("${driver.state}").that(effects)
                .containsExactly(Persist, deadlineAlarm(started.deadline), UpdateNotification)
                .inOrder()
        }
    }
}
