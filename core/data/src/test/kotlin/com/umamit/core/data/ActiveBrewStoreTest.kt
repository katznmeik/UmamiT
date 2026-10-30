package com.umamit.core.data

import android.database.sqlite.SQLiteConstraintException
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerSnapshot
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
 * `session_step` rows unchanged (TMR-12, D-048). From umamit.db v4 (D-098) the
 * table holds one row per slot, brew and quick: each slot is written, read and
 * cleared on its own, both at once, losslessly.
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

    /** [slot]'s stored snapshot equals the engine's (none for Idle), and restoring it gives the same state. */
    private suspend fun assertStored(slot: TimerSlot, name: String, state: TimerState) {
        val stored = data.activeBrew.read(slot)
        assertWithMessage("$name: stored snapshot in $slot").that(stored).isEqualTo(BrewTimerEngine.snapshot(state))
        assertWithMessage("$name: restored state").that(BrewTimerEngine.restore(stored)).isEqualTo(state)
    }

    /** This snapshot with its alert ringing since the given instant, or held (both null). */
    private fun TimerSnapshot.ringingSince(elapsedMs: Long?, wallMs: Long?) = copy(
        activeBrew = activeBrew.copy(alertRingingSinceElapsedMs = elapsedMs, alertRingingSinceWallMs = wallMs),
    )

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
            "Running with the steps' own pre-alerts, the running one's changed, and an added step",
            TimerEvent.EditSteps(
                listOf(
                    rinse(10),
                    infusion(60, preAlert = 25),
                    infusion(20, preAlert = 0),
                    infusion(45, tempC = 65, preAlert = 15),
                ),
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
    fun `a cold brew reads back with long steps only, from the session's method (D-053)`() = runTest {
        data.teas.saveTea(tea("t1"))
        val coldBrew = snapshotOf().copy(method = BrewMethod.COLD_BREW)
        val driver = TimerDriver(data, start = { id, steps, preAlert ->
            sessionStart(id, steps, preAlert, TestClocks(), snapshot = coldBrew)
        })
        driver.send(
            TimerEvent.StartSession(
                SESSION_ID,
                listOf(infusion(60)),
                PreAlertSettings.inherited(recipeSeconds = null, globalSeconds = 10),
                longStepsOnly = true,
            ),
        )

        val running = driver.send(TimerEvent.Start)

        assertThat(data.activeBrew.read()!!.longStepsOnly).isTrue()
        assertStored("Running cold brew", running)
    }

    @Test
    fun `values the engine sets later round-trip too - pre-alert, demoted, the steps' own pre-alerts`() = runTest {
        data.teas.saveTea(tea("t1"))
        val driver = TimerDriver(data)
        driver.send(startEvent(infusion(60), infusion(20)))
        val ready = driver.state as TimerState.Ready
        val running = driver.send(TimerEvent.Start) as TimerState.Running
        val handMade = mapOf(
            "Running with the pre-alert due" to running.copy(preAlertAtElapsedMs = running.deadlineElapsedMs - 10_000),
            "Running with the pre-alert played" to running.copy(preAlertAtElapsedMs = null, preAlertPlayed = true),
            "Ready, demoted" to ready.copy(session = ready.session.copy(demoted = true)),
            "Ready with each step's own pre-alert" to
                ready.copy(
                    session = ready.session.copy(
                        steps = ready.session.steps.zip(listOf(0, 25)) { step, own ->
                            step.copy(preAlertSeconds = own)
                        },
                    ),
                ),
            "Ready with B off" to ready.copy(session = ready.session.copy(preAlert = PreAlertSettings(0))),
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
            TimerEvent.EditSteps(listOf(rinse(10), infusion(45, tempC = 75, preAlert = 15))),
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
    fun `the quick timer's own pre-alert choice round-trips through active_brew (umamit_db v3)`() = runTest {
        val running = TimerDriver(data).run {
            send(TimerEvent.StartQuickTimer(120, globalPreAlertSeconds = 10))
            send(TimerEvent.Start)
        }
        val automatic = BrewTimerEngine.snapshot(running)!!
        // The engine leaves the choice null (*Automatisch*) until the selector (v0.2.2-03, #116) sets it.
        assertThat(data.activeBrew.read()).isEqualTo(automatic)
        assertThat(automatic.activeBrew.quickPreAlertSeconds).isNull()

        listOf(0, 5, 30).forEach { choice ->
            val chosen = automatic.copy(activeBrew = automatic.activeBrew.copy(quickPreAlertSeconds = choice))

            data.activeBrew.persist(chosen)

            assertWithMessage("choice $choice").that(data.activeBrew.read()).isEqualTo(chosen)
        }
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

    // --- One row per slot (umamit.db v4, D-098).

    @Test
    fun `each slot reads back its own timer while the other slot stays idle`() = runTest {
        data.teas.saveTea(tea("t1"))
        val brew = TimerDriver(data, slot = TimerSlot.BREW).run {
            send(startEvent(infusion(60), infusion(20)))
            send(TimerEvent.Start)
        }

        assertStored(TimerSlot.BREW, "brew running", brew)
        assertThat(data.activeBrew.read(TimerSlot.QUICK)).isNull()

        data.activeBrew.clear(TimerSlot.BREW)
        val quick = TimerDriver(data, slot = TimerSlot.QUICK).run {
            send(TimerEvent.StartQuickTimer(90, globalPreAlertSeconds = 15))
            send(TimerEvent.Start)
        }

        assertStored(TimerSlot.QUICK, "quick running", quick)
        assertThat(data.activeBrew.read(TimerSlot.BREW)).isNull()
    }

    @Test
    fun `both slots hold a timer at once and every write keeps the other slot as it was`() = runTest {
        data.teas.saveTea(tea("t1"))
        val brew = TimerDriver(data, slot = TimerSlot.BREW)
        val quick = TimerDriver(data, slot = TimerSlot.QUICK)
        suspend fun step(name: String, driver: TimerDriver, event: TimerEvent, advanceSeconds: Long = 0) {
            driver.clocks.advanceSeconds(advanceSeconds)
            driver.send(event)
            assertStored(TimerSlot.BREW, "$name, brew", brew.state)
            assertStored(TimerSlot.QUICK, "$name, quick", quick.state)
        }

        step("Brew ready", brew, startEvent(rinse(10), infusion(60), infusion(20)))
        step("Quick ready", quick, TimerEvent.StartQuickTimer(120, globalPreAlertSeconds = 10))
        step("Brew ready after skipping the rinse", brew, TimerEvent.SkipStep, advanceSeconds = 2)
        step("Brew running", brew, TimerEvent.Start, advanceSeconds = 3)
        step("Quick running", quick, TimerEvent.Start, advanceSeconds = 5)
        step("Quick paused", quick, TimerEvent.Pause, advanceSeconds = 30)
        step("Brew alerting", brew, TimerEvent.DeadlineReached, advanceSeconds = 60)
        step("Quick running again", quick, TimerEvent.Resume, advanceSeconds = 10)
        step("Brew ready after the pour", brew, TimerEvent.Poured, advanceSeconds = 4)
        step("Quick alerting", quick, TimerEvent.DeadlineReached, advanceSeconds = 90)
        step("Quick ended", quick, TimerEvent.Poured, advanceSeconds = 3)

        assertThat(data.activeBrew.read(TimerSlot.QUICK)).isNull()
        assertThat(data.activeBrew.read(TimerSlot.BREW)!!.activeBrew.phase).isEqualTo(TimerPhase.READY)
    }

    @Test
    fun `clearing one slot leaves the other slot and the brew session`() = runTest {
        data.teas.saveTea(tea("t1"))
        val brew = TimerDriver(data, slot = TimerSlot.BREW).run {
            send(startEvent(infusion(60)))
            send(TimerEvent.Start)
        }
        val quickDriver = TimerDriver(data, slot = TimerSlot.QUICK)
        val quick = quickDriver.send(TimerEvent.StartQuickTimer(90, globalPreAlertSeconds = 10))

        data.activeBrew.clear(TimerSlot.QUICK)

        assertThat(data.activeBrew.read(TimerSlot.QUICK)).isNull()
        assertStored(TimerSlot.BREW, "brew after clearing the quick slot", brew)

        data.activeBrew.persist(TimerSlot.QUICK, BrewTimerEngine.snapshot(quick))
        data.activeBrew.persist(TimerSlot.BREW, null)

        assertThat(data.activeBrew.read(TimerSlot.BREW)).isNull()
        assertStored(TimerSlot.QUICK, "quick after clearing the brew slot", quick)
        assertThat(data.sessions.getSession(SESSION_ID)).isNotNull()
    }

    @Test
    fun `an alert's ringing start round-trips in either slot, held as none (umamit_db v4)`() = runTest {
        data.teas.saveTea(tea("t1"))
        val brewAlert = TimerDriver(data, slot = TimerSlot.BREW).run {
            send(startEvent(infusion(60)))
            send(TimerEvent.Start)
            clocks.advanceSeconds(60)
            send(TimerEvent.DeadlineReached)
        }
        val quickAlert = TimerDriver(data, slot = TimerSlot.QUICK).run {
            send(TimerEvent.StartQuickTimer(90, globalPreAlertSeconds = 10))
            send(TimerEvent.Start)
            clocks.advanceSeconds(90)
            send(TimerEvent.DeadlineReached)
        }
        val brew = BrewTimerEngine.snapshot(brewAlert)!!
        val quick = BrewTimerEngine.snapshot(quickAlert)!!
        // The brew rang from its deadline; the quick alert was held 12 min, then released.
        val brewRinging = brew.ringingSince(brew.activeBrew.deadlineElapsedMs!!, brew.activeBrew.deadlineWallMs!!)
        val released = quick.ringingSince(
            quick.activeBrew.deadlineElapsedMs!! + TWELVE_MINUTES_MS,
            quick.activeBrew.deadlineWallMs!! + TWELVE_MINUTES_MS,
        )
        val held = quick.ringingSince(null, null)

        listOf("brew ringing" to brewRinging, "quick released" to released, "quick held" to held).forEach {
            val (name, snapshot) = it
            val slot = snapshot.activeBrew.slot
            data.activeBrew.persist(slot, snapshot)

            assertWithMessage(name).that(data.activeBrew.read(slot)).isEqualTo(snapshot)
        }
        assertWithMessage("the brew slot after the quick writes").that(data.activeBrew.read(TimerSlot.BREW))
            .isEqualTo(brewRinging)
    }

    @Test
    fun `a snapshot of the other kind is refused for a slot and nothing is written`() = runTest {
        data.teas.saveTea(tea("t1"))
        val ready = TimerDriver(data, slot = TimerSlot.BREW).send(startEvent(infusion(60)))
        val brew = BrewTimerEngine.snapshot(ready)!!
        val quick = BrewTimerEngine.snapshot(TimerDriver(data).send(TimerEvent.StartQuickTimer(90, 10)))!!
        data.activeBrew.clear()

        val wrong = listOf(TimerSlot.QUICK to brew, TimerSlot.BREW to quick)

        wrong.forEach { (slot, snapshot) ->
            val failure = runCatching { data.activeBrew.persist(slot, snapshot) }.exceptionOrNull()
            assertWithMessage("$slot").that(failure).isInstanceOf(IllegalArgumentException::class.java)
        }
        TimerSlot.entries.forEach { assertWithMessage("$it").that(data.activeBrew.read(it)).isNull() }
    }

    @Test
    fun `the single-timer methods keep the one timer in the slot of its kind`() = runTest {
        data.teas.saveTea(tea("t1"))
        val quick = BrewTimerEngine.snapshot(TimerDriver(data).send(TimerEvent.StartQuickTimer(90, 10)))!!

        assertThat(data.activeBrew.read(TimerSlot.QUICK)).isEqualTo(quick)
        assertThat(data.activeBrew.read()).isEqualTo(quick)

        // A brew started over the quick timer replaces it, as the single row did (v3).
        val brew = BrewTimerEngine.snapshot(TimerDriver(data).send(startEvent(infusion(60))))!!

        assertThat(data.activeBrew.read(TimerSlot.QUICK)).isNull()
        assertThat(data.activeBrew.read(TimerSlot.BREW)).isEqualTo(brew)
        assertThat(data.activeBrew.read()).isEqualTo(brew)

        // With both slots written (v0.3.0-03), the one timer is the brew's; clear() ends both.
        data.activeBrew.persist(TimerSlot.QUICK, quick)

        assertThat(data.activeBrew.read()).isEqualTo(brew)

        data.activeBrew.clear()

        TimerSlot.entries.forEach { assertWithMessage("$it").that(data.activeBrew.read(it)).isNull() }
        assertThat(data.sessions.getSession(SESSION_ID)).isNotNull()
    }

    @Test
    fun `clear removes the active brew but keeps the session`() = runTest {
        data.teas.saveTea(tea("t1"))
        TimerDriver(data).send(startEvent(infusion(60)))

        data.activeBrew.clear()

        assertThat(data.activeBrew.read()).isNull()
        assertThat(data.sessions.getSession(SESSION_ID)).isNotNull()
    }

    /**
     * The Schritte-Sheet's request: the plan without its last [drop] pending steps, with [choice] in every step that
     * has not ended (its single selector before #92).
     */
    private fun editPlan(driver: TimerDriver, choice: PreAlertChoice, drop: Int): TimerEvent.EditSteps {
        val steps = (driver.state as TimerState.Active).session.steps.map { step ->
            val ended = step.status == StepStatus.DONE || step.status == StepStatus.SKIPPED
            if (ended) step.planned else step.planned.copy(preAlertSeconds = choice.explicitSeconds)
        }
        return TimerEvent.EditSteps(steps.dropLast(drop))
    }

    private companion object {
        const val TWELVE_MINUTES_MS = 12 * 60_000L
    }
}
