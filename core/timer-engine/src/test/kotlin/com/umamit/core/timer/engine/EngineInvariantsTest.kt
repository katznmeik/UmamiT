package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.common.ClockReading
import com.umamit.core.model.StepKind
import org.junit.Test
import kotlin.random.Random

/**
 * Model-based sequence test: random events and clock advances (including the
 * milliseconds around the deadline, long waits and reboots), with the
 * engine's invariants checked after every event. Seeded, so a failure is
 * reproducible from its message.
 */
class EngineInvariantsTest {

    private val dataEffects = setOf(
        TimerEffect.CreateSession::class,
        TimerEffect.ReplacePendingSteps::class,
        TimerEffect.RecordStep::class,
        TimerEffect.CompleteSession::class,
        TimerEffect.DiscardSession::class,
    )

    @Test
    fun `random event sequences keep every invariant`() {
        repeat(RUNS) { seed -> run(Random(seed), seed) }

        // The generator reaches every event and every outcome, so the invariants are checked on all of them.
        assertWithMessage("accepted event kinds").that(accepted.map { it.simpleName })
            .containsAtLeast(
                "StartSession", "StartQuickTimer", "Recover", "Start", "Pause", "Resume", "DeadlineReached",
                "AlertCompleted", "Poured", "EndStepNow", "SkipStep", "FinishSession", "Discard", "AddStep",
                "EditSteps", "Cancel", "QuickTimerTimeout", "PreAlertReached", "OvertimeTimeout", "IdleDemote",
                "MidnightTimeout",
            )
        assertWithMessage("rejections").that(rejections).containsAtLeast(
            RejectionReason.BootChanged,
            RejectionReason.StartedStepChanged,
            RejectionReason.NoBrewStepLeft,
            RejectionReason.ActiveTimerExists(SessionKind.Quick),
        )
    }

    private val accepted = mutableSetOf<Class<out TimerEvent>>()
    private val rejections = mutableSetOf<RejectionReason>()

    private fun run(random: Random, seed: Int) {
        val driver = EngineDriver()
        val recorded = mutableSetOf<Int>()
        repeat(EVENTS_PER_RUN) { n ->
            advanceClock(random, driver)
            val before = driver.state
            val event = randomEvent(random, before)
            val transition = BrewTimerEngine.reduce(before, event, driver.clocks.now())
            driver.state = transition.state
            val label = "seed $seed, event $n: $event in ${before::class.simpleName}"

            if (transition.outcome != TransitionOutcome.Accepted) {
                assertWithMessage("$label: not accepted keeps the instance").that(transition.state)
                    .isSameInstanceAs(before)
                assertWithMessage("$label: not accepted has no effects").that(transition.effects).isEmpty()
                (transition.outcome as? TransitionOutcome.Rejected)?.let { rejections += it.reason }
                return@repeat
            }
            accepted += event.javaClass
            if (transition.effects.any { it is TimerEffect.CreateSession }) recorded.clear()
            checkEffectOrder(label, transition)
            checkAlertEdges(label, before, event, transition)
            checkService(label, before, event, transition)
            checkStepRecords(label, transition, recorded)
            checkPlanShape(label, transition.state)
            checkPendingRows(label, transition)
            checkQuickTimer(label, before, transition)
            checkSnapshot(label, transition.state)
            checkNothingOverdue(label, transition.state, driver)
        }
    }

    private fun checkEffectOrder(label: String, transition: TimerTransition) {
        val effects = transition.effects
        val persistAt = effects.indexOf(TimerEffect.Persist)
        assertWithMessage("$label: exactly one Persist").that(effects.count { it == TimerEffect.Persist }).isEqualTo(1)
        assertWithMessage("$label: data effects come first")
            .that(effects.take(persistAt).all { it::class in dataEffects }).isTrue()
        assertWithMessage("$label: no data effect after Persist")
            .that(effects.drop(persistAt).none { it::class in dataEffects }).isTrue()
        assertWithMessage("$label: ScheduleAlarms right after Persist is the due set of the new state")
            .that(effects[persistAt + 1])
            .isEqualTo(TimerEffect.ScheduleAlarms(BrewTimerEngine.dueTimes(transition.state)))
        assertWithMessage("$label: one ScheduleAlarms")
            .that(effects.count { it is TimerEffect.ScheduleAlarms }).isEqualTo(1)
    }

