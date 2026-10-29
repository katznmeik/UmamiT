package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.ActiveBrewSnapshot
import com.umamit.core.model.SessionStepRow
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.ZoneId

/**
 * The persistence contract (04 §7.1, 05 §3.12): every engine state maps to an
 * `active_brew` snapshot plus the session's step rows and back, losslessly, so
 * M1-09 can persist and recover it without engine changes.
 */
class SnapshotTest {

    /** Every kind of reachable state, by name. */
    private fun reachableStates(): Map<String, TimerState> = stepStates() + sessionStates()

    /** The states of the step lifecycle. */
    private fun stepStates(): Map<String, TimerState> = buildMap {
        put("Ready, first step", sessionWith(rinse(10), infusion(60), infusion(20)).state)
        put("Ready after a skip and a pour", afterSkipAndPour().state)
        put("Running", running().state)
        put("Running with the pre-alert played", preAlertPlayed().state)
        put("Running with the pre-alert due", running().apply { clocks.advanceSeconds(51) }.state)
        put(
            "Paused mid-pause after the pre-alert",
            preAlertPlayed().apply {
                clocks.advanceSeconds(2)
                send(TimerEvent.Pause)
                clocks.advanceSeconds(30)
            }.state,
        )
        put("Alerting", alerting().state)
        put("Alerting, alert played", alerting().apply { send(TimerEvent.AlertCompleted) }.state)
        put("Finished", sessionWith(infusion(60)).apply { send(TimerEvent.SkipStep) }.state)
        put(
            "Finished after an overtime timeout",
            alerting(infusion(60)).apply {
                clocks.advance(10 * 60_000L)
                send(TimerEvent.Recover)
            }.state,
        )
    }

    /** Demoted, edited, recovered and quick-timer states. */
    private fun sessionStates(): Map<String, TimerState> = buildMap {
        put("Ready, demoted", sessionWith(infusion(60), infusion(20)).demoted().state)
        put("Paused, demoted", running().apply { send(TimerEvent.Pause) }.demoted().state)
        put("Finished, demoted", sessionWith(infusion(60)).apply { send(TimerEvent.SkipStep) }.demoted().state)
        put(
            "Running with the steps' own pre-alert",
            sessionWith(infusion(60), infusion(20)).apply {
                send(editSteps(PreAlertChoice.Seconds(25), infusion(60), infusion(20)))
                send(TimerEvent.Start)
            }.state,
        )
        put(
            "Ready with the pre-alert off",
            sessionWith(infusion(60)).apply {
                send(editSteps(PreAlertChoice.Off, infusion(60, tempC = null), whisk(20)))
            }.state,
        )
        put(
            "Running after a reboot, in another zone",
            EngineDriver(VirtualClocks(zoneId = ZoneId.of("Asia/Tokyo"))).apply {
                send(startSession(infusion(3_600)))
                send(TimerEvent.Start)
                clocks.reboot(offMs = 60_000, upMs = 10_000)
                send(TimerEvent.Recover)
            }.state,
        )
        put("Quick timer, Ready", quick().state)
        put("Quick timer, Running", quick().apply { send(TimerEvent.Start) }.state)
        put(
            "Quick timer, Paused",
            quick().apply {
                send(TimerEvent.Start)
                clocks.advanceSeconds(20)
                send(TimerEvent.Pause)
            }.state,
        )
        put(
            "Quick timer, Alerting",
            quick().apply {
                send(TimerEvent.Start)
                clocks.advanceSeconds(90)
                send(TimerEvent.DeadlineReached)
            }.state,
        )
    }

    @Test
    fun `every reachable state survives the round trip through the snapshot and the step rows`() {
        reachableStates().forEach { (name, state) ->
            val snapshot = BrewTimerEngine.snapshot(state)

            assertWithMessage(name).that(BrewTimerEngine.restore(snapshot)).isEqualTo(state)
        }
    }

    @Test
    fun `Idle has no snapshot and restores from none`() {
        assertThat(BrewTimerEngine.snapshot(TimerState.Idle)).isNull()
        assertThat(BrewTimerEngine.restore(null)).isEqualTo(TimerState.Idle)
    }

