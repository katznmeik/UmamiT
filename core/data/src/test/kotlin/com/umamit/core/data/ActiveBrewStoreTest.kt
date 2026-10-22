package com.umamit.core.data

import android.database.sqlite.SQLiteConstraintException
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * The active brew store (05 §3.12, 04 §7.1): every engine state, produced by
 * the engine itself, survives the way through `active_brew` and the session's
 * `session_step` rows unchanged (TMR-12, D-048).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActiveBrewStoreTest {
    private val data = TestDatabase()

    @After
    fun tearDown() = data.close()

    /** The stored snapshot equals the engine's, and restoring it gives the same state. */
    private suspend fun assertStored(name: String, state: TimerState) {
        val stored = data.activeBrew.read()
        assertWithMessage("$name: stored snapshot").that(stored).isEqualTo(BrewTimerEngine.snapshot(state))
        assertWithMessage("$name: restored state").that(BrewTimerEngine.restore(stored)).isEqualTo(state)
    }

    @Test
    fun `no active brew reads as none`() = runTest {
        assertThat(data.activeBrew.read()).isNull()
    }

    @Test
    fun `every state of a brew session round-trips through active_brew and the step rows`() = runTest {
        data.teas.saveTea(tea("t1"))
        val driver = TimerDriver(data)
        val clocks = driver.clocks
        suspend fun step(name: String, event: TimerEvent, advanceSeconds: Long = 0) {
            clocks.advanceSeconds(advanceSeconds)
            assertStored(name, driver.send(event))
        }

        step("Ready, first step", startEvent(rinse(10), infusion(60), infusion(20)))
        step("Ready after a skipped rinse", TimerEvent.SkipStep, advanceSeconds = 3)
        step("Running", TimerEvent.Start, advanceSeconds = 40)
        step("Paused", TimerEvent.Pause, advanceSeconds = 30)
        step("Running after a pause", TimerEvent.Resume, advanceSeconds = 12)
        step(
            "Running with a session pre-alert and an added step",
            TimerEvent.EditSteps(
                listOf(rinse(10), infusion(60), infusion(20), infusion(45, tempC = 65)),
                PreAlertChoice.Seconds(25),
            ),
        )
        step("Alerting", TimerEvent.DeadlineReached, advanceSeconds = 34)
        step("Alerting, alert played", TimerEvent.AlertCompleted, advanceSeconds = 2)
        step("Ready after a pour with overtime", TimerEvent.Poured, advanceSeconds = 5)
        step("Ready with the pre-alert off and a step removed", editPlan(driver, PreAlertChoice.Off, drop = 1))
        step("Running the last step", TimerEvent.Start, advanceSeconds = 60)
        step("Finished after ending a step early", TimerEvent.EndStepNow, advanceSeconds = 10)
        step("Ready for an added step", TimerEvent.AddStep, advanceSeconds = 120)
        step("Running the added step", TimerEvent.Start, advanceSeconds = 1)
        clocks.reboot(offMs = 20_000, upMs = 5_000)
        clocks.zone = ZoneId.of("Asia/Tokyo")
        step("Running after a reboot in another zone", TimerEvent.Recover)
        step("Alerting after the reboot", TimerEvent.DeadlineReached, advanceSeconds = 30)
        step("Finished", TimerEvent.Poured, advanceSeconds = 1)

        driver.send(TimerEvent.FinishSession)

        assertThat(data.activeBrew.read()).isNull()
    }

    @Test
    fun `values the engine sets later round-trip too - pre-alert, demoted, override`() = runTest {
        data.teas.saveTea(tea("t1"))
        val driver = TimerDriver(data)
        driver.send(startEvent(infusion(60), infusion(20)))
        val ready = driver.state as TimerState.Ready
        val running = driver.send(TimerEvent.Start) as TimerState.Running
        val handMade = mapOf(
            "Running with the pre-alert due" to running.copy(preAlertAtElapsedMs = running.deadlineElapsedMs - 10_000),
            "Running with the pre-alert played" to
                running.copy(preAlertAtElapsedMs = running.deadlineElapsedMs - 10_000, preAlertPlayed = true),
            "Ready, demoted" to ready.copy(session = ready.session.copy(demoted = true)),
            "Ready with a 0 s override" to
                ready.copy(
                    session = ready.session.copy(preAlert = ready.session.preAlert.copy(sessionOverrideSeconds = 0)),
                ),
        )

        handMade.forEach { (name, state) ->
            data.activeBrew.persist(BrewTimerEngine.snapshot(state))
            assertStored(name, state)
        }
    }

    @Test
    fun `persisting a snapshot alone brings the step rows to the engine's plan`() = runTest {
        data.teas.saveTea(tea("t1"))
        val driver = TimerDriver(data)
        driver.send(startEvent(rinse(10), infusion(60), infusion(20)))
        val clocks = driver.clocks
        // Transitions whose data effects were never executed: only their snapshot is written.
        var state = driver.state
        listOf(
            TimerEvent.SkipStep,
            TimerEvent.EditSteps(listOf(rinse(10), infusion(45, tempC = 75)), PreAlertChoice.Inherit),
            TimerEvent.Start,
        ).forEach { event ->
            clocks.advanceSeconds(5)
            state = BrewTimerEngine.reduce(state, event, clocks.now()).state
        }

        data.activeBrew.persist(BrewTimerEngine.snapshot(state))

        assertStored("Running after an unexecuted skip and edit", state)
    }

    @Test
    fun `every quick timer state round-trips without step rows`() = runTest {
        val driver = TimerDriver(data)
        val clocks = driver.clocks
        suspend fun step(name: String, event: TimerEvent, advanceSeconds: Long = 0) {
            clocks.advanceSeconds(advanceSeconds)
            assertStored(name, driver.send(event))
        }

        step("Quick timer, Ready", TimerEvent.StartQuickTimer(90, globalPreAlertSeconds = 15))
        step("Quick timer, Running", TimerEvent.Start, advanceSeconds = 2)
        step("Quick timer, Paused", TimerEvent.Pause, advanceSeconds = 20)
        step("Quick timer, Running again", TimerEvent.Resume, advanceSeconds = 7)
        step("Quick timer, Alerting", TimerEvent.DeadlineReached, advanceSeconds = 75)

        driver.send(TimerEvent.Poured)

        assertThat(data.activeBrew.read()).isNull()
    }

    @Test
    fun `a failed write leaves the stored active brew unchanged`() = runTest {
        data.teas.saveTea(tea("t1"))
        val driver = TimerDriver(data)
        val ready = driver.send(startEvent(infusion(60), infusion(20)))
        val before = data.activeBrew.read()
        // A snapshot of a session without a brew_session row breaks the foreign key.
        val orphan = BrewTimerEngine.snapshot(ready)!!.let {
            it.copy(activeBrew = it.activeBrew.copy(sessionId = "no-such-session"))
        }

        val failure = runCatching { data.activeBrew.persist(orphan) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SQLiteConstraintException::class.java)

        assertThat(data.activeBrew.read()).isEqualTo(before)
        assertThat(data.sessions.getSession(SESSION_ID)!!.steps).isEqualTo(before!!.steps)
    }

    @Test
    fun `clear removes the active brew but keeps the session`() = runTest {
        data.teas.saveTea(tea("t1"))
        TimerDriver(data).send(startEvent(infusion(60)))

        data.activeBrew.clear()

        assertThat(data.activeBrew.read()).isNull()
        assertThat(data.sessions.getSession(SESSION_ID)).isNotNull()
    }

    /** The Schritte-Sheet's request: the plan without its last [drop] pending steps and [choice]. */
    private fun editPlan(driver: TimerDriver, choice: PreAlertChoice, drop: Int): TimerEvent.EditSteps {
        val steps = (driver.state as TimerState.Active).session.steps.map { it.planned }
        return TimerEvent.EditSteps(steps.dropLast(drop), choice)
    }
}