    /**
     * StartAlert exactly on entering Alerting, or when recovery finds an
     * alert that has not played (at least once); StopAlert exactly on leaving.
     */
    private fun checkAlertEdges(label: String, before: TimerState, event: TimerEvent, transition: TimerTransition) {
        val after = transition.state
        val entering = before !is TimerState.Alerting && after is TimerState.Alerting
        val replay = event == TimerEvent.Recover && before is TimerState.Alerting && after is TimerState.Alerting &&
            !after.alertPlayed
        val leaving = before is TimerState.Alerting && after !is TimerState.Alerting
        assertWithMessage("$label: StartAlert exactly on entering Alerting or replaying an unplayed alert")
            .that(TimerEffect.StartAlert in transition.effects).isEqualTo(entering || replay)
        assertWithMessage("$label: StopAlert exactly on leaving Alerting")
            .that(TimerEffect.StopAlert in transition.effects).isEqualTo(leaving)
        if (event == TimerEvent.Recover) {
            assertWithMessage("$label: recovery never shows the saved sheet")
                .that(transition.effects.none { it is TimerEffect.ShowSavedSheet }).isTrue()
        }
    }

    /**
     * The foreground-service contract (S5, D-053): Idle stops the service; a
     * demoted state or a starting long step never starts it; a user event on a
     * demoted session ends the demotion and decides the service; the service
     * is ensured before every alert.
     */
    private fun checkService(label: String, before: TimerState, event: TimerEvent, transition: TimerTransition) {
        val after = transition.state
        val effects = transition.effects
        val starts = TimerEffect.StartForeground in effects
        val stops = TimerEffect.StopForeground in effects
        assertWithMessage("$label: never both StartForeground and StopForeground").that(starts && stops).isFalse()
        if (TimerEffect.StartAlert in effects) {
            assertWithMessage("$label: the service is ensured before the alert")
                .that(effects.indexOf(TimerEffect.StartForeground))
                .isIn(0 until effects.indexOf(TimerEffect.StartAlert))
        }
        if (after == TimerState.Idle) {
            assertWithMessage("$label: Idle stops the service").that(stops).isTrue()
            assertWithMessage("$label: Idle posts no timer notification")
                .that(TimerEffect.UpdateNotification in effects).isFalse()
        }
        if (after is TimerState.Active && after.session.demoted) {
            assertWithMessage("$label: a demoted session never starts the service").that(starts).isFalse()
        }
        val countingDown = event == TimerEvent.Start || event == TimerEvent.Resume
        if (countingDown && BrewTimerEngine.isLongStep(after)) {
            assertWithMessage("$label: a long step counts down without the service").that(stops).isTrue()
        }
        checkDemotionEnds(label, before, event, transition)
    }

    /** A user event on a demoted session that stays active ends the demotion and decides the service. */
    private fun checkDemotionEnds(label: String, before: TimerState, event: TimerEvent, transition: TimerTransition) {
        val after = transition.state
        val demotedBefore = before is TimerState.Active && before.session.demoted
        if (!demotedBefore || event !in userEvents || after !is TimerState.Active) return
        assertWithMessage("$label: a user event ends the demotion").that(after.session.demoted).isFalse()
        assertWithMessage("$label: a user event on a demoted session decides the service")
            .that(transition.effects.any { it == TimerEffect.StartForeground || it == TimerEffect.StopForeground })
            .isTrue()
    }

    private val userEvents = setOf(
        TimerEvent.Start, TimerEvent.Pause, TimerEvent.Resume, TimerEvent.Poured, TimerEvent.EndStepNow,
        TimerEvent.SkipStep, TimerEvent.AddStep, TimerEvent.FinishSession, TimerEvent.Discard, TimerEvent.Cancel,
    )