    @Test
    fun `a brew session maps to the active_brew fields and one row per planned step`() {
        val driver = afterSkipAndPour()
        driver.send(TimerEvent.Start)
        val running = driver.state as TimerState.Running

        val snapshot = BrewTimerEngine.snapshot(running)!!

        assertThat(snapshot.activeBrew).isEqualTo(
            ActiveBrewSnapshot(
                sessionId = SESSION_ID,
                isQuickTimer = false,
                quickDurationSeconds = null,
                stepIndex = 2,
                phase = TimerPhase.RUNNING,
                deadlineElapsedMs = running.deadlineElapsedMs,
                deadlineWallMs = running.deadlineWallMs,
                // The 20 s step's automatic pre-alert: min(10, 5 · ⌊20 / 15⌋) = 5 s (TMR-23, D-093).
                preAlertElapsedMs = running.deadlineElapsedMs - 5_000,
                preAlertPlayed = false,
                remainingMs = null,
                pausedTotalMs = 0,
                pausedSinceElapsedMs = null,
                pausedSinceWallMs = null,
                readySinceWallMs = null,
                stepStartedWallMs = running.stepStartedWallMs,
                alertPlayed = false,
                bootCount = START_BOOT_COUNT,
                lastInteractionWallMs = driver.clocks.wall,
                demoted = false,
                preAlertInheritedSeconds = 10,
                zoneId = "Europe/Berlin",
            ),
        )
        val pour = running.session.steps[1].record!!
        assertThat(snapshot.steps).containsExactly(
            SessionStepRow(0, StepKind.RINSE, 10, 70, 0, 0, 0, null, null, skipped = true, overtimeAutoAck = false),
            SessionStepRow(
                1,
                StepKind.INFUSION,
                60,
                60,
                pour.actualSeconds,
                pour.pausedSeconds,
                pour.overtimeSeconds,
                pour.startedAtWallMs,
                pour.endedAtWallMs,
                skipped = false,
                overtimeAutoAck = false,
            ),
            SessionStepRow(2, StepKind.INFUSION, 20, 60, null, null, null, null, null, false, false),
        ).inOrder()
    }

    @Test
    fun `the quick timer has no step rows, its duration is in the snapshot`() {
        val snapshot = BrewTimerEngine.snapshot(quick().state)!!

        assertThat(snapshot.steps).isEmpty()
        assertThat(snapshot.activeBrew.sessionId).isNull()
        assertThat(snapshot.activeBrew.isQuickTimer).isTrue()
        assertThat(snapshot.activeBrew.quickDurationSeconds).isEqualTo(90)
    }

    @Test
    fun `an inconsistent snapshot is refused instead of restoring a wrong timer`() {
        val brew = BrewTimerEngine.snapshot(running().state)!!
        val quick = BrewTimerEngine.snapshot(quick().state)!!
        val broken = listOf(
            "a brew snapshot without a session id" to brew.copy(activeBrew = brew.activeBrew.copy(sessionId = null)),
            "a quick timer with a session id" to quick.copy(activeBrew = quick.activeBrew.copy(sessionId = "x")),
            "a quick timer without a duration" to
                quick.copy(activeBrew = quick.activeBrew.copy(quickDurationSeconds = null)),
            "a quick timer with step rows" to quick.copy(steps = brew.steps),
            "a quick timer as a cold brew" to quick.copy(longStepsOnly = true),
            "a brew session without step rows" to brew.copy(steps = emptyList()),
            "a running step without a deadline" to
                brew.copy(activeBrew = brew.activeBrew.copy(deadlineElapsedMs = null)),
            "a step index outside the plan" to brew.copy(activeBrew = brew.activeBrew.copy(stepIndex = 7)),
            "rows out of order" to brew.copy(steps = brew.steps.reversed()),
            "a recorded running step" to brew.copy(
                steps = brew.steps.mapIndexed { i, row -> if (i == 0) row.copy(actualSeconds = 1) else row },
            ),
            "a recorded step after a pending one" to brew.copy(
                steps = brew.steps.mapIndexed { i, row -> if (i == 1) row.copy(actualSeconds = 1) else row },
            ),
        )
        broken.forEach { (name, snapshot) ->
            assertThrows(name, IllegalArgumentException::class.java) { BrewTimerEngine.restore(snapshot) }
        }
    }

