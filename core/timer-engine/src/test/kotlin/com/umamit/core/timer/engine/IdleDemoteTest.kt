package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.ScheduleAlarms
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopForeground
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification
import org.junit.Test

/**
 * Idle demote (TMR-14, 04 §3.2, §6.1) and the foreground-service contract
 * around it (S5 of the #28 review, D-053): after 30 min without interaction
 * in a waiting state the service stops and the notification turns into a
 * regular one; any user event brings the service back.
 */
class IdleDemoteTest {

    private val thirtyMinutes = 30 * 60_000L

    private fun ready() = sessionWith(infusion(60), infusion(20))

    private fun paused() = ready().apply {
        send(TimerEvent.Start)
        clocks.advanceSeconds(10)
        send(TimerEvent.Pause)
    }

    private fun finished() = sessionWith(infusion(60)).apply { send(TimerEvent.SkipStep) }

    private fun demoted(driver: EngineDriver) = driver.apply {
        clocks.advance(thirtyMinutes)
        send(TimerEvent.IdleDemote)
    }

    @Test
    fun `idle demote is due 30 min after the last interaction in Ready, Paused and Finished`() {
        listOf("Ready" to ready(), "Paused" to paused(), "Finished" to finished()).forEach { (name, driver) ->
            assertWithMessage(name).that(BrewTimerEngine.dueTimes(driver.state).idleDemoteAtWallMs)
                .isEqualTo(driver.clocks.wall + thirtyMinutes)
        }
    }

    @Test
    fun `a running or alerting step, the quick timer and Idle have no idle demote`() {
        val running = ready().apply { send(TimerEvent.Start) }
        val alerting = ready().apply {
            send(TimerEvent.Start)
            clocks.advanceSeconds(60)
            send(TimerEvent.DeadlineReached)
        }
        val quick = EngineDriver().apply { send(TimerEvent.StartQuickTimer(60, globalPreAlertSeconds = 10)) }
        listOf(running, alerting, quick, EngineDriver()).forEach { driver ->
            assertWithMessage("${driver.state}").that(BrewTimerEngine.dueTimes(driver.state).idleDemoteAtWallMs)
                .isNull()
        }
    }

    @Test
    fun `idleDemote stops the service and demotes the notification without another change`() {
        listOf("Ready" to ready(), "Paused" to paused(), "Finished" to finished()).forEach { (name, driver) ->
            val before = driver.state as TimerState.Active
            driver.clocks.advance(thirtyMinutes - 1)
            assertWithMessage(name).that(driver.send(TimerEvent.IdleDemote)).isEmpty()
            driver.clocks.advance(1)

            val effects = driver.send(TimerEvent.IdleDemote)

            val after = driver.state as TimerState.Active
            assertWithMessage(name).that(driver.outcome).isEqualTo(TransitionOutcome.Accepted)
            assertWithMessage(name).that(after.session.demoted).isTrue()
            assertWithMessage(name).that(after.session.copy(demoted = false)).isEqualTo(before.session)
            assertWithMessage(name).that(after.phase).isEqualTo(before.phase)
            assertWithMessage(name).that(BrewTimerEngine.dueTimes(after).idleDemoteAtWallMs).isNull()
            assertWithMessage(name).that(effects).containsExactly(
                Persist,
                ScheduleAlarms(BrewTimerEngine.dueTimes(after)),
                StopForeground,
                UpdateNotification,
            ).inOrder()
            assertWithMessage(name).that(driver.send(TimerEvent.IdleDemote)).isEmpty()
        }
    }

    @Test
    fun `every interaction moves the idle demote`() {
        val driver = ready()
        driver.clocks.advance(20 * 60_000L)

        driver.send(TimerEvent.AddStep)

        assertThat(BrewTimerEngine.dueTimes(driver.state).idleDemoteAtWallMs)
            .isEqualTo(START_WALL_MS + 20 * 60_000L + thirtyMinutes)
        driver.clocks.advance(thirtyMinutes - 1)
        assertThat(driver.send(TimerEvent.IdleDemote)).isEmpty()
    }

    @Test
    fun `after the overtime timeout the idle time counts from entering Ready`() {
        val driver = ready().apply {
            send(TimerEvent.Start)
            clocks.advanceSeconds(60)
            send(TimerEvent.DeadlineReached)
            clocks.advance(10 * 60_000L)
            send(TimerEvent.OvertimeTimeout)
        }

        assertThat(BrewTimerEngine.dueTimes(driver.state).idleDemoteAtWallMs)
            .isEqualTo(START_WALL_MS + 11 * 60_000L + thirtyMinutes)
    }

    @Test
    fun `a user event on a demoted session brings the service back`() {
        // (name, demoted session, user event, phase after)
        val cases = listOf(
            Triple("Ready + Start", demoted(ready()), TimerEvent.Start),
            Triple("Ready + SkipStep", demoted(ready()), TimerEvent.SkipStep),
            Triple("Ready + AddStep", demoted(ready()), TimerEvent.AddStep),
            Triple("Paused + Resume", demoted(paused()), TimerEvent.Resume),
            Triple("Paused + EndStepNow", demoted(paused()), TimerEvent.EndStepNow),
            Triple(
                "Paused + EditSteps",
                demoted(paused()),
                TimerEvent.EditSteps(listOf(infusion(60)), PreAlertChoice.Off),
            ),
            Triple("Finished + AddStep", demoted(finished()), TimerEvent.AddStep),
        )
        cases.forEach { (name, driver, event) ->
            val effects = driver.send(event)

            assertWithMessage(name).that(driver.outcome).isEqualTo(TransitionOutcome.Accepted)
            assertWithMessage(name).that((driver.state as TimerState.Active).session.demoted).isFalse()
            assertWithMessage(name).that(effects.count { it == StartForeground }).isEqualTo(1)
            assertWithMessage(name).that(effects).doesNotContain(StopForeground)
            assertWithMessage(name).that(
                BrewTimerEngine.dueTimes(driver.state).idleDemoteAtWallMs?.let {
                    it >
                        driver.clocks.wall
                },
            )
                .isNotEqualTo(false)
        }
    }

    @Test
    fun `ending a demoted session stops the service and does not start it`() {
        listOf(TimerEvent.FinishSession, TimerEvent.Discard).forEach { event ->
            val driver = demoted(ready())

            val effects = driver.send(event)

            assertWithMessage("$event").that(driver.state).isEqualTo(TimerState.Idle)
            assertWithMessage("$event").that(effects).contains(StopForeground)
            assertWithMessage("$event").that(effects).doesNotContain(StartForeground)
        }
    }

    @Test
    fun `a time-based event does not bring a demoted session back`() {
        val driver = demoted(ready())

        driver.send(TimerEvent.Recover)

        assertThat((driver.state as TimerState.Ready).session.demoted).isTrue()
        assertThat(driver.effects).doesNotContain(StartForeground)
    }
}