    private fun checkStepRecords(label: String, transition: TimerTransition, recorded: MutableSet<Int>) {
        transition.effects.filterIsInstance<TimerEffect.RecordStep>().forEach { effect ->
            assertWithMessage("$label: step ${effect.stepIndex} recorded once").that(recorded.add(effect.stepIndex))
                .isTrue()
            val record = effect.record
            if (record.overtimeSeconds > 0) {
                assertWithMessage("$label: actual = planned + overtime")
                    .that(record.actualSeconds).isEqualTo(record.plannedSeconds + record.overtimeSeconds)
            } else {
                assertWithMessage("$label: no overtime, actual <= planned")
                    .that(record.actualSeconds).isAtMost(record.plannedSeconds)
            }
            if (record.overtimeAutoAck) {
                assertWithMessage("$label: auto-ack is poured on time")
                    .that(record.actualSeconds).isEqualTo(record.plannedSeconds)
            }
        }
    }

    /**
     * Started or ended steps form a prefix, pending steps the suffix; the
     * current step is the first one after the ended steps.
     */
    private fun checkPlanShape(label: String, state: TimerState) {
        if (state !is TimerState.Active) return
        val statuses = state.session.steps.map { it.status }
        val ended = statuses.takeWhile { it == StepStatus.DONE || it == StepStatus.SKIPPED }.size
        val started = statuses.drop(ended).takeWhile { it == StepStatus.STARTED }.size
        assertWithMessage("$label: ended prefix, at most one started, pending suffix ($statuses)")
            .that(started <= 1 && statuses.drop(ended + started).all { it == StepStatus.PENDING }).isTrue()
        when (state) {
            is TimerState.StepStarted -> assertWithMessage("$label: the current step is the started one")
                .that(started == 1 && state.stepIndex == ended).isTrue()

            is TimerState.Ready -> assertWithMessage("$label: Ready waits for the first pending step")
                .that(started == 0 && state.stepIndex == ended).isTrue()

            is TimerState.Finished -> assertWithMessage("$label: Finished has no pending step")
                .that(ended).isEqualTo(statuses.size)
        }
        assertWithMessage("$label: the plan is never empty").that(state.session.steps).isNotEmpty()
    }

    /** ReplacePendingSteps rewrites exactly the pending suffix of the new plan. */
    private fun checkPendingRows(label: String, transition: TimerTransition) {
        transition.effects.filterIsInstance<TimerEffect.ReplacePendingSteps>().forEach { effect ->
            val steps = (transition.state as TimerState.Active).session.steps
            val firstPending = steps.indexOfFirst { it.status == StepStatus.PENDING }.let {
                if (it <
                    0
                ) {
                    steps.size
                } else {
                    it
                }
            }
            assertWithMessage("$label: rows replaced from the first pending step")
                .that(effect.fromIndex).isEqualTo(firstPending)
            assertWithMessage("$label: the replaced rows are the pending steps")
                .that(effect.steps).isEqualTo(steps.drop(firstPending).map { it.planned })
        }
    }

    /** The quick timer has one infusion and never writes a journal row or shows the saved sheet. */
    private fun checkQuickTimer(label: String, before: TimerState, transition: TimerTransition) {
        val after = transition.state
        if (after is TimerState.Active && after.session.kind == SessionKind.Quick) {
            assertWithMessage("$label: a quick timer has one infusion").that(after.session.steps.map { it.kind })
                .containsExactly(StepKind.INFUSION)
        }
        if (before.isQuick() || after.isQuick()) {
            assertWithMessage("$label: the quick timer records nothing")
                .that(transition.effects.none { it::class in dataEffects || it is TimerEffect.ShowSavedSheet })
                .isTrue()
        }
    }

    private fun TimerState.isQuick() = this is TimerState.Active && session.kind == SessionKind.Quick

    /** Every accepted state survives the persistence round trip (05 §3.12). */
    private fun checkSnapshot(label: String, state: TimerState) {
        assertWithMessage("$label: snapshot round trip")
            .that(BrewTimerEngine.restore(BrewTimerEngine.snapshot(state))).isEqualTo(state)
    }

