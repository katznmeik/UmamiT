package com.umamit.core.data

import com.umamit.core.common.ClockReading
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.TimerEffect
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerState
import java.time.ZoneId
import java.time.ZonedDateTime

val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

/** 2026-10-22 10:00 in Berlin. */
val START_WALL_MS: Long = ZonedDateTime.of(2026, 10, 22, 10, 0, 0, 0, BERLIN).toInstant().toEpochMilli()

/** Virtual clocks for the engine: [advanceSeconds] moves the monotonic and the wall clock together. */
class TestClocks(var elapsed: Long = 5_000_000L, var wall: Long = START_WALL_MS, var zone: ZoneId = BERLIN) {
    var boot: Int = 42

    fun now() = ClockReading(elapsed, wall, zone, boot)

    fun advanceSeconds(seconds: Long) {
        elapsed += seconds * 1000
        wall += seconds * 1000
    }

    /** The phone was off for [offMs] and has been up for [upMs]. */
    fun reboot(offMs: Long, upMs: Long) {
        boot += 1
        wall += offMs + upMs
        elapsed = upMs
    }
}

/**
 * Drives the engine like the M1-09 controller: each accepted transition's data
 * effects and its `Persist` go through the repositories in one transaction
 * (04 §2.3, §7.1). [start] is what the controller holds for `CreateSession`.
 * `Persist` writes one slot only (umamit.db v4, D-098): [slot], or without one
 * the slot of the timer's kind (the brew's until a timer ran).
 */
class TimerDriver(
    private val data: TestDatabase,
    val clocks: TestClocks = TestClocks(),
    private val slot: TimerSlot? = null,
    private val start: (sessionId: String, steps: List<PlannedStep>, preAlertSeconds: Int) -> SessionStart =
        { id, steps, preAlert -> sessionStart(id, steps, preAlert, clocks) },
) {
    var state: TimerState = TimerState.Idle
        private set

    private var pendingStart: TimerEvent.StartSession? = null

    /** The slot `Persist` writes; it follows the timer's kind unless a [slot] is given. */
    private var persistSlot: TimerSlot = slot ?: TimerSlot.BREW

    suspend fun send(event: TimerEvent): TimerState {
        if (event is TimerEvent.StartSession) pendingStart = event
        val transition = BrewTimerEngine.reduce(state, event, clocks.now())
        state = transition.state
        data.transactions.inTransaction {
            transition.effects.forEach { effect -> execute(effect, transition.state) }
        }
        return state
    }

    private suspend fun execute(effect: TimerEffect, newState: TimerState) {
        when (effect) {
            is TimerEffect.CreateSession -> {
                val event = checkNotNull(pendingStart)
                data.sessions.startSession(start(effect.sessionId, event.steps, event.preAlert.inheritedSeconds))
            }

            is TimerEffect.ReplacePendingSteps ->
                data.sessions.replacePendingSteps(effect.sessionId, effect.fromIndex, effect.steps)

            is TimerEffect.RecordStep -> data.sessions.recordStep(effect.sessionId, effect.stepIndex, effect.record)

            is TimerEffect.CompleteSession -> data.sessions.completeSession(effect.sessionId, effect.autoFinished)

            is TimerEffect.DiscardSession -> data.sessions.discardSession(effect.sessionId)

            TimerEffect.Persist -> {
                val snapshot = BrewTimerEngine.snapshot(newState)
                if (slot == null) snapshot?.let { persistSlot = it.activeBrew.slot }
                data.activeBrew.persist(persistSlot, snapshot)
            }

            else -> Unit // alarms, sound, service and notification belong to M1-09
        }
    }
}

const val SESSION_ID = "session-1"

fun rinse(seconds: Int) = PlannedStep(StepKind.RINSE, seconds, tempC = 70)

/** An infusion with its own [preAlert]: null *Automatisch*, 0 *Aus*, else the lead (TMR-23, D-093). */
fun infusion(seconds: Int, tempC: Int? = 60, preAlert: Int? = null) =
    PlannedStep(StepKind.INFUSION, seconds, tempC, preAlert)

fun startEvent(vararg steps: PlannedStep, id: String = SESSION_ID, preAlert: Int = 10) = TimerEvent.StartSession(
    id,
    steps.toList(),
    PreAlertSettings.inherited(recipeSeconds = null, globalSeconds = preAlert),
    longStepsOnly = false,
)

/** The snapshot of tea „t1" with 4 g in the „Kyusu" recipe. */
fun snapshotOf(teaId: String? = "t1", leafGrams: Double? = 4.0) = SessionSnapshot(
    teaId = teaId,
    teaName = "Bio Sencha Hisui",
    teaSecondaryName = "Hisui",
    teaType = "Sencha",
    styleKey = "sencha",
    category = TeaCategory.GREEN,
    profileId = null,
    profileName = "Kyusu",
    method = BrewMethod.KYUSU,
    vesselId = null,
    vesselName = "Kyusu",
    leafGrams = leafGrams,
    waterMl = 200,
    baseTempC = 60,
)

fun sessionStart(
    id: String,
    steps: List<PlannedStep>,
    preAlertSeconds: Int,
    clocks: TestClocks,
    snapshot: SessionSnapshot = snapshotOf(),
) = SessionStart(
    id = id,
    snapshot = snapshot,
    steps = steps,
    preAlertSeconds = preAlertSeconds,
    startedAtMs = clocks.wall,
    zoneId = clocks.zone.id,
)
