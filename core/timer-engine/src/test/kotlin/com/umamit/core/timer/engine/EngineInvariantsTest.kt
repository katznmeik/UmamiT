package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertWithMessage
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
                "EditSteps", "Cancel", "QuickTimerTimeout",
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
            checkStepRecords(label, transition, recorded)
            checkPlanShape(label, transition.state)
            checkPendingRows(label, transition)
            checkQuickTimer(label, before, transition)
            checkSnapshot(label, transition.state)
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

    private fun advanceClock(random: Random, driver: EngineDriver) {
        val running = driver.state as? TimerState.Running
        val alerting = driver.state as? TimerState.Alerting
        when {
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

            random.nextInt(20) == 0 -> driver.clocks.advance(QUICK_TIMER_IDLE_MS + random.nextLong(-1, 2))

            random.nextInt(4) == 0 -> Unit

            else -> driver.clocks.advance(random.nextLong(0, 90_000))
        }
    }

    /** Event generators, repeated by weight. */
    private val generators: List<(Random, TimerState) -> TimerEvent> = listOf<(Random, TimerState) -> TimerEvent>(
        { random, _ -> TimerEvent.StartSession("session-${random.nextInt()}", randomPlan(random), PRE_ALERT) },
        { random, _ -> TimerEvent.StartQuickTimer(random.nextInt(5, 120), globalPreAlertSeconds = 10) },
        { _, _ -> TimerEvent.Start },
        { _, _ -> TimerEvent.Start },
        { _, _ -> TimerEvent.Pause },
        { _, _ -> TimerEvent.Resume },
        { _, _ -> TimerEvent.DeadlineReached },
        { _, _ -> TimerEvent.DeadlineReached },
        { _, _ -> TimerEvent.AlertCompleted },
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

    private fun randomPlan(random: Random): List<PlannedStep> = List(random.nextInt(1, 5)) {
        PlannedStep(StepKind.entries.random(random), random.nextInt(5, 120), tempC = null)
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
