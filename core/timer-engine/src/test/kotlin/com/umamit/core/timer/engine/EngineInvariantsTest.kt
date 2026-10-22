package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.StepKind
import org.junit.Test
import kotlin.random.Random

/**
 * Model-based sequence test: random events and clock advances (including the
 * milliseconds around the deadline), with the engine's invariants checked
 * after every event. Seeded, so a failure is reproducible from its message.
 */
class EngineInvariantsTest {

    private val dataEffects = setOf(
        TimerEffect.CreateSession::class,
        TimerEffect.RecordStep::class,
        TimerEffect.CompleteSession::class,
        TimerEffect.DiscardSession::class,
    )

    @Test
    fun `random event sequences keep every invariant`() {
        repeat(RUNS) { seed -> run(Random(seed), seed) }
    }

    private fun run(random: Random, seed: Int) {
        val driver = EngineDriver()
        val recorded = mutableSetOf<Int>()
        repeat(EVENTS_PER_RUN) { n ->
            advanceClock(random, driver)
            val event = randomEvent(random)
            val before = driver.state
            val transition = BrewTimerEngine.reduce(before, event, driver.clocks.now())
            driver.state = transition.state
            val label = "seed $seed, event $n: $event in ${before::class.simpleName}"

            if (transition.outcome != TransitionOutcome.Accepted) {
                assertWithMessage("$label: ignored keeps the instance").that(transition.state).isSameInstanceAs(before)
                assertWithMessage("$label: ignored has no effects").that(transition.effects).isEmpty()
                return@repeat
            }
            if (transition.effects.any { it is TimerEffect.CreateSession }) recorded.clear()
            checkEffectOrder(label, transition)
            checkAlertEdges(label, before, transition)
            checkStepRecords(label, transition, recorded)
            checkPlanShape(label, transition.state)
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
            .that(
                effects[persistAt + 1],
            ).isEqualTo(TimerEffect.ScheduleAlarms(BrewTimerEngine.dueTimes(transition.state)))
        assertWithMessage("$label: one ScheduleAlarms")
            .that(effects.count { it is TimerEffect.ScheduleAlarms }).isEqualTo(1)
    }

    private fun checkAlertEdges(label: String, before: TimerState, transition: TimerTransition) {
        val entering = before !is TimerState.Alerting && transition.state is TimerState.Alerting
        val leaving = before is TimerState.Alerting && transition.state !is TimerState.Alerting
        assertWithMessage("$label: StartAlert exactly on entering Alerting")
            .that(TimerEffect.StartAlert in transition.effects).isEqualTo(entering)
        assertWithMessage("$label: StopAlert exactly on leaving Alerting")
            .that(TimerEffect.StopAlert in transition.effects).isEqualTo(leaving)
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
        }
    }

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
    }

    private fun advanceClock(random: Random, driver: EngineDriver) {
        val running = driver.state as? TimerState.Running
        when {
            running != null && random.nextInt(3) == 0 ->
                driver.advanceTo(
                    (running.deadlineElapsedMs + random.nextLong(-1, 2)).coerceAtLeast(driver.clocks.elapsed),
                )

            random.nextInt(4) == 0 -> Unit

            else -> driver.clocks.advance(random.nextLong(0, 90_000))
        }
    }

    private fun randomEvent(random: Random): TimerEvent = when (random.nextInt(12)) {
        0 -> TimerEvent.StartSession(
            "session-${random.nextInt()}",
            List(random.nextInt(1, 5)) {
                PlannedStep(StepKind.entries.random(random), random.nextInt(5, 120), tempC = null)
            },
            PRE_ALERT,
        )

        1, 2 -> TimerEvent.Start

        3 -> TimerEvent.Pause

        4 -> TimerEvent.Resume

        5, 6 -> TimerEvent.DeadlineReached

        7 -> TimerEvent.AlertCompleted

        8 -> TimerEvent.Poured

        9 -> TimerEvent.EndStepNow

        10 -> TimerEvent.SkipStep

        else -> if (random.nextBoolean()) TimerEvent.FinishSession else TimerEvent.Discard
    }

    private companion object {
        const val RUNS = 300
        const val EVENTS_PER_RUN = 80
    }
}