    /**
     * A due time-based event is never ignored, it is applied or consumed
     * (D-048): after an accepted transition, the event of every time in the
     * due set that has come is accepted in the new state, so the controller's
     * wake-up never spins.
     */
    private fun checkNothingOverdue(label: String, state: TimerState, driver: EngineDriver) {
        val due = BrewTimerEngine.dueTimes(state)
        val now = driver.clocks.now()
        dueEvents(due, now).forEach { event ->
            assertWithMessage("$label: the due $event is accepted")
                .that(BrewTimerEngine.reduce(state, event, now).outcome).isEqualTo(TransitionOutcome.Accepted)
        }
        if (state !is TimerState.Running) {
            assertWithMessage("$label: only a running step has a deadline").that(due.deadlineElapsedMs).isNull()
        }
        // The times of the state are possible on this elapsed clock (D-048, reboot rebase).
        when (state) {
            is TimerState.Running -> assertWithMessage("$label: at most the planned time left")
                .that(state.deadlineElapsedMs - now.elapsedMs).isAtMost(state.plannedMs)

            is TimerState.Paused -> assertWithMessage("$label: the pause started before now")
                .that(state.pausedSinceElapsedMs).isAtMost(now.elapsedMs)

            is TimerState.Alerting -> assertWithMessage("$label: the deadline of an alert has passed")
                .that(state.deadlineElapsedMs).isAtMost(now.elapsedMs)

            else -> Unit
        }
    }

    /** The time-based events whose time in [due] has come at [now]. */
    private fun dueEvents(due: DueTimes, now: ClockReading): List<TimerEvent> = listOfNotNull(
        TimerEvent.PreAlertReached.takeIf { due.preAlertAtElapsedMs?.let { it <= now.elapsedMs } == true },
        TimerEvent.DeadlineReached.takeIf { due.deadlineElapsedMs?.let { it <= now.elapsedMs } == true },
        TimerEvent.OvertimeTimeout.takeIf { due.overtimeTimeoutAtElapsedMs?.let { it <= now.elapsedMs } == true },
        TimerEvent.QuickTimerTimeout.takeIf { due.quickCancelAtWallMs?.let { it <= now.wallMs } == true },
        TimerEvent.IdleDemote.takeIf { due.idleDemoteAtWallMs?.let { it <= now.wallMs } == true },
        TimerEvent.MidnightTimeout.takeIf { due.midnightAtWallMs?.let { it <= now.wallMs } == true },
    )

    /** Now and then moves both clocks to 1 ms around the pre-alert, the idle demote or the midnight timeout. */
    private fun advancedToTimeout(random: Random, driver: EngineDriver): Boolean {
        val due = BrewTimerEngine.dueTimes(driver.state)
        val jitter = random.nextLong(-1, 2)
        val by = when {
            due.preAlertAtElapsedMs != null && random.nextInt(4) == 0 ->
                due.preAlertAtElapsedMs + jitter - driver.clocks.elapsed

            due.idleDemoteAtWallMs != null && random.nextInt(8) == 0 ->
                due.idleDemoteAtWallMs + jitter - driver.clocks.wall

            due.midnightAtWallMs != null && random.nextInt(12) == 0 ->
                due.midnightAtWallMs + jitter - driver.clocks.wall

            else -> null
        }
        by?.let { driver.clocks.advance(it.coerceAtLeast(0)) }
        return by != null
    }

    private fun advanceClock(random: Random, driver: EngineDriver) {
        val running = driver.state as? TimerState.Running
        val alerting = driver.state as? TimerState.Alerting
        when {
            advancedToTimeout(random, driver) -> Unit

            running != null && random.nextInt(3) == 0 ->
                driver.advanceTo(
                    (running.deadlineElapsedMs + random.nextLong(-1, 2)).coerceAtLeast(driver.clocks.elapsed),
                )

            alerting != null && random.nextInt(4) == 0 ->
                driver.advanceTo(
                    (alerting.deadlineElapsedMs + OVERTIME_TIMEOUT_MS + random.nextLong(-1, 2))
                        .coerceAtLeast(driver.clocks.elapsed),
                )

            random.nextInt(25) == 0 -> driver.clocks.reboot(offMs = random.nextLong(0, 900_000), upMs = 30_000)

            // A reboot during which the wall clock did not move: wall values are behind the new elapsed clock.
            random.nextInt(25) == 0 -> driver.clocks.apply {
                reboot(offMs = 0, upMs = 30_000)
                wall -= 30_000
            }

            // The user or the network moves the wall clock alone, either way (E1).
            random.nextInt(25) == 0 -> driver.clocks.wall += random.nextLong(-3_600_000, 3_600_000)

            random.nextInt(20) == 0 -> driver.clocks.advance(QUICK_TIMER_IDLE_MS + random.nextLong(-1, 2))

            random.nextInt(4) == 0 -> Unit

            else -> driver.clocks.advance(random.nextLong(0, 90_000))
        }
    }

