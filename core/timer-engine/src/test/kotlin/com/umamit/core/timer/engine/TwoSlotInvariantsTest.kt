package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerSlot
import org.junit.Test
import kotlin.random.Random

/**
 * The seeded invariant test of 04 §12 for two slots with the alert arbitration of 04 §9.3 (D-098): a brew
 * session and a quick timer side by side, random user events to either slot, the due events of both (the brew's
 * first, the quick slot's through [AlertArbitration.quickEvent]), the player's reports, process deaths and
 * reboots; after every event the controller's [AlertArbitration.settle] and [PlayerSound.release], as each turn
 * does. An independent player oracle checks the sound: never two sounds, the brew's alert never cut by the quick
 * slot, a pre-alert never over another slot's sound. And the states: the brew never held, never two slots
 * ringing, a held quick alert always rings before its silent end, every slot's due event handled.
 */
class TwoSlotInvariantsTest {

    @Test
    fun `random two-timer sequences keep the arbitration invariants`() {
        repeat(RUNS) { seed -> Run(Random(seed), seed).go() }

        assertWithMessage("arbitration events accepted").that(accepted)
            .containsAtLeast("AlertHold", "AlertRingStart", "RecoverHeld", "AlertCompleted", "OvertimeTimeout")
        assertWithMessage("situations reached").that(situations).containsAtLeast(
            "preemption",
            "held at deadline",
            "released",
            "pre-alert waited, then played",
            "pre-alert waited, then consumed",
            "quick silent end after ringing",
            "both running",
        )
    }

    private val accepted = mutableSetOf<String>()
    private val situations = mutableSetOf<String>()

    /** What the one player physically sounds, from the commands the controller would send it. */
    private sealed interface Playing {
        val slot: TimerSlot

        data class Alert(override val slot: TimerSlot, val sinceMs: Long) : Playing

        data class PreAlert(override val slot: TimerSlot, val untilMs: Long) : Playing
    }