    @Test
    fun `out-of-range values and fields of another phase are refused with IllegalArgumentException`() {
        val ready = BrewTimerEngine.snapshot(sessionWith(infusion(60), infusion(20)).state)!!
        val paused = BrewTimerEngine.snapshot(running().apply { send(TimerEvent.Pause) }.state)!!
        val alerting = BrewTimerEngine.snapshot(alerting().state)!!
        val finished = BrewTimerEngine.snapshot(sessionWith(infusion(60)).apply { send(TimerEvent.SkipStep) }.state)!!
        val quick = BrewTimerEngine.snapshot(quick().state)!!
        val running = BrewTimerEngine.snapshot(running().state)!!
        val longRunning = BrewTimerEngine.snapshot(sessionWith(infusion(600)).apply { send(TimerEvent.Start) }.state)!!
        fun TimerSnapshot.row(transform: ActiveBrewSnapshot.() -> ActiveBrewSnapshot) =
            copy(activeBrew = activeBrew.transform())
        fun TimerSnapshot.step(index: Int, transform: SessionStepRow.() -> SessionStepRow) =
            copy(steps = steps.mapIndexed { i, step -> if (i == index) step.transform() else step })
        val broken = listOf(
            "Ready without a pending step" to finished.row { copy(phase = TimerPhase.READY) },
            "Finished with a step index inside the plan" to finished.row { copy(stepIndex = 0) },
            "a planned duration of 0" to ready.step(1) { copy(plannedSeconds = 0) },
            "a quick timer of 0 s" to quick.row { copy(quickDurationSeconds = 0) },
            "a quick timer with a step index" to quick.row { copy(stepIndex = 1) },
            "nothing remaining in a pause" to paused.row { copy(remainingMs = 0) },
            "more remaining than planned" to paused.row { copy(remainingMs = 60_001) },
            "a negative paused total" to paused.row { copy(pausedTotalMs = -1) },
            "a pending step marked skipped" to ready.step(1) { copy(skipped = true) },
            "a pending step with a start time" to ready.step(1) { copy(startedAtWallMs = START_WALL_MS) },
            "a recorded step without its paused time" to finished.step(0) { copy(pausedSeconds = null) },
            "Ready with a deadline" to ready.row { copy(deadlineElapsedMs = 1, deadlineWallMs = 1) },
            "Ready with a played alert" to ready.row { copy(alertPlayed = true) },
            "Ready with a step start" to ready.row { copy(stepStartedWallMs = START_WALL_MS) },
            "Paused with a deadline" to paused.row { copy(deadlineElapsedMs = 1) },
            "Paused with a ready time" to paused.row { copy(readySinceWallMs = START_WALL_MS) },
            "Alerting with a pre-alert time" to alerting.row { copy(preAlertElapsedMs = 1) },
            "Alerting with a remaining time" to alerting.row { copy(remainingMs = 1) },
            "a negative pre-alert" to ready.row { copy(preAlertInheritedSeconds = -1) },
            "an unknown time zone" to ready.row { copy(zoneId = "Mars/Olympus") },
            // States the engine never produces (O2 of the #27 slice review).
            "a demoted running step" to running.row { copy(demoted = true) },
            "a demoted alert" to alerting.row { copy(demoted = true) },
            "a played pre-alert that is still due" to running.row { copy(preAlertPlayed = true) },
            "a pre-alert on a long step" to longRunning.row { copy(preAlertElapsedMs = deadlineElapsedMs!! - 10_000) },
        )
        broken.forEach { (name, snapshot) ->
            assertThrows(name, IllegalArgumentException::class.java) { BrewTimerEngine.restore(snapshot) }
        }
    }

    private fun running() = sessionWith(infusion(60), infusion(20)).apply { send(TimerEvent.Start) }

    private fun alerting(vararg steps: PlannedStep = arrayOf(infusion(60), infusion(20))) = sessionWith(*steps).apply {
        send(TimerEvent.Start)
        clocks.advanceSeconds(steps.first().plannedSeconds + 4L)
        send(TimerEvent.DeadlineReached)
    }

    private fun afterSkipAndPour() = sessionWith(rinse(10), infusion(60), infusion(20)).apply {
        send(TimerEvent.SkipStep)
        send(TimerEvent.Start)
        clocks.advanceSeconds(30)
        send(TimerEvent.Pause)
        clocks.advanceSeconds(12)
        send(TimerEvent.Resume)
        clocks.advanceSeconds(33)
        send(TimerEvent.DeadlineReached)
        send(TimerEvent.Poured)
    }

    private fun quick() = EngineDriver().apply { send(TimerEvent.StartQuickTimer(90, globalPreAlertSeconds = 15)) }

    /** A running 60 s step whose pre-alert played, through the reducer. */
    private fun preAlertPlayed() = running().apply {
        clocks.advanceSeconds(50)
        send(TimerEvent.PreAlertReached)
    }

    /** This waiting session after 30 min without an interaction: demoted by the reducer. */
    private fun EngineDriver.demoted() = apply {
        clocks.advance(30 * 60_000L)
        send(TimerEvent.IdleDemote)
        check(outcome == TransitionOutcome.Accepted) { "only a waiting brew session is demoted" }
    }
}
