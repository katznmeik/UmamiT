package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.common.MonotonicClock
import com.umamit.core.common.WallClock
import com.umamit.core.common.readClocks
import com.umamit.core.model.StepKind
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

/** 2026-10-22 10:00 in Berlin; the elapsed clock starts at an arbitrary boot offset. */
val START_WALL_MS: Long = ZonedDateTime.of(2026, 10, 22, 10, 0, 0, 0, BERLIN).toInstant().toEpochMilli()
const val START_ELAPSED_MS: Long = 5_000_000L
const val START_BOOT_COUNT: Int = 42

/**
 * Virtual monotonic and wall clocks (04 §2.6). [advance] moves both; the wall
 * clock and the zone can also be changed alone, as the user or the network can.
 */
class VirtualClocks(
    var elapsed: Long = START_ELAPSED_MS,
    var wall: Long = START_WALL_MS,
    var zoneId: ZoneId = BERLIN,
    var boot: Int = START_BOOT_COUNT,
) : MonotonicClock,
    WallClock {
    override fun elapsedMs(): Long = elapsed

    override fun bootCount(): Int = boot

    override fun nowMs(): Long = wall

    override fun zone(): ZoneId = zoneId

    fun advance(ms: Long) {
        elapsed += ms
        wall += ms
    }

    /** The phone was off for [offMs] and has been up for [upMs]: a new boot whose elapsed clock restarted. */
    fun reboot(offMs: Long, upMs: Long) {
        boot += 1
        wall += offMs + upMs
        elapsed = upMs
    }

    /** A reboot whose boot count could not be read: the elapsed clock restarted, the boot count did not change. */
    fun rebootUnnoticed(offMs: Long, upMs: Long) {
        wall += offMs + upMs
        elapsed = upMs
    }

    fun advanceSeconds(seconds: Long) = advance(seconds * 1000)

    fun now(): ClockReading = readClocks(this, this)
}

/** Drives the reducer like the controller will: one state, events in order, the result of the last event. */
class EngineDriver(val clocks: VirtualClocks = VirtualClocks(), var state: TimerState = TimerState.Idle) {
    var effects: List<TimerEffect> = emptyList()
        private set
    var outcome: TransitionOutcome? = null
        private set

    fun send(event: TimerEvent): List<TimerEffect> {
        val transition = BrewTimerEngine.reduce(state, event, clocks.now())
        state = transition.state
        effects = transition.effects
        outcome = transition.outcome
        return effects
    }

    fun display(): TimerDisplay? = BrewTimerEngine.display(state, clocks.now())

    /** Moves both clocks to [elapsedMs] on the monotonic clock. */
    fun advanceTo(elapsedMs: Long) = clocks.advance(elapsedMs - clocks.elapsed)

    val deadline: Long get() = (state as TimerState.Running).deadlineElapsedMs
}

const val SESSION_ID = "session-1"
val BREW = SessionKind.Brew(SESSION_ID)
val PRE_ALERT = PreAlertSettings(sessionOverrideSeconds = null, inheritedSeconds = 10)

/** `ScheduleAlarms` with nothing due: every alarm cancelled. */
val NO_ALARMS = TimerEffect.ScheduleAlarms(DueTimes.NONE)

/** `ScheduleAlarms` with only the deadline due: the pre-alert played or the step has none. */
fun deadlineAlarm(deadlineElapsedMs: Long) = TimerEffect.ScheduleAlarms(DueTimes(deadlineElapsedMs = deadlineElapsedMs))

/** The midnight timeout of a session idle from 10:00 to 18:00 on 22 Oct 2026 in Berlin: 23 Oct, 00:00 CEST. */
val MIDNIGHT_AFTER_START: Long = Instant.parse("2026-10-22T22:00:00Z").toEpochMilli()

/** `ScheduleAlarms` of a waiting brew session idle since [idleSinceWallMs] (before 18:00 on the start day). */
fun waitingAlarms(idleSinceWallMs: Long) = TimerEffect.ScheduleAlarms(
    DueTimes(idleDemoteAtWallMs = idleSinceWallMs + 30 * 60_000L, midnightAtWallMs = MIDNIGHT_AFTER_START),
)

/** `ScheduleAlarms` of an alerting step: its overtime timeout 10 min after the deadline. */
fun alertingAlarms(deadlineElapsedMs: Long) =
    TimerEffect.ScheduleAlarms(DueTimes(overtimeTimeoutAtElapsedMs = deadlineElapsedMs + 10 * 60_000L))

/** `ScheduleAlarms` of a running step with the default 10 s pre-alert still to play. */
fun runningAlarms(deadlineElapsedMs: Long) = TimerEffect.ScheduleAlarms(
    DueTimes(preAlertAtElapsedMs = deadlineElapsedMs - 10_000, deadlineElapsedMs = deadlineElapsedMs),
)

fun rinse(seconds: Int) = PlannedStep(StepKind.RINSE, seconds, tempC = 70)

fun infusion(seconds: Int, tempC: Int? = 60) = PlannedStep(StepKind.INFUSION, seconds, tempC)

fun whisk(seconds: Int) = PlannedStep(StepKind.WHISK, seconds, tempC = 80)

fun startSession(vararg steps: PlannedStep) =
    TimerEvent.StartSession(SESSION_ID, steps.toList(), PRE_ALERT, longStepsOnly = false)

/** A driver whose session with [steps] has just started (Ready(0)). */
fun sessionWith(vararg steps: PlannedStep): EngineDriver = EngineDriver().apply { send(startSession(*steps)) }

fun EngineDriver.recordOf(stepIndex: Int): StepRecord = (state as TimerState.Active).session.steps[stepIndex].record!!