    private inner class Run(private val random: Random, private val seed: Int) {
        private val clocks = VirtualClocks()
        private val states = mutableMapOf<TimerSlot, TimerState>(TimerSlot.BREW to TimerState.Idle)
        private var sound = PlayerSound(RING_MS)
        private var playing: Playing? = null
        private var n = 0
        private var label = ""

        init {
            states[TimerSlot.QUICK] = TimerState.Idle
        }

        private val brew get() = states.getValue(TimerSlot.BREW)
        private val quick get() = states.getValue(TimerSlot.QUICK)

        fun go() {
            repeat(EVENTS_PER_RUN) {
                n = it
                advanceClock()
                when (val pick = random.nextInt(100)) {
                    in 0..5 -> send(TimerSlot.BREW, randomSession())
                    in 6..9 -> send(TimerSlot.QUICK, randomQuick())
                    in 10..13 -> quickAlongside()
                    in 14..40 -> send(randomSlot(), userEvents.random(random))
                    in 41..60 -> dueEvents()
                    in 61..75 -> onTime()
                    in 76..88 -> playerReports()
                    in 89..94 -> processRestart(reboot = false)
                    else -> processRestart(reboot = pick == 99)
                }
                checkStates()
            }
        }

        /** The due events of both slots, the brew's first, as the alarm and the wake-up deliver them (04 §4). */
        private fun dueEvents() {
            TimerSlot.entries.forEach { slot ->
                BrewTimerEngine.dueEvents(states.getValue(slot), clocks.now()).forEach { send(slot, it) }
            }
        }

        /**
         * The next alarm or wake-up comes on time: the clock moves to the earliest due time of either slot, or to
         * the end of a pre-alert another one waits for, and that turn runs (04 §4).
         */
        private fun onTime() {
            val next = TimerSlot.entries.flatMap { slot ->
                val due = BrewTimerEngine.dueTimes(states.getValue(slot))
                listOfNotNull(due.preAlertAtElapsedMs, due.deadlineElapsedMs, due.overtimeTimeoutAtElapsedMs)
            }.plus(listOfNotNull(sound.nextReleaseAtElapsedMs())).filter { it >= clocks.elapsed }.minOrNull()
            next?.let { clocks.advance(it - clocks.elapsed + random.nextLong(0, 3)) }
            dueEvents()
            release()
        }

        /**
         * Start's *Schnelltimer* while a brew step runs, ending within a few seconds of it, so the two timers'
         * pre-alerts and deadlines meet (S1).
         */
        private fun quickAlongside() {
            val running = brew as? TimerState.Running ?: return send(TimerSlot.QUICK, randomQuick())
            val left = ((running.deadlineElapsedMs - clocks.elapsed) / 1_000).toInt()
            val seconds = (left + random.nextInt(-4, 5)).coerceIn(15, 3_000)
            send(
                TimerSlot.QUICK,
                TimerEvent.StartQuickTimer(seconds, globalPreAlertSeconds = 10, preAlertSeconds = null),
            )
            send(TimerSlot.QUICK, TimerEvent.Start)
        }

        /** The player ran an alert's whole cycle and reports it (at least 15 s, Normal). */
        private fun playerReports() {
            val alert = playing as? Playing.Alert ?: return
            if (clocks.elapsed - alert.sinceMs < ALERT_CYCLE_MS) return
            playing = null
            sound.alertEnded(alert.slot)
            send(alert.slot, TimerEvent.AlertCompleted)
        }

        /** The process died (and maybe the phone rebooted): a new one recovers both slots, the brew first. */
        private fun processRestart(reboot: Boolean) {
            if (reboot) clocks.reboot(offMs = random.nextLong(0, 900_000), upMs = 30_000)
            sound = PlayerSound(RING_MS)
            playing = null
            send(TimerSlot.BREW, TimerEvent.Recover)
            send(TimerSlot.QUICK, TimerEvent.Recover)
        }

        /** One event of [slot] in a turn, then the arbitration and the waiting pre-alerts, as the controller does. */
        private fun send(slot: TimerSlot, original: TimerEvent) {
            val event = if (slot == TimerSlot.QUICK) AlertArbitration.quickEvent(original, brew) else original
            if (event != original && event == TimerEvent.AlertHold && quick is TimerState.Running) {
                situations += "held at deadline"
            }
            apply(slot, event)
            AlertArbitration.settle(brew, quick)?.let { settle ->
                if (settle == TimerEvent.AlertHold) situations += "preemption"
                if (settle == TimerEvent.AlertRingStart) situations += "released"
                apply(TimerSlot.QUICK, settle)
            }
            release()
        }

        private fun apply(slot: TimerSlot, event: TimerEvent) {
            val before = states.getValue(slot)
            val now = clocks.now()
            val transition = BrewTimerEngine.reduce(before, event, now)
            label = "seed $seed, event $n: $event to $slot in ${before::class.simpleName}"
            if (transition.outcome != TransitionOutcome.Accepted) return
            accepted += event::class.simpleName.orEmpty()
            states[slot] = transition.state
            checkSilentEnd(slot, before, event, transition.state)
            transition.effects.forEach { effect -> play(slot, effect, transition.state) }
            checkSnapshot(slot)
        }

        /** The controller's side of the player for one effect of [slot], against the oracle. */
        private fun play(slot: TimerSlot, effect: TimerEffect, state: TimerState) {
            val now = clocks.elapsed
            when (effect) {
                TimerEffect.StartAlert -> {
                    val other = sounding()?.takeIf { it.slot != slot }
                    if (other is Playing.Alert) {
                        assertWithMessage("$label: only the brew's alert takes the player from the other's alert")
                            .that(slot).isEqualTo(TimerSlot.BREW)
                    }
                    if (slot == TimerSlot.QUICK) {
                        assertWithMessage("$label: a quick alert never starts while the brew alerts")
                            .that(brew).isNotInstanceOf(TimerState.Alerting::class.java)
                    }
                    sound.alertStarted(slot)
                    playing = Playing.Alert(slot, now)
                }

                TimerEffect.StopAlert -> {
                    val other = sounding()?.takeIf { it.slot != slot }
                    val stops = sound.mayStop(slot, now)
                    assertWithMessage("$label: a stop never silences the other slot's sound")
                        .that(stops).isEqualTo(other == null)
                    if (stops) playing = null
                }

                TimerEffect.PlayPreAlert -> {
                    val deadline = (state as TimerState.Running).deadlineElapsedMs
                    if (sound.preAlertDue(slot, deadline, now)) {
                        startPreAlert(slot, deadline)
                    } else {
                        assertWithMessage("$label: a pre-alert waits only for the other slot's sound")
                            .that(sounding()?.slot).isEqualTo(other(slot))
                        waited += slot
                    }
                }

                else -> Unit
            }
        }

        private val waited = mutableSetOf<TimerSlot>()

        /** The waiting pre-alerts whose sound ended, as at the end of every turn. */
        private fun release() {
            val now = clocks.elapsed
            val before = sound.waitingPreAlerts.map { it.slot }.toSet()
            sound.release(now) { (states.getValue(it) as? TimerState.Running)?.deadlineElapsedMs }.forEach {
                situations += "pre-alert waited, then played"
                startPreAlert(it.slot, it.deadlineElapsedMs)
            }
            val gone = before - sound.waitingPreAlerts.map { it.slot }.toSet()
            if (gone.any { slot -> (playing as? Playing.PreAlert)?.slot != slot }) {
                situations += "pre-alert waited, then consumed"
            }
        }

        private fun startPreAlert(slot: TimerSlot, deadline: Long) {
            val now = clocks.elapsed
            val other = sounding()
            assertWithMessage("$label: a pre-alert never plays over another sound")
                .that(other == null || other.slot == slot).isTrue()
            assertWithMessage("$label: a pre-alert plays at least 1 s before its deadline")
                .that(deadline - now).isAtLeast(PRE_ALERT_MIN_LEFT_MS)
            playing = Playing.PreAlert(slot, now + RING_MS)
        }

        /** The oracle's sound now; a pre-alert has rung out after [RING_MS]. */
        private fun sounding(): Playing? =
            playing?.takeUnless { it is Playing.PreAlert && it.untilMs <= clocks.elapsed }

        /** A quick timer that could not ring never ends silently; a ringing one only 10 min after it began. */
        private fun checkSilentEnd(slot: TimerSlot, before: TimerState, event: TimerEvent, after: TimerState) {
            if (slot != TimerSlot.QUICK || after != TimerState.Idle || event in endingUserEvents) return
            when (before) {
                is TimerState.Alerting -> {
                    assertWithMessage("$label: a held quick alert never ends silently").that(before.held).isFalse()
                    // On the wall clock, which survives a reboot (the wall moves only forward in this test).
                    val ringingFor = clocks.wall - checkNotNull(before.ringingSinceWallMs)
                    assertWithMessage("$label: a quick alert ends silently only 10 min after it began ringing")
                        .that(ringingFor).isAtLeast(OVERTIME_TIMEOUT_MS)
                    situations += "quick silent end after ringing"
                }

                is TimerState.Running -> assertWithMessage(
                    "$label: a late quick deadline ends it only if it could ring",
                )
                    .that(brew).isNotInstanceOf(TimerState.Alerting::class.java)

                else -> Unit
            }
        }

        private fun checkStates() {
            val brewAlert = brew as? TimerState.Alerting
            assertWithMessage("$label: the brew is never held").that(brewAlert?.held ?: false).isFalse()
            val quickRings = (quick as? TimerState.Alerting)?.held == false
            assertWithMessage("$label: never two slots ringing").that(brewAlert != null && quickRings).isFalse()
            assertWithMessage("$label: a held quick alert waits only for the brew's")
                .that((quick as? TimerState.Alerting)?.held == true && brewAlert == null).isFalse()
            if (brew is TimerState.Running && quick is TimerState.Running) situations += "both running"
            checkNothingOverdue()
        }

        /** Every slot's due event is accepted (as the quick slot gets it), so no wake-up spins (D-048). */
        private fun checkNothingOverdue() {
            val now = clocks.now()
            TimerSlot.entries.forEach { slot ->
                val state = states.getValue(slot)
                if (state is TimerState.Active && state.session.bootCount != now.bootCount) return@forEach
                BrewTimerEngine.dueEvents(state, now).forEach { due ->
                    val event = if (slot == TimerSlot.QUICK) AlertArbitration.quickEvent(due, brew) else due
                    assertWithMessage("$label: the due $event of $slot is accepted")
                        .that(BrewTimerEngine.reduce(state, event, now).outcome).isEqualTo(TransitionOutcome.Accepted)
                }
            }
        }

        private fun checkSnapshot(slot: TimerSlot) {
            val state = states.getValue(slot)
            assertWithMessage("$label: snapshot round trip of $slot")
                .that(BrewTimerEngine.restore(BrewTimerEngine.snapshot(state))).isEqualTo(state)
        }

        private fun advanceClock() {
            val targets = TimerSlot.entries.flatMap { slot ->
                val due = BrewTimerEngine.dueTimes(states.getValue(slot))
                listOfNotNull(due.preAlertAtElapsedMs, due.deadlineElapsedMs, due.overtimeTimeoutAtElapsedMs)
            }.filter { it >= clocks.elapsed }
            when {
                targets.isNotEmpty() && random.nextInt(3) == 0 ->
                    clocks.advance((targets.random(random) + random.nextLong(-1, 2) - clocks.elapsed).coerceAtLeast(0))

                random.nextInt(10) == 0 -> clocks.advance(random.nextLong(5 * 60_000, 15 * 60_000))

                random.nextInt(4) == 0 -> clocks.advance(random.nextLong(0, 4_000))

                else -> clocks.advance(random.nextLong(0, 40_000))
            }
        }

        private fun randomSlot() = if (random.nextBoolean()) TimerSlot.BREW else TimerSlot.QUICK

        private fun randomSession() = TimerEvent.StartSession(
            "session-${random.nextInt()}",
            List(random.nextInt(1, 4)) {
                PlannedStep(StepKind.INFUSION, random.nextInt(15, 120), tempC = 60, randomPreAlert())
            },
            PRE_ALERT,
            longStepsOnly = false,
        )

        private fun randomQuick() = TimerEvent.StartQuickTimer(
            durationSeconds = random.nextInt(15, 120),
            globalPreAlertSeconds = 10,
            preAlertSeconds = randomPreAlert(),
        )

        private fun randomPreAlert(): Int? = when (random.nextInt(3)) {
            0 -> null
            1 -> 5
            else -> random.nextInt(1, 7) * 5
        }

        private fun other(slot: TimerSlot) = if (slot == TimerSlot.BREW) TimerSlot.QUICK else TimerSlot.BREW
    }

    private val userEvents = listOf(
        TimerEvent.Start, TimerEvent.Start, TimerEvent.Pause, TimerEvent.Resume, TimerEvent.Poured,
        TimerEvent.Poured, TimerEvent.Cancel, TimerEvent.EndStepNow, TimerEvent.SkipStep, TimerEvent.AddStep,
        TimerEvent.FinishSession, TimerEvent.Discard,
    )

    /** The user events that end a quick timer on purpose (*Fertig*, *Abbrechen*). */
    private val endingUserEvents = setOf(TimerEvent.Poured, TimerEvent.Cancel)

    private companion object {
        const val RUNS = 300
        const val EVENTS_PER_RUN = 120
        const val RING_MS = 3_450L
        const val ALERT_CYCLE_MS = 15_000L
    }
}