    /** Event generators, repeated by weight. */
    private val generators: List<(Random, TimerState) -> TimerEvent> = listOf<(Random, TimerState) -> TimerEvent>(
        { random, _ ->
            TimerEvent.StartSession(
                "session-${random.nextInt()}",
                randomPlan(random),
                PRE_ALERT,
                random.nextInt(8) == 0,
            )
        },
        { random, _ -> TimerEvent.StartQuickTimer(random.nextInt(5, 120), globalPreAlertSeconds = 10) },
        { _, _ -> TimerEvent.Start },
        { _, _ -> TimerEvent.Start },
        { _, _ -> TimerEvent.Pause },
        { _, _ -> TimerEvent.Resume },
        { _, _ -> TimerEvent.DeadlineReached },
        { _, _ -> TimerEvent.PreAlertReached },
        { _, _ -> TimerEvent.DeadlineReached },
        { _, _ -> TimerEvent.PreAlertReached },
        { _, _ -> TimerEvent.AlertCompleted },
        { _, _ -> TimerEvent.OvertimeTimeout },
        { _, _ -> TimerEvent.IdleDemote },
        { _, _ -> TimerEvent.MidnightTimeout },
        { _, _ -> TimerEvent.Poured },
        { _, _ -> TimerEvent.EndStepNow },
        { _, _ -> TimerEvent.SkipStep },
        { _, _ -> TimerEvent.AddStep },
        { random, state -> TimerEvent.EditSteps(editedPlan(random, state), randomPreAlert(random)) },
        { random, _ -> if (random.nextBoolean()) TimerEvent.Cancel else TimerEvent.QuickTimerTimeout },
        { _, _ -> TimerEvent.Recover },
        { _, _ -> TimerEvent.Recover },
        { random, _ -> if (random.nextBoolean()) TimerEvent.FinishSession else TimerEvent.Discard },
    )

    private fun randomEvent(random: Random, state: TimerState): TimerEvent = generators.random(random)(random, state)

    /** Short steps, and now and then a long one (TMR-18). */
    private fun randomPlan(random: Random): List<PlannedStep> = List(random.nextInt(1, 5)) {
        val seconds = if (random.nextInt(6) == 0) random.nextInt(600, 3600) else random.nextInt(5, 120)
        PlannedStep(StepKind.entries.random(random), seconds, tempC = null)
    }

    /** Mostly a valid edit (the current prefix plus new pending steps), sometimes a random plan. */
    private fun editedPlan(random: Random, state: TimerState): List<PlannedStep> {
        val steps = (state as? TimerState.Active)?.session?.steps
        return if (steps == null || random.nextInt(4) == 0) {
            randomPlan(random)
        } else {
            steps.filter { it.status != StepStatus.PENDING }.map { it.planned } + List(random.nextInt(0, 3)) {
                PlannedStep(StepKind.entries.random(random), random.nextInt(5, 120), tempC = 60)
            }
        }
    }

    private fun randomPreAlert(random: Random): PreAlertChoice = when (random.nextInt(3)) {
        0 -> PreAlertChoice.Inherit
        1 -> PreAlertChoice.Off
        else -> PreAlertChoice.Seconds(random.nextInt(1, 7) * 5)
    }

    private companion object {
        const val RUNS = 300
        const val EVENTS_PER_RUN = 80
    }
}
